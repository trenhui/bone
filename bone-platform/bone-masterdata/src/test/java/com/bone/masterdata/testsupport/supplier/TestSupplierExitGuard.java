package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.SupplierExitGuard;
import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.SupplierExitContext;

/**
 * 退出守卫的联调夹具：按上下文事实逐项裁决，<b>不返回硬编码 true</b>。
 *
 * <p>本夹具存在的意义正是让「有未结 PO 时退出必须失败」这条 spec Scenario 真的能红 —— 若守卫恒真，测试无论上游有多少未结事实都会通过（假绿）。
 * 夹具只实现端口契约的裁决语义，不代表上游三域（PO / 合同 / 应付）已接入；生产实现的装配仍属 R-2。
 */
public final class TestSupplierExitGuard implements SupplierExitGuard {

  @Override
  public GuardVerdict canExit(SupplierExitContext context) {
    if (context == null) {
      return GuardVerdict.reject("EXIT_CONTEXT_MISSING");
    }
    if (context.cleared()) {
      return GuardVerdict.pass("EXIT_PRECONDITIONS_CLEARED");
    }
    // 三项事实共用上下文谓词：null（上游未作答）与「有未结」走同一拒绝分支，避免守卫里再写一遍口径。
    if (!context.purchaseOrdersSettled()) {
      return GuardVerdict.reject("OPEN_PURCHASE_ORDERS");
    }
    if (!context.contractsSettled()) {
      return GuardVerdict.reject("OPEN_CONTRACTS");
    }
    // 拒绝分支复用上下文的 payableSettled()：null（应付域未作答）与「余额为零」必须同一口径，不在守卫里重写一遍。
    if (!context.payableSettled()) {
      return GuardVerdict.reject("PAYABLE_BALANCE_UNSETTLED");
    }
    return GuardVerdict.reject("OPEN_VENDOR_ONBOARDING");
  }
}
