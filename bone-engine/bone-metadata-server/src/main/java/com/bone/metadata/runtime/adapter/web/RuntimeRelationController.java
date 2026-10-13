package com.bone.metadata.runtime.adapter.web;

import com.bone.core.model.ApiResponse;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeRelationSpec;
import com.bone.metadata.engine.runtime.RuntimeRelationSupport;
import com.bone.metadata.runtime.application.ChildAmountAggregator;
import com.bone.metadata.runtime.application.RuntimeAccessGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * G10 运行时关系端点：引用候选（lookup）、子表（hasMany）CRUD。
 *
 * <p><b>路径形状遵循「动态资源」原则</b>（Runtime 设计 §4）：一切以 {@code entityCode} + 字段编码为键，不为任何业务开名词路径。前端换场景只换 code，
 * 不换路由模板。
 *
 * <p><b>与 {@link RuntimeRecordController} 的分工</b>：本类只管「关系」——引用候选与子表行；单表CRUD 仍在 {@code
 * RuntimeRecordController}，避免把 记录与关系混在一个控制器里让权限与校验规则互相干扰。
 */
@RestController
@RequestMapping("/api/v1/runtime/entities/{entityCode}")
@RequiredArgsConstructor
public class RuntimeRelationController {

  private final RuntimeEntityCatalog runtimeEntityCatalog;
  private final RuntimeRelationSupport relationSupport;
  private final JdbcRuntimeRecordService runtimeRecordService;
  private final RuntimeAccessGuard accessGuard;
  private final TenantProvider tenantProvider;
  private final TransactionTemplate transactionTemplate;
  private final ChildAmountAggregator childAmountAggregator;

  /**
   * 引用候选列表：给关联选择器用。
   *
   * <p><b>为什么单独一个端点而不把 label 嵌进列表</b>：候选是「按需、限量、带关键字」的查询，与记录列表的分页语义完全不同；且权限上候选读取应能独立
   * 收口。前端在选择器展开时调用，未展开则零请求。
   */
  @GetMapping("/lookup-options")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<List<Map<String, Object>>> lookupOptions(
      @PathVariable String entityCode,
      @RequestParam String field,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "50") int limit) {
    accessGuard.requireRead(entityCode);
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity entity = requireEntity(entityCode, tenantId);
    RuntimeFieldColumn col = requireField(entity, field);
    List<Map<String, Object>> options =
        relationSupport.lookupOptions(entity, col, tenantId, q, limit, currentOperator());
    String pk = entity.primaryKeyColumn();
    RuntimeRelationSpec spec = RuntimeRelationSpec.of(col);
    List<Map<String, Object>> shaped = new ArrayList<>(options.size());
    for (Map<String, Object> row : options) {
      Map<String, Object> item = new java.util.LinkedHashMap<>();
      Object idValue = row.get(pk);
      item.put("value", idValue);
      // label 取自目标实体的 labelField（缺省 id）——候选行是目标表原始行，故直接按逻辑列名取
      Object label = row.get(spec.labelField());
      item.put("label", label != null ? label : idValue);
      shaped.add(item);
    }
    return ApiResponse.success(shaped);
  }

  /** 列出一行父记录的全部子行。 */
  @GetMapping("/records/{id}/children/{childEntityCode}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
  public ApiResponse<List<Map<String, Object>>> listChildren(
      @PathVariable String entityCode,
      @PathVariable String id,
      @PathVariable String childEntityCode) {
    accessGuard.requireRead(entityCode);
    accessGuard.requireRead(childEntityCode);
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity parent = requireEntity(entityCode, tenantId);
    RuntimeRelationSpec spec = requireChildSpec(parent, childEntityCode);
    // v0.50：子表可见性由父行行级 ACL 控制——非本人/非审批人/非管理员访问他人单据明细 → 404
    // （不泄露行存在性；与主行 getById 同口径）。豁免：管理员（null）/ system 回调 / 非数值主体。
    runtimeRecordService.assertRowOwner(parent, tenantId, id, currentOperator());
    List<Map<String, Object>> rows = relationSupport.listChildren(parent, tenantId, id, spec);
    PublishedRuntimeEntity child = requireEntity(childEntityCode, tenantId);
    relationSupport.enrichReferenceLabels(child, tenantId, rows);
    return ApiResponse.success(rows);
  }

  /**
   * 批量替换子表行（父记录表单一次性提交明细）。
   *
   * <p><b>整个替换在一个事务内</b>：父行存在性校验 + 子行增删改 + 孤儿软删必须原子——中途失败留下半截子表比直接报错更难收拾。
   *
   * <p><b>明细金额聚合</b>：同一事务内，子行替换完成后按场景约定（如报销单）把子行金额合计回填父记录金额字段 （服务端计算为准，见 {@link
   * ChildAmountAggregator}）；回填值经响应体 {@code aggregatedAmount} 透出，前端以此对齐展示。
   */
  @PostMapping("/records/{id}/children/{childEntityCode}")
  @PreAuthorize("hasAnyAuthority('metadata:runtime:write', 'metadata:write')")
  public ApiResponse<List<Map<String, Object>>> replaceChildren(
      @PathVariable String entityCode,
      @PathVariable String id,
      @PathVariable String childEntityCode,
      @RequestBody List<Map<String, Object>> rows) {
    accessGuard.requireWrite(entityCode);
    accessGuard.requireWrite(childEntityCode);
    long tenantId = tenantProvider.currentTenantId();
    PublishedRuntimeEntity parent = requireEntity(entityCode, tenantId);
    RuntimeRelationSpec spec = requireChildSpec(parent, childEntityCode);
    // aggregateAndBackfill 在事务 lambda 内调用，结果经单元素容器带出（TransactionStatus 无法传出事务外）
    final java.math.BigDecimal[] aggregatedAmount = {null};
    List<Map<String, Object>> result =
        transactionTemplate.execute(
            status -> {
              // v0.50：子表替换同样受父行行级 ACL 约束（与 listChildren 同口径），防「伪造他人单据明细」旁路
              runtimeRecordService.assertRowOwner(parent, tenantId, id, currentOperator());
              List<Map<String, Object>> replaced =
                  relationSupport.replaceChildren(parent, tenantId, id, spec, rows);
              // 同事务内聚合回填（报销等已登记场景）；失败抛异常 → 明细替换一并回滚，杜绝「明细已替换、头表未更新」
              aggregatedAmount[0] =
                  childAmountAggregator.aggregateAndBackfill(parent, tenantId, id, childEntityCode);
              return replaced;
            });
    PublishedRuntimeEntity child = requireEntity(childEntityCode, tenantId);
    relationSupport.enrichReferenceLabels(child, tenantId, result);
    // 聚合回填已在事务内完成；响应保持 List 契约不变，前端保存明细成功后 re-fetch 主记录对齐头表金额展示
    // （避免为带一个标量破坏 GET /records/{id} 的既有缓存与 ETag 语义）
    return ApiResponse.success(result);
  }

  /** 取子表在某父记录下的关系声明（按子实体编码定位；多子表并存时以 encoding 唯一标识）。 */
  private RuntimeRelationSpec requireChildSpec(
      PublishedRuntimeEntity parent, String childEntityCode) {
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

  private PublishedRuntimeEntity requireEntity(String entityCode, long tenantId) {
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
   * 行级 ACL 操作者解析（与 {@code RuntimeRecordController.currentOperator} 同口径）：null=管理员/匿名豁免，
   * "system"=系统回调豁免，数值=本人，非数值主体交下层按不可比跳过。子表端点复用主行行级 ACL。
   */
  private static String currentOperator() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !auth.isAuthenticated()
        || auth
            instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
      return null;
    }
    Object details = auth.getDetails();
    if (details instanceof Number) {
      return details.toString();
    }
    if (details instanceof String s && s.matches("\\d+")) {
      return s;
    }
    String name = auth.getName();
    if (name != null && name.matches("\\d+")) {
      return name;
    }
    // 主体名为非数值（如 username）时仍返回，交由下层按数值校验跳过 BIGINT 列
    return name;
  }
}
