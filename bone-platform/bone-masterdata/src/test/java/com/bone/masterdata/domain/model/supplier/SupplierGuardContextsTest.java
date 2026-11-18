package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 守卫上下文的**判定谓词**单测（独立审查 SUGGESTION：退出/准入的否定性此前只由测试夹具保障）。
 *
 * <p>为什么要在 domain 层直接测：谓词是「什么算结清 / 什么算就绪」的唯一表达式，守卫实现（本切片为夹具、生产为 R-2）只负责 把false
 * 翻译成原因码。若只在应用层经夹具间接验证，一旦有人把谓词改成 null-tolerant，夹具的拒绝分支还会兜住测试， 缺陷就被掩盖（P8 探针首版正是这样假绿）。
 */
@DisplayName("守卫上下文谓词：未知事实一律按未清/未就绪处理")
class SupplierGuardContextsTest {

  private static final long TENANT = 7L;

  @Test
  @DisplayName("退出：四项事实齐全且全为零才算可退出")
  void clearedRequiresAllFourFactsKnownAndZero() {
    assertThat(exit(0, 0, BigDecimal.ZERO, 0).cleared()).isTrue();
    // Vendor 角色未关闭 → 不可退出。
    assertThat(exit(0, 0, BigDecimal.ZERO, 1).cleared()).isFalse();
    // 任一项有未结 → 不可退出。
    assertThat(exit(1, 0, BigDecimal.ZERO, 0).cleared()).isFalse();
    assertThat(exit(0, 2, BigDecimal.ZERO, 0).cleared()).isFalse();
    assertThat(exit(0, 0, new BigDecimal("0.01"), 0).cleared()).isFalse();
  }

  @Test
  @DisplayName("退出：任一事实为 null（上游未作答）都判未清，不得当成零")
  void unknownFactsBlockExit() {
    assertThat(exit(null, 0, BigDecimal.ZERO, 0).cleared()).isFalse();
    assertThat(exit(0, null, BigDecimal.ZERO, 0).cleared()).isFalse();
    assertThat(exit(0, 0, null, 0).cleared()).isFalse();

    // 逐项谓词是守卫拒绝分支的唯一判据。
    assertThat(exit(null, 0, BigDecimal.ZERO, 0).purchaseOrdersSettled()).isFalse();
    assertThat(exit(0, null, BigDecimal.ZERO, 0).contractsSettled()).isFalse();
    assertThat(exit(0, 0, null, 0).payableSettled()).isFalse();
  }

  @Test
  @DisplayName("准入：变更请求全段批准与至少 1 个 ACTIVE_VENDOR 同时成立才就绪")
  void onboardingReadyRequiresBothPreconditions() {
    assertThat(onboarding(true, 1).ready()).isTrue();
    assertThat(onboarding(true, 0).ready()).isFalse();
    assertThat(onboarding(false, 3).ready()).isFalse();
  }

  @Test
  @DisplayName("Vendor 开通：五项全通过才 allPassed，缺一项即 false")
  void activationRequiresAllFiveGates() {
    assertThat(context(true, true, true, true, true).allPassed()).isTrue();
    assertThat(context(false, true, true, true, true).allPassed()).isFalse();
    assertThat(context(true, false, true, true, true).allPassed()).isFalse();
    assertThat(context(true, true, false, true, true).allPassed()).isFalse();
    assertThat(context(true, true, true, false, true).allPassed()).isFalse();
    assertThat(context(true, true, true, true, false).allPassed()).isFalse();
  }

  private static SupplierExitContext exit(
      Integer po, Integer contracts, BigDecimal payable, int openVendorCount) {
    return new SupplierExitContext(TENANT, "PARTY-1", po, contracts, payable, openVendorCount);
  }

  private static SupplierOnboardingContext onboarding(
      boolean changeRequestFullyApproved, int activeVendorCount) {
    return new SupplierOnboardingContext(
        TENANT, "PARTY-1", changeRequestFullyApproved, activeVendorCount);
  }

  private static VendorActivationContext context(
      boolean admission, boolean bank, boolean tax, boolean terms, boolean duplicateInvoiceFree) {
    return new VendorActivationContext(
        TENANT, "PARTY-1", admission, bank, tax, terms, duplicateInvoiceFree);
  }
}
