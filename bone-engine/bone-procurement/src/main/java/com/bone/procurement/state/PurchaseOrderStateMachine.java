package com.bone.procurement.state;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 采购订单状态机
 * 管理订单状态的流转、条件验证和事件处理
 */
@Component
public class PurchaseOrderStateMachine {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderStateMachine.class);
    
    // 状态转换规则映射
    private final Map<OrderStatus, List<Transition>> transitions = new ConcurrentHashMap<>();
    // 状态进入监听器
    private final Map<OrderStatus, List<Consumer<PurchaseOrder>>> onEntryActions = new ConcurrentHashMap<>();
    // 状态退出监听器
    private final Map<OrderStatus, List<Consumer<PurchaseOrder>>> onExitActions = new ConcurrentHashMap<>();
    // 转换监听器
    private final Map<TransitionKey, List<Consumer<PurchaseOrder>>> onTransitionActions = new ConcurrentHashMap<>();
    
    public PurchaseOrderStateMachine() {
        // 初始化状态转换规则
        initializeTransitions();
        // 初始化状态监听器
        initializeStateListeners();
    }
    
    /**
     * 执行状态转换
     * @param order 采购订单
     * @param targetStatus 目标状态
     * @return 是否转换成功
     */
    public synchronized boolean transition(PurchaseOrder order, OrderStatus targetStatus) {
        OrderStatus currentStatus = order.getStatus();
        
        if (currentStatus == targetStatus) {
            logger.warn("Order is already in status: {}", targetStatus);
            return true;
        }
        
        logger.info("Attempting transition from {} to {} for order: {}", 
                currentStatus, targetStatus, order.getId());
        
        // 查找有效的转换规则
        Transition transition = findValidTransition(currentStatus, targetStatus, order);
        if (transition == null) {
            logger.error("Invalid transition from {} to {} for order: {}", 
                    currentStatus, targetStatus, order.getId());
            return false;
        }
        
        try {
            // 执行前置条件检查
            if (!transition.checkConditions(order)) {
                logger.error("Transition conditions not met for order: {}", order.getId());
                return false;
            }
            
            // 执行状态退出动作
            executeOnExitActions(currentStatus, order);
            
            // 执行转换动作
            executeTransitionActions(currentStatus, targetStatus, order);
            
            // 更新订单状态
            order.setStatus(targetStatus);
            order.setLastUpdatedTime(new Date());
            
            // 执行状态进入动作
            executeOnEntryActions(targetStatus, order);
            
            logger.info("Successfully transitioned from {} to {} for order: {}", 
                    currentStatus, targetStatus, order.getId());
            return true;
        } catch (Exception e) {
            logger.error("Error during state transition for order: {}", order.getId(), e);
            return false;
        }
    }
    
    /**
     * 查找有效的转换规则
     */
    private Transition findValidTransition(OrderStatus from, OrderStatus to, PurchaseOrder order) {
        List<Transition> fromTransitions = transitions.get(from);
        if (fromTransitions == null) {
            return null;
        }
        
        for (Transition transition : fromTransitions) {
            if (transition.getTo() == to) {
                return transition;
            }
        }
        
        return null;
    }
    
    /**
     * 初始化状态转换规则
     */
    private void initializeTransitions() {
        // 草稿 -> 待审批
        addTransition(OrderStatus.DRAFT, OrderStatus.PENDING_APPROVAL, 
                order -> order.getOrderItems() != null && !order.getOrderItems().isEmpty(),
                "订单必须包含至少一个订单项");
        
        // 待审批 -> 已审批
        addTransition(OrderStatus.PENDING_APPROVAL, OrderStatus.APPROVED, 
                order -> {
                    // 检查审批金额限制
                    return order.getTotalAmount() < 10000 || 
                           (order.getTotalAmount() >= 10000 && order.getApprovalLevel() >= 2);
                },
                "大额订单需要高级别审批");
        
        // 待审批 -> 已拒绝
        addTransition(OrderStatus.PENDING_APPROVAL, OrderStatus.REJECTED);
        
        // 已审批 -> 已完成
        addTransition(OrderStatus.APPROVED, OrderStatus.COMPLETED);
        
        // 已审批 -> 已取消
        addTransition(OrderStatus.APPROVED, OrderStatus.CANCELLED);
        
        // 已完成 -> 已关闭
        addTransition(OrderStatus.COMPLETED, OrderStatus.CLOSED);
        
        // 草稿 -> 已取消
        addTransition(OrderStatus.DRAFT, OrderStatus.CANCELLED);
    }
    
    /**
     * 初始化状态监听器
     */
    private void initializeStateListeners() {
        // 待审批状态进入动作
        addOnEntryAction(OrderStatus.PENDING_APPROVAL, order -> {
            logger.info("Order {} entered PENDING_APPROVAL state, sending for approval", order.getId());
            // 这里可以添加发送审批通知的逻辑
        });
        
        // 已审批状态进入动作
        addOnEntryAction(OrderStatus.APPROVED, order -> {
            logger.info("Order {} approved, updating approval information", order.getId());
            order.setApprovedTime(new Date());
            // 这里可以添加生成采购单、通知供应商等逻辑
        });
        
        // 已拒绝状态进入动作
        addOnEntryAction(OrderStatus.REJECTED, order -> {
            logger.info("Order {} rejected, recording rejection reason", order.getId());
            order.setRejectedTime(new Date());
            // 这里可以添加通知申请人等逻辑
        });
        
        // 已完成状态进入动作
        addOnEntryAction(OrderStatus.COMPLETED, order -> {
            logger.info("Order {} completed, updating completion time", order.getId());
            order.setCompletedTime(new Date());
            // 这里可以添加结算、归档等逻辑
        });
    }
    
    /**
     * 添加状态转换规则
     */
    public void addTransition(OrderStatus from, OrderStatus to, Predicate<PurchaseOrder> condition, String errorMessage) {
        Transition transition = new Transition(from, to, condition, errorMessage);
        transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(transition);
    }
    
    /**
     * 添加无条件的状态转换规则
     */
    public void addTransition(OrderStatus from, OrderStatus to) {
        addTransition(from, to, order -> true, null);
    }
    
    /**
     * 添加状态进入动作
     */
    public void addOnEntryAction(OrderStatus status, Consumer<PurchaseOrder> action) {
        onEntryActions.computeIfAbsent(status, k -> new ArrayList<>()).add(action);
    }
    
    /**
     * 添加状态退出动作
     */
    public void addOnExitAction(OrderStatus status, Consumer<PurchaseOrder> action) {
        onExitActions.computeIfAbsent(status, k -> new ArrayList<>()).add(action);
    }
    
    /**
     * 添加转换动作
     */
    public void addOnTransitionAction(OrderStatus from, OrderStatus to, Consumer<PurchaseOrder> action) {
        TransitionKey key = new TransitionKey(from, to);
        onTransitionActions.computeIfAbsent(key, k -> new ArrayList<>()).add(action);
    }
    
    /**
     * 执行状态进入动作
     */
    private void executeOnEntryActions(OrderStatus status, PurchaseOrder order) {
        List<Consumer<PurchaseOrder>> actions = onEntryActions.get(status);
        if (actions != null) {
            actions.forEach(action -> {
                try {
                    action.accept(order);
                } catch (Exception e) {
                    logger.error("Error executing onEntry action for status: {}", status, e);
                }
            });
        }
    }
    
    /**
     * 执行状态退出动作
     */
    private void executeOnExitActions(OrderStatus status, PurchaseOrder order) {
        List<Consumer<PurchaseOrder>> actions = onExitActions.get(status);
        if (actions != null) {
            actions.forEach(action -> {
                try {
                    action.accept(order);
                } catch (Exception e) {
                    logger.error("Error executing onExit action for status: {}", status, e);
                }
            });
        }
    }
    
    /**
     * 执行转换动作
     */
    private void executeTransitionActions(OrderStatus from, OrderStatus to, PurchaseOrder order) {
        TransitionKey key = new TransitionKey(from, to);
        List<Consumer<PurchaseOrder>> actions = onTransitionActions.get(key);
        if (actions != null) {
            actions.forEach(action -> {
                try {
                    action.accept(order);
                } catch (Exception e) {
                    logger.error("Error executing transition action from {} to {}", from, to, e);
                }
            });
        }
    }
    
    /**
     * 获取某个状态可以转换到的所有目标状态
     */
    public List<OrderStatus> getPossibleTransitions(OrderStatus status) {
        List<Transition> statusTransitions = transitions.get(status);
        if (statusTransitions == null) {
            return Collections.emptyList();
        }
        
        return statusTransitions.stream()
                .map(Transition::getTo)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 检查某个状态转换是否有效
     */
    public boolean isTransitionValid(OrderStatus from, OrderStatus to) {
        List<OrderStatus> possibleTransitions = getPossibleTransitions(from);
        return possibleTransitions.contains(to);
    }
    
    /**
     * 验证订单是否可以转换到目标状态
     */
    public ValidationResult validateTransition(PurchaseOrder order, OrderStatus targetStatus) {
        OrderStatus currentStatus = order.getStatus();
        
        if (currentStatus == targetStatus) {
            return new ValidationResult(true, "订单已处于该状态");
        }
        
        Transition transition = findValidTransition(currentStatus, targetStatus, order);
        if (transition == null) {
            return new ValidationResult(false, "无效的状态转换");
        }
        
        if (!transition.checkConditions(order)) {
            return new ValidationResult(false, transition.getErrorMessage());
        }
        
        return new ValidationResult(true, "状态转换有效");
    }
    
    /**
     * 转换规则内部类
     */
    private static class Transition {
        private final OrderStatus from;
        private final OrderStatus to;
        private final Predicate<PurchaseOrder> condition;
        private final String errorMessage;
        
        public Transition(OrderStatus from, OrderStatus to, Predicate<PurchaseOrder> condition, String errorMessage) {
            this.from = from;
            this.to = to;
            this.condition = condition;
            this.errorMessage = errorMessage != null ? errorMessage : "转换条件不满足";
        }
        
        public OrderStatus getFrom() {
            return from;
        }
        
        public OrderStatus getTo() {
            return to;
        }
        
        public boolean checkConditions(PurchaseOrder order) {
            return condition.test(order);
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
    
    /**
     * 转换键内部类
     */
    private static class TransitionKey {
        private final OrderStatus from;
        private final OrderStatus to;
        
        public TransitionKey(OrderStatus from, OrderStatus to) {
            this.from = from;
            this.to = to;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransitionKey that = (TransitionKey) o;
            return from == that.from && to == that.to;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}