package com.bone.procurement.service;

import com.bone.procurement.mapper.PurchaseOrderMapper;
import com.bone.smartmeta.engine.context.UserContext;
import com.bone.smartmeta.engine.exception.BusinessRuleException;
import com.bone.smartmeta.engine.exception.EntityNotFoundException;
import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.MetadataRegistry;
import com.bone.smartmeta.engine.model.DynamicSmartEntity;
import com.bone.smartmeta.engine.model.SmartBaseEntity;
import com.bone.smartmeta.engine.query.SmartQueryExecutor;
import com.bone.smartmeta.engine.rule.BusinessRuleEngine;
import com.bone.smartmeta.engine.rule.BusinessRuleResult;
import com.bone.smartmeta.engine.workflow.WorkflowEngine;
import com.bone.procurement.dto.PurchaseOrderCriteria;
import com.bone.procurement.dto.PurchaseOrderRequest;
import com.bone.procurement.dto.PurchaseOrderResponse;
import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.repository.PurchaseOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购订单服务
 * 处理采购订单的全生命周期管理
 */
@Service
public class PurchaseOrderService {

    private static final String ENTITY_NAME = "PurchaseOrder";
    private static final String WORKFLOW_NAME = "PurchaseOrderApproval";
    
    private final SmartQueryExecutor queryExecutor;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final BusinessRuleEngine ruleEngine;
    private final WorkflowEngine workflowEngine;
    private final MetadataRegistry metadataRegistry;
    private final UserContext userContext;
    private final BudgetService budgetService;
    private final NotificationService notificationService;
    private final VendorService vendorService;
    
    // Logger instance
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);
    
    // Constructor for dependency injection
    public PurchaseOrderService(SmartQueryExecutor queryExecutor,
                              PurchaseOrderRepository purchaseOrderRepository,
                              BusinessRuleEngine ruleEngine,
                              WorkflowEngine workflowEngine,
                              MetadataRegistry metadataRegistry,
                              UserContext userContext,
                              BudgetService budgetService,
                              NotificationService notificationService,
                              VendorService vendorService) {
        this.queryExecutor = queryExecutor;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.ruleEngine = ruleEngine;
        this.workflowEngine = workflowEngine;
        this.metadataRegistry = metadataRegistry;
        this.userContext = userContext;
        this.budgetService = budgetService;
        this.notificationService = notificationService;
        this.vendorService = vendorService;
    }

    /**
     * 创建采购订单
     */
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        log.info("创建采购订单: {}", request.getOrderTitle());
        
        try {
            // 1. 验证供应商是否存在且活跃
            vendorService.validateActiveVendor(request.getVendorId());
            
            // 2. 验证预算是否充足
            budgetService.checkBudgetAvailability(
                    request.getDepartmentId(), 
                    request.getTotalAmount()
            );
            
            // 3. 创建采购订单实体
            PurchaseOrder order = new PurchaseOrder();
            
            // 4. 设置字段值
            order.setName(request.getOrderTitle());
            order.setOrderTitle(request.getOrderTitle());
            order.setVendorId(request.getVendorId());
            order.setDepartmentId(request.getDepartmentId());
            order.setTotalAmount(request.getTotalAmount());
            order.setOrderStatus("Draft");
            order.setNeedByDate(request.getNeedByDate());
            order.setDescription(request.getDescription());
            order.setPriority(request.getPriority());
            
            // 5. 执行业务规则验证
            BusinessRuleResult ruleResult = ruleEngine.executeRules(order, "CREATE");
            if (!ruleResult.isPassed()) {
                log.warn("采购订单业务规则验证失败: {}", ruleResult.getViolations());
                throw new BusinessRuleException("创建采购订单失败", ruleResult.getViolations());
            }
            
            // 6. 保存实体
            purchaseOrderRepository.insert(order);
            PurchaseOrder createdOrder = order;
            log.info("采购订单创建成功: {}", createdOrder.getOrderNumber());
            
            // 7. 发送创建通知
            notificationService.sendPurchaseOrderCreatedNotification(createdOrder);
            
            // 8. 转换为响应DTO并返回
            return PurchaseOrderMapper.INSTANCE.toResponse(createdOrder);
            
        } catch (Exception e) {
            log.error("创建采购订单失败", e);
            throw e;
        }
    }

    /**
     * 提交采购订单进行审批
     */
    @Transactional
    public PurchaseOrderResponse submitPurchaseOrder(String orderId) {
        log.info("提交采购订单审批: {}", orderId);
        
        // 1. 获取采购订单
        PurchaseOrder order = purchaseOrderRepository.findById(orderId);
        if (order == null) {
            throw new EntityNotFoundException("Purchase order not found with id: " + orderId);
        }
        
        // 2. 检查当前状态
        String currentStatus = order.getOrderStatus();
        if (!"Draft".equals(currentStatus)) {
            throw new IllegalStateException("只有草稿状态的采购订单可以提交审批，当前状态: " + currentStatus);
        }
        
        // 3. 更新状态
        order.setOrderStatus("Submitted");
        order.setField("submittedDate", LocalDateTime.now());
        order.setField("submittedBy", userContext.getCurrentUserId());
        
        // 4. 保存更新
        purchaseOrderRepository.update(order);
        PurchaseOrder updatedOrder = order;
        log.debug("采购订单状态更新为已提交: {}", orderId);
        
        // 5. 启动审批流程
        workflowEngine.startWorkflow(WORKFLOW_NAME, orderId, Map.of(
            "initiator", userContext.getCurrentUserId(),
            "orderAmount", order.getTotalAmount()
        ));
        
        // 6. 发送提交通知
        notificationService.sendPurchaseOrderSubmittedNotification(updatedOrder);
        
        return PurchaseOrderMapper.INSTANCE.toResponse(updatedOrder);
    }

    /**
     * 审批采购订单
     */
    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(String orderId, String approvalNotes) {
        log.info("审批采购订单: {}", orderId);
        
        // 1. 获取采购订单
        PurchaseOrder order = purchaseOrderRepository.findById(orderId);
        if (order == null) {
            throw new EntityNotFoundException("Purchase order not found with id: " + orderId);
        }
        
        // 2. 更新状态和审批信息
        order.setOrderStatus("Approved");
        order.setField("approvedBy", userContext.getCurrentUserId());
        order.setField("approvedDate", LocalDateTime.now());
        order.setField("approvalNotes", approvalNotes);
        
        // 3. 保存更新
        purchaseOrderRepository.update(order);
        PurchaseOrder updatedOrder = order;
        log.debug("采购订单已批准: {}", orderId);
        
        // 4. 完成工作流任务
        workflowEngine.completeTask(WORKFLOW_NAME, orderId, "approve", Map.of(
            "approver", userContext.getCurrentUserId(),
            "notes", approvalNotes
        ));
        
        // 5. 扣减预算
        budgetService.reserveBudget(
                order.getDepartmentId(),
                order.getTotalAmount(),
                orderId
        );
        
        // 6. 发送批准通知
        notificationService.sendPurchaseOrderApprovedNotification(updatedOrder);
        
        return PurchaseOrderMapper.INSTANCE.toResponse(updatedOrder);
    }

    /**
     * 拒绝采购订单
     */
    @Transactional
    public PurchaseOrderResponse rejectPurchaseOrder(String orderId, String rejectionReason) {
        log.info("拒绝采购订单: {}", orderId);
        
        // 1. 获取采购订单
        PurchaseOrder order = purchaseOrderRepository.findById(orderId);
        if (order == null) {
            throw new EntityNotFoundException("Purchase order not found with id: " + orderId);
        }
        
        // 2. 更新状态和拒绝信息
        order.setOrderStatus("Rejected");
        order.setField("rejectedBy", userContext.getCurrentUserId());
        order.setField("rejectedDate", LocalDateTime.now());
        order.setField("rejectionReason", rejectionReason);
        
        // 3. 保存更新
        purchaseOrderRepository.update(order);
        PurchaseOrder updatedOrder = order;
        log.debug("采购订单已拒绝: {}", orderId);
        
        // 4. 完成工作流任务
        workflowEngine.completeTask(WORKFLOW_NAME, orderId, "reject", Map.of(
            "rejecter", userContext.getCurrentUserId(),
            "reason", rejectionReason
        ));
        
        // 5. 发送拒绝通知
        notificationService.sendPurchaseOrderRejectedNotification(updatedOrder);
        
        return PurchaseOrderMapper.INSTANCE.toResponse(updatedOrder);
    }

    /**
     * 查询采购订单
     */
    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> searchPurchaseOrders(PurchaseOrderCriteria criteria, Pageable pageable) {
        log.debug("查询采购订单: {}", criteria);
        
        // 1. 构建查询语句
        StringBuilder queryBuilder = new StringBuilder("""
            SELECT Id, Name, orderNumber, orderTitle, totalAmount, totalAmountWithTax,
                   vendorId, departmentId, orderStatus, createdBy, createdDate,
                   priority, isHighValueOrder
            FROM PurchaseOrder
            WHERE 1=1
            """);
        
        // 2. 设置查询参数
        Map<String, Object> params = new HashMap<>();
        
        // 3. 构建查询条件
        if (criteria.getStatuses() != null && !criteria.getStatuses().isEmpty()) {
            queryBuilder.append(" AND orderStatus IN (:statuses)");
            params.put("statuses", criteria.getStatuses());
        }
        
        if (criteria.getDepartmentId() != null) {
            queryBuilder.append(" AND departmentId = :departmentId");
            params.put("departmentId", criteria.getDepartmentId());
        }
        
        if (criteria.getVendorId() != null) {
            queryBuilder.append(" AND vendorId = :vendorId");
            params.put("vendorId", criteria.getVendorId());
        }
        
        if (criteria.getMinAmount() != null) {
            queryBuilder.append(" AND totalAmount >= :minAmount");
            params.put("minAmount", criteria.getMinAmount());
        }
        
        if (criteria.getMaxAmount() != null) {
            queryBuilder.append(" AND totalAmount <= :maxAmount");
            params.put("maxAmount", criteria.getMaxAmount());
        }
        
        if (criteria.getStartDate() != null) {
            queryBuilder.append(" AND createdDate >= :startDate");
            params.put("startDate", criteria.getStartDate());
        }
        
        if (criteria.getEndDate() != null) {
            queryBuilder.append(" AND createdDate <= :endDate");
            params.put("endDate", criteria.getEndDate());
        }
        
        if (criteria.getIsHighValue() != null) {
            queryBuilder.append(" AND isHighValueOrder = :isHighValue");
            params.put("isHighValue", criteria.getIsHighValue());
        }
        
        // 4. 添加排序
        queryBuilder.append(" ORDER BY createdDate DESC");
        
        // 5. 执行分页查询
        Page<PurchaseOrder> results = queryExecutor.executePaginatedQuery(
                queryBuilder.toString(), 
                params, 
                PurchaseOrder.class,
                pageable
        );
        
        // 6. 转换结果并返回
        return results.map(PurchaseOrderMapper.INSTANCE::toResponse);
    }

    /**
     * 获取高价值采购订单
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getHighValuePurchaseOrders() {
        String query = """
            SELECT Id, Name, orderNumber, orderTitle, totalAmount, totalAmountWithTax,
                   vendorId, departmentId, orderStatus, createdDate, priority
            FROM PurchaseOrder
            WHERE isHighValueOrder = true AND orderStatus IN ('Submitted', 'In_Review')
            ORDER BY totalAmount DESC, createdDate DESC
            """;
        
        List<PurchaseOrder> results = queryExecutor.executeQuery(
                query, 
                Map.of(), 
                PurchaseOrder.class
        );
        
        return results.stream()
                .map(PurchaseOrderMapper.INSTANCE::toResponse)
                .collect(Collectors.toList());
    }
}
