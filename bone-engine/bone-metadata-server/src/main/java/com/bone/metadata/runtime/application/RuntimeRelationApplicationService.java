package com.bone.metadata.runtime.application;

import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeRelationSpec;
import com.bone.metadata.engine.runtime.RuntimeRelationSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 运行时关系应用服务（G10：引用候选 lookup、子表 hasMany CRUD）。
 *
 * <p><b>为什么存在</b>（P2-1）：原 {@code RuntimeRelationController} 直注 {@link RuntimeRelationSupport}
 * （engine.runtime 持久化组件）并内联事务编排与行级 ACL 判定——这正是「adapter 不得 depend on engine 运行时持久化服务」门禁（{@code
 * BoneDddArchRules#adapterMustNotDependOnEngineRuntimePersistence}）要拦的 形态：god-object 规则只查 {@code
 * ..application..} 包，engine 服务从谓词缝隙绕过。本类把关系编排收敛到 application 层后，控制器只留 HTTP 适配。
 *
 * <p><b>事务边界</b>：子表替换（父行 ACL 断言 + 子行增删改 + 金额聚合回填）必须原子——中途失败留下半截子表 比直接报错更难收拾，故 {@code
 * replaceChildren} 自带 {@link TransactionTemplate} 边界（编排属应用层， 适配层不持事务模板）。
 */
@Service
@RequiredArgsConstructor
public class RuntimeRelationApplicationService {

  private final RuntimeEntityCatalog runtimeEntityCatalog;
  private final RuntimeRelationSupport relationSupport;
  private final RuntimeRecordApplicationService recordService;
  private final ChildAmountAggregator childAmountAggregator;
  private final TransactionTemplate transactionTemplate;
  private final TenantProvider tenantProvider;

  /** 引用候选列表（给关联选择器用）：按目标实体限量查询 + 当前操作者口径（行级 ACL 由引擎侧按 operator 过滤，管理员/匿名传 null 豁免）。 */
  public List<Map<String, Object>> lookupOptions(
      String entityCode, String field, String q, int limit) {
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity entity = requireEntity(entityCode, tenantId);
    RuntimeFieldColumn col = requireField(entity, field);
    return relationSupport.lookupOptions(
        entity, col, tenantId, q, limit, recordService.currentOperator());
  }

  /**
   * 列出一行父记录的全部子行（含引用标签补全）。
   *
   * <p>可见性由父行行级 ACL 控制（v0.50）：非本人/非审批人/非管理员访问他人单据明细 → 404（不泄露行存在性； 与主行 getById 同口径）。豁免：管理员（null）/
   * system 回调 / 非数值主体。
   */
  public List<Map<String, Object>> listChildren(
      String entityCode, String id, String childEntityCode) {
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity parent = requireEntity(entityCode, tenantId);
    RuntimeRelationSpec spec = requireChildSpec(parent, childEntityCode);
    recordService.assertRowOwner(parent, tenantId, id);
    List<Map<String, Object>> rows = relationSupport.listChildren(parent, tenantId, id, spec);
    PublishedRuntimeEntity child = requireEntity(childEntityCode, tenantId);
    relationSupport.enrichReferenceLabels(child, tenantId, rows);
    return rows;
  }

  /**
   * 批量替换子表行（父记录表单一次性提交明细）+ 同事务金额聚合回填。
   *
   * <p>回填发生在事务内（头表金额字段由 {@link ChildAmountAggregator} 直接写库）；响应保持 {@code List} 契约不变——前端保存明细成功后
   * re-fetch 主记录对齐头表金额展示（避免为带一个标量破坏 GET /records/{id} 的既有缓存与 ETag 语义）。回填失败抛异常 →
   * 明细替换一并回滚，杜绝「明细已替换、头表未更新」。
   */
  public List<Map<String, Object>> replaceChildren(
      String entityCode, String id, String childEntityCode, List<Map<String, Object>> rows) {
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity parent = requireEntity(entityCode, tenantId);
    RuntimeRelationSpec spec = requireChildSpec(parent, childEntityCode);
    List<Map<String, Object>> replaced =
        transactionTemplate.execute(
            status -> {
              // v0.50：子表替换同样受父行行级 ACL 约束（与 listChildren 同口径），防「伪造他人单据明细」旁路
              recordService.assertRowOwner(parent, tenantId, id);
              List<Map<String, Object>> result =
                  relationSupport.replaceChildren(parent, tenantId, id, spec, rows);
              // 同事务内聚合回填（报销等已登记场景）
              childAmountAggregator.aggregateAndBackfill(
                  parent, tenantId, id, childEntityCode, spec);
              return result;
            });
    PublishedRuntimeEntity child = requireEntity(childEntityCode, tenantId);
    relationSupport.enrichReferenceLabels(child, tenantId, replaced);
    return replaced;
  }

  PublishedRuntimeEntity requireEntity(String entityCode, long tenantId) {
    return runtimeEntityCatalog
        .findPublishedRuntime(entityCode, tenantId)
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND,
                    "未找到已发布的 RUNTIME 实体: " + entityCode));
  }

  private RuntimeFieldColumn requireField(PublishedRuntimeEntity entity, String field) {
    return entity.columns().stream()
        .filter(c -> c.code().equals(field))
        .findFirst()
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_INVALID_QUERY, "字段不存在: " + field));
  }

  /**
   * 取子表在某父记录下的关系声明（按子实体编码定位；多子表并存时以 encoding 唯一标识）。
   *
   * <p>包级可见：控制器在需要 {@link RuntimeRelationSpec} 做响应整形（如 label 键名）时复用，避免第二份 「父→子」声明查找逻辑漂移。
   */
  RuntimeRelationSpec requireChildSpec(PublishedRuntimeEntity parent, String childEntityCode) {
    RuntimeRelationSpec matched =
        parent.columns().stream()
            .map(RuntimeRelationSpec::of)
            .filter(s -> s.isChildTable() && childEntityCode.equals(s.childEntityCode()))
            .findFirst()
            .orElse(null);
    if (matched == null) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID,
          "实体「" + parent.entityCode() + "」未声明指向子实体「" + childEntityCode + "」的 CHILD_TABLE 字段");
    }
    return matched;
  }

  /** label 兜底键：候选行按目标实体 labelField 取值，缺省 id（与引擎侧口径一致）。 */
  public static Map<String, Object> shapeOption(Object idValue, Object label) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("value", idValue);
    item.put("label", label != null ? label : idValue);
    return item;
  }
}
