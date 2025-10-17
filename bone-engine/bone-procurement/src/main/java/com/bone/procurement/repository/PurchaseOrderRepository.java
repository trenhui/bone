package com.bone.procurement.repository;

import com.bone.procurement.entity.PurchaseOrder;

import java.util.List;

/**
 * 采购订单仓库接口
 */
public interface PurchaseOrderRepository {
    
    /**
     * 根据ID查询采购订单
     */
    PurchaseOrder findById(Long id);
    
    /**
     * 查询所有采购订单
     */
    List<PurchaseOrder> findAll();
    
    /**
     * 保存采购订单
     */
    PurchaseOrder save(PurchaseOrder purchaseOrder);
    
    /**
     * 删除采购订单
     */
    void deleteById(Long id);
}