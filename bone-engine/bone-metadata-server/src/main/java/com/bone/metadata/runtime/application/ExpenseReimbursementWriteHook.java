package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeWriteHook;
import com.bone.metadata.engine.runtime.RuntimeWriteHookContext;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 报销单写前置钩子（实体级业务规则，Directus Filter 分层示例）。
 *
 * <p>规则：<strong>SUBMITTED（含）之后不允许修改金额</strong>——防止「审批通过后把金额改大再打款」
 * 的绕过审批路径。该规则属报销业务域，不写进通用引擎（引擎只承载平台级 required/type/validation_rules）， 经 {@link RuntimeWriteHook}
 * SPI 挂在 {@code expense_reimbursement} 实体上，业务模块自行注册/卸载。
 *
 * <p>实现说明：
 *
 * <ul>
 *   <li>用同源 {@link NamedParameterJdbcTemplate} 直查当前状态，避免注入 {@code JdbcRuntimeRecordService}
 *       造成构造循环（service 持有 hooks 列表）；
 *   <li>状态流转（updateState 改 status 字段）载荷不含 {@code amount}，天然放行；只有显式改金额的 PUT 载荷才触发阻断；
 *   <li>阻断错误码复用 {@code META_RUNTIME_VALIDATION_FAILED}（422，已登记），不新增契约面。
 * </ul>
 */
@Component("expenseReimbursementWriteHook")
public class ExpenseReimbursementWriteHook implements RuntimeWriteHook {

  /** 审批链上不允许改金额的状态（DRAFT 允许修改）。 */
  private static final Set<String> FROZEN_STATUSES = Set.of("SUBMITTED", "APPROVED", "PAID");

  private final NamedParameterJdbcTemplate jdbc;

  public ExpenseReimbursementWriteHook(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String entityCode() {
    return "expense_reimbursement";
  }

  @Override
  public void beforeUpdate(RuntimeWriteHookContext ctx) {
    Map<String, Object> payload = ctx.payload();
    Object amount = payload.get("amount");
    if (amount == null) {
      return; // 本次修改不涉及金额，放行
    }
    String currentStatus = currentStatus(ctx);
    if (currentStatus != null && FROZEN_STATUSES.contains(currentStatus)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "报销单当前状态为 " + currentStatus + "，不允许修改金额（审批链上金额已冻结）");
    }
  }

  /** 直查当前状态（同源连接；记录不存在视为 null，交由引擎后续报错）。 */
  private String currentStatus(RuntimeWriteHookContext ctx) {
    String table = ctx.entity().physicalTableName();
    String pk = ctx.entity().primaryKeyColumn();
    String sql = "SELECT `status` FROM " + table + " WHERE `" + pk + "` = :pk AND `deleted` = 0";
    MapSqlParameterSource params = new MapSqlParameterSource("pk", ctx.recordId());
    try {
      return jdbc.queryForObject(sql, params, String.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }
}
