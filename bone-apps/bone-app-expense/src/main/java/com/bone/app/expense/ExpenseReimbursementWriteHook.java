package com.bone.app.expense;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeWriteHook;
import com.bone.metadata.engine.runtime.RuntimeWriteHookContext;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 报销单写前置钩子（实体级业务规则，Directus Filter 分层示例）。
 *
 * <p>金额冻结（status∈审批链时 amount
 * 不可变）的<b>规范实现已下沉为平台字段锁</b>（UiSchemaRuleEngine.fieldLockRules，数据完整性语义，区别于 UI readonly 渲染提示），由
 * RuntimeRecordApplicationService.update 在写库前经 UiSchemaRuleEnforcer.enforceFieldLock 统一强制； 元数据声明见
 * 0047 / 0051 种子（expense_reimbursement.ui_schema_json.fieldLockRules）。
 *
 * <p><b>规则一（过渡兜底，待 0047/0051
 * 种子全环境生效后删除）</b>：status∈审批链时拒绝改金额。平台字段锁是主防线，本规则是<b>防止「种子漏跑/回滚导致冻结保护静默失效」的兜底</b>——
 * 双保险期间二者语义一致（都拦冻结态改金额）。
 *
 * <p><b>规则二</b>：DRAFT 态直改头表 amount = 明细合计（G3 残留闭环的编辑态兜底，聚合断言）：子表替换路径由 {@link
 * ReimbursementChildAmountAggregator} 服务端回填、提交路径由 {@code UiSchemaRuleEnforcer} 的 Σ
 * 规则兜底；本规则补上「绕过前两者直接 PUT 头表金额」的最后一环，使 G10「头表金额 = 明细合计」在编辑态恒成立。子实体未发布（无明细可挂）时跳过，不阻断。
 *
 * <p><b>规则三（SoD）</b>：显式设置 {@code approver_id} 时不得等于申请人（applicant，缺省回退 {@code created_by}）——职责分离
 * （审批人≠申请人）。ui_schema linkageRules 里这条只有 {@code notice} 提示、服务端不拦，故在此补<b>服务端底线</b>断言，绕过 前端直接调 API
 * 同样被拦。
 */
@Component("expenseReimbursementWriteHook")
public class ExpenseReimbursementWriteHook implements RuntimeWriteHook {

  /**
   * 审批链上不允许改金额的状态（DRAFT 允许；REJECTED/CANCELLED 亦冻结，要走 withdraw/reopen 回 DRAFT 后再改）。 与 0047/0051 种子
   * fieldLockRules 的 in 列表保持一致，直到本过渡兜底删除。
   */
  private static final Set<String> FROZEN_STATUSES =
      Set.of("SUBMITTED", "APPROVED", "AUDITED", "PAID", "REJECTED", "CANCELLED");

  /** 明细子实体 code（0-1 方案 §4.1；子表物理名经目录解析，兼容 exp_* 生产表，G22）。 */
  private static final String ITEM_ENTITY_CODE = "expense_reimbursement_item";

  private final NamedParameterJdbcTemplate jdbc;
  private final RuntimeEntityCatalog runtimeEntityCatalog;

  public ExpenseReimbursementWriteHook(
      NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog runtimeEntityCatalog) {
    this.jdbc = jdbc;
    this.runtimeEntityCatalog = runtimeEntityCatalog;
  }

  @Override
  public String entityCode() {
    return "expense_reimbursement";
  }

  @Override
  public void beforeUpdate(RuntimeWriteHookContext ctx) {
    Map<String, Object> payload = ctx.payload();
    // 规则三（SoD，R3）：审批人不得为申请人本人。服务端底线——ui_schema linkageRules 的 notice 只是提示，
    // 绕过前端直接调 API 也必须被拦（职责分离是资金域红线）。
    assertApproverNotApplicant(ctx, payload);
    Object amount = payload.get("amount");
    if (amount == null) {
      return; // 本次修改不涉及金额，放行
    }
    // 规则一（过渡兜底）：冻结态拒绝改金额。主防线是平台字段锁（UiSchemaRuleEnforcer.enforceFieldLock），
    // 本规则防「种子漏跑导致字段锁未生效」的静默失效窗口，双保险。
    String currentStatus = currentStatus(ctx);
    if (currentStatus != null && FROZEN_STATUSES.contains(currentStatus)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "报销单当前状态为 " + currentStatus + "，不允许修改金额（审批链上金额已冻结）");
    }
    // 规则二：DRAFT 态直改金额须与明细合计一致（G3 残留闭环）
    assertAmountEqualsItems(ctx, amount);
  }

  /** 直查当前状态（同源连接；记录不存在视为 null，交由平台字段锁/引擎后续处理）。 */
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

  /**
   * 规则三（SoD，R3）：本次载荷若设置 {@code approver_id}，不得等于记录申请人（applicant，缺省回退 {@code created_by}）。
   *
   * <p>服务端底线的理由：ui_schema linkageRules 里这条规则的 {@code then} 只有 {@code notice}（纯提示文案，前端渲染 Alert），服务端
   * {@code runRequiredShortcuts} 只强制 {@code required}，因此**绕过界面直接调 API 可让审批人=申请人**。
   * 职责分离是资金内控红线，故在写钩子层补断言。只拦「显式设置审批人」；不设置则放行（交 R5 大额必填与 linkage 处理）。
   */
  private void assertApproverNotApplicant(
      RuntimeWriteHookContext ctx, Map<String, Object> payload) {
    Object approverId = payload.get("approver_id");
    if (approverId == null || approverId.toString().isBlank()) {
      return; // 本次不设置审批人，放行
    }
    String applicant = queryString(ctx, "applicant");
    if (applicant == null || applicant.isBlank()) {
      applicant = queryString(ctx, "created_by");
    }
    if (applicant != null
        && !applicant.isBlank()
        && approverId.toString().trim().equals(applicant.trim())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "审批人不得为申请人本人（职责分离 SoD）：approver_id 与申请人相同");
    }
  }

  /** 直查字符串列（列名为代码字面量，非用户输入，无注入面）；记录不存在或列不存在视为 null。 */
  private String queryString(RuntimeWriteHookContext ctx, String column) {
    String table = ctx.entity().physicalTableName();
    String pk = ctx.entity().primaryKeyColumn();
    String sql =
        "SELECT `" + column + "` FROM " + table + " WHERE `" + pk + "` = :pk AND `deleted` = 0";
    MapSqlParameterSource params = new MapSqlParameterSource("pk", ctx.recordId());
    try {
      Object value = jdbc.queryForObject(sql, params, String.class);
      return value == null ? null : value.toString();
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      return null;
    }
  }

  /**
   * 头表金额 = Σ明细（G10 契约的编辑态兜底）。
   *
   * <p>只拦「显式改头表金额」：{@link com.bone.metadata.runtime.application.ReimbursementChildAmountAggregator}
   * 的回填写入口径相同（payload 即 Σ），天然自洽通过； 子实体未发布时无明细可挂，跳过不阻断（fail-open 于「不可能发生」的场景，fail-closed 于真实不一致）。
   */
  private void assertAmountEqualsItems(RuntimeWriteHookContext ctx, Object amount) {
    Optional<PublishedRuntimeEntity> itemEntity =
        runtimeEntityCatalog.findPublishedRuntime(ITEM_ENTITY_CODE, ctx.tenantId());
    if (itemEntity.isEmpty()) {
      return;
    }
    BigDecimal newAmount = toDecimal(amount);
    if (newAmount == null) {
      return; // 非数值交由平台校验器报错，不越厨代庖
    }
    String itemTable = itemEntity.get().physicalTableName();
    String sql =
        "SELECT COALESCE(SUM(`amount`), 0) FROM "
            + itemTable
            + " WHERE `claim_id` = :pk AND `deleted` = 0";
    BigDecimal sum;
    try {
      sum =
          jdbc.queryForObject(
              sql, new MapSqlParameterSource("pk", ctx.recordId()), BigDecimal.class);
    } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
      sum = BigDecimal.ZERO;
    }
    if (newAmount.compareTo(sum) != 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "申报金额必须等于费用明细合计（" + sum.toPlainString() + "），当前传值 " + newAmount.toPlainString());
    }
  }

  private static BigDecimal toDecimal(Object value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    try {
      return new BigDecimal(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
