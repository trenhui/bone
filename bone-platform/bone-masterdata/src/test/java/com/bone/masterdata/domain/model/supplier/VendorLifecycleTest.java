package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Vendor 交易与付款资格状态机（LLD `01-供应商主数据模块详细设计.md` §5）。
 *
 * <p>判据来源：spec「Vendor 交易与付款资格状态机」4 个 Scenario —— 开通闸门全项通过 / 未经闸门不得开通 / 本域不得自行解除付款冻结 /
 * 停用不得留下进行中采购。Owner = 财务，采购域不得越权放行付款。
 *
 * <p><b>付款冻结的两条硬约束分开验证</b>（独立审查 I-04/I-05 修复后）：{@code payment_block_raised} 与 {@code
 * payment_block_released} 都必须来自 {@link
 * TransitionCommandSource#EXTERNAL_FINANCE}；解除时还须另有「人工复核留痕」（spec:83
 * 两个条件）。此前只校验解除方向，且留痕完全由调用方口头声明，等于两个条件只兑现了半个。
 */
@DisplayName("Vendor 交易与付款资格状态机：闸门 + 命令来源校验")
class VendorLifecycleTest {

  private static final long TENANT = 7L;
  private static final String OPERATOR = "finance-01";
  private static final Instant AT = Instant.parse("2026-11-16T02:00:00Z");

  @Test
  @DisplayName("闸门全项通过才可开通交易付款资格")
  void allowsActivationWhenGatePasses() {
    Vendor pending = Vendor.pending(TENANT, "PARTY-1");

    Vendor active =
        pending.transition(
            VendorEvent.ACTIVATION_PASSED,
            TransitionCommandSource.DOMAIN,
            GuardVerdict.pass("ADMISSION+BANK+TAX+PAYMENT_TERMS+DUPLICATE_INVOICE_ALL_PASS"),
            OPERATOR,
            AT,
            false);

    assertThat(active.state()).isEqualTo(VendorState.ACTIVE_VENDOR);
    assertThat(active.version()).isEqualTo(2L);
    assertThat(active.history()).hasSize(1);
    assertThat(active.history().get(0).basis())
        .isEqualTo("ADMISSION+BANK+TAX+PAYMENT_TERMS+DUPLICATE_INVOICE_ALL_PASS");
  }

  @Test
  @DisplayName("闸门未通过时拒绝开通并保持 PENDING_VENDOR")
  void rejectsActivationWhenGateFails() {
    Vendor pending = Vendor.pending(TENANT, "PARTY-1");

    assertThatThrownBy(
            () ->
                pending.transition(
                    VendorEvent.ACTIVATION_PASSED,
                    TransitionCommandSource.DOMAIN,
                    GuardVerdict.reject("BANK_VERIFICATION_FAILED"),
                    OPERATOR,
                    AT,
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("BANK_VERIFICATION_FAILED");

    assertThat(pending.state()).isEqualTo(VendorState.PENDING_VENDOR);
    assertThat(pending.history()).isEmpty();
  }

  @Test
  @DisplayName("未经闸门不得开通：缺少守卫结论直接被拒")
  void rejectsActivationWithoutVerdict() {
    Vendor pending = Vendor.pending(TENANT, "PARTY-1");

    assertThatThrownBy(
            () ->
                pending.transition(
                    VendorEvent.ACTIVATION_PASSED,
                    TransitionCommandSource.DOMAIN,
                    null,
                    OPERATOR,
                    AT,
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("守卫");
  }

  @Test
  @DisplayName("本域不得自行解除付款冻结：域内来源的 payment_block_released 被拒")
  void rejectsDomainInitiatedPaymentBlockRelease() {
    Vendor blocked = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.PAYMENT_BLOCKED, 3L);

    assertThatThrownBy(
            () ->
                blocked.transition(
                    VendorEvent.PAYMENT_BLOCK_RELEASED,
                    TransitionCommandSource.DOMAIN,
                    GuardVerdict.pass("MANUAL_REVIEW_DONE"),
                    OPERATOR,
                    AT,
                    true))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("外部财务事件");

    assertThat(blocked.state()).isEqualTo(VendorState.PAYMENT_BLOCKED);
  }

  @Test
  @DisplayName("本域同样不得自行提起付款冻结：域内来源的 payment_block_raised 被拒")
  void rejectsDomainInitiatedPaymentBlockRaise() {
    Vendor active = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 2L);

    assertThatThrownBy(
            () ->
                active.transition(
                    VendorEvent.PAYMENT_BLOCK_RAISED,
                    TransitionCommandSource.DOMAIN,
                    GuardVerdict.pass("RISK_EVENT"),
                    OPERATOR,
                    AT,
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("外部财务事件");

    assertThat(active.state()).isEqualTo(VendorState.ACTIVE_VENDOR);
  }

  @Test
  @DisplayName("外部财务事件但无人工复核留痕：解除被拒（spec:83 两条件缺一不可）")
  void rejectsFinanceReleaseWithoutManualReviewTrace() {
    Vendor blocked = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.PAYMENT_BLOCKED, 3L);

    assertThatThrownBy(
            () ->
                blocked.transition(
                    VendorEvent.PAYMENT_BLOCK_RELEASED,
                    TransitionCommandSource.EXTERNAL_FINANCE,
                    GuardVerdict.pass("FINANCE_RELEASED"),
                    OPERATOR,
                    AT,
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("人工复核留痕");

    assertThat(blocked.state()).isEqualTo(VendorState.PAYMENT_BLOCKED);
  }

  @Test
  @DisplayName("外部财务事件 + 人工复核留痕：解除付款冻结回到 ACTIVE_VENDOR")
  void allowsFinanceInitiatedPaymentBlockRelease() {
    Vendor blocked = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.PAYMENT_BLOCKED, 3L);

    Vendor released =
        blocked.transition(
            VendorEvent.PAYMENT_BLOCK_RELEASED,
            TransitionCommandSource.EXTERNAL_FINANCE,
            GuardVerdict.pass("FINANCE_RELEASED+MANUAL_REVIEW_RECORDED"),
            OPERATOR,
            AT,
            true);

    assertThat(released.state()).isEqualTo(VendorState.ACTIVE_VENDOR);
  }

  @Test
  @DisplayName("停用不得留下进行中采购：存在未允许走完的进行中 PO 时拒绝")
  void rejectsDeactivateWithInProgressPurchaseOrder() {
    Vendor active = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 4L);

    assertThatThrownBy(
            () ->
                active.transition(
                    VendorEvent.DEACTIVATE,
                    TransitionCommandSource.DOMAIN,
                    GuardVerdict.reject("IN_PROGRESS_PURCHASE_ORDER"),
                    OPERATOR,
                    AT,
                    false))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("IN_PROGRESS_PURCHASE_ORDER");

    assertThat(active.state()).isEqualTo(VendorState.ACTIVE_VENDOR);
  }

  @Test
  @DisplayName("允许存量 PO 走完时可停用")
  void allowsDeactivateWhenExistingOrdersMayFinish() {
    Vendor active = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.ACTIVE_VENDOR, 4L);

    Vendor inactive =
        active.transition(
            VendorEvent.DEACTIVATE,
            TransitionCommandSource.DOMAIN,
            GuardVerdict.pass("NO_IN_PROGRESS_PO"),
            OPERATOR,
            AT,
            false);

    assertThat(inactive.state()).isEqualTo(VendorState.INACTIVE_VENDOR);
    assertThat(inactive.state().isTerminal()).isTrue();
  }

  @Test
  @DisplayName("INACTIVE_VENDOR 为终态：不得直接回写为 ACTIVE_VENDOR，须重走闸门")
  void rejectsDirectReactivationFromTerminalState() {
    Vendor inactive = Vendor.rehydrate(TENANT, "PARTY-1", VendorState.INACTIVE_VENDOR, 5L);

    for (VendorEvent event : VendorEvent.values()) {
      // manualReviewTraced=true：让「终态拒绝」成为唯一可能的失败原因，而不是被留痕校验提前拦下。
      assertThatThrownBy(
              () ->
                  inactive.transition(
                      event,
                      TransitionCommandSource.EXTERNAL_FINANCE,
                      GuardVerdict.pass("OK"),
                      OPERATOR,
                      AT,
                      true))
          .as("终态收到 %s 必须被拒绝", event.code())
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("INACTIVE_VENDOR");
    }
    assertThat(VendorLifecycle.allowedEvents(VendorState.INACTIVE_VENDOR)).isEmpty();
  }
}
