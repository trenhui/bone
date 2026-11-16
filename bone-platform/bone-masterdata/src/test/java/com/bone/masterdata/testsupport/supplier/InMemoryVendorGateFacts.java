package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.VendorGateFactsPort;
import com.bone.masterdata.domain.model.supplier.VendorActivationContext;
import com.bone.masterdata.domain.model.supplier.VendorDeactivationContext;

/**
 * 财务域闸门事实的联调夹具（开通五项 / 停用两项）。
 *
 * <p><b>五项默认全部 false</b>（fail-closed）：只有显式 {@link #passAllGates()} 之后开通才可能成功。 默认 true
 * 会让「未经闸门不得开通」在任何忘记配置的用例里静默通过——这正是把事实从命令搬到端口后必须守住的默认值。
 */
public final class InMemoryVendorGateFacts implements VendorGateFactsPort {

  private boolean admissionPassed;
  private boolean bankVerified;
  private boolean taxVerified;
  private boolean paymentTermsAgreed;
  private boolean duplicateInvoiceFree;
  private int inProgressPurchaseOrders;
  private boolean existingOrdersMayFinish;

  @Override
  public VendorActivationContext activationContext(long tenantId, String partyId) {
    return new VendorActivationContext(
        tenantId,
        partyId,
        admissionPassed,
        bankVerified,
        taxVerified,
        paymentTermsAgreed,
        duplicateInvoiceFree);
  }

  @Override
  public VendorDeactivationContext deactivationContext(long tenantId, String partyId) {
    return new VendorDeactivationContext(
        tenantId, partyId, inProgressPurchaseOrders, existingOrdersMayFinish);
  }

  /** 五项全通过。 */
  public void passAllGates() {
    admissionPassed = true;
    bankVerified = true;
    taxVerified = true;
    paymentTermsAgreed = true;
    duplicateInvoiceFree = true;
  }

  /** 只让某项失败，便于验证「缺任一项即拒绝」。 */
  public void failBankVerification() {
    bankVerified = false;
  }

  public void withInProgressPurchaseOrders(int count, boolean mayFinish) {
    this.inProgressPurchaseOrders = count;
    this.existingOrdersMayFinish = mayFinish;
  }

  public void reset() {
    admissionPassed = false;
    bankVerified = false;
    taxVerified = false;
    paymentTermsAgreed = false;
    duplicateInvoiceFree = false;
    inProgressPurchaseOrders = 0;
    existingOrdersMayFinish = false;
  }
}
