package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.entity.Supplier;
import com.bone.procurement.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;

/**
 * 采购订单服务类
 * 提供采购订单的创建、审批、执行等业务逻辑
 */
@Slf4j
@Service
@CacheConfig(cacheNames = "purchaseOrders")
public class PurchaseOrderService {

    // 常量定义
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_EXECUTED = "EXECUTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String ORDER_TYPE_REGULAR = "REGULAR";
    private static final String ORDER_TYPE_EMERGENCY = "EMERGENCY";
    private static final String ORDER_TYPE_SPECIAL = "SPECIAL";
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal(10000);
    private static final double DEFAULT_TAX_RATE = 0.13;
    
    /**
     * 记录审批历史
     * @param order 订单
     * @param approverId 审批人ID
     * @param action 审批动作
     * @param comment 审批意见
     */
    private void recordApprovalHistory(PurchaseOrder order, String approverId, String action, String comment) {
        // 这里可以实现审批历史记录逻辑
        // 由于使用的是模拟存储，可以暂时记录到日志中
        log.info("审批历史 - 订单ID: {}, 审批人: {}, 动作: {}, 意见: {}", 
                order.getId(), approverId, action, comment);
    }
    
    // 每个订单使用独立的锁，提高并发性能
    private final Map<Long, Lock> orderLocks = new ConcurrentHashMap<>();
    
    // 模拟数据存储 - 修复无法解析PurchaseOrderRepository的问题
    private final Map<Long, PurchaseOrder> orderRepository = Collections.synchronizedMap(new HashMap<>());
    private final Map<Long, List<PurchaseOrderItem>> orderItemsRepository = Collections.synchronizedMap(new HashMap<>());
    private Long nextId = 1L;
    
    private final SupplierService supplierService;
    
    @Autowired
    public PurchaseOrderService(SupplierService supplierService) {
        this.supplierService = supplierService;
    }
    
    /**
     * 验证订单项
     * @param orderItems 订单项列表
     * @throws BusinessException 订单项验证失败时抛出
     */
    private void validateOrderItems(List<PurchaseOrderItem> orderItems) {
        if (CollectionUtils.isEmpty(orderItems)) {
            throw new BusinessException("采购订单必须包含至少一个订单项");
        }
        
        // 验证每个订单项
        for (int i = 0; i < orderItems.size(); i++) {
            PurchaseOrderItem item = orderItems.get(i);
            Assert.notNull(item, "订单项不能为空");
            
            // 验证必填字段
            if (item.getProductId() == null || item.getProductId() <= 0) {
                throw new BusinessException("订单项[" + i + "]: 产品ID不能为空且必须大于0");
            }
            
            if (StringUtils.isEmpty(item.getProductCode())) {
                throw new BusinessException("订单项[" + i + "]: 产品编码不能为空");
            }
            
            if (StringUtils.isEmpty(item.getProductName())) {
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
            if (StringUtils.isEmpty(item.getUnit())) {
                throw new BusinessException("订单项[" + i + "]: 单位不能为空");
            }
            
            // 设置默认税率
            if (item.getTaxRate() == null) {
                item.setTaxRate(DEFAULT_TAX_RATE);
            }
            
            // 计算订单项金额
            calculateItemAmounts(item);
        }
    }
    
    /**
     * 计算订单项金额
     * @param item 订单项
     */
    private void calculateItemAmounts(PurchaseOrderItem item) {
        // 不含税金额 = 单价 * 数量
        BigDecimal amountWithoutTax = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        item.setAmountWithoutTax(amountWithoutTax);
        
        // 税额 = 不含税金额 * 税率
        BigDecimal taxAmount = amountWithoutTax.multiply(BigDecimal.valueOf(item.getTaxRate()));
        item.setTaxAmount(taxAmount);
        
        // 含税总金额 = 不含税金额 + 税额
        BigDecimal totalAmount = amountWithoutTax.add(taxAmount);
        item.setTotalAmount(totalAmount);
    }
    
    /**
     * 计算订单字段值
     * @param order 采购订单
     */
    private void calculateFields(PurchaseOrder order) {
        // 计算金额相关字段
        calculateAmountFields(order);
        
        // 计算虚拟字段
        calculateVirtualFields(order);
        
        // 生成订单摘要
        generateOrderSummary(order);
    }
    
    /**
     * 计算订单金额相关字段
     * @param order 采购订单
     */
    private void calculateAmountFields(PurchaseOrder order) {
        if (CollectionUtils.isEmpty(order.getOrderItems())) {
            return;
        }
        
        BigDecimal totalAmountWithoutTax = BigDecimal.ZERO;
        BigDecimal totalTaxAmount = BigDecimal.ZERO;
        
        // 汇总所有订单项金额
        for (PurchaseOrderItem item : order.getOrderItems()) {
            calculateItemAmounts(item);
            totalAmountWithoutTax = totalAmountWithoutTax.add(item.getAmountWithoutTax());
            totalTaxAmount = totalTaxAmount.add(item.getTaxAmount());
        }
        
        order.setTotalAmountWithoutTax(totalAmountWithoutTax);
        order.setTaxAmount(totalTaxAmount);
        order.setTotalAmountWithTax(totalAmountWithoutTax.add(totalTaxAmount));
    }
    
    /**
     * 计算订单虚拟字段
     * @param order 采购订单
     */
    private void calculateVirtualFields(PurchaseOrder order) {
        // 计算是否逾期
        if (order.getExpectedDeliveryDate() != null && LocalDateTime.now().isAfter(order.getExpectedDeliveryDate())) {
            order.setIsOverdue(true);
            // 计算延迟天数
            long days = ChronoUnit.DAYS.between(order.getExpectedDeliveryDate(), LocalDateTime.now());
            order.setDelayDays(days);
        } else {
            order.setIsOverdue(false);
            order.setDelayDays(0L);
        }
    }
    
    /**
     * 生成订单摘要
     * @param order 采购订单
     */
    private void generateOrderSummary(PurchaseOrder order) {
        StringBuilder summary = new StringBuilder();
        summary.append("订单编号: ").append(order.getOrderCode()).append(", ");
        summary.append("类型: ").append(order.getOrderType()).append(", ");
        summary.append("金额: ").append(order.getTotalAmountWithTax()).append(", ");
        
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            summary.append("商品数量: ").append(order.getOrderItems().size()).append("项, ");
            summary.append("主要商品: ").append(order.getOrderItems().get(0).getProductName());
        }
        
        order.setOrderSummary(summary.toString());
    }
    
    /**
     * 评估是否需要多级审批
     * @param order 采购订单
     * @return 是否需要多级审批
     */
    private boolean evaluateMultiLevelApproval(PurchaseOrder order) {
        // 紧急采购订单直接进入多级审批
        if (ORDER_TYPE_EMERGENCY.equals(order.getOrderType())) {
            return true;
        }
        
        // 金额超过阈值的订单需要多级审批
        BigDecimal totalAmount = order.getTotalAmountWithTax() != null ? 
                order.getTotalAmountWithTax() : BigDecimal.ZERO;
        
        return totalAmount.compareTo(HIGH_AMOUNT_THRESHOLD) > 0;
    }
    
    /**
     * 获取订单锁
     * @param orderId 订单ID
     * @return 订单对应的锁对象
     */
    private Lock getOrderLock(Long orderId) {
        return orderLocks.computeIfAbsent(orderId, k -> new ReentrantLock());
    }
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建后的采购订单
     * @throws BusinessException 业务规则验证失败时抛出
     * @throws DataIntegrityViolationException 数据完整性错误时抛出
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CachePut(key = "#result.id")
    public PurchaseOrder createOrder(PurchaseOrder order) {
        log.info("开始创建采购订单，订单编号: {}", order != null ? order.getOrderCode() : "未知");
        
        try {
            // 使用Spring Assert进行参数验证
            Assert.notNull(order, "采购订单对象不能为空");
            
            // 设置创建时间
            LocalDateTime now = LocalDateTime.now();
            order.setCreationDate(now);
            
            // 设置订单ID (模拟自动生成)
            order.setId(nextId++);
            
            // 如果没有指定状态，设置为草稿
            if (order.getOrderStatus() == null) {
                order.setOrderStatus(STATUS_DRAFT);
            }
            
            // 验证业务规则
            validateBusinessRules(order);
            
            // 验证订单项
            validateOrderItems(order.getOrderItems());
            
            // 计算字段值
            calculateFields(order);
            
            // 保存订单到模拟存储
            orderRepository.put(order.getId(), order);
            
            // 保存订单项
            if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                orderItemsRepository.put(order.getId(), new ArrayList<>(order.getOrderItems()));
            }
            
            log.info("采购订单创建成功，ID: {}", order.getId());
            return order;
        } catch (IllegalArgumentException e) {
            log.error("创建采购订单参数验证失败: {}", e.getMessage());
            throw new BusinessException("订单创建失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("创建采购订单失败", e);
            throw new BusinessException("订单创建失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 提交订单审批
     * @param orderId 订单ID
     * @return 提交审批后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CachePut(key = "#orderId")
    public PurchaseOrder submitForApproval(Long orderId) {
        log.info("开始提交订单审批，订单ID: {}", orderId);
        
        // 验证订单ID
        Assert.notNull(orderId, "订单ID不能为空");
        Assert.isTrue(orderId > 0, "订单ID必须大于0");
        
        // 获取订单锁
        Lock lock = getOrderLock(orderId);
        
        try {
            // 加锁确保并发安全
            lock.lock();
            
            // 获取订单
            PurchaseOrder order = orderRepository.get(orderId);
            if (order == null) {
                throw new BusinessException("订单不存在: " + orderId);
            }
            
            // 验证订单状态
            if (!STATUS_DRAFT.equals(order.getOrderStatus())) {
                throw new BusinessException("只有草稿状态的订单才能提交审批，当前状态: " + order.getOrderStatus());
            }
            
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
            
            // 保存更新后的订单到模拟存储
            orderRepository.put(order.getId(), order);
            log.info("订单已提交审批，订单编号: {}, 当前节点: {}", order.getOrderCode(), order.getCurrentApprovalNode());
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("提交订单审批失败", e);
            throw new BusinessException("订单提交失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 审批订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param isApproved 是否批准
     * @param comments 审批意见
     * @return 审批后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional
    @CacheEvict(value = "purchaseOrders", key = "#orderId")
    public PurchaseOrder approveOrder(Long orderId, String approverId, String comment) {
        Lock lock = getOrderLock(orderId);
        lock.lock();
        try {
            PurchaseOrder order = getOrder(orderId);
            if (order == null) {
                throw new BusinessException("采购订单不存在");
            }
            
            // 验证订单状态
            if (!STATUS_PENDING_APPROVAL.equals(order.getOrderStatus())) {
                throw new BusinessException("订单当前状态不允许审批");
            }
            
            // 更新审批信息
            order.setLastApprover(approverId);
            order.setLastApprovalComment(comment);
            order.setLastApprovalTime(LocalDateTime.now());
            
            // 记录审批历史
            recordApprovalHistory(order, approverId, "APPROVED", comment);
            
            // 检查是否为最后一级审批
            if (order.getCurrentApprovalLevel() >= order.getMaxApprovalLevel()) {
                // 最终审批通过
                order.setOrderStatus(STATUS_APPROVED);
                order.setApprovalEndTime(LocalDateTime.now());
                order.setApprovalStatus("APPROVED");
                
                log.info("采购订单最终审批通过，ID: {}", order.getId());
            } else {
                // 进入下一级审批
                order.setCurrentApprovalLevel(order.getCurrentApprovalLevel() + 1);
                order.setApprovalStatus("PENDING_NEXT_LEVEL");
                
                log.info("采购订单进入下一级审批，ID: {}, 当前级别: {}", 
                        order.getId(), order.getCurrentApprovalLevel());
            }
            
            // 保存更新后的订单
            orderRepository.put(order.getId(), order);
            return order;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 处理审批流程
     * @param order 采购订单
     * @throws BusinessException 审批流程处理失败时抛出
     */
    private void processApprovalFlow(PurchaseOrder order) {
        // 根据订单类型和金额决定审批流程
        if (evaluateMultiLevelApproval(order)) {
            // 紧急订单或大额订单进入多级审批
            order.setApprovalLevel("MULTI_LEVEL");
            order.setCurrentApprovalLevel(1);
            order.setMaxApprovalLevel(3);
        } else {
            // 普通订单进入单级审批
            order.setApprovalLevel("SINGLE_LEVEL");
            order.setCurrentApprovalLevel(1);
            order.setMaxApprovalLevel(1);
        }
        
        // 记录审批开始时间
        order.setApprovalStartTime(LocalDateTime.now());
        order.setLastApprovalTime(LocalDateTime.now());
    }
    
    /**
     * 执行订单（正式下单）
     * @param orderId 订单ID
     * @param executorId 执行人ID
     * @return 执行后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional
    @CacheEvict(value = "purchaseOrders", key = "#orderId")
    public PurchaseOrder executeOrder(Long orderId) {
        Lock lock = getOrderLock(orderId);
        lock.lock();
        try {
            PurchaseOrder order = getOrder(orderId);
            if (order == null) {
                throw new BusinessException("采购订单不存在");
            }
            
            // 验证订单状态
            if (!STATUS_APPROVED.equals(order.getOrderStatus())) {
                throw new BusinessException("订单必须先审批通过才能执行");
            }
            
            // 验证供应商状态
            boolean isSupplierActive = supplierService.checkSupplierStatus(order.getSupplierId());
            if (!isSupplierActive) {
                throw new BusinessException("供应商状态异常，无法执行订单");
            }
            
            // 验证订单项是否有效
            List<PurchaseOrderItem> orderItems = orderItemsRepository.get(orderId);
            if (CollectionUtils.isEmpty(orderItems)) {
                throw new BusinessException("订单缺少有效订单项，无法执行");
            }
            
            // 更新订单状态
            order.setOrderStatus(STATUS_EXECUTED);
            order.setExecutionTime(LocalDateTime.now());
            
            // 保存更新后的订单
            orderRepository.put(order.getId(), order);
            
            log.info("采购订单执行成功，ID: {}", order.getId());
            return order;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取订单
     * @param orderId 订单ID
     * @return 订单对象，不存在返回null
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "purchaseOrders", key = "#orderId")
    public PurchaseOrder getOrder(Long orderId) {
        Lock lock = getOrderLock(orderId);
        lock.lock();
        try {
            // 验证订单ID
            if (orderId == null || orderId <= 0) {
                return null;
            }
            
            // 从模拟存储中获取订单
            PurchaseOrder order = orderRepository.get(orderId);
            if (order != null) {
                // 获取关联的订单项
                List<PurchaseOrderItem> orderItems = orderItemsRepository.get(orderId);
                if (orderItems != null) {
                    order.setOrderItems(new ArrayList<>(orderItems));
                }
                
                // 计算虚拟字段（如逾期状态）
                calculateVirtualFields(order);
                
                log.debug("获取订单成功，ID: {}, 状态: {}", orderId, order.getOrderStatus());
            } else {
                log.debug("订单不存在，ID: {}", orderId);
            }
            return order;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @param operatorId 操作人ID
     * @return 取消后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional
    @CacheEvict(value = "purchaseOrders", key = "#orderId")
    public PurchaseOrder cancelOrder(Long orderId, String cancelReason) {
        Lock lock = getOrderLock(orderId);
        lock.lock();
        try {
            PurchaseOrder order = getOrder(orderId);
            if (order == null) {
                throw new BusinessException("采购订单不存在");
            }
            
            // 验证订单状态是否可以取消
            if (STATUS_APPROVED.equals(order.getOrderStatus()) || 
                    STATUS_EXECUTED.equals(order.getOrderStatus()) || 
                    STATUS_CANCELLED.equals(order.getOrderStatus())) {
                throw new BusinessException("当前订单状态不允许取消");
            }
            
            // 更新订单状态和取消信息
            order.setOrderStatus(STATUS_CANCELLED);
            order.setCancelReason(cancelReason);
            order.setCancelTime(LocalDateTime.now());
            
            // 如果订单正在审批中，记录审批结束时间
            if (STATUS_PENDING_APPROVAL.equals(order.getOrderStatus())) {
                order.setApprovalEndTime(LocalDateTime.now());
                order.setApprovalStatus("CANCELLED");
            }
            
            // 保存更新后的订单
            orderRepository.put(order.getId(), order);
            
            log.info("采购订单已取消，ID: {}", order.getId());
            return order;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取订单的专用锁
     */
    private Lock getOrderLock(Long orderId) {
        return orderLocks.computeIfAbsent(orderId, k -> new ReentrantLock(true));
    }
    
    /**
     * 计算订单中的所有计算字段
     */
    private void calculateFields(PurchaseOrder order) {
        if (order == null) {
            return;
        }
        
        log.debug("计算订单字段值: {}", order.getOrderCode());
        
        try {
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
        } catch (Exception e) {
            log.error("计算订单字段失败: {}", e.getMessage(), e);
            throw new RuntimeException("计算订单字段失败", e);
        }
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
            log.error("计算订单项字段失败: {}", e.getMessage(), e);
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
            log.warn("计算订单摘要失败: {}", e.getMessage(), e);
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
        
        log.debug("验证订单业务规则: {}", order.getOrderCode());
        
        // 验证订单编号
        Assert.hasText(order.getOrderCode(), "订单编号不能为空");
        
        // 验证预计金额必须大于0
        Assert.notNull(order.getEstimatedAmount(), "预计金额不能为空");
        Assert.isTrue(order.getEstimatedAmount().compareTo(BigDecimal.ZERO) > 0, "预计金额必须大于0");
        
        // 验证供应商ID
        Assert.notNull(order.getSupplierId(), "供应商ID不能为空");
        
        // 验证期望交货日期必须晚于当前日期
        Assert.notNull(order.getExpectedDeliveryDate(), "期望交货日期不能为空");
        Assert.isTrue(order.getExpectedDeliveryDate().isAfter(LocalDateTime.now()), "期望交货日期必须晚于当前日期");
        
        // 验证订单必须包含至少一个采购项目
        Assert.notEmpty(order.getOrderItems(), "采购订单必须包含至少一个采购项目");
        
        // 验证订单项
        validateOrderItems(order.getOrderItems());
        
        log.debug("订单业务规则验证通过: {}", order.getOrderCode());
    }
    
    /**
     * 验证订单项
     */
    private void validateOrderItems(List<PurchaseOrderItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            if (item == null) {
                throw new IllegalArgumentException("订单项 " + i + " 不能为空");
            }
            
            // 验证数量
            Assert.notNull(item.getQuantity(), "订单项 " + i + " 数量不能为空");
            Assert.isTrue(item.getQuantity() > 0, "订单项 " + i + " 数量必须大于0");
            
            // 验证单价
            Assert.notNull(item.getUnitPrice(), "订单项 " + i + " 单价不能为空");
            Assert.isTrue(item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0, "订单项 " + i + " 单价必须大于0");
            
            // 验证产品信息
            Assert.hasText(item.getProductCode(), "订单项 " + i + " 产品编码不能为空");
            Assert.hasText(item.getProductName(), "订单项 " + i + " 产品名称不能为空");
            
            // 订单项已在前面验证，不再设置序号
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
        
        log.debug("订单: {}，高额订单: {}, 紧急订单: {}, 需要多级审批: {}", 
            order.getOrderCode(), highAmount, emergencyOrder, highAmount || emergencyOrder);
        
        return highAmount || emergencyOrder;
    }
}
