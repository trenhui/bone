package com.bone.masterdata.domain.model.supplier;

/**
 * Vendor 停用前置事实：不得留下进行中的采购。
 *
 * @param inProgressPurchaseOrders 进行中的采购订单数
 * @param existingOrdersMayFinish 存量 PO 是否允许走完（允许时可不阻塞停用）
 */
public record VendorDeactivationContext(
    long tenantId, String partyId, int inProgressPurchaseOrders, boolean existingOrdersMayFinish) {

  public boolean allowedToDeactivate() {
    return inProgressPurchaseOrders == 0 || existingOrdersMayFinish;
  }
}
