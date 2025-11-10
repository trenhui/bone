package com.bone.procurement.model;

/**
 * 订单事件枚举
 * 定义采购订单生命周期中的所有可能事件
 */
public enum OrderEvent {
    
    /**
     * 创建事件
     * 用于创建操作的通用事件
     */
    CREATE("创建", 0),
    
    /**
     * 订单创建事件
     * 当新订单被创建时触发
     */
    ORDER_CREATED("订单创建", 1),
    
    /**
     * 订单提交事件
     * 当订单从草稿状态提交到待审批状态时触发
     */
    ORDER_SUBMITTED("订单提交", 2),
    
    /**
     * 订单审批事件
     * 当订单被最终审批通过时触发
     */
    ORDER_APPROVED("订单审批", 3),
    
    /**
     * 订单拒绝事件
     * 当订单被任一审批人拒绝时触发
     */
    ORDER_REJECTED("订单拒绝", 4),
    
    /**
     * 订单重新提交事件
     * 当被拒绝的订单重新提交时触发
     */
    ORDER_RESUBMITTED("订单重新提交", 5),
    
    /**
     * 订单取消事件
     * 当订单在执行前被取消时触发
     */
    ORDER_CANCELLED("订单取消", 6),
    
    /**
     * 订单完成事件
     * 当订单执行完毕，所有货物已收到时触发
     */
    ORDER_COMPLETED("订单完成", 7),
    
    /**
     * 订单关闭事件
     * 当订单已完成并且已归档时触发
     */
    ORDER_CLOSED("订单关闭", 8),
    
    /**
     * 订单修改事件
     * 当订单信息被修改时触发
     */
    ORDER_MODIFIED("订单修改", 9),
    
    /**
     * 订单审批请求事件
     * 当需要审批人审批时触发
     */
    APPROVAL_REQUESTED("审批请求", 10),
    
    /**
     * 审批级别完成事件
     * 当某一级别的审批完成时触发
     */
    APPROVAL_LEVEL_COMPLETED("审批级别完成", 11),
    
    /**
     * 订单超时事件
     * 当订单在指定时间内未完成某一状态时触发
     */
    ORDER_TIMED_OUT("订单超时", 12),
    
    /**
     * 订单异常事件
     * 当订单处理过程中出现异常时触发
     */
    ORDER_EXCEPTION("订单异常", 13);
    
    private final String displayName;
    private final int order;
    
    OrderEvent(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
    
    /**
     * 获取事件的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取事件的顺序值
     */
    public int getOrder() {
        return order;
    }
    
    /**
     * 根据显示名称查找事件枚举
     */
    public static OrderEvent fromDisplayName(String displayName) {
        for (OrderEvent event : values()) {
            if (event.displayName.equalsIgnoreCase(displayName)) {
                return event;
            }
        }
        throw new IllegalArgumentException("No enum constant with display name: " + displayName);
    }
    
    /**
     * 判断事件是否为状态转换事件
     */
    public boolean isStatusTransitionEvent() {
        return this == ORDER_SUBMITTED || 
               this == ORDER_APPROVED || 
               this == ORDER_REJECTED || 
               this == ORDER_CANCELLED || 
               this == ORDER_COMPLETED || 
               this == ORDER_CLOSED || 
               this == ORDER_RESUBMITTED;
    }
    
    /**
     * 判断事件是否为审批相关事件
     */
    public boolean isApprovalRelatedEvent() {
        return this == ORDER_APPROVED || 
               this == ORDER_REJECTED || 
               this == APPROVAL_REQUESTED || 
               this == APPROVAL_LEVEL_COMPLETED;
    }
}