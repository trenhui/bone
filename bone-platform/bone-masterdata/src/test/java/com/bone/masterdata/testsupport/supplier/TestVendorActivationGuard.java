package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.VendorActivationGuard;
import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.VendorActivationContext;
import com.bone.masterdata.domain.model.supplier.VendorDeactivationContext;

/**
 * Vendor 闸门守卫的联调夹具：五项逐项检查，缺任一项即拒绝（fail-closed），<b>不做「部分通过 + 默认放行」</b>。
 *
 * <p>停用前置同理：有进行中 PO 且不允许存量走完时拒绝。夹具保证 spec 的「未经闸门不得开通」「停用不得留下进行中采购」两条 Scenario 在测试中真的具备否决能力。
 */
public final class TestVendorActivationGuard implements VendorActivationGuard {

  @Override
  public GuardVerdict canActivate(VendorActivationContext context) {
    if (context == null) {
      return GuardVerdict.reject("ACTIVATION_CONTEXT_MISSING");
    }
    // 放行判据只认上下文谓词 allPassed()：五项规则的唯一实现留在上下文里。
    if (context.allPassed()) {
      return GuardVerdict.pass("ACTIVATION_GATE_PASSED");
    }
    if (!context.admissionPassed()) {
      return GuardVerdict.reject("ADMISSION_NOT_PASSED");
    }
    if (!context.bankVerified()) {
      return GuardVerdict.reject("BANK_NOT_VERIFIED");
    }
    if (!context.taxVerified()) {
      return GuardVerdict.reject("TAX_NOT_VERIFIED");
    }
    if (!context.paymentTermsAgreed()) {
      return GuardVerdict.reject("PAYMENT_TERMS_NOT_AGREED");
    }
    return GuardVerdict.reject("DUPLICATE_INVOICE_RISK");
  }

  @Override
  public GuardVerdict canDeactivate(VendorDeactivationContext context) {
    if (context == null) {
      return GuardVerdict.reject("DEACTIVATION_CONTEXT_MISSING");
    }
    return context.allowedToDeactivate()
        ? GuardVerdict.pass("DEACTIVATION_ALLOWED")
        : GuardVerdict.reject("IN_PROGRESS_PURCHASE_ORDERS");
  }
}
