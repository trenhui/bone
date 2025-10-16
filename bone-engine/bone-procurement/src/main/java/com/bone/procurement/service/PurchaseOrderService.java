package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.entity.Supplier;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.ExpressionEngine;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

/**
 * 采购订单服务类
 * 演示如何使用bone-smartmeta引擎进行元数据管理、表达式计算和业务规则验证
 */
@Service
public class PurchaseOrderService {
    
    private static final Logger logger = Logger.getLogger(PurchaseOrderService.class.getName());
    
    private final MetadataEngine metadataEngine;
    private final ExpressionEngine expressionEngine;
    private final SupplierService supplierService;
    
    // 模拟数据存储
    private final Map<Long, PurchaseOrder> orderRepository = new HashMap<>();
    private long nextId = 1;
    
    @Autowired
    public PurchaseOrderService(MetadataEngine metadataEngine, 
                              ExpressionEngine expressionEngine, 
                              SupplierService supplierService) {
        this.metadataEngine = metadataEngine;
        this.expressionEngine = expressionEngine;
        this.supplierService = supplierService;
    }
    
    /**
     * 创建采购订单
     * 使用bone-smartmeta引擎进行字段计算和业务规则验证
     */
    public PurchaseOrder createOrder(PurchaseOrder order) {
        logger.info("创建采购订单: " + order.getOrderCode());
        
        // 设置创建时间
        order.setCreationDate(LocalDateTime.now());
        
        // 如果没有指定状态，设置为草稿
        if (order.getOrderStatus() == null) {
            order.setOrderStatus("草稿");
        }
        
        // 使用MetadataEngine注册实体元数据
        metadataEngine.registerEntity(PurchaseOrder.class);
        
        // 验证业务规则
        validateBusinessRules(order);
        
        // 计算字段值
        calculateFields(order);
        
        // 保存订单
        synchronized (this) {
            order.setId(nextId++);
            orderRepository.put(order.getId(), order);
        }
        
        logger.info("采购订单创建成功，ID: " + order.getId());
        return order;
    }
    
    /**
     * 提交订单审批
     * 使用表达式引擎计算审批条件和流程
     */
    public PurchaseOrder submitForApproval(Long orderId) {
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("提交订单审批: " + order.getOrderCode());
        
        // 重新计算字段值确保最新
        calculateFields(order);
        
        // 使用表达式引擎评估是否需要多级审批
        boolean requiresMultiLevelApproval = evaluateMultiLevelApproval(order);
        
        // 设置审批节点
        if (requiresMultiLevelApproval) {
            order.setCurrentApprovalNode("部门经理审批");
        } else {
            order.setCurrentApprovalNode("采购经理审批");
        }
        
        // 更新订单状态
        order.setOrderStatus("待审批");
        
        // 生成审批流程ID
        order.setApprovalProcessId("AP" + System.currentTimeMillis());
        
        logger.info("订单已提交审批，当前节点: " + order.getCurrentApprovalNode());
        return order;
    }
    
    /**
     * 审批订单
     */
    public PurchaseOrder approveOrder(Long orderId, Long approverId) {
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("审批订单: " + order.getOrderCode());
        
        // 检查订单状态
        if (!"待审批".equals(order.getOrderStatus())) {
            throw new RuntimeException("订单不在待审批状态: " + order.getOrderStatus());
        }
        
        // 更新审批信息
        order.setApprovedBy(approverId);
        order.setApprovedDate(LocalDateTime.now());
        
        // 模拟审批流程
        if ("部门经理审批".equals(order.getCurrentApprovalNode())) {
            // 进入下一审批节点
            order.setCurrentApprovalNode("采购总监审批");
        } else {
            // 审批完成
            order.setOrderStatus("已审批");
            order.setCurrentApprovalNode(null);
        }
        
        logger.info("订单审批处理完成，状态: " + order.getOrderStatus());
        return order;
    }
    
    /**
     * 执行订单（正式下单）
     */
    public PurchaseOrder executeOrder(Long orderId) {
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("执行订单: " + order.getOrderCode());
        
        // 检查订单状态
        if (!"已审批".equals(order.getOrderStatus())) {
            throw new RuntimeException("订单未通过审批，无法执行");
        }
        
        // 使用表达式引擎执行订单前的最后验证
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        
        // 验证供应商状态
        Supplier supplier = supplierService.getSupplier(order.getSupplierId());
        if (supplier != null) {
            context.put("supplier", supplier);
            
            boolean supplierValid = expressionEngine.evaluateBooleanExpression("${supplier.enabled}", context);
            if (!supplierValid) {
                throw new RuntimeException("供应商已禁用，无法下单");
            }
        }
        
        // 更新订单状态
        order.setOrderStatus("已下单");
        
        logger.info("订单执行成功");
        return order;
    }
    
    /**
     * 获取订单
     */
    public PurchaseOrder getOrder(Long orderId) {
        PurchaseOrder order = orderRepository.get(orderId);
        if (order != null) {
            // 每次获取订单时重新计算虚拟字段
            calculateVirtualFields(order);
        }
        return order;
    }
    
    /**
     * 计算订单中的所有计算字段
     */
    private void calculateFields(PurchaseOrder order) {
        logger.info("计算订单字段值: " + order.getOrderCode());
        
        // 构建上下文
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        
        // 计算订单总金额（不含税）
        BigDecimal totalAmountWithoutTax = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            for (PurchaseOrderItem item : order.getOrderItems()) {
                // 计算每个订单项的金额
                calculateItemFields(item);
                // 累加总金额
                totalAmountWithoutTax = totalAmountWithoutTax.add(item.getAmountWithoutTax());
            }
        }
        order.setTotalAmountWithoutTax(totalAmountWithoutTax);
        
        // 计算税额
        Double taxRate = order.getTaxRate() != null ? order.getTaxRate() : 0.13;
        BigDecimal taxAmount = totalAmountWithoutTax.multiply(BigDecimal.valueOf(taxRate));
        order.setTaxAmount(taxAmount);
        
        // 计算含税总金额
        order.setTotalAmountWithTax(totalAmountWithoutTax.add(taxAmount));
        
        // 计算是否超时
        boolean isOverdue = "已下单".equals(order.getOrderStatus()) && 
                           order.getExpectedDeliveryDate() != null && 
                           order.getExpectedDeliveryDate().isBefore(LocalDateTime.now());
        order.setIsOverdue(isOverdue);
        
        // 计算延迟天数
        if (isOverdue && order.getExpectedDeliveryDate() != null) {
            order.setDelayDays(java.time.temporal.ChronoUnit.DAYS.between(
                order.getExpectedDeliveryDate(), LocalDateTime.now()));
        } else {
            order.setDelayDays(0L);
        }
    }
    
    /**
     * 计算订单项的字段值
     */
    private void calculateItemFields(PurchaseOrderItem item) {
        // 计算不含税金额
        BigDecimal amountWithoutTax = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        item.setAmountWithoutTax(amountWithoutTax);
        
        // 计算税额
        Double taxRate = item.getTaxRate() != null ? item.getTaxRate() : 0.13;
        BigDecimal taxAmount = amountWithoutTax.multiply(BigDecimal.valueOf(taxRate));
        item.setTaxAmount(taxAmount);
        
        // 计算含税总金额
        item.setTotalAmount(amountWithoutTax.add(taxAmount));
    }
    
    /**
     * 计算虚拟字段
     */
    private void calculateVirtualFields(PurchaseOrder order) {
        // 构建上下文
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        
        // 获取供应商信息用于订单摘要
        Supplier supplier = supplierService.getSupplier(order.getSupplierId());
        if (supplier != null) {
            context.put("supplierName", supplier.getName());
        }
        
        // 计算订单摘要
        try {
            String orderSummary = expressionEngine.evaluateExpression(
                "订单${order.orderCode} - ${supplierName} - ${order.totalAmountWithTax}元 - ${order.orderStatus}", context);
            order.setOrderSummary(orderSummary);
        } catch (Exception e) {
            logger.warning("计算订单摘要失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证业务规则
     */
    private void validateBusinessRules(PurchaseOrder order) {
        logger.info("验证订单业务规则: " + order.getOrderCode());
        
        // 构建上下文
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        
        // 验证预计金额必须大于0
        boolean amountValid = order.getEstimatedAmount() != null && 
                             order.getEstimatedAmount().compareTo(BigDecimal.ZERO) > 0;
        if (!amountValid) {
            throw new RuntimeException("预计金额必须大于0");
        }
        
        // 验证期望交货日期必须晚于当前日期
        boolean dateValid = order.getExpectedDeliveryDate() != null && 
                           order.getExpectedDeliveryDate().isAfter(LocalDateTime.now());
        if (!dateValid) {
            throw new RuntimeException("期望交货日期必须晚于当前日期");
        }
        
        // 验证订单必须包含至少一个采购项目
        boolean itemsValid = order.getOrderItems() != null && !order.getOrderItems().isEmpty();
        if (!itemsValid) {
            throw new RuntimeException("采购订单必须包含至少一个采购项目");
        }
        
        logger.info("订单业务规则验证通过");
    }
    
    /**
     * 评估是否需要多级审批
     */
    private boolean evaluateMultiLevelApproval(PurchaseOrder order) {
        // 使用表达式引擎评估多级审批条件
        // 例如：订单金额大于10万元或紧急采购需要多级审批
        Map<String, Object> context = new HashMap<>();
        context.put("order", order);
        
        String expression = "${order.totalAmountWithTax}.compareTo(java.math.BigDecimal.valueOf(100000)) > 0 || ${order.orderType}.equals('紧急采购')";
        try {
            return expressionEngine.evaluateBooleanExpression(expression, context);
        } catch (Exception e) {
            logger.warning("评估多级审批条件失败: " + e.getMessage());
            // 默认返回true，保守处理
            return true;
        }
    }
}
