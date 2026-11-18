package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.SupplierOnboardingGuard;
import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.SupplierOnboardingContext;

/**
 * 准入闸门的联调夹具：按 spec:54 的两项前置逐项裁决，<b>不返回硬编码 true</b>。
 *
 * <p>缺任一项即拒绝，并给出可审计的原因码。若这个守卫恒真，「未经闸门不得准入」的用例无论上游状态如何都会通过（假绿）。
 */
public final class TestSupplierOnboardingGuard implements SupplierOnboardingGuard {

  @Override
  public GuardVerdict canActivate(SupplierOnboardingContext context) {
    if (context == null) {
      return GuardVerdict.reject("ONBOARDING_CONTEXT_MISSING");
    }
    // 放行判据只认上下文谓词 ready()：规则不在夹具里再写一遍，否则上下文与夹具会各自演化。
    if (context.ready()) {
      return GuardVerdict.pass("ONBOARDING_PRECONDITIONS_MET");
    }
    // 拒绝时按缺哪一项给原因码，便于审计定位。
    return context.changeRequestFullyApproved()
        ? GuardVerdict.reject("NO_ACTIVE_VENDOR")
        : GuardVerdict.reject("CHANGE_REQUEST_NOT_FULLY_APPROVED");
  }
}
