package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.SupplierOnboardingFactsPort;

/**
 * 变更请求域事实的联调夹具。
 *
 * <p><b>默认 {@code false}</b>（fail-closed）：不显式声明「全段已批准」，准入就应当失败。默认 {@code true} 会让
 * 「变更请求未全段批准不得准入」这条用例在任何忘记配置的情况下静默通过。
 */
public final class InMemorySupplierOnboardingFacts implements SupplierOnboardingFactsPort {

  private boolean fullyApproved;

  @Override
  public boolean changeRequestFullyApproved(long tenantId, String partyId) {
    return fullyApproved;
  }

  public void approve() {
    this.fullyApproved = true;
  }

  public void reset() {
    this.fullyApproved = false;
  }
}
