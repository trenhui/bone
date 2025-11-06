package com.bone.procurement.state;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.model.ApprovalLevel;
import com.bone.procurement.model.OrderStatus;
import com.bone.procurement.model.OrderEvent;
import com.bone.procurement.service.notification.NotificationService;
import com.bone.procurement.service.security.UserService;
import com.bone.procurement.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 采购订单状态处理器
 * 处理订单状态转换、多级审批流程和状态变更事件
 */
@Component
public class PurchaseOrderStateHandler {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderStateHandler.class);
    
    private final PurchaseOrderStateMachine stateMachine;
    private final NotificationService notificationService;
    private final UserService userService;
    
    // 事件监听器注册表
    private final Map<OrderEvent, List<Consumer<PurchaseOrderEventContext>>> eventListeners = new ConcurrentHashMap<>();
    
    // 审批金额阈值配置
    private final Map<ApprovalLevel, Double> approvalThresholds = new ConcurrentHashMap<>();
    
    @Autowired
    public PurchaseOrderStateHandler(PurchaseOrderStateMachine stateMachine, 
                                   NotificationService notificationService,
                                   UserService userService) {
        this.stateMachine = stateMachine;
        this.notificationService = notificationService;
        this.userService = userService;
        
        // 初始化审批金额阈值
        initializeApprovalThresholds();
        
        // 注册状态事件监听器
        registerStateEventListeners();
    }
    
    /**
     * 提交采购订单进行审批
     */
    @Transactional
    public OrderProcessingResult submitForApproval(PurchaseOrder order, String submitterId) {
        logger.info("Submitting order {} for approval by user {}", order.getId(), submitterId);
        
        try {
            // 验证订单基本信息
            if (!validateOrderBeforeSubmission(order)) {
                return OrderProcessingResult.failure("订单信息不完整，无法提交审批");
            }
            
            // 计算所需的审批级别
            ApprovalLevel requiredLevel = determineApprovalLevel(order);
            order.setApprovalLevel(requiredLevel.getValue());
            order.setSubmittedBy(submitterId);
            order.setSubmittedTime(new Date());
            
            // 转换状态为待审批
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.PENDING_APPROVAL);
            
            if (transitionSuccess) {
                // 处理审批流程启动
                processApprovalStart(order);
                
                // 发布提交事件
                publishEvent(OrderEvent.ORDER_SUBMITTED, new PurchaseOrderEventContext(order, submitterId));
                
                return OrderProcessingResult.success("订单已成功提交审批", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error submitting order {} for approval", order.getId(), e);
            return OrderProcessingResult.failure("提交审批时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 审批采购订单
     */
    @Transactional
    public OrderProcessingResult approveOrder(PurchaseOrder order, String approverId, String comment) {
        logger.info("Approving order {} by user {}", order.getId(), approverId);
        
        try {
            // 验证审批权限
            if (!validateApprovalAuthority(order, approverId)) {
                return OrderProcessingResult.failure("审批人没有足够的权限审批此订单");
            }
            
            // 记录审批信息
            recordApproval(order, approverId, true, comment);
            
            // 检查是否需要多级审批
            if (needsFurtherApproval(order)) {
                // 进入下一级审批
                processNextApprovalLevel(order);
                return OrderProcessingResult.success("订单已通过当前级别审批，等待进一步审批", order);
            } else {
                // 最终审批通过，转换为已审批状态
                boolean transitionSuccess = stateMachine.transition(order, OrderStatus.APPROVED);
                
                if (transitionSuccess) {
                    publishEvent(OrderEvent.ORDER_APPROVED, new PurchaseOrderEventContext(order, approverId, comment));
                    return OrderProcessingResult.success("订单已成功审批", order);
                } else {
                    return OrderProcessingResult.failure("订单状态转换失败");
                }
            }
        } catch (Exception e) {
            logger.error("Error approving order {}", order.getId(), e);
            return OrderProcessingResult.failure("审批过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 拒绝采购订单
     */
    @Transactional
    public OrderProcessingResult rejectOrder(PurchaseOrder order, String approverId, String rejectionReason) {
        logger.info("Rejecting order {} by user {}", order.getId(), approverId);
        
        try {
            // 验证审批权限
            if (!validateApprovalAuthority(order, approverId)) {
                return OrderProcessingResult.failure("审批人没有足够的权限审批此订单");
            }
            
            // 记录拒绝信息
            recordApproval(order, approverId, false, rejectionReason);
            order.setRejectionReason(rejectionReason);
            
            // 转换状态为已拒绝
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.REJECTED);
            
            if (transitionSuccess) {
                publishEvent(OrderEvent.ORDER_REJECTED, new PurchaseOrderEventContext(order, approverId, rejectionReason));
                return OrderProcessingResult.success("订单已拒绝", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error rejecting order {}", order.getId(), e);
            return OrderProcessingResult.failure("拒绝过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 取消采购订单
     */
    @Transactional
    public OrderProcessingResult cancelOrder(PurchaseOrder order, String operatorId, String cancelReason) {
        logger.info("Cancelling order {} by user {}", order.getId(), operatorId);
        
        try {
            // 检查是否可以取消
            if (!canCancelOrder(order)) {
                return OrderProcessingResult.failure("当前订单状态不允许取消");
            }
            
            // 记录取消信息
            order.setCancelledBy(operatorId);
            order.setCancelReason(cancelReason);
            
            // 转换状态为已取消
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.CANCELLED);
            
            if (transitionSuccess) {
                publishEvent(OrderEvent.ORDER_CANCELLED, new PurchaseOrderEventContext(order, operatorId, cancelReason));
                return OrderProcessingResult.success("订单已成功取消", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error cancelling order {}", order.getId(), e);
            return OrderProcessingResult.failure("取消过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 完成采购订单
     */
    @Transactional
    public OrderProcessingResult completeOrder(PurchaseOrder order, String operatorId) {
        logger.info("Completing order {} by user {}", order.getId(), operatorId);
        
        try {
            // 转换状态为已完成
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.COMPLETED);
            
            if (transitionSuccess) {
                order.setCompletedBy(operatorId);
                publishEvent(OrderEvent.ORDER_COMPLETED, new PurchaseOrderEventContext(order, operatorId));
                return OrderProcessingResult.success("订单已成功完成", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error completing order {}", order.getId(), e);
            return OrderProcessingResult.failure("完成过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 关闭采购订单
     */
    @Transactional
    public OrderProcessingResult closeOrder(PurchaseOrder order, String operatorId) {
        logger.info("Closing order {} by user {}", order.getId(), operatorId);
        
        try {
            // 转换状态为已关闭
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.CLOSED);
            
            if (transitionSuccess) {
                order.setClosedBy(operatorId);
                order.setClosedTime(new Date());
                publishEvent(OrderEvent.ORDER_CLOSED, new PurchaseOrderEventContext(order, operatorId));
                return OrderProcessingResult.success("订单已成功关闭", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error closing order {}", order.getId(), e);
            return OrderProcessingResult.failure("关闭过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 重新提交被拒绝的订单
     */
    @Transactional
    public OrderProcessingResult resubmitRejectedOrder(PurchaseOrder order, String submitterId) {
        logger.info("Resubmitting rejected order {} by user {}", order.getId(), submitterId);
        
        try {
            // 验证订单状态
            if (order.getStatus() != OrderStatus.REJECTED) {
                return OrderProcessingResult.failure("只有被拒绝的订单才能重新提交");
            }
            
            // 清除之前的审批信息
            resetApprovalInfo(order);
            
            // 转换回待审批状态
            boolean transitionSuccess = stateMachine.transition(order, OrderStatus.PENDING_APPROVAL);
            
            if (transitionSuccess) {
                order.setResubmittedBy(submitterId);
                order.setResubmittedTime(new Date());
                publishEvent(OrderEvent.ORDER_RESUBMITTED, new PurchaseOrderEventContext(order, submitterId));
                return OrderProcessingResult.success("订单已成功重新提交", order);
            } else {
                return OrderProcessingResult.failure("订单状态转换失败");
            }
        } catch (Exception e) {
            logger.error("Error resubmitting order {}", order.getId(), e);
            return OrderProcessingResult.failure("重新提交过程中发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 确定订单所需的审批级别
     */
    public ApprovalLevel determineApprovalLevel(PurchaseOrder order) {
        double totalAmount = order.getTotalAmount();
        
        if (totalAmount >= approvalThresholds.get(ApprovalLevel.LEVEL_3)) {
            return ApprovalLevel.LEVEL_3;
        } else if (totalAmount >= approvalThresholds.get(ApprovalLevel.LEVEL_2)) {
            return ApprovalLevel.LEVEL_2;
        } else {
            return ApprovalLevel.LEVEL_1;
        }
    }
    
    /**
     * 处理审批流程启动
     */
    private void processApprovalStart(PurchaseOrder order) {
        // 根据审批级别确定审批人
        List<User> approvers = userService.findApproversByLevel(order.getApprovalLevel());
        order.setCurrentApprovers(approvers);
        order.setCurrentApprovalLevel(1); // 从第一级开始
        
        // 发送审批通知给第一级审批人
        notificationService.sendApprovalNotification(order, approvers);
    }
    
    /**
     * 处理下一级审批
     */
    private void processNextApprovalLevel(PurchaseOrder order) {
        int nextLevel = order.getCurrentApprovalLevel() + 1;
        order.setCurrentApprovalLevel(nextLevel);
        
        // 查找下一级审批人
        List<User> nextLevelApprovers = userService.findApproversByLevel(nextLevel);
        order.setCurrentApprovers(nextLevelApprovers);
        
        // 发送通知
        notificationService.sendApprovalNotification(order, nextLevelApprovers);
    }
    
    /**
     * 检查是否需要进一步审批
     */
    private boolean needsFurtherApproval(PurchaseOrder order) {
        return order.getCurrentApprovalLevel() < order.getApprovalLevel();
    }
    
    /**
     * 验证审批权限
     */
    private boolean validateApprovalAuthority(PurchaseOrder order, String approverId) {
        User approver = userService.findById(approverId);
        if (approver == null) {
            return false;
        }
        
        // 检查审批人是否在当前审批列表中
        return order.getCurrentApprovers().stream()
                .anyMatch(user -> Objects.equals(user.getId(), approverId));
    }
    
    /**
     * 记录审批信息
     */
    private void recordApproval(PurchaseOrder order, String approverId, boolean approved, String comment) {
        // 创建审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setOrderId(order.getId());
        record.setApproverId(approverId);
        record.setApprovalLevel(order.getCurrentApprovalLevel());
        record.setApproved(approved);
        record.setComment(comment);
        record.setApprovalTime(new Date());
        
        // 添加到订单的审批历史
        order.addApprovalRecord(record);
    }
    
    /**
     * 验证订单提交前的状态
     */
    private boolean validateOrderBeforeSubmission(PurchaseOrder order) {
        return order != null && 
               order.getOrderItems() != null && 
               !order.getOrderItems().isEmpty() &&
               order.getSupplierId() != null &&
               order.getExpectedDeliveryDate() != null;
    }
    
    /**
     * 检查订单是否可以取消
     */
    private boolean canCancelOrder(PurchaseOrder order) {
        OrderStatus status = order.getStatus();
        // 只有草稿、待审批和已审批状态的订单可以取消
        return status == OrderStatus.DRAFT || 
               status == OrderStatus.PENDING_APPROVAL || 
               status == OrderStatus.APPROVED;
    }
    
    /**
     * 重置订单的审批信息
     */
    private void resetApprovalInfo(PurchaseOrder order) {
        order.getApprovalRecords().clear();
        order.setCurrentApprovers(null);
        order.setCurrentApprovalLevel(0);
        order.setRejectionReason(null);
    }
    
    /**
     * 初始化审批金额阈值
     */
    private void initializeApprovalThresholds() {
        approvalThresholds.put(ApprovalLevel.LEVEL_1, 0.0);
        approvalThresholds.put(ApprovalLevel.LEVEL_2, 10000.0);
        approvalThresholds.put(ApprovalLevel.LEVEL_3, 50000.0);
    }
    
    /**
     * 注册状态事件监听器
     */
    private void registerStateEventListeners() {
        // 注册订单提交事件监听器
        addEventListener(OrderEvent.ORDER_SUBMITTED, context -> {
            logger.info("Order submitted event handled for order: {}", context.getOrder().getId());
            // 可以添加额外的业务逻辑，如记录审计日志等
        });
        
        // 注册订单审批事件监听器
        addEventListener(OrderEvent.ORDER_APPROVED, context -> {
            PurchaseOrder order = context.getOrder();
            logger.info("Order approved event handled for order: {}", order.getId());
            // 发送审批成功通知给订单提交者
            notificationService.sendApprovalResultNotification(order, true);
        });
        
        // 注册订单拒绝事件监听器
        addEventListener(OrderEvent.ORDER_REJECTED, context -> {
            PurchaseOrder order = context.getOrder();
            logger.info("Order rejected event handled for order: {}", order.getId());
            // 发送拒绝通知给订单提交者
            notificationService.sendApprovalResultNotification(order, false);
        });
    }
    
    /**
     * 添加事件监听器
     */
    public void addEventListener(OrderEvent event, Consumer<PurchaseOrderEventContext> listener) {
        eventListeners.computeIfAbsent(event, k -> new java.util.ArrayList<>()).add(listener);
    }
    
    /**
     * 发布事件
     */
    private void publishEvent(OrderEvent event, PurchaseOrderEventContext context) {
        List<Consumer<PurchaseOrderEventContext>> listeners = eventListeners.get(event);
        if (listeners != null) {
            listeners.forEach(listener -> {
                try {
                    listener.accept(context);
                } catch (Exception e) {
                    logger.error("Error handling event {} for order {}", event, context.getOrder().getId(), e);
                }
            });
        }
    }
    
    /**
     * 订单处理结果类
     */
    public static class OrderProcessingResult {
        private final boolean success;
        private final String message;
        private final PurchaseOrder order;
        
        private OrderProcessingResult(boolean success, String message, PurchaseOrder order) {
            this.success = success;
            this.message = message;
            this.order = order;
        }
        
        public static OrderProcessingResult success(String message, PurchaseOrder order) {
            return new OrderProcessingResult(true, message, order);
        }
        
        public static OrderProcessingResult failure(String message) {
            return new OrderProcessingResult(false, message, null);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public PurchaseOrder getOrder() {
            return order;
        }
    }
    
    /**
     * 采购订单事件上下文
     */
    public static class PurchaseOrderEventContext {
        private final PurchaseOrder order;
        private final String operatorId;
        private final String comment;
        private final Date timestamp;
        
        public PurchaseOrderEventContext(PurchaseOrder order, String operatorId) {
            this(order, operatorId, null);
        }
        
        public PurchaseOrderEventContext(PurchaseOrder order, String operatorId, String comment) {
            this.order = order;
            this.operatorId = operatorId;
            this.comment = comment;
            this.timestamp = new Date();
        }
        
        public PurchaseOrder getOrder() {
            return order;
        }
        
        public String getOperatorId() {
            return operatorId;
        }
        
        public String getComment() {
            return comment;
        }
        
        public Date getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * 审批记录内部类
     */
    public static class ApprovalRecord {
        private String orderId;
        private String approverId;
        private int approvalLevel;
        private boolean approved;
        private String comment;
        private Date approvalTime;
        
        // Getters and Setters
        public String getOrderId() {
            return orderId;
        }
        
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
        
        public String getApproverId() {
            return approverId;
        }
        
        public void setApproverId(String approverId) {
            this.approverId = approverId;
        }
        
        public int getApprovalLevel() {
            return approvalLevel;
        }
        
        public void setApprovalLevel(int approvalLevel) {
            this.approvalLevel = approvalLevel;
        }
        
        public boolean isApproved() {
            return approved;
        }
        
        public void setApproved(boolean approved) {
            this.approved = approved;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
        
        public Date getApprovalTime() {
            return approvalTime;
        }
        
        public void setApprovalTime(Date approvalTime) {
            this.approvalTime = approvalTime;
        }
    }
}