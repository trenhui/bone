package com.bone.app.expense;

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
 * 借款单写前置钩子（实体级业务规则）：金额冻结兜底（与报销单 {@link ExpenseReimbursementWriteHook} 规则一同口径）。
 *
 * <p><b>规则（唯一）</b>：status∈审批链/资金链时拒绝改 {@code amount}。借款单 6
 * 态（DRAFT/SUBMITTED/APPROVED/PAID/REJECTED/CANCELLED）中 仅 DRAFT 可改金额；REJECTED/CANCELLED 亦冻结——须走
 * withdraw/reopen 回 DRAFT 后再改，与报销单冻结集语义一致。
 *
 * <p><b>双保险定位</b>：金额冻结的规范实现是平台字段锁（{@code UiSchemaRuleEnforcer.enforceFieldLock}，主防线）；本钩子是防
 * 「种子漏跑/回滚导致字段锁未生效」的兜底。二者语义一致（都拦冻结态改金额）。
 *
 * <p><b>无 Σ 规则</b>：借款单是单笔金额（无明细结构），不存在「头表 = Σ明细」口径，故不设规则二。
 */
@Component("expenseAdvanceWriteHook")
public class ExpenseAdvanceWriteHook implements RuntimeWriteHook {

  /** 审批链/资金链上不允许改金额的状态（DRAFT 允许）。与报销单冻结集对齐（借款单无 AUDITED 审票态，放款从 APPROVED 直触发）。 */
  private static final Set<String> FROZEN_STATUSES =
      Set.of("SUBMITTED", "APPROVED", "PAID", "REJECTED", "CANCELLED");

  private final NamedParameterJdbcTemplate jdbc;

  public ExpenseAdvanceWriteHook(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String entityCode() {
    return "expense_advance";
  }

  @Override
  public void beforeUpdate(RuntimeWriteHookContext ctx) {
    Map<String, Object> payload = ctx.payload();
    if (payload.get("amount") == null) {
      return; // 本次修改不涉及金额，放行
    }
    String currentStatus = currentStatus(ctx);
    if (currentStatus != null && FROZEN_STATUSES.contains(currentStatus)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "借款单当前状态为 " + currentStatus + "，不允许修改金额（审批链上金额已冻结）");
    }
  }

  /** 直查当前状态（同源连接；记录不存在视为 null，交由平台字段锁/引擎后续处理）。 */
  private String currentStatus(RuntimeWriteHookContext ctx) {
    String table = ctx.entity().physicalTableName();
    String pk = ctx.entity().primaryKeyColumn();
    String sql = "SELECT `status` FROM " + table + " WHERE `" + pk + "` = :pk AND `deleted` = 0";
    try {
      return jdbc.queryForObject(
          sql, new MapSqlParameterSource("pk", ctx.recordId()), String.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }
}
