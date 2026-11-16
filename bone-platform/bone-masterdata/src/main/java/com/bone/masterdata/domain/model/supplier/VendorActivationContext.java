package com.bone.masterdata.domain.model.supplier;

/**
 * Vendor 开通闸门事实（准入 / 银行核验 / 税务 / 付款条件 / 重复发票检查）。
 *
 * <p>五项**全项**通过才可开通；任何一项缺省即视为未通过（fail-closed），不允许「部分通过 + 默认放行」。
 */
public record VendorActivationContext(
    long tenantId,
    String partyId,
    boolean admissionPassed,
    boolean bankVerified,
    boolean taxVerified,
    boolean paymentTermsAgreed,
    boolean duplicateInvoiceFree) {

  public boolean allPassed() {
    return admissionPassed
        && bankVerified
        && taxVerified
        && paymentTermsAgreed
        && duplicateInvoiceFree;
  }
}
