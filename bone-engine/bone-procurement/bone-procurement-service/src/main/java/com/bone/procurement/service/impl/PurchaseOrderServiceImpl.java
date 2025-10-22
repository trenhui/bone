package com.bone.procurement.service.impl;

import com.bone.procurement.engine.model.*;
import com.bone.procurement.service.config.ProcurementConstants;
import com.bone.procurement.engine.rules.PurchaseOrderRuleEngine;
import com.bone.procurement.service.PurchaseOrderService;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.tenant.TenantContext;
import com.bone.smartmeta.exception.EntityValidationException;
import com.bone.smartmeta.model.MetadataContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采购订单服务实现类
 * 实现采购订单的核心业务操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    
    private final PurchaseOrderRuleEngine ruleEngine;
    private final MetadataEngine metadataEngine;
    
    // 简化实现，实际应该注入订单仓库
    private final Map<String, PurchaseOrder> orderRepository = new ConcurrentHashMap<>();
    
    @Override
    @Transactional
    public PurchaseOrder createOrder(PurchaseOrder order, RuleExecutionContext context) {
        // 参数验证
        if (order == null) {
            log.error("Cannot create null purchase order");
            throw new IllegalArgumentException("Purchase order cannot be null");
        }
        
        log.info("Starting to create purchase order");
        
        try {
            // 使用元数据引擎验证订单数据
            metadataEngine.validateEntity(order, false);
            
            // 准备元数据上下文
            MetadataContext metadataContext = new MetadataContext();
            metadataContext.setUserId(context != null ? context.getUserId() : null);
            metadataContext.setTenantId(TenantContext.getCurrentTenant() != null ? 
                TenantContext.getCurrentTenant() : "DEFAULT_TENANT");
            
            // 使用元数据引擎填充默认值
            metadataEngine.populateDefaultValues(order, metadataContext);
            
            // 1. 验证订单业务规则
            List<RuleValidationResult> validationResults = validateOrder(order, context);
            
            // 检查是否有严重错误
            List<RuleValidationResult> errors = validationResults.stream()
                    .filter(result -> !result.isPassed() && "ERROR".equals(result.getSeverity()))
                    .collect(Collectors.toList());
            
            if (!errors.isEmpty()) {
                String errorMessages = errors.stream()
                        .map(RuleValidationResult::getErrorMessage)
                        .collect(Collectors.joining(", "));
                log.error("Order validation failed with {} errors: {}", errors.size(), errorMessages);
                throw new IllegalArgumentException("Order validation failed: " + errorMessages);
            }
            
            // 记录警告信息
            List<RuleValidationResult> warnings = validationResults.stream()
                    .filter(result -> "WARNING".equals(result.getSeverity()))
                    .collect(Collectors.toList());
            
            if (!warnings.isEmpty()) {
                String warningMessages = warnings.stream()
                        .map(RuleValidationResult::getErrorMessage)
                        .collect(Collectors.joining(", "));
                log.warn("Order has {} warnings: {}", warnings.size(), warningMessages);
            }
            
            // 2. 生成订单ID和填充默认值
            if (order.getOrderId() == null) {
                order.setOrderId(generateOrderId());
            }
            
            if (order.getOrderCode() == null) {
                order.setOrderCode(generateOrderCode());
            }
            
            if (order.getStatus() == null) {
                order.setStatus("DRAFT");
            }
            
            if (order.getApprovalStatus() == null) {
                order.setApprovalStatus("PENDING");
            }
            
            // 3. 设置创建和更新时间
            LocalDateTime now = LocalDateTime.now();
            order.setCreateTime(now);
            order.setUpdateTime(now);
            
            // 4. 保存订单
            orderRepository.put(order.getOrderId(), order);
            
            // 触发元数据引擎的创建事件
            metadataEngine.onEntityCreated(order, metadataContext);
            
            log.info("Purchase order created successfully: {}, order code: {}", 
                     order.getOrderId(), order.getOrderCode());
            return order;
        } catch (EntityValidationException e) {
            log.error("Entity validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage());
            throw e;
        }
    }
    
    @Override
    @Transactional
    public PurchaseOrder updateOrder(String orderId, PurchaseOrder order, RuleExecutionContext context) {
        // 参数验证
        if (orderId == null || order == null) {
            log.error("Invalid input: orderId or order is null");
            throw new IllegalArgumentException("Order ID and order cannot be null");
        }
        
        log.info("Starting to update purchase order: {}", orderId);
        
        try {
            // 1. 检查订单是否存在
            PurchaseOrder existingOrder = getOrderById(orderId);
            if (existingOrder == null) {
                log.error("Purchase order not found: {}", orderId);
                throw new IllegalArgumentException("Purchase order not found: " + orderId);
            }
            
            // 2. 检查订单状态是否允许更新
            String currentStatus = existingOrder.getStatus();
            if ("APPROVED".equals(currentStatus) || 
                "CLOSED".equals(currentStatus) || 
                "CANCELLED".equals(currentStatus)) {
                log.error("Cannot update order {} in status: {}", orderId, currentStatus);
                throw new IllegalStateException("Cannot update order in status: " + currentStatus);
            }
            
            // 准备元数据上下文
            MetadataContext metadataContext = new MetadataContext();
            metadataContext.setUserId(context != null ? context.getUserId() : null);
            metadataContext.setTenantId(TenantContext.getCurrentTenant() != null ? 
                TenantContext.getCurrentTenant() : "DEFAULT_TENANT");
            
            // 使用元数据引擎验证订单数据
            metadataEngine.validateEntity(order, true);
            
            // 3. 验证更新后的订单
            List<RuleValidationResult> validationResults = validateOrder(order, context);
            
            // 检查是否有严重错误
            List<RuleValidationResult> errors = validationResults.stream()
                    .filter(result -> !result.isPassed() && "ERROR".equals(result.getSeverity()))
                    .collect(Collectors.toList());
            
            if (!errors.isEmpty()) {
                String errorMessages = errors.stream()
                        .map(RuleValidationResult::getErrorMessage)
                        .collect(Collectors.joining(", "));
                log.error("Order validation failed with {} errors: {}", errors.size(), errorMessages);
                throw new IllegalArgumentException("Order validation failed: " + errorMessages);
            }
            
            // 记录警告信息
            List<RuleValidationResult> warnings = validationResults.stream()
                    .filter(result -> "WARNING".equals(result.getSeverity()))
                    .collect(Collectors.toList());
            
            if (!warnings.isEmpty()) {
                String warningMessages = warnings.stream()
                        .map(RuleValidationResult::getErrorMessage)
                        .collect(Collectors.joining(", "));
                log.warn("Order has {} warnings: {}", warnings.size(), warningMessages);
            }
            
            // 4. 更新订单信息
            order.setOrderId(orderId);
            order.setOrderCode(existingOrder.getOrderCode()); // 订单编号不可修改
            order.setCreateTime(existingOrder.getCreateTime()); // 创建时间不可修改
            order.setUpdateTime(LocalDateTime.now());
            
            // 保留原有审批状态，除非明确修改
            if (order.getApprovalStatus() == null) {
                order.setApprovalStatus(existingOrder.getApprovalStatus());
            }
            
            // 获取变更信息
            Map<String, Object> changes = metadataEngine.compareEntities(existingOrder, order);
            
            // 5. 保存更新后的订单
            orderRepository.put(orderId, order);
            
            // 触发元数据引擎的更新事件
            metadataEngine.onEntityUpdated(existingOrder, order, changes, metadataContext);
            
            log.info("Purchase order updated successfully: {}", orderId);
            return order;
        } catch (EntityValidationException e) {
            log.error("Entity validation failed during update: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating order: {}", e.getMessage());
            throw e;
        }
    }
    
    @Override
    public PurchaseOrder getOrderById(String orderId) {
        log.debug("Getting purchase order by ID: {}", orderId);
        return orderRepository.get(orderId);
    }
    
    @Override
    public PurchaseOrder getOrderByCode(String orderCode) {
        log.debug("Getting purchase order by code: {}", orderCode);
        return orderRepository.values().stream()
                .filter(order -> orderCode.equals(order.getOrderCode()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public List<PurchaseOrder> findOrdersByConditions(Map<String, Object> conditions, int page, int pageSize) {
        // 参数验证
        if (page < 1) {
            log.warn("Invalid page number: {}, setting to 1", page);
            page = 1;
        }
        
        if (pageSize < 1 || pageSize > 100) {
            log.warn("Invalid page size: {}, setting to 20", pageSize);
            pageSize = 20;
        }
        
        log.debug("Finding orders with conditions: {}, page: {}, pageSize: {}", conditions, page, pageSize);
        
        try {
            // 使用元数据引擎处理条件查询
            MetadataContext context = new MetadataContext();
            context.setTenantId(conditions != null && conditions.containsKey("tenantId") ? 
                conditions.get("tenantId").toString() : "DEFAULT_TENANT");
            
            // 根据条件过滤
            List<PurchaseOrder> filteredOrders = orderRepository.values().stream()
                    .filter(order -> {
                        // 多租户隔离检查
                        if (!context.getTenantId().equals("DEFAULT_TENANT") && 
                            (order.getTenantId() == null || !order.getTenantId().equals(context.getTenantId()))) {
                            return false;
                        }
                        return matchConditions(order, conditions);
                    })
                    .collect(Collectors.toList());
            
            // 分页处理
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, filteredOrders.size());
            
            if (startIndex >= filteredOrders.size()) {
                log.debug("No orders found for page {}", page);
                return Collections.emptyList();
            }
            
            List<PurchaseOrder> pagedOrders = filteredOrders.subList(startIndex, endIndex);
            
            // 触发元数据引擎的查询事件
            metadataEngine.onEntityQuery("PurchaseOrder", conditions, pagedOrders.size(), context);
            
            log.debug("Found {} orders for the specified conditions, returning page {} with {} items", 
                     filteredOrders.size(), page, endIndex - startIndex);
            
            return pagedOrders;
        } catch (Exception e) {
            log.error("Error finding orders with conditions: {}", e.getMessage());
            throw new RuntimeException("Failed to find orders with conditions", e);
        }
    }
    
    @Override
    @Transactional
    public ApprovalResult submitForApproval(String orderId, RuleExecutionContext context) {
        // 参数验证
        if (orderId == null) {
            log.error("Cannot submit null order ID for approval");
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        
        log.info("Starting to submit purchase order for approval: {}", orderId);
        
        // 1. 获取订单
        PurchaseOrder order = getOrderById(orderId);
        if (order == null) {
            log.error("Purchase order not found: {}", orderId);
            throw new IllegalArgumentException("Purchase order not found: " + orderId);
        }
        
        // 2. 验证订单状态
        String currentStatus = order.getStatus();
        if (!"DRAFT".equals(currentStatus)) {
            log.error("Order {} is not in DRAFT status: {}", orderId, currentStatus);
            throw new IllegalStateException("Order must be in DRAFT status to submit for approval");
        }
        
        // 3. 执行最终验证
        List<RuleValidationResult> validationResults = validateOrder(order, context);
        List<RuleValidationResult> errors = validationResults.stream()
                .filter(result -> !result.isPassed())
                .collect(Collectors.toList());
        
        if (!errors.isEmpty()) {
            String errorMessages = errors.stream()
                    .map(RuleValidationResult::getErrorMessage)
                    .collect(Collectors.joining(", "));
            log.error("Order validation failed before approval: {}", errorMessages);
            throw new IllegalArgumentException("Order validation failed before approval: " + errorMessages);
        }
        
        // 记录提交人信息
        if (context != null) {
            order.setSubmitterId(context.getUserId());
            order.setSubmitterName(context.getUserName());
        }
        
        // 4. 判断是否需要审批
        boolean requiresApproval = ruleEngine.requiresApproval(order, context);
        
        if (!requiresApproval) {
            // 不需要审批，直接通过
            order.setStatus(ProcurementConstants.ORDER_STATUS_APPROVED);
            order.setApprovalStatus(ProcurementConstants.APPROVAL_STATUS_APPROVED);
            order.setApprovalDate(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            
            orderRepository.put(orderId, order);
            
            log.info("Order {} automatically approved (no approval required)", orderId);
            return ApprovalResult.approved("system", "系统自动审批");
        }
        
        // 5. 处理紧急采购
        ApprovalResult result = ruleEngine.executeEmergencyPurchaseRule(order, context);
        
        // 6. 更新订单状态
        order.setStatus(ProcurementConstants.ORDER_STATUS_PENDING_APPROVAL);
        order.setApprovalStatus(ProcurementConstants.APPROVAL_STATUS_IN_PROGRESS);
        order.setApproverId(result.getNextApproverId());
        order.setUpdateTime(LocalDateTime.now());
        order.setSubmissionDate(LocalDateTime.now()); // 添加提交日期
        
        // 记录审批流程信息
        String approvalFlow = ruleEngine.getApprovalFlowDefinition(order, context);
        order.setApprovalFlow(approvalFlow);
        
        orderRepository.put(orderId, order);
        
        log.info("Order submitted for approval: {}, approval flow: {}, next approver: {}", 
                 orderId, approvalFlow, result.getNextApproverId());
        
        return result;
    }
    
    @Override
    @Transactional
    public ApprovalResult approveOrder(String orderId, String action, String comment, RuleExecutionContext context) {
        // 参数验证
        if (orderId == null || action == null || context == null) {
            log.error("Invalid input: missing required parameters");
            throw new IllegalArgumentException("Order ID, action and context cannot be null");
        }
        
        log.info("Processing approval for purchase order: {}, action: {}", orderId, action);
        
        try {
            // 1. 获取订单
            PurchaseOrder order = getOrderById(orderId);
            if (order == null) {
                log.error("Purchase order not found: {}", orderId);
                throw new IllegalArgumentException("Purchase order not found: " + orderId);
            }
            
            // 准备元数据上下文
            MetadataContext metadataContext = new MetadataContext();
            metadataContext.setUserId(context.getUserId());
            metadataContext.setTenantId(TenantContext.getCurrentTenant() != null ? 
                TenantContext.getCurrentTenant() : "DEFAULT_TENANT");
            
            // 保存原始订单状态用于变更跟踪
            PurchaseOrder originalOrder = new PurchaseOrder();
            // 复制必要的字段，实际实现中可能需要深拷贝
            originalOrder.setOrderId(order.getOrderId());
            originalOrder.setStatus(order.getStatus());
            originalOrder.setApprovalStatus(order.getApprovalStatus());
            originalOrder.setApproverId(order.getApproverId());
            
            // 2. 验证订单状态
            String currentStatus = order.getStatus();
            if (!ProcurementConstants.ORDER_STATUS_PENDING_APPROVAL.equals(currentStatus)) {
                log.error("Order {} is not in PENDING_APPROVAL status: {}", orderId, currentStatus);
                throw new IllegalStateException("Order must be in PENDING_APPROVAL status");
            }
            
            // 验证当前用户是否有权限审批
            String currentUserId = context.getUserId();
            String assignedApproverId = order.getApproverId();
            
            if (assignedApproverId != null && !assignedApproverId.equals(currentUserId)) {
                log.error("User {} not authorized to approve order {}, assigned approver: {}", 
                         currentUserId, orderId, assignedApproverId);
                throw new SecurityException("User not authorized to approve this order");
            }
            
            String currentUserName = context.getUserName();
            
            // 3. 处理审批动作
            if ("approve".equalsIgnoreCase(action)) {
                // 审批通过
                order.setApprovalStatus(ProcurementConstants.APPROVAL_STATUS_APPROVED);
                order.setApprovalDate(LocalDateTime.now());
                order.setUpdateTime(LocalDateTime.now());
                
                // 记录当前审批人信息
                order.setApproverId(currentUserId);
                order.setApproverName(currentUserName);
                
                // 检查是否有下一审批人
                String nextApprover = ruleEngine.getNextApprover(order, currentUserId, context);
                
                if (nextApprover != null) {
                    // 还有下一审批人
                    order.setStatus("PENDING_APPROVAL");
                    order.setApproverId(nextApprover);
                    
                    // 记录审批历史
                    recordApprovalHistory(order, currentUserId, currentUserName, "APPROVED", comment);
                    
                    // 获取状态变更信息
                    Map<String, Object> changes = metadataEngine.compareEntities(originalOrder, order);
                    
                    ApprovalResult result = ApprovalResult.pending(
                            ruleEngine.getApprovalFlowDefinition(order, context),
                            nextApprover,
                            ruleEngine.getApprovalLevel(order, context) + 1
                    );
                    
                    orderRepository.put(orderId, order);
                    
                    // 触发元数据引擎的更新事件
                    metadataEngine.onEntityUpdated(originalOrder, order, changes, metadataContext);
                    
                    log.info("Order {} partially approved by {}, moving to next approver: {}", 
                             orderId, currentUserId, nextApprover);
                    return result;
                } else {
                    // 所有审批完成
                    order.setStatus(ProcurementConstants.ORDER_STATUS_APPROVED);
                    
                    // 记录审批历史
                    recordApprovalHistory(order, currentUserId, currentUserName, "APPROVED", comment);
                    
                    // 获取状态变更信息
                    Map<String, Object> changes = metadataEngine.compareEntities(originalOrder, order);
                    
                    ApprovalResult result = ApprovalResult.approved(currentUserId, currentUserName);
                    result.setComment(comment);
                    
                    orderRepository.put(orderId, order);
                    
                    // 触发元数据引擎的更新事件
                    metadataEngine.onEntityUpdated(originalOrder, order, changes, metadataContext);
                    
                    log.info("Order {} fully approved by {}", orderId, currentUserId);
                    return result;
                }
            } else if ("reject".equalsIgnoreCase(action)) {
                // 审批拒绝
                order.setStatus(ProcurementConstants.ORDER_STATUS_REJECTED);
                order.setApprovalStatus(ProcurementConstants.APPROVAL_STATUS_REJECTED);
                order.setApprovalDate(LocalDateTime.now());
                order.setApproverId(currentUserId);
                order.setApproverName(currentUserName);
                order.setUpdateTime(LocalDateTime.now());
                
                // 记录审批历史
                recordApprovalHistory(order, currentUserId, currentUserName, "REJECTED", comment);
                
                // 获取状态变更信息
                Map<String, Object> changes = metadataEngine.compareEntities(originalOrder, order);
                
                orderRepository.put(orderId, order);
                
                // 触发元数据引擎的更新事件
                metadataEngine.onEntityUpdated(originalOrder, order, changes, metadataContext);
                
                log.info("Order {} rejected by {}, reason: {}", orderId, currentUserId, comment);
                return ApprovalResult.rejected(currentUserId, currentUserName, comment);
            } else {
                log.error("Invalid approval action: {}", action);
                throw new IllegalArgumentException("Invalid approval action: " + action + ". Must be 'approve' or 'reject'");
            }
        } catch (Exception e) {
            log.error("Error processing approval: {}", e.getMessage());
            throw e;
        }
    }
    
    @Override
    @Transactional
    public PurchaseOrder cancelOrder(String orderId, String reason, RuleExecutionContext context) {
        // 参数验证
        if (orderId == null) {
            log.error("Cannot cancel null order ID");
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        
        log.info("Starting to cancel purchase order: {}, reason: {}", orderId, reason);
        
        try {
            // 1. 获取订单
            PurchaseOrder order = getOrderById(orderId);
            if (order == null) {
                log.error("Purchase order not found: {}", orderId);
                throw new IllegalArgumentException("Purchase order not found: " + orderId);
            }
            
            // 准备元数据上下文
            MetadataContext metadataContext = new MetadataContext();
            metadataContext.setUserId(context != null ? context.getUserId() : null);
            metadataContext.setTenantId(order.getTenantId() != null ? 
                order.getTenantId() : "DEFAULT_TENANT");
            
            // 保存原始状态用于变更跟踪
            PurchaseOrder originalOrder = new PurchaseOrder();
            originalOrder.setOrderId(order.getOrderId());
            originalOrder.setStatus(order.getStatus());
            originalOrder.setApprovalStatus(order.getApprovalStatus());
            
            // 2. 检查是否可以取消
            String currentStatus = order.getStatus();
            if (ProcurementConstants.ORDER_STATUS_CLOSED.equals(currentStatus) || ProcurementConstants.ORDER_STATUS_CANCELLED.equals(currentStatus)) {
                log.error("Order {} is already {}", orderId, currentStatus);
                throw new IllegalStateException("Order is already " + currentStatus);
            }

            if (ProcurementConstants.ORDER_STATUS_APPROVED.equals(currentStatus)) {
                log.error("Cannot cancel approved order: {}", orderId);
                throw new IllegalStateException("Cannot cancel approved order");
            }
            
            // 3. 更新订单状态
            order.setStatus(ProcurementConstants.ORDER_STATUS_CANCELLED);
            order.setApprovalStatus(ProcurementConstants.APPROVAL_STATUS_CANCELLED);
            order.setUpdateTime(LocalDateTime.now());
            order.setCancellationDate(LocalDateTime.now()); // 添加取消日期
            
            // 记录取消原因
            if (reason != null) {
                order.setCancellationReason(reason);
            }
            
            // 记录取消人信息
            if (context != null) {
                order.setCancelledById(context.getUserId());
                order.setCancelledByName(context.getUserName());
            }
            
            // 获取状态变更信息
            Map<String, Object> changes = metadataEngine.compareEntities(originalOrder, order);
            
            // 保存更新后的订单
            orderRepository.put(orderId, order);
            
            // 触发元数据引擎的更新事件
            metadataEngine.onEntityUpdated(originalOrder, order, changes, metadataContext);
            
            log.info("Purchase order cancelled: {}, by user: {}", 
                     orderId, context != null ? context.getUserId() : "unknown");
            return order;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 直接重新抛出参数验证和状态检查的异常
            throw e;
        } catch (Exception e) {
            log.error("Error cancelling order: {}", e.getMessage());
            throw new RuntimeException("Failed to cancel order", e);
        }
    }
    
    @Override
    @Transactional
    public PurchaseOrder closeOrder(String orderId, RuleExecutionContext context) {
        // 参数验证
        if (orderId == null) {
            log.error("Cannot close null order ID");
            throw new IllegalArgumentException("Order ID cannot be null");
        }
        
        log.info("Starting to close purchase order: {}", orderId);
        
        try {
            // 1. 获取订单
            PurchaseOrder order = getOrderById(orderId);
            if (order == null) {
                log.error("Purchase order not found: {}", orderId);
                throw new IllegalArgumentException("Purchase order not found: " + orderId);
            }
            
            // 准备元数据上下文
            MetadataContext metadataContext = new MetadataContext();
            metadataContext.setUserId(context != null ? context.getUserId() : null);
            metadataContext.setTenantId(order.getTenantId() != null ? 
                order.getTenantId() : "DEFAULT_TENANT");
            
            // 保存原始状态用于变更跟踪
            PurchaseOrder originalOrder = new PurchaseOrder();
            originalOrder.setOrderId(order.getOrderId());
            originalOrder.setStatus(order.getStatus());
            originalOrder.setApprovalStatus(order.getApprovalStatus());
            
            // 2. 检查是否可以关闭
            String currentStatus = order.getStatus();
            if (ProcurementConstants.ORDER_STATUS_CLOSED.equals(currentStatus) || ProcurementConstants.ORDER_STATUS_CANCELLED.equals(currentStatus)) {
                log.error("Order {} is already {}", orderId, currentStatus);
                throw new IllegalStateException("Order is already " + currentStatus);
            }

            if (!ProcurementConstants.ORDER_STATUS_APPROVED.equals(currentStatus)) {
                log.error("Order {} cannot be closed, status must be APPROVED, current status: {}", 
                         orderId, currentStatus);
                throw new IllegalStateException("Only approved orders can be closed");
            }
            
            // 3. 更新订单状态
            order.setStatus(ProcurementConstants.ORDER_STATUS_CLOSED);
            order.setUpdateTime(LocalDateTime.now());
            order.setCloseDate(LocalDateTime.now()); // 添加关闭日期
            
            // 记录关闭人信息
            if (context != null) {
                order.setClosedById(context.getUserId());
                order.setClosedByName(context.getUserName());
            }
            
            // 获取状态变更信息
            Map<String, Object> changes = metadataEngine.compareEntities(originalOrder, order);
            
            // 保存更新后的订单
            orderRepository.put(orderId, order);
            
            // 触发元数据引擎的更新事件
            metadataEngine.onEntityUpdated(originalOrder, order, changes, metadataContext);
            
            log.info("Purchase order closed: {}, by user: {}", 
                     orderId, context != null ? context.getUserId() : "unknown");
            return order;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 直接重新抛出参数验证和状态检查的异常
            throw e;
        } catch (Exception e) {
            log.error("Error closing order: {}", e.getMessage());
            throw new RuntimeException("Failed to close order", e);
        }
    }
    
    @Override
    public List<RuleValidationResult> validateOrder(PurchaseOrder order, RuleExecutionContext context) {
        try {
            // 参数验证
            if (order == null) {
                log.error("Cannot validate null purchase order");
                List<RuleValidationResult> errorResult = new ArrayList<>();
                errorResult.add(RuleValidationResult.failure(
                               "PO_NULL", ProcurementConstants.ERROR_VALIDATION_FAILED, 
                               "NULL_ORDER", "无法验证空订单"));
                return errorResult;
            }
            
            String orderCode = order.getOrderCode() != null ? order.getOrderCode() : "<new_order>";
            log.debug("Validating purchase order: {}", orderCode);
            
            // 调用规则引擎进行验证
            List<RuleValidationResult> results = ruleEngine.validateOrder(order, context);
            
            // 记录验证结果
            long errorCount = results.stream().filter(r -> !r.isPassed()).count();
            long warningCount = results.stream().filter(r -> ProcurementConstants.SEVERITY_WARNING.equals(r.getSeverity())).count();
            
            if (errorCount > 0) {
                log.warn("Order validation for {} found {} errors and {} warnings", 
                         orderCode, errorCount, warningCount);
            } else if (warningCount > 0) {
                log.info("Order validation for {} found {} warnings", orderCode, warningCount);
            } else {
                log.debug("Order validation for {} passed successfully", orderCode);
            }
            
            return results;
        } catch (Exception e) {
            log.error("Error during purchase order validation: {}", e.getMessage(), e);
            // 创建验证失败结果
            List<RuleValidationResult> errorResult = new ArrayList<>();
            errorResult.add(RuleValidationResult.failure(
                           "VALIDATION_EXCEPTION", ProcurementConstants.ERROR_VALIDATION_FAILED, 
                           "VALIDATION_EXCEPTION", "验证过程中发生错误: " + e.getMessage()));
            return errorResult;
        }
    }
    
    @Override
    public Map<String, Object> getOrderStatistics(Map<String, Object> conditions) {
        log.debug("Getting order statistics with conditions: {}", conditions);
        
        // 根据条件过滤订单
        List<PurchaseOrder> filteredOrders = orderRepository.values().stream()
                .filter(order -> matchConditions(order, conditions))
                .collect(Collectors.toList());
        
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalOrders", filteredOrders.size());
        
        // 按状态统计
        Map<String, Long> statusCount = filteredOrders.stream()
                .collect(Collectors.groupingBy(PurchaseOrder::getStatus, Collectors.counting()));
        statistics.put("statusCount", statusCount);
        
        // 计算总金额
        BigDecimal totalAmount = filteredOrders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        statistics.put("totalAmount", totalAmount);
        
        // 计算平均订单金额
        if (!filteredOrders.isEmpty()) {
            BigDecimal averageAmount = totalAmount.divide(
                    new BigDecimal(filteredOrders.size()), 2, BigDecimal.ROUND_HALF_UP);
            statistics.put("averageAmount", averageAmount);
        } else {
            statistics.put("averageAmount", BigDecimal.ZERO);
        }
        
        // 计算最高和最低订单金额
        Optional<BigDecimal> maxAmount = filteredOrders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo);
        statistics.put("maxAmount", maxAmount.orElse(BigDecimal.ZERO));
        
        Optional<BigDecimal> minAmount = filteredOrders.stream()
                .map(PurchaseOrder::getTotalAmount)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo);
        statistics.put("minAmount", minAmount.orElse(BigDecimal.ZERO));
        
        log.debug("Statistics calculated: totalOrders={}, totalAmount={}, statusCount={}",
                 filteredOrders.size(), totalAmount, statusCount);
        
        return statistics;
    }
    
    // 辅助方法：匹配查询条件
    private boolean matchConditions(PurchaseOrder order, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        // 空值安全检查
        if (order == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 跳过空值条件
            if (value == null) {
                continue;
            }
            
            switch (key) {
                case "status":
                    if (!value.equals(order.getStatus())) {
                        return false;
                    }
                    break;
                case "supplierId":
                    if (!value.equals(order.getSupplierId())) {
                        return false;
                    }
                    break;
                case "createdBy":
                    if (!value.equals(order.getCreatedBy())) {
                        return false;
                    }
                    break;
                case "orderType":
                    if (!value.equals(order.getOrderType())) {
                        return false;
                    }
                    break;
                case "approvalStatus":
                    if (!value.equals(order.getApprovalStatus())) {
                        return false;
                    }
                    break;
                // 可以添加更多条件匹配
            }
        }
        
        return true;
    }
    
    // 生成订单ID
    private String generateOrderId() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomPart = UUID.randomUUID().toString().substring(0, 8);
        return "PO" + timestamp + randomPart;
    }
    
    // 生成订单编号
    private String generateOrderCode() {
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomNum = (int)(Math.random() * 1000);
        return "PO" + timestamp + String.format("%03d", randomNum);
    }
    
    // 辅助方法：记录审批历史
    private void recordApprovalHistory(PurchaseOrder order, String approverId, String approverName, 
                                      String action, String comment) {
        // 在实际系统中，这里应该创建并保存审批历史记录
        // 由于当前是简化实现，我们只记录日志
        log.debug("Order {} approval history: approver={}, name={}, action={}, comment={}",
                 order.getOrderId(), approverId, approverName, action, comment);
    }
}