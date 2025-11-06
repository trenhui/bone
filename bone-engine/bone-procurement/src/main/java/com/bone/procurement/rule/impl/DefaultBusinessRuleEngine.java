/**
 * 默认业务规则引擎实现
 * 提供采购订单相关的业务规则验证和计算功能
 */
package com.bone.procurement.rule.impl;

import com.bone.procurement.rule.BusinessRuleEngine;
import com.bone.procurement.model.PurchaseOrder;
import com.bone.procurement.model.PurchaseOrderItem;
import com.bone.procurement.model.OrderStatus;
import com.bone.procurement.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.Collections;

@Component
public class DefaultBusinessRuleEngine implements BusinessRuleEngine {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessRuleEngine.class);
    
    // 金额阈值常量
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal(10000);
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD = new BigDecimal(50000);
    private static final double DEFAULT_TAX_RATE = 0.13;
    
    @Override
    public void validateOrderItems(List<PurchaseOrderItem> items) throws BusinessException {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("采购订单必须包含至少一个订单项");
        }
        
        // 验证每个订单项
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            if (item == null) {
                throw new BusinessException("订单项不能为空");
            }
            
            // 验证必填字段
            if (item.getProductId() == null || item.getProductId() <= 0) {
                throw new BusinessException("订单项[" + i + "]: 产品ID不能为空且必须大于0");
            }
            
            if (item.getProductCode() == null || item.getProductCode().trim().isEmpty()) {
                throw new BusinessException("订单项[" + i + "]: 产品编码不能为空");
            }
            
            if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
                throw new BusinessException("订单项[" + i + "]: 产品名称不能为空");
            }
            
            // 验证数量和单价
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException("订单项[" + i + "]: 数量必须大于0");
            }
            
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("订单项[" + i + "]: 单价必须大于0");
            }
            
            // 验证单位
            if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                throw new BusinessException("订单项[" + i + "]: 单位不能为空");
            }
            
            // 设置默认税率
            if (item.getTaxRate() == null) {
                item.setTaxRate(DEFAULT_TAX_RATE);
            }
        }
    }
    
    @Override
    public void calculateOrderFields(PurchaseOrder order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            log.warn("计算订单字段时，订单或订单项为空");
            return;
        }
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        
        for (PurchaseOrderItem item : order.getItems()) {
            // 计算每行总金额
            BigDecimal itemAmount = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            item.setTotalAmount(itemAmount);
            
            // 计算税额
            double taxRate = item.getTaxRate() != null ? item.getTaxRate() : DEFAULT_TAX_RATE;
            BigDecimal itemTax = itemAmount.multiply(BigDecimal.valueOf(taxRate)).setScale(2, BigDecimal.ROUND_HALF_UP);
            item.setTaxAmount(itemTax);
            
            // 累计总金额和总税额
            totalAmount = totalAmount.add(itemAmount);
            totalTax = totalTax.add(itemTax);
        }
        
        // 设置订单总金额
        order.setTotalAmount(totalAmount);
        order.setTotalTaxAmount(totalTax);
        order.setGrandTotal(totalAmount.add(totalTax));
        
        log.debug("订单字段计算完成，ID: {}, 总金额: {}, 税额: {}", 
                order.getId(), totalAmount, totalTax);
    }
    
    @Override
    public boolean requiresMultiLevelApproval(PurchaseOrder order) {
        return order != null && order.getTotalAmount() != null && 
               order.getTotalAmount().compareTo(HIGH_AMOUNT_THRESHOLD) > 0;
    }
    
    @Override
    public int getApprovalLevel(PurchaseOrder order) {
        if (order == null || order.getTotalAmount() == null) {
            return 1; // 默认级别
        }
        
        BigDecimal amount = order.getTotalAmount();
        if (amount.compareTo(VERY_HIGH_AMOUNT_THRESHOLD) > 0) {
            return 3; // 高级别审批
        } else if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0) {
            return 2; // 中级别审批
        }
        return 1; // 低级别审批
    }
    
    @Override
    public boolean hasApprovalPermission(String approverId, int approvalLevel, BigDecimal orderAmount) {
        // 这里简化实现，实际应该调用UserService检查权限
        // 例如：return userService.hasApprovalPermission(approverId, approvalLevel, orderAmount);
        return true; // 默认允许，等待权限系统集成
    }
    
    @Override
    public boolean canCancelOrder(PurchaseOrder order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        
        String status = order.getStatus();
        // 只有草稿、待审批、已审批状态可以取消
        return OrderStatus.DRAFT.name().equals(status) || 
               OrderStatus.PENDING_APPROVAL.name().equals(status) ||
               OrderStatus.APPROVED.name().equals(status);
    }
    
    @Override
    public boolean canModifyOrder(PurchaseOrder order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        
        // 只有草稿状态可以修改
        return OrderStatus.DRAFT.name().equals(order.getStatus());
    }
    
    @Override
    public List<String> getValidStateTransitions(String currentStatus) {
        if (currentStatus == null) {
            return Collections.emptyList();
        }
        
        // 根据当前状态返回可以流转到的目标状态
        switch (currentStatus) {
            case "DRAFT":
                return Arrays.asList("PENDING_APPROVAL", "CANCELLED");
            case "PENDING_APPROVAL":
                return Arrays.asList("APPROVED", "DRAFT", "CANCELLED");
            case "APPROVED":
                return Arrays.asList("EXECUTED", "CANCELLED");
            case "EXECUTED":
                return Arrays.asList("COMPLETED");
            default:
                return Collections.emptyList();
        }
    }
}