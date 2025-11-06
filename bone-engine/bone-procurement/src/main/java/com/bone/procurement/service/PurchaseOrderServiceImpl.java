package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.exception.BusinessException;
import com.bone.procurement.exception.OrderNotFoundException;
import com.bone.procurement.model.OrderStatus;
import com.bone.procurement.model.OrderEvent;
import com.bone.procurement.repository.PurchaseOrderRepository;
import com.bone.procurement.state.PurchaseOrderStateHandler;
import com.bone.procurement.state.PurchaseOrderStateHandler.OrderProcessingResult;
import com.bone.procurement.lock.DistributedLockManager;
import com.bone.smartmeta.engine.service.DynamicDataService;
import com.bone.smartmeta.engine.service.GenericOperationService;
import com.bone.smartmeta.engine.service.BusinessRuleEngine;
import com.bone.smartmeta.engine.model.RuleResult;
import com.bone.smartmeta.engine.util.EntityObjectConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 采购订单服务实现类
 * 提供采购订单的创建、审批、执行等业务逻辑实现
 */
@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);
    
    // 金额阈值和税率常量
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD = new BigDecimal(10000);
    
    // 依赖注入
    private final PurchaseOrderRepository repository;
    private final PurchaseOrderStateHandler stateHandler;
    private final DistributedLockManager lockManager;
    private final BusinessRuleEngine ruleEngine;
    private final EntityObjectConverter converter;
    private final SupplierService supplierService;
    
    // 保留原有元数据引擎服务以兼容现有功能
    private final DynamicDataService dynamicDataService;
    private final GenericOperationService genericOperationService;
    
    @Autowired
    public PurchaseOrderServiceImpl(SupplierService supplierService,
                               DynamicDataService dynamicDataService,
                               GenericOperationService genericOperationService,
                               PurchaseOrderRepository repository,
                               PurchaseOrderStateHandler stateHandler,
                               DistributedLockManager lockManager,
                               BusinessRuleEngine ruleEngine,
                               EntityObjectConverter converter,
                               OrderCacheService orderCacheService) {
        this.supplierService = supplierService;
        this.dynamicDataService = dynamicDataService;
        this.genericOperationService = genericOperationService;
        this.repository = repository;
        this.stateHandler = stateHandler;
        this.lockManager = lockManager;
        this.ruleEngine = ruleEngine;
        this.converter = converter;
        this.orderCacheService = orderCacheService;
    }
    
    private final OrderCacheService orderCacheService;

    /**
     * 更新订单信息
     * @param order 待更新的订单
     * @return 更新后的订单
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder updateOrder(PurchaseOrder order) throws BusinessException {
        if (order == null || order.getId() == null) {
            throw new BusinessException("订单信息或ID不能为空");
        }
        
        String lockKey = "purchase_order:" + order.getId();
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 验证订单是否存在
                PurchaseOrder existingOrder = repository.findById(order.getId())
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 检查订单状态是否允许更新
                if (!OrderStatus.DRAFT.name().equals(existingOrder.getOrderStatus())) {
                    throw new BusinessException("只有草稿状态的订单可以更新");
                }
                
                // 验证订单项
                validateOrderItems(order.getOrderItems());
                
                // 重新计算订单字段
                calculateFields(order);
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                // 更新缓存
                orderCacheService.cacheOrder(updatedOrder);
                
                // 清除列表缓存
                orderCacheService.evictOrderListCache();
                
                log.info("订单更新成功: ID={}", order.getId());
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("更新订单过程被中断");
        } catch (BusinessException e) {
            log.error("更新订单失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 记录审批历史
     * @param order 订单
     * @param approverId 审批人ID
     * @param action 审批动作
     * @param comment 审批意见
     */
    private void recordApprovalHistory(PurchaseOrder order, String approverId, String action, String comment) {
        // 这里可以实现审批历史记录逻辑
        log.info("审批历史 - 订单ID: {}, 审批人: {}, 动作: {}, 意见: {}", 
                order.getId(), approverId, action, comment);
    }
    
    /**
     * 验证订单项
     * @param orderItems 订单项列表
     * @throws BusinessException 验证失败时抛出异常
     */
    private void validateOrderItems(List<PurchaseOrderItem> orderItems) {
        // 使用业务规则引擎进行验证
        ruleEngine.validateOrderItems(orderItems);
    }
    
    /**
     * 计算订单字段值
     * @param order 订单对象
     */
    private void calculateOrderFields(PurchaseOrder order) {
        // 委托给业务规则引擎计算订单字段
        ruleEngine.calculateOrderFields(order);
    }
    
    /**
     * 计算订单项字段值
     * @param item 采购订单项
     */

    
        // 不再需要内部锁管理，使用分布式锁替代
    
    /**
     * 计算订单字段（金额、税额等）
     * @param order 订单对象
     */
    private void calculateFields(PurchaseOrder order) {
        // 使用业务规则引擎计算订单字段
        ruleEngine.calculateOrderFields(order);
    }
    
    /**
     * 使用元数据引擎处理实体计算字段
     * @param order 采购订单
     */
    private void processCalculatedFields(PurchaseOrder order) {
        if (order == null) {
            return;
        }
        
        log.debug("通过元数据引擎处理计算字段");
        
        try {
            // 转换为Map，交给元数据引擎处理计算字段
            Map<String, Object> orderMap = convertToMap(order);
            
            // 调用元数据引擎处理计算字段
            Map<String, Object> processedMap = dynamicDataService.validateData("PurchaseOrder", orderMap);
            
            // 更新计算结果回订单对象
            updateOrderFromMap(order, processedMap);
            
            log.debug("元数据引擎计算字段处理完成");
        } catch (Exception e) {
            log.error("元数据引擎处理计算字段失败", e);
            // 即使计算失败也不中断流程
        }
    }
    
    /**
     * 将PurchaseOrder对象转换为Map
     */
    private Map<String, Object> convertToMap(PurchaseOrder order) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("orderCode", order.getOrderCode());
        map.put("supplierId", order.getSupplierId());
        map.put("orderType", order.getOrderType());
        map.put("orderStatus", order.getOrderStatus());
        map.put("estimatedAmount", order.getEstimatedAmount());
        map.put("expectedDeliveryDate", order.getExpectedDeliveryDate());
        map.put("taxRate", order.getTaxRate());
        map.put("totalAmountWithoutTax", order.getTotalAmountWithoutTax());
        map.put("taxAmount", order.getTaxAmount());
        map.put("totalAmountWithTax", order.getTotalAmountWithTax());
        map.put("orderItems", order.getOrderItems());
        map.put("creationDate", order.getCreationDate());
        map.put("createdBy", order.getCreatedBy());
        map.put("approvedBy", order.getApprovedBy());
        map.put("approvedDate", order.getApprovedDate());
        map.put("executionDate", order.getExecutionDate());
        map.put("currentApprovalNode", order.getCurrentApprovalNode());
        map.put("approvalProcessId", order.getApprovalProcessId());
        map.put("deliveryAddress", order.getDeliveryAddress());
        map.put("paymentTerms", order.getPaymentTerms());
        map.put("deliveryMethod", order.getDeliveryMethod());
        map.put("trackingNumber", order.getTrackingNumber());
        map.put("internalRemarks", order.getInternalRemarks());
        map.put("externalRemarks", order.getExternalRemarks());
        return map;
    }
    
    /**
     * 从Map更新PurchaseOrder对象
     */
    private void updateOrderFromMap(PurchaseOrder order, Map<String, Object> map) {
        if (map.containsKey("isOverdue")) order.setIsOverdue((Boolean) map.get("isOverdue"));
        if (map.containsKey("delayDays")) order.setDelayDays((Long) map.get("delayDays"));
        if (map.containsKey("orderSummary")) order.setOrderSummary((String) map.get("orderSummary"));
        // 可以根据需要更新更多计算字段
    }
    
    /**
     * 评估订单是否需要多级审批
     * @param order 订单对象
     * @return 是否需要多级审批
     */
    private boolean evaluateMultiLevelApproval(PurchaseOrder order) {
        // 使用业务规则引擎评估是否需要多级审批
        return ruleEngine.requiresMultiLevelApproval(order);
    }
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建后的采购订单
     * @throws BusinessException 业务规则验证失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder createOrder(PurchaseOrder order) throws BusinessException {
        // 验证订单参数
        Assert.notNull(order, "订单对象不能为空");
        log.info("开始创建采购订单");
        
        try {
            // 验证业务规则
            validateBusinessRules(order);
            
            // 验证订单项
            validateOrderItems(order.getOrderItems());
            
            // 设置初始状态
            order.setOrderStatus(OrderStatus.DRAFT.name());
            
            // 使用状态处理器进行状态初始化
            OrderProcessingResult result = stateHandler.handleEvent(order, OrderEvent.CREATE);
            if (!result.isSuccess()) {
                throw new BusinessException("订单状态初始化失败: " + result.getErrorMessage());
            }
            
            // 计算订单字段
            calculateFields(order);
            
            // 使用Repository保存到数据库
            PurchaseOrder savedOrder = repository.save(order);
            
            // 缓存订单
            orderCacheService.cacheOrder(savedOrder);
            
            // 清除列表缓存
            orderCacheService.evictOrderListCache();
            
            log.info("采购订单创建成功，ID: {}", savedOrder.getId());
            return savedOrder;
        } catch (BusinessException e) {
            log.error("采购订单创建失败: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("采购订单创建时发生系统错误", e);
            throw new BusinessException("创建订单失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 提交订单进行审批
     * @param orderId 订单ID
     * @param submitterId 提交人ID
     * @return 提交后的订单
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder submitForApproval(Long orderId, String submitterId) throws BusinessException {
        log.info("提交订单审批，ID: {}, 提交人ID: {}", orderId, submitterId);
        
        String lockKey = "purchase_order:" + orderId;
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 获取订单信息
                PurchaseOrder order = repository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 使用状态处理器处理提交审批事件
                OrderProcessingResult result = stateHandler.submitForApproval(order, submitterId);
                
                if (!result.isSuccess()) {
                    throw new BusinessException("提交审批失败: " + result.getErrorMessage());
                }
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                log.info("订单提交审批成功: ID={}, 提交人={}", orderId, submitterId);
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("提交审批被中断");
        } catch (BusinessException e) {
            log.error("提交订单审批失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 审批订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param comment 审批意见
     * @return 审批后的订单
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder approveOrder(Long orderId, String approverId, String comment) throws BusinessException {
        String lockKey = "purchase_order:" + orderId;
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 获取订单信息
                PurchaseOrder order = repository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 使用状态处理器处理审批事件
                OrderProcessingResult result = stateHandler.approveOrder(order, approverId, comment);
                
                if (!result.isSuccess()) {
                    throw new BusinessException("审批失败: " + result.getErrorMessage());
                }
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                log.info("订单审批成功: ID={}, 审批人={}", orderId, approverId);
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("审批过程被中断");
        } catch (BusinessException e) {
            log.error("订单审批失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 处理审批流程
     * @param order 采购订单
     */
    private void processApprovalFlow(PurchaseOrder order) {
        if (order == null) {
            return;
        }
        
        log.debug("开始处理订单审批流程");
        
        try {
            // 使用业务规则引擎确定是否需要多级审批
            boolean requiresMultiLevelApproval = evaluateMultiLevelApproval(order);
            
            if (requiresMultiLevelApproval) {
                // 根据订单金额确定审批级别
                log.info("订单ID: {}, 需要多级审批流程", order.getId());
                // 后续可通过ruleEngine获取具体审批级别
            } else {
                log.info("订单ID: {}, 需要一级审批", order.getId());
            }
            
            log.debug("订单审批流程处理完成");
        } catch (Exception e) {
            log.error("处理订单审批流程失败", e);
        }
    }
    
    /**
     * 拒绝订单
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param comment 拒绝原因
     * @return 拒绝后的订单
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder rejectOrder(Long orderId, String approverId, String comment) throws BusinessException {
        String lockKey = "purchase_order:" + orderId;
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 获取订单信息
                PurchaseOrder order = repository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 使用状态处理器处理拒绝事件
                OrderProcessingResult result = stateHandler.rejectOrder(order, approverId, comment);
                
                if (!result.isSuccess()) {
                    throw new BusinessException("拒绝订单失败: " + result.getErrorMessage());
                }
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                log.info("订单被拒绝: ID={}, 审批人={}, 原因={}", orderId, approverId, comment);
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("拒绝订单过程被中断");
        } catch (BusinessException e) {
            log.error("拒绝订单失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param orderId 订单ID
     * @return 执行后的订单
     * @throws BusinessException 业务规则验证失败或订单不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder executeOrder(Long orderId) throws BusinessException {
        Assert.notNull(orderId, "订单ID不能为空");
        log.info("执行订单，ID: {}", orderId);
        
        String lockKey = "purchase_order:" + orderId;
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 从Repository获取订单
                PurchaseOrder order = repository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 使用状态处理器处理执行事件
                OrderProcessingResult result = stateHandler.executeOrder(order);
                
                if (!result.isSuccess()) {
                    throw new BusinessException("执行订单失败: " + result.getErrorMessage());
                }
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                log.info("订单执行成功: ID={}", orderId);
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("执行订单过程被中断");
        } catch (BusinessException e) {
            log.error("执行订单失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 根据ID获取订单信息
     * @param orderId 订单ID
     * @return 订单对象
     * @throws BusinessException 订单不存在时抛出
     */
    @Override
    public PurchaseOrder getOrder(Long orderId) throws BusinessException {
        Assert.notNull(orderId, "订单ID不能为空");
        log.info("获取订单详情，ID: {}", orderId);
        
        // 先从缓存获取
        PurchaseOrder cachedOrder = orderCacheService.getCachedOrder(orderId);
        if (cachedOrder != null) {
            log.info("从缓存获取订单，ID: {}", orderId);
            // 处理计算字段
            processCalculatedFields(cachedOrder);
            return cachedOrder;
        }
        
        // 缓存未命中，从数据库查询
        PurchaseOrder order = repository.findById(orderId)
            .orElseThrow(() -> new BusinessException("订单不存在"));
        
        // 处理计算字段
        processCalculatedFields(order);
        
        // 缓存查询结果
        orderCacheService.cacheOrder(order);
        
        return order;
    }
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @param cancellerId 取消人ID
     * @param reason 取消原因
     * @return 取消后的订单
     * @throws BusinessException 业务异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder cancelOrder(Long orderId, String cancellerId, String reason) throws BusinessException {
        log.info("取消订单，ID: {}, 取消人ID: {}, 原因: {}", orderId, cancellerId, reason);
        
        String lockKey = "purchase_order:" + orderId;
        
        try {
            // 使用分布式锁保证并发安全
            if (!lockManager.tryLock(lockKey, 5000, TimeUnit.MILLISECONDS)) {
                throw new BusinessException("获取订单锁失败，请稍后重试");
            }
            
            try {
                // 获取订单信息
                PurchaseOrder order = repository.findById(orderId)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
                
                // 使用状态处理器处理取消事件
                OrderProcessingResult result = stateHandler.cancelOrder(order, cancellerId, reason);
                
                if (!result.isSuccess()) {
                    throw new BusinessException("取消订单失败: " + result.getErrorMessage());
                }
                
                // 保存更新后的订单
                PurchaseOrder updatedOrder = repository.save(order);
                
                log.info("订单取消成功: ID={}, 取消人={}", orderId, cancellerId);
                return updatedOrder;
            } finally {
                // 释放分布式锁
                lockManager.unlock(lockKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("取消订单过程被中断");
        } catch (BusinessException e) {
            log.error("取消订单失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 根据状态查询订单
     * @param status 订单状态
     * @return 订单列表
     */
    @Override
    public List<PurchaseOrder> findOrdersByStatus(String status) {
        log.info("按状态查询订单，状态: {}", status);
        
        // 构建缓存键
        String cacheKey = orderCacheService.buildOrderListCacheKey(0, Integer.MAX_VALUE, status);
        
        // 尝试从缓存获取
        List<PurchaseOrder> cachedOrders = orderCacheService.getCachedOrderList(cacheKey);
        if (cachedOrders != null) {
            log.info("从缓存获取状态订单列表");
            return cachedOrders;
        }
        
        // 缓存未命中，从数据库查询
        // 使用Repository的条件查询
        List<PurchaseOrder> orders = repository.findByStatus(status);
        
        // 缓存查询结果
        orderCacheService.cacheOrderList(cacheKey, orders);
        
        return orders;
    }
    
    /**
     * 查询所有订单（带分页）
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 订单列表
     */
    @Override
    public List<PurchaseOrder> findAllOrders(int page, int size) {
        log.info("查询所有订单，页码: {}, 每页大小: {}", page, size);
        
        // 构建缓存键
        String cacheKey = orderCacheService.buildOrderListCacheKey(page, size, null);
        
        // 尝试从缓存获取
        List<PurchaseOrder> cachedOrders = orderCacheService.getCachedOrderList(cacheKey);
        if (cachedOrders != null) {
            log.info("从缓存获取订单列表");
            return cachedOrders;
        }
        
        // 缓存未命中，从数据库查询
        // 使用Repository的分页查询
        List<PurchaseOrder> orders = repository.findAll(PageRequest.of(page, size)).getContent();
        
        // 缓存查询结果
        orderCacheService.cacheOrderList(cacheKey, orders);
        
        return orders;
    }
    
    /**
     * 验证业务规则
     * @param order 订单对象
     * @throws IllegalArgumentException 验证失败时抛出
     */
    private void validateBusinessRules(PurchaseOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("订单对象不能为空");
        }
        
        // 使用业务规则引擎验证订单业务规则
        ruleEngine.validateOrderRules(order);
    }
    
    /**
     * 保存订单并更新缓存
     * @param order 订单对象
     * @return 保存后的订单
     */
    private PurchaseOrder saveAndCacheOrder(PurchaseOrder order) {
        PurchaseOrder savedOrder = repository.save(order);
        orderCacheService.cacheOrder(savedOrder);
        orderCacheService.evictOrderListCache();
        return savedOrder;
    }
}