package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.exception.BusinessException;

import java.math.BigDecimal;
import java.util.List;

/**
 * 业务规则引擎接口
 * 负责执行采购订单相关的业务规则验证
 */
public interface BusinessRuleEngine {
    
    /**
     * 验证订单是否符合业务规则
     * @param order 采购订单对象
     * @throws BusinessException 当订单不符合业务规则时抛出
     */
    void validateOrder(PurchaseOrder order) throws BusinessException;
    
    /**
     * 验证订单更新是否符合业务规则
     * @param order 新的订单对象
     * @param existingOrder 现有的订单对象
     * @throws BusinessException 当订单更新不符合业务规则时抛出
     */
    void validateOrderUpdate(PurchaseOrder order, PurchaseOrder existingOrder) throws BusinessException;
    
    /**
     * 计算订单总金额
     * @param items 订单项列表
     * @return 订单总金额
     */
    BigDecimal calculateTotalAmount(List<PurchaseOrderItem> items);
}