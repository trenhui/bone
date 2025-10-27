package com.bone.procurement.repository;

import com.bone.procurement.entity.PurchaseOrder;

/**
 * 采购订单仓库接口
 */
public interface PurchaseOrderRepository {
    // 基础CRUD方法
    PurchaseOrder save(PurchaseOrder order);
    PurchaseOrder findById(Long id);
    void deleteById(Long id);
}