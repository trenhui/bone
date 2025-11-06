package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 默认业务规则引擎实现
 * 实现采购订单的业务规则验证
 */
@Service
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {
    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);
    
    // 最大订单金额限制
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal(100000);
    // 最小订单金额限制
    private static final BigDecimal MIN_ORDER_AMOUNT = new BigDecimal(0);
    // 最大订单项数量
    private static final int MAX_ORDER_ITEMS = 100;
    
    @Override
    public void validateOrder(PurchaseOrder order) throws BusinessException {
        log.info("开始验证订单规则，订单号: {}", order.getOrderNumber());
        
        // 1. 基本验证
        validateBasicInfo(order);
        
        // 2. 验证订单项
        validateOrderItems(order.getItems());
        
        // 3. 验证金额
        validateOrderAmount(order.getTotalAmount());
        
        // 4. 验证供应商
        validateSupplier(order.getSupplierId());
        
        log.info("订单规则验证通过，订单号: {}", order.getOrderNumber());
    }
    
    /**
     * 验证订单基本信息
     */
    private void validateBasicInfo(PurchaseOrder order) throws BusinessException {
        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            throw new BusinessException("订单号不能为空", HttpStatus.BAD_REQUEST);
        }
        
        if (order.getDepartment() == null || order.getDepartment().trim().isEmpty()) {
            throw new BusinessException("部门不能为空", HttpStatus.BAD_REQUEST);
        }
        
        if (order.getCreator() == null || order.getCreator().trim().isEmpty()) {
            throw new BusinessException("创建人不能为空", HttpStatus.BAD_REQUEST);
        }
    }
    
    /**
     * 验证订单项
     */
    private void validateOrderItems(List<PurchaseOrderItem> items) throws BusinessException {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单必须包含至少一个订单项", HttpStatus.BAD_REQUEST);
        }
        
        if (items.size() > MAX_ORDER_ITEMS) {
            throw new BusinessException("订单项数量不能超过" + MAX_ORDER_ITEMS + "个", HttpStatus.BAD_REQUEST);
        }
        
        // 验证每个订单项
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            validateOrderItem(item, i + 1);
        }
    }
    
    /**
     * 验证单个订单项
     */
    private void validateOrderItem(PurchaseOrderItem item, int index) throws BusinessException {
        if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
            throw new BusinessException("第" + index + "个订单项的产品ID不能为空", HttpStatus.BAD_REQUEST);
        }
        
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new BusinessException("第" + index + "个订单项的数量必须大于0", HttpStatus.BAD_REQUEST);
        }
        
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("第" + index + "个订单项的单价必须大于0", HttpStatus.BAD_REQUEST);
        }
    }
    
    /**
     * 验证订单金额
     */
    private void validateOrderAmount(BigDecimal amount) throws BusinessException {
        if (amount == null) {
            throw new BusinessException("订单金额不能为空", HttpStatus.BAD_REQUEST);
        }
        
        if (amount.compareTo(MIN_ORDER_AMOUNT) <= 0) {
            throw new BusinessException("订单金额必须大于0", HttpStatus.BAD_REQUEST);
        }
        
        if (amount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BusinessException("订单金额不能超过" + MAX_ORDER_AMOUNT, HttpStatus.BAD_REQUEST);
        }
    }
    
    /**
     * 验证供应商
     */
    private void validateSupplier(String supplierId) throws BusinessException {
        if (supplierId == null || supplierId.trim().isEmpty()) {
            throw new BusinessException("供应商不能为空", HttpStatus.BAD_REQUEST);
        }
        
        // 这里可以添加更多的供应商验证逻辑，比如验证供应商是否存在、是否有效等
    }
    
    @Override
    public void validateOrderUpdate(PurchaseOrder order, PurchaseOrder existingOrder) throws BusinessException {
        log.info("开始验证订单更新规则，订单ID: {}", order.getId());
        
        // 验证订单状态是否允许更新
        String currentStatus = existingOrder.getStatus();
        if ("APPROVED".equals(currentStatus) || "CANCELLED".equals(currentStatus) || "COMPLETED".equals(currentStatus)) {
            throw new BusinessException("当前状态的订单不允许修改", HttpStatus.BAD_REQUEST);
        }
        
        // 调用基本验证
        validateOrder(order);
        
        log.info("订单更新规则验证通过，订单ID: {}", order.getId());
    }
    
    @Override
    public BigDecimal calculateTotalAmount(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (item.getQuantity() > 0 && item.getUnitPrice() != null) {
                BigDecimal itemTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(itemTotal);
            }
        }
        
        return total;
    }
}