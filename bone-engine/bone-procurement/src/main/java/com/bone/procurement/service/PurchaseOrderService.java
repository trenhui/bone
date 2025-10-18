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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 采购订单服务类
 * 提供采购订单的创建、审批、执行等业务逻辑
 */
@Slf4j
@Service
@CacheConfig(cacheNames = "purchaseOrders")
public class PurchaseOrderService {

    // 订单状态常量定义
    public static final String STATUS_DRAFT = "草稿";
    public static final String STATUS_PENDING_APPROVAL = "待审批";
    public static final String STATUS_APPROVED = "已审批";
    public static final String STATUS_ORDERED = "已下单";
    public static final String STATUS_REJECTED = "已拒绝";
    public static final String STATUS_CANCELLED = "已取消";
    
    // 订单类型常量
    public static final String ORDER_TYPE_EMERGENCY = "紧急采购";
    
    // 供应商状态常量
    public static final String SUPPLIER_STATUS_ACTIVE = "合作中";
    
    // 审批节点常量
    public static final String APPROVAL_NODE_DEPT_MANAGER = "部门经理审批";
    public static final String APPROVAL_NODE_PURCHASE_MANAGER = "采购经理审批";
    public static final String APPROVAL_NODE_DIRECTOR = "采购总监审批";
    
    // 业务阈值和默认值
    public static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal("100000.00");
    public static final double DEFAULT_TAX_RATE = 0.13;
    
    // 每个订单使用独立的锁，提高并发性能
    private final Map<Long, Lock> orderLocks = new ConcurrentHashMap<>();
    
    // 模拟数据存储 - 修复无法解析PurchaseOrderRepository的问题
    private final Map<Long, PurchaseOrder> orderRepository = Collections.synchronizedMap(new HashMap<>());
    private Long nextId = 1L;
    
    private final SupplierService supplierService;
    
    @Autowired
    public PurchaseOrderService(SupplierService supplierService) {
        this.supplierService = supplierService;
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
            
            // 计算字段值
            calculateFields(order);
            
            // 保存订单到模拟存储
            orderRepository.put(order.getId(), order);
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
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CachePut(key = "#orderId")
    public PurchaseOrder approveOrder(Long orderId, Long approverId, boolean isApproved, String comments) {
        log.info("开始审批订单，订单ID: {}, 审批人ID: {}, 审批结果: {}", 
                orderId, approverId, isApproved ? "批准" : "拒绝");
        
        // 验证参数
        Assert.notNull(orderId, "订单ID不能为空");
        Assert.isTrue(orderId > 0, "订单ID必须大于0");
        Assert.notNull(approverId, "审批人ID不能为空");
        Assert.isTrue(approverId > 0, "审批人ID必须大于0");
        
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
            
            // 检查订单状态
            if (!STATUS_PENDING_APPROVAL.equals(order.getOrderStatus())) {
                throw new BusinessException("订单不在待审批状态: " + order.getOrderStatus());
            }
            
            // 更新审批信息
            order.setApprovedBy(approverId);
            order.setApprovedDate(LocalDateTime.now());
            
            if (isApproved) {
                // 处理审批流程
                processApprovalFlow(order);
                log.info("订单审批通过，订单编号: {}, 新状态: {}", order.getOrderCode(), order.getOrderStatus());
            } else {
                // 拒绝订单
                order.setOrderStatus(STATUS_REJECTED);
                order.setCurrentApprovalNode(null);
                log.info("订单审批拒绝，订单编号: {}", order.getOrderCode());
            }
            
            // 保存更新后的订单到模拟存储
            orderRepository.put(order.getId(), order);
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("订单审批失败", e);
            throw new BusinessException("订单审批失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 处理审批流程
     * @throws BusinessException 审批流程异常时抛出
     */
    private void processApprovalFlow(PurchaseOrder order) {
        String currentNode = order.getCurrentApprovalNode();
        
        if (APPROVAL_NODE_DEPT_MANAGER.equals(currentNode)) {
            // 部门经理审批通过，进入采购总监审批
            order.setCurrentApprovalNode(APPROVAL_NODE_DIRECTOR);
            log.debug("订单{}从{}节点流转到{}节点", 
                    order.getOrderCode(), APPROVAL_NODE_DEPT_MANAGER, APPROVAL_NODE_DIRECTOR);
        } else if (APPROVAL_NODE_DIRECTOR.equals(currentNode) || APPROVAL_NODE_PURCHASE_MANAGER.equals(currentNode)) {
            // 采购总监或采购经理审批通过，审批完成
            order.setOrderStatus(STATUS_APPROVED);
            order.setCurrentApprovalNode(null);
            log.debug("订单{}审批完成，进入已审批状态", order.getOrderCode());
        } else {
            throw new BusinessException("无效的审批节点: " + currentNode);
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param orderId 订单ID
     * @param executorId 执行人ID
     * @return 执行后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CachePut(key = "#orderId")
    public PurchaseOrder executeOrder(Long orderId, Long executorId) {
        log.info("开始执行订单，订单ID: {}, 执行人ID: {}", orderId, executorId);
        
        // 验证参数
        Assert.notNull(orderId, "订单ID不能为空");
        Assert.isTrue(orderId > 0, "订单ID必须大于0");
        Assert.notNull(executorId, "执行人ID不能为空");
        Assert.isTrue(executorId > 0, "执行人ID必须大于0");
        
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
            
            // 检查订单状态
            if (!STATUS_APPROVED.equals(order.getOrderStatus())) {
                throw new BusinessException("订单未通过审批，无法执行，当前状态: " + order.getOrderStatus());
            }
            
            // 获取供应商信息
            Supplier supplier = supplierService.getSupplier(order.getSupplierId())
                    .orElseThrow(() -> new BusinessException("供应商不存在，无法下单: " + order.getSupplierId()));
            
            // 检查供应商合作状态
            if (!SUPPLIER_STATUS_ACTIVE.equals(supplier.getCooperationStatus())) {
                throw new BusinessException("供应商不在合作状态，无法下单，供应商状态: " + supplier.getCooperationStatus());
            }
            
            // 更新订单状态和执行信息
            order.setOrderStatus(STATUS_ORDERED);
            order.setExecutionDate(LocalDateTime.now());
            
            log.info("订单执行成功，订单编号: {}", order.getOrderCode());
            // 保存更新后的订单到模拟存储
            orderRepository.put(order.getId(), order);
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("订单执行失败", e);
            throw new BusinessException("订单执行失败: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取订单
     * @param orderId 订单ID
     * @return 订单对象，不存在返回null
     */
    @Cacheable(key = "#orderId", unless = "#result == null")
    public PurchaseOrder getOrder(Long orderId) {
        log.debug("获取订单，订单ID: {}", orderId);
        
        // 验证订单ID
        if (orderId == null || orderId <= 0) {
            return null;
        }
        
        // 从模拟存储获取订单
        PurchaseOrder order = orderRepository.get(orderId);
        if (order != null) {
            // 每次获取订单时重新计算虚拟字段
            calculateVirtualFields(order);
        }
        return order;
    }
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @param operatorId 操作人ID
     * @return 取消后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    @CachePut(key = "#orderId")
    public PurchaseOrder cancelOrder(Long orderId, Long operatorId) {
        log.info("开始取消订单，订单ID: {}, 操作人ID: {}", orderId, operatorId);
        
        // 验证参数
        Assert.notNull(orderId, "订单ID不能为空");
        Assert.isTrue(orderId > 0, "订单ID必须大于0");
        Assert.notNull(operatorId, "操作人ID不能为空");
        Assert.isTrue(operatorId > 0, "操作人ID必须大于0");
        
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
            if (STATUS_ORDERED.equals(order.getOrderStatus()) || 
                STATUS_CANCELLED.equals(order.getOrderStatus()) || 
                STATUS_REJECTED.equals(order.getOrderStatus())) {
                throw new BusinessException("订单状态不允许取消: " + order.getOrderStatus());
            }
            
            // 更新订单状态
            order.setOrderStatus(STATUS_CANCELLED);
            
            log.info("订单取消成功，订单编号: {}", order.getOrderCode());
            // 保存更新后的订单到模拟存储
            orderRepository.put(order.getId(), order);
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("订单取消失败", e);
            throw new BusinessException("订单取消失败: " + e.getMessage(), e);
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
