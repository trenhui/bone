package com.bone.app.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeWriteHookContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * {@link ExpenseReimbursementWriteHook}：规则一（冻结态拒改金额，过渡兜底，待种子全环境生效后删）+ 规则二（DRAFT 态直改头表金额 =
 * 明细合计的编辑态对账兜底）。 金额冻结的规范实现已下沉为平台字段锁（UiSchemaRuleEngine.fieldLockRules），由
 * UiSchemaRuleEnforcer.enforceFieldLock 在记录更新路径强制； 本钩子规则一只作防「种子漏跑导致字段锁未生效」的兜底，双保险。
 */
@ExtendWith(MockitoExtension.class)
class ExpenseReimbursementWriteHookTest {

  private static final long TENANT = 7L;

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Mock private RuntimeEntityCatalog runtimeEntityCatalog;

  private ExpenseReimbursementWriteHook hook;

  @BeforeEach
  void setUp() {
    // 必须在 @BeforeEach 构造：字段初始化早于 MockitoExtension 的 @Mock 注入（既有测试均在方法体内构造的同款时序坑）
    hook = new ExpenseReimbursementWriteHook(jdbc, runtimeEntityCatalog);
  }

  private static PublishedRuntimeEntity claimEntity(String table) {
    return new PublishedRuntimeEntity("expense_reimbursement", table, "id", TENANT, List.of());
  }

  private static RuntimeWriteHookContext ctx(
      String statusTable, String recordId, Map<String, Object> payload) {
    return new RuntimeWriteHookContext(claimEntity(statusTable), TENANT, recordId, payload, "u1");
  }

  private static Map<String, Object> amountPayload(Object amount) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("amount", amount);
    return payload;
  }

  private void stubStatus(String status) {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn(status);
  }

  private void stubItemEntityPublished(String itemTable) {
    when(runtimeEntityCatalog.findPublishedRuntime("expense_reimbursement_item", TENANT))
        .thenReturn(
            Optional.of(
                new PublishedRuntimeEntity(
                    "expense_reimbursement_item", itemTable, "id", TENANT, List.of())));
  }

  private void stubItemsSum(String sum) {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
        .thenReturn(new BigDecimal(sum));
  }

  @Test
  @DisplayName("SUBMITTED 态改金额 → 422（冻结主防线是平台字段锁；Hook 规则一作过渡兜底，双保险拦截）")
  void submitted_amount_frozen_byHookFallback() {
    stubStatus("SUBMITTED");

    assertThatThrownBy(
            () ->
                hook.beforeUpdate(
                    ctx("expense_reimbursement", "9", amountPayload(new BigDecimal("100")))))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("SUBMITTED");
  }

  @Test
  @DisplayName("REJECTED/CANCELLED/AUDITED 态改金额 → 422（v3.0 冻结集对齐 §4.6）")
  void rejectedCancelledAudited_amount_frozen() {
    stubStatus("REJECTED");
    assertThatThrownBy(
            () -> hook.beforeUpdate(ctx("expense_reimbursement", "9", amountPayload("100"))))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("REJECTED");

    stubStatus("CANCELLED");
    assertThatThrownBy(
            () -> hook.beforeUpdate(ctx("expense_reimbursement", "9", amountPayload("100"))))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("CANCELLED");

    stubStatus("AUDITED");
    assertThatThrownBy(
            () -> hook.beforeUpdate(ctx("expense_reimbursement", "9", amountPayload("100"))))
        .isInstanceOf(RuntimeRecordException.class)
        .hasMessageContaining("AUDITED");
  }

  @Test
  @DisplayName("DRAFT 态直改金额 ≠ 明细合计 → 422（G3 残留闭环）")
  void draft_amount_mustEqualItemsSum() {
    stubStatus("DRAFT");
    stubItemEntityPublished("expense_reimbursement_item");
    stubItemsSum("80");

    assertThatThrownBy(
            () ->
                hook.beforeUpdate(
                    ctx("expense_reimbursement", "9", amountPayload(new BigDecimal("100")))))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("明细合计");
  }

  @Test
  @DisplayName("DRAFT 态金额 = 明细合计 → 放行（ReimbursementChildAmountAggregator 回写口径自洽通过）")
  void draft_amount_equalsItemsSum_passes() {
    stubStatus("DRAFT");
    stubItemEntityPublished("expense_reimbursement_item");
    stubItemsSum("100");

    assertThatNoException()
        .isThrownBy(
            () ->
                hook.beforeUpdate(
                    ctx("expense_reimbursement", "9", amountPayload(new BigDecimal("100")))));
  }

  @Test
  @DisplayName("子实体未发布（无明细可挂）→ 跳过对账不阻断")
  void unpublishedItemEntity_skipsSumCheck() {
    when(runtimeEntityCatalog.findPublishedRuntime("expense_reimbursement_item", TENANT))
        .thenReturn(Optional.empty());

    assertThatNoException()
        .isThrownBy(
            () -> hook.beforeUpdate(ctx("expense_reimbursement", "9", amountPayload("100"))));
  }

  @Test
  @DisplayName("载荷不含 amount（纯状态/备注修改）→ 不触发任何查询")
  void nonAmountPayload_noQueries() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("title", "新标题");

    assertThatNoException()
        .isThrownBy(() -> hook.beforeUpdate(ctx("expense_reimbursement", "9", payload)));
    verifyNoInteractions(jdbc, runtimeEntityCatalog);
  }

  @Test
  @DisplayName("明细合计查询锚定主单外键列 claim_id（租户经父记录主键间接隔离，与 currentStatus 同口径）")
  void sumQuery_anchoredByForeignKey() {
    stubStatus("DRAFT");
    stubItemEntityPublished("expense_reimbursement_item");
    stubItemsSum("100");

    hook.beforeUpdate(ctx("expense_reimbursement", "9", amountPayload("100")));

    ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
        ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(jdbc).queryForObject(anyString(), paramsCaptor.capture(), eq(BigDecimal.class));
    assertThat(paramsCaptor.getValue().getValue("pk")).isEqualTo("9");
  }

  @Test
  @DisplayName("规则三SoD：显式设置 approver_id = 申请人 → 422（服务端底线，绕不过 linkageRules notice）")
  void approverEqualsApplicant_sodBlocked() {
    // 载荷只含 approver_id（不含 amount）：只触发 SoD 的 applicant 查询，不进冻结/Σ 分支
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn("42"); // applicant

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("approver_id", "42");

    assertThatThrownBy(() -> hook.beforeUpdate(ctx("expense_reimbursement", "9", payload)))
        .isInstanceOf(RuntimeRecordException.class)
        .satisfies(
            ex ->
                assertThat(((RuntimeRecordException) ex).getErrorCode())
                    .isEqualTo(MetadataErrorCodes.RUNTIME_VALIDATION_FAILED))
        .hasMessageContaining("SoD");
  }

  @Test
  @DisplayName("规则三 SoD：approver_id ≠ 申请人 → 放行")
  void approverDifferentFromApplicant_passes() {
    when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
        .thenReturn("42"); // applicant

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("approver_id", "99");

    assertThatNoException()
        .isThrownBy(() -> hook.beforeUpdate(ctx("expense_reimbursement", "9", payload)));
  }
}
