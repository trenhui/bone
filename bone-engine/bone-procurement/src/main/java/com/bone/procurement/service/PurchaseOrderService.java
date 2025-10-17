package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.entity.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 采购订单服务类
 * 提供采购订单的创建、审批、执行等业务逻辑
 */
@Service
public class PurchaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderService.class);
    
    // 常量定义
    private static final String STATUS_DRAFT = "草稿";
    private static final String STATUS_PENDING_APPROVAL = "待审批";
    private static final String STATUS_APPROVED = "已审批";
    private static final String STATUS_ORDERED = "已下单";
    private static final String ORDER_TYPE_EMERGENCY = "紧急采购";
    private static final String SUPPLIER_STATUS_ACTIVE = "合作中";
    private static final String APPROVAL_NODE_DEPT_MANAGER = "部门经理审批";
    private static final String APPROVAL_NODE_PURCHASE_MANAGER = "采购经理审批";
    private static final String APPROVAL_NODE_DIRECTOR = "采购总监审批";
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("100000.00");
    private static final double DEFAULT_TAX_RATE = 0.13;
    
    // 模拟数据存储
    private final Map<Long, PurchaseOrder> orderRepository = Collections.synchronizedMap(new HashMap<>());
    private long nextId = 1;
    
    private final SupplierService supplierService;
    
    @Autowired
    public PurchaseOrderService(SupplierService supplierService) {
        this.supplierService = supplierService;
    }
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建后的采购订单
     * @throws IllegalArgumentException 参数验证失败时抛出
     */
    @Transactional
    public PurchaseOrder createOrder(PurchaseOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("采购订单对象不能为空");
        }
        
        logger.info("创建采购订单: {}", order.getOrderCode());
        
        try {
            // 设置创建时间
            order.setCreationDate(LocalDateTime.now());
            
            // 如果没有指定状态，设置为草稿
            if (order.getOrderStatus() == null) {
                order.setOrderStatus(STATUS_DRAFT);
            }
            
            // 验证业务规则
            validateBusinessRules(order);
            
            // 计算字段值
            calculateFields(order);
            
            // 保存订单（线程安全）
            long id;
            synchronized (this) {
                id = nextId++;
                order.setId(id);
                orderRepository.put(id, order);
            }
            
            logger.info("采购订单创建成功，ID: {}", id);
            return order;
        } catch (Exception e) {
            logger.error("创建采购订单失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 提交订单审批
     * @param orderId 订单ID
     * @return 提交审批后的订单
     * @throws IllegalArgumentException 参数验证失败时抛出
     * @throws RuntimeException 业务逻辑错误时抛出
     */
    @Transactional
    public PurchaseOrder submitForApproval(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("提交订单审批: {}", order.getOrderCode());
        
        try {
            // 重新计算字段值确保最新
            calculateFields(order);
            
            // 评估是否需要多级审批
            boolean requiresMultiLevelApproval = evaluateMultiLevelApproval(order);
            
            // 设置审批节点
            String firstApprovalNode = requiresMultiLevelApproval ? APPROVAL_NODE_DEPT_MANAGER : APPROVAL_NODE_PURCHASE_MANAGER;
            order.setCurrentApprovalNode(firstApprovalNode);
            
            // 更新订单状态
            order.setOrderStatus(STATUS_PENDING_APPROVAL);
            
            // 生成审批流程ID
            order.setApprovalProcessId("AP" + System.currentTimeMillis());
            
            logger.info("订单已提交审批，当前节点: {}", order.getCurrentApprovalNode());
            return order;
        } catch (Exception e) {
            logger.error("提交订单审批失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 审批订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @return 审批后的订单
     * @throws IllegalArgumentException 参数验证失败时抛出
     * @throws RuntimeException 业务逻辑错误时抛出
     */
    @Transactional
    public PurchaseOrder approveOrder(Long orderId, Long approverId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        if (approverId == null || approverId <= 0) {
            throw new IllegalArgumentException("审批人ID无效");
        }
        
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("审批订单: {}，审批人ID: {}", order.getOrderCode(), approverId);
        
        // 检查订单状态
        if (!STATUS_PENDING_APPROVAL.equals(order.getOrderStatus())) {
            throw new RuntimeException("订单不在待审批状态: " + order.getOrderStatus());
        }
        
        // 更新审批信息
        order.setApprovedBy(approverId);
        order.setApprovedDate(LocalDateTime.now());
        
        // 处理审批流程
        processApprovalFlow(order);
        
        logger.info("订单审批处理完成，状态: {}", order.getOrderStatus());
        return order;
    }
    
    /**
     * 处理审批流程
     */
    private void processApprovalFlow(PurchaseOrder order) {
        String currentNode = order.getCurrentApprovalNode();
        
        if (APPROVAL_NODE_DEPT_MANAGER.equals(currentNode)) {
            // 部门经理审批通过，进入采购总监审批
            order.setCurrentApprovalNode(APPROVAL_NODE_DIRECTOR);
        } else if (APPROVAL_NODE_DIRECTOR.equals(currentNode) || APPROVAL_NODE_PURCHASE_MANAGER.equals(currentNode)) {
            // 采购总监或采购经理审批通过，审批完成
            order.setOrderStatus(STATUS_APPROVED);
            order.setCurrentApprovalNode(null);
        } else {
            throw new RuntimeException("无效的审批节点: " + currentNode);
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param orderId 订单ID
     * @return 执行后的订单
     * @throws IllegalArgumentException 参数验证失败时抛出
     * @throws RuntimeException 业务逻辑错误时抛出
     */
    @Transactional
    public PurchaseOrder executeOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单ID无效");
        }
        
        PurchaseOrder order = getOrder(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderId);
        }
        
        logger.info("执行订单: {}", order.getOrderCode());
        
        // 检查订单状态
        if (!STATUS_APPROVED.equals(order.getOrderStatus())) {
            throw new RuntimeException("订单未通过审批，无法执行");
        }
        
        // 获取供应商信息
        Supplier supplier = supplierService.getSupplier(order.getSupplierId())
                .orElseThrow(() -> new RuntimeException("供应商不存在，无法下单: " + order.getSupplierId()));
        
        // 检查供应商合作状态
        if (!SUPPLIER_STATUS_ACTIVE.equals(supplier.getCooperationStatus())) {
            throw new RuntimeException("供应商不在合作状态，无法下单");
        }
        
        // 更新订单状态
        order.setOrderStatus(STATUS_ORDERED);
        order.setExecutionDate(LocalDateTime.now());
        
        logger.info("订单执行成功");
        return order;
    }
    
    /**
     * 获取订单
     * @param orderId 订单ID
     * @return 订单对象，不存在返回null
     */
    public PurchaseOrder getOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return null;
        }
        
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
        if (order == null) {
            return;
        }
        
        logger.debug("计算订单字段值: {}", order.getOrderCode());
        
        // 计算订单总金额（不含税）
        BigDecimal totalAmountWithoutTax = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = Optional.ofNullable(order.getOrderItems())
                .orElse(Collections.emptyList());
        
        for (PurchaseOrderItem item : items) {
            // 计算每个订单项的金额
            calculateItemFields(item);
            // 累加总金额
            totalAmountWithoutTax = totalAmountWithoutTax.add(
                    Optional.ofNullable(item.getAmountWithoutTax()).orElse(BigDecimal.ZERO));
        }
        order.setTotalAmountWithoutTax(totalAmountWithoutTax);
        
        // 计算税额
        double taxRate = Optional.ofNullable(order.getTaxRate()).orElse(DEFAULT_TAX_RATE);
        BigDecimal taxAmount = totalAmountWithoutTax.multiply(BigDecimal.valueOf(taxRate));
        order.setTaxAmount(taxAmount);
        
        // 计算含税总金额
        order.setTotalAmountWithTax(totalAmountWithoutTax.add(taxAmount));
        
        // 计算是否超时
        boolean isOverdue = STATUS_ORDERED.equals(order.getOrderStatus()) && 
                           order.getExpectedDeliveryDate() != null && 
                           order.getExpectedDeliveryDate().isBefore(LocalDateTime.now());
        order.setIsOverdue(isOverdue);
        
        // 计算延迟天数
        Long delayDays = 0L;
        if (isOverdue && order.getExpectedDeliveryDate() != null) {
            delayDays = ChronoUnit.DAYS.between(
                order.getExpectedDeliveryDate(), LocalDateTime.now());
        }
        order.setDelayDays(delayDays);
    }
    
    /**
     * 计算订单项的字段值
     */
    private void calculateItemFields(PurchaseOrderItem item) {
        if (item == null) {
            return;
        }
        
        try {
            // 计算不含税金额
            BigDecimal unitPrice = Optional.ofNullable(item.getUnitPrice()).orElse(BigDecimal.ZERO);
            int quantity = Optional.ofNullable(item.getQuantity()).orElse(0);
            BigDecimal amountWithoutTax = unitPrice.multiply(BigDecimal.valueOf(quantity));
            item.setAmountWithoutTax(amountWithoutTax);
            
            // 计算税额
            double taxRate = Optional.ofNullable(item.getTaxRate()).orElse(DEFAULT_TAX_RATE);
            BigDecimal taxAmount = amountWithoutTax.multiply(BigDecimal.valueOf(taxRate));
            item.setTaxAmount(taxAmount);
            
            // 计算含税总金额
            item.setTotalAmount(amountWithoutTax.add(taxAmount));
        } catch (Exception e) {
            logger.error("计算订单项字段失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算订单项字段失败", e);
        }
    }
    
    /**
     * 计算虚拟字段
     */
    private void calculateVirtualFields(PurchaseOrder order) {
        if (order == null) {
            return;
        }
        
        try {
            // 获取供应商信息
            String supplierName = supplierService.getSupplier(order.getSupplierId())
                    .map(Supplier::getName)
                    .orElse("未知供应商");
            
            // 计算订单摘要
            String orderSummary = String.format("订单%s - %s - %s元 - %s", 
                order.getOrderCode(), 
                supplierName, 
                Optional.ofNullable(order.getTotalAmountWithTax()).orElse(BigDecimal.ZERO), 
                order.getOrderStatus());
            order.setOrderSummary(orderSummary);
        } catch (Exception e) {
            logger.warn("计算订单摘要失败: {}", e.getMessage(), e);
            // 即使失败也不中断流程
            order.setOrderSummary("订单摘要计算失败");
        }
    }
    
    /**
     * 验证业务规则
     * @throws IllegalArgumentException 验证失败时抛出
     */
    private void validateBusinessRules(PurchaseOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("采购订单对象不能为空");
        }
        
        logger.debug("验证订单业务规则: {}", order.getOrderCode());
        
        // 验证订单编号
        if (order.getOrderCode() == null || order.getOrderCode().trim().isEmpty()) {
            throw new IllegalArgumentException("订单编号不能为空");
        }
        
        // 验证预计金额必须大于0
        if (order.getEstimatedAmount() == null || 
            order.getEstimatedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("预计金额必须大于0");
        }
        
        // 验证期望交货日期必须晚于当前日期
        if (order.getExpectedDeliveryDate() == null || 
            !order.getExpectedDeliveryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("期望交货日期必须晚于当前日期");
        }
        
        // 验证订单必须包含至少一个采购项目
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            throw new IllegalArgumentException("采购订单必须包含至少一个采购项目");
        }
        
        // 验证订单项
        validateOrderItems(order.getOrderItems());
        
        logger.debug("订单业务规则验证通过: {}", order.getOrderCode());
    }
    
    /**
     * 验证订单项
     */
    private void validateOrderItems(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            if (item == null) {
                throw new IllegalArgumentException("订单项 " + i + " 不能为空");
            }
            
            // 验证数量
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("订单项 " + i + " 数量必须大于0");
            }
            
            // 验证单价
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("订单项 " + i + " 单价必须大于0");
            }
            
            // 验证产品信息
            if (item.getProductCode() == null || item.getProductCode().trim().isEmpty()) {
                throw new IllegalArgumentException("订单项 " + i + " 产品编码不能为空");
            }
            if (item.getProductName() == null || item.getProductName().trim().isEmpty()) {
                throw new IllegalArgumentException("订单项 " + i + " 产品名称不能为空");
            }
        }
    }
    
    /**
     * 评估是否需要多级审批
     */
    private boolean evaluateMultiLevelApproval(PurchaseOrder order) {
        if (order == null) {
            return false;
        }
        
        // 订单金额大于10万元或紧急采购需要多级审批
        boolean highAmount = order.getTotalAmountWithTax() != null && 
                            order.getTotalAmountWithTax().compareTo(HIGH_AMOUNT_THRESHOLD) > 0;
        boolean emergencyOrder = ORDER_TYPE_EMERGENCY.equals(order.getOrderType());
        
        logger.debug("订单: {}，高额订单: {}, 紧急订单: {}, 需要多级审批: {}", 
            order.getOrderCode(), highAmount, emergencyOrder, highAmount || emergencyOrder);
        
        return highAmount || emergencyOrder;
    }
}
