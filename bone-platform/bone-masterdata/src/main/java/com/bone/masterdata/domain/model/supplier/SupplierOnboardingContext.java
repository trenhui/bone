package com.bone.masterdata.domain.model.supplier;

/**
 * 供应商准入（{@code ONBOARDING_APPROVED}）的前置条件事实。
 *
 * <p><b>两项输入的来源刻意分开</b>（spec:54 要求「变更请求全段已批准 + 至少存在 1 个 ACTIVE_VENDOR」）：
 *
 * <ul>
 *   <li>{@code changeRequestFullyApproved} 属变更请求域的上游事实，由 {@link
 *       com.bone.masterdata.domain.gateway.SupplierOnboardingFactsPort} 提供；
 *   <li>{@code activeVendorCount} 属本域可直接查询的事实，由应用层从 Vendor 仓库点算。
 * </ul>
 *
 * <p>不允许调用方在命令里自报任一事实：自报等于把守卫交给被守卫方，spec 的「合法准入路径」会退化成恒真断言。
 *
 * @param changeRequestFullyApproved 变更请求是否已全段批准
 * @param activeVendorCount 当前处于 {@code ACTIVE_VENDOR} 的角色数量
 */
public record SupplierOnboardingContext(
    long tenantId, String partyId, boolean changeRequestFullyApproved, int activeVendorCount) {

  public SupplierOnboardingContext {
    if (activeVendorCount < 0) {
      throw new IllegalArgumentException("ACTIVE_VENDOR 数量不得为负");
    }
  }

  /** 两项前置同时成立才可就绪。 */
  public boolean ready() {
    return changeRequestFullyApproved && activeVendorCount >= 1;
  }
}
