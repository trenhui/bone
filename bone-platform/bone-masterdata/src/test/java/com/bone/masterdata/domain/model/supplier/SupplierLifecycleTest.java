package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Supplier 合作资格状态机（LLD `01-供应商主数据模块详细设计.md` §5）。
 *
 * <p>判据来源：spec「Supplier 合作资格生命周期状态机」4 个 Scenario —— 合法准入路径 / 非法迁移被拒 / 退出前置校验 /
 * 终态不可回写。迁移表未列出即拒绝（fail-closed），守卫集中在 domain。
 */
@DisplayName("Supplier 合作资格状态机：显式迁移表 + 守卫集中 domain")
class SupplierLifecycleTest {

  private static final long TENANT = 7L;
  private static final String OPERATOR = "steward-01";
  private static final Instant AT = Instant.parse("2026-11-16T02:00:00Z");

  @Test
  @DisplayName("合法准入路径：PROSPECT → ONBOARDING → ACTIVE，并记录迁移时间与操作人")
  void allowsLegalOnboardingPath() {
    Supplier prospect = Supplier.prospect(TENANT, "PARTY-1");

    Supplier onboarding =
        prospect.transition(
            SupplierEvent.ONBOARDING_SUBMITTED,
            GuardVerdict.pass("BACKGROUND_CHECK_PASSED"),
            OPERATOR,
            AT);
    Supplier active =
        onboarding.transition(
            SupplierEvent.ONBOARDING_APPROVED,
            GuardVerdict.pass("CHANGE_REQUEST_APPROVED+ACTIVE_VENDOR_EXISTS"),
            OPERATOR,
            AT);

    assertThat(onboarding.state()).isEqualTo(SupplierState.ONBOARDING);
    assertThat(onboarding.version()).isEqualTo(2L);
    assertThat(active.state()).isEqualTo(SupplierState.ACTIVE);
    assertThat(active.version()).isEqualTo(3L);
    assertThat(active.history()).hasSize(2);
    SupplierStateChange last = active.history().get(1);
    assertThat(last.from()).isEqualTo(SupplierState.ONBOARDING);
    assertThat(last.to()).isEqualTo(SupplierState.ACTIVE);
    assertThat(last.operator()).isEqualTo(OPERATOR);
    assertThat(last.at()).isEqualTo(AT);
    assertThat(last.basis()).isEqualTo("CHANGE_REQUEST_APPROVED+ACTIVE_VENDOR_EXISTS");
  }

  @Test
  @DisplayName("非法迁移被拒：PROSPECT 直接收 onboarding_approved，返回当前状态与允许事件集")
  void rejectsIllegalTransition() {
    Supplier prospect = Supplier.prospect(TENANT, "PARTY-1");

    assertThatThrownBy(
            () ->
                prospect.transition(
                    SupplierEvent.ONBOARDING_APPROVED, GuardVerdict.pass("OK"), OPERATOR, AT))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("PROSPECT")
        .hasMessageContaining("onboarding_approved")
        .hasMessageContaining("onboarding_submitted");
  }

  @Test
  @DisplayName("退出前置校验：守卫拒绝时保持活动状态且不产生任何副作用")
  void rejectsExitWhenGuardFails() {
    Supplier active = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L);

    assertThatThrownBy(
            () ->
                active.transition(
                    SupplierEvent.EXIT_APPROVED,
                    GuardVerdict.reject("OPEN_PURCHASE_ORDER"),
                    OPERATOR,
                    AT))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("OPEN_PURCHASE_ORDER");

    assertThat(active.state()).isEqualTo(SupplierState.ACTIVE);
    assertThat(active.version()).isEqualTo(3L);
    assertThat(active.history()).isEmpty();
  }

  @Test
  @DisplayName("退出前置校验：守卫通过才可迁移至 EXITED")
  void allowsExitWhenGuardPasses() {
    Supplier active = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L);

    Supplier exited =
        active.transition(
            SupplierEvent.EXIT_APPROVED, GuardVerdict.pass("NO_OPEN_OBLIGATION"), OPERATOR, AT);

    assertThat(exited.state()).isEqualTo(SupplierState.EXITED);
    assertThat(exited.state().isTerminal()).isTrue();
  }

  @Test
  @DisplayName("终态不可回写：EXITED 收任一事件一律拒绝，且允许事件集为空")
  void rejectsAnyTransitionFromTerminalState() {
    Supplier exited = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.EXITED, 4L);

    for (SupplierEvent event : SupplierEvent.values()) {
      assertThatThrownBy(() -> exited.transition(event, GuardVerdict.pass("OK"), OPERATOR, AT))
          .as("终态收到 %s 必须被拒绝", event.code())
          .isInstanceOf(DomainException.class)
          .hasMessageContaining("EXITED");
    }
    assertThat(SupplierLifecycle.allowedEvents(SupplierState.EXITED)).isEmpty();
  }

  @Test
  @DisplayName("需要守卫的迁移未提供守卫结论时拒绝，不允许默认放行")
  void rejectsGuardedTransitionWithoutVerdict() {
    Supplier active = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.ACTIVE, 3L);

    assertThatThrownBy(() -> active.transition(SupplierEvent.EXIT_APPROVED, null, OPERATOR, AT))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("守卫");
  }

  @Test
  @DisplayName("暂停后整改通过可恢复 ACTIVE")
  void allowsRestoreFromSuspended() {
    Supplier suspended = Supplier.rehydrate(TENANT, "PARTY-1", SupplierState.SUSPENDED, 4L);

    Supplier restored =
        suspended.transition(
            SupplierEvent.RESTORED, GuardVerdict.pass("RECTIFICATION_VERIFIED"), OPERATOR, AT);

    assertThat(restored.state()).isEqualTo(SupplierState.ACTIVE);
  }
}
