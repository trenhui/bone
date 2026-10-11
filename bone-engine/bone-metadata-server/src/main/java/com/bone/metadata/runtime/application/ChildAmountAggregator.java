package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 子表明细金额聚合回填（G10 配套，报销场景 §3「头表金额 = 明细合计（服务端计算）」契约）。
 *
 * <p><b>职责</b>：子表（CHILD_TABLE）整组替换完成后，把子行 {@code amount} 求和回填到父记录的
 * 金额字段。服务器端计算为准——前端展示值仅作即时反馈，保存时一律以本聚合结果覆盖头表， 防止「改明细不改头表」或前端篡改头表金额的两类不一致。
 *
 * <p><b>为什么做成独立组件而不塞进 {@code RuntimeRelationSupport}</b>：聚合规则（哪个子字段、
 * 回填哪个父字段）是<strong>场景约定</strong>而非平台机制；平台层保持通用（逐行 upsert + 孤儿软删）， 场景层按需注册。组件按「父实体 code + 子实体
 * code」匹配，未匹配的子表调用零开销直通。
 *
 * <p><b>事务边界</b>：由调用方（{@code RuntimeRelationController#replaceChildren}）的 {@code
 * transactionTemplate} 包裹——聚合失败抛异常即整体回滚，不会出现「明细已替换、头表未更新」 的半截状态。
 *
 * <p><b>状态保护</b>：与 {@link ExpenseClaimWriteHook} 同口径——审批链上（SUBMITTED 及之后）
 * 不回填，避免「审批通过后靠改明细把金额改大」的绕过路径；抛 422 与主行写路径一致。
 *
 * <p><b>审计</b>：回填经 {@code JdbcRuntimeRecordService#update} 公共写链执行——普通字段校验、 乐观锁、from→to
 * 逐字段数据面审计（source=update）全部复用，本组件只算不改。
 *
 * <p><b>空明细语义</b>：明细清空时 SUM=0，回填受父字段 min:0.01 校验约束（0031 模板）抛 422—— 没有明细的报销单金额为 0 本身违反
 * RC-007，显式失败优于静默落库。
 */
@Component
public class ChildAmountAggregator {

  /** 父实体 → (子实体, 父金额字段, 子金额字段) 的场景约定。 */
  private static final Map<String, AggSpec> SPECS =
      Map.of("expense_claim", new AggSpec("expense_claim_item", "amount", "amount"));

  private final NamedParameterJdbcTemplate jdbc;
  private final com.bone.metadata.engine.runtime.JdbcRuntimeRecordService recordService;

  public ChildAmountAggregator(
      NamedParameterJdbcTemplate jdbc,
      com.bone.metadata.engine.runtime.JdbcRuntimeRecordService recordService) {
    this.jdbc = jdbc;
    this.recordService = recordService;
  }

  /**
   * 子表替换完成后调用：聚合子行金额并回填父记录金额字段。
   *
   * <p>未登记聚合约定的实体对直接返回（通用子表不受影响）；父记录不存在（并发删除）抛 404； 审批链上冻结状态抛 422（不走静默跳过——「改了明细却没生效」必须显式失败）。
   *
   * @return 回填后的金额（供响应附加展示；未匹配场景返回 null）
   */
  public BigDecimal aggregateAndBackfill(
      PublishedRuntimeEntity parent, long tenantId, String parentId, String childEntityCode) {
    AggSpec spec = SPECS.get(parent.entityCode());
    if (spec == null || !spec.childEntityCode().equals(childEntityCode)) {
      return null;
    }
    String parentTable = parent.physicalTableName();
    String pk = parent.primaryKeyColumn();

    // 冻结状态检查：SUBMITTED/APPROVED/PAID/REJECTED 不允许通过明细替换间接改金额
    String status;
    try {
      status =
          jdbc.queryForObject(
              "SELECT `status` FROM `"
                  + parentTable
                  + "` WHERE `"
                  + pk
                  + "` = :pk AND `deleted` = 0",
              new MapSqlParameterSource("pk", Long.valueOf(parentId)),
              String.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "父记录不存在或已删除: " + parentId);
    }
    if (status != null && !"DRAFT".equals(status)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "报销单当前状态为 " + status + "，不允许修改明细（审批链上金额已冻结）");
    }

    // 聚合：SUM(子行金额)，仅未删除行（replaceChildren 已把孤儿软删，此处天然一致）
    String childTable = childTableOf(parent, spec);
    if (childTable == null) {
      return null;
    }
    BigDecimal sum =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(`"
                + spec.childAmountColumn()
                + "`), 0) FROM `"
                + childTable
                + "` WHERE `"
                + childFkColumn(parent, spec)
                + "` = :pk AND `deleted` = 0",
            new MapSqlParameterSource("pk", Long.valueOf(parentId)),
            BigDecimal.class);

    // 回填父记录金额字段：走公共写链（filterWritable/校验/乐观锁/审计复用，见类 javadoc）。
    // 金额列非 state_field，_stateField 保护不适用；DRAFT 门禁已在本方法前置完成。
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(spec.parentAmountColumn(), sum);
    recordService.update(parent.entityCode(), tenantId, parentId, payload, null, null);
    return sum;
  }

  private String childTableOf(PublishedRuntimeEntity parent, AggSpec spec) {
    // 子实体物理表名：运行时目录按实体编码解析（缺省同 code 表名约定）
    return "t_" + spec.childEntityCode();
  }

  private String childFkColumn(PublishedRuntimeEntity parent, AggSpec spec) {
    // CHILD_TABLE 外键列约定：{父实体去前缀}_id（t_expense_claim → claim_id）
    String code = parent.entityCode();
    if (code.startsWith("t_")) {
      code = code.substring(2);
    }
    return code + "_id";
  }

  /** 场景聚合声明。 */
  private record AggSpec(
      String childEntityCode, String parentAmountColumn, String childAmountColumn) {}
}
