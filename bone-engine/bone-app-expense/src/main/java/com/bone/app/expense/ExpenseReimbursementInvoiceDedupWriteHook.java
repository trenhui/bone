package com.bone.app.expense;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeWriteHook;
import com.bone.metadata.engine.runtime.RuntimeWriteHookContext;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 发票查重写前置钩子（实体级业务规则，G9）。
 *
 * <p>规则：同一租户内 {@code invoice_no} 只允许出现一次——防「一票多报」（同一张增值税发票在两张报销单、
 * 或同一张单的两行明细里重复报销）。这是中国企业报销场景的刚性内控（税务合规），平台无服务端唯一机制 （{@code is_unique} 不落物理索引、写路径不校验，见 0-1 方案 §9
 * G9），故以实体级钩子承载。
 *
 * <p>口径：
 *
 * <ul>
 *   <li>仅拦非空 {@code invoice_no}（无票行不参与）；
 *   <li>跨单据查重：统计范围为租户内全部未删明细行，update 时排除自身（{@code id <> :selfId}）；
 *   <li>create 时 {@code recordId} 为 null，不带自身排除条件；
 *   <li>命中即 422 {@code META_RUNTIME_VALIDATION_FAILED}，文案带发票号便于定位；
 *   <li>软删行（{@code deleted = 1}）不参与统计——退回报销作废后该票可重新报销（与「软删释放编码」同口径）。
 * </ul>
 *
 * <p>与前端 {@code unique} lint 的关系：UiSchemaRuleEngine 的 unique 只是前端提示；本钩子是服务端底线， 绕过界面直接调 API 同样被拦（与
 * {@link ExpenseReimbursementWriteHook} 同分层哲学）。
 */
@Component("expenseReimbursementInvoiceDedupWriteHook")
public class ExpenseReimbursementInvoiceDedupWriteHook implements RuntimeWriteHook {

  private final NamedParameterJdbcTemplate jdbc;

  public ExpenseReimbursementInvoiceDedupWriteHook(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String entityCode() {
    return "expense_reimbursement_item";
  }

  @Override
  public void beforeCreate(RuntimeWriteHookContext ctx) {
    assertInvoiceUnique(ctx);
  }

  @Override
  public void beforeUpdate(RuntimeWriteHookContext ctx) {
    assertInvoiceUnique(ctx);
  }

  private void assertInvoiceUnique(RuntimeWriteHookContext ctx) {
    Map<String, Object> payload = ctx.payload();
    Object invoiceNo = payload.get("invoice_no");
    if (invoiceNo == null || invoiceNo.toString().isBlank()) {
      return; // 无票行不参与查重
    }
    String table = ctx.entity().physicalTableName();
    String pk = ctx.entity().primaryKeyColumn();
    boolean isUpdate = ctx.recordId() != null;
    String sql =
        "SELECT COUNT(*) FROM "
            + table
            + " WHERE `tenant_id` = :tenant AND `invoice_no` = :invoiceNo AND `deleted` = 0"
            + (isUpdate ? " AND `" + pk + "` <> :selfId" : "");
    MapSqlParameterSource params =
        new MapSqlParameterSource("tenant", ctx.tenantId())
            .addValue("invoiceNo", invoiceNo.toString());
    if (isUpdate) {
      params.addValue("selfId", ctx.recordId());
    }
    Long duplicates;
    try {
      duplicates = jdbc.queryForObject(sql, params, Long.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      duplicates = 0L;
    }
    if (duplicates != null && duplicates > 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "发票号 " + invoiceNo + " 已在本租户其他报销明细中使用（一票多报拦截）");
    }
  }
}
