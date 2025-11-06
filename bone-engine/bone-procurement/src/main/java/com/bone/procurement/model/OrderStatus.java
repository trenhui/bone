package com.bone.procurement.model;

/**
 * 订单状态枚举
 * 定义采购订单的所有可能状态
 */
public enum OrderStatus {
    
    /**
     * 草稿状态
     * 订单正在创建中，尚未提交
     */
    DRAFT("草稿", 0),
    
    /**
     * 待审批状态
     * 订单已提交，等待审批人审批
     */
    PENDING_APPROVAL("待审批", 10),
    
    /**
     * 已审批状态
     * 订单已通过所有必要的审批
     */
    APPROVED("已审批", 20),
    
    /**
     * 已拒绝状态
     * 订单被任一审批人拒绝
     */
    REJECTED("已拒绝", 30),
    
    /**
     * 已完成状态
     * 订单已执行完毕，所有货物已收到
     */
    COMPLETED("已完成", 40),
    
    /**
     * 已取消状态
     * 订单在执行前被取消
     */
    CANCELLED("已取消", 50),
    
    /**
     * 已关闭状态
     * 订单已完成并且已归档
     */
    CLOSED("已关闭", 60);
    
    private final String displayName;
    private final int order;
    
    OrderStatus(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }
    
    /**
     * 获取状态的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取状态的排序值
     */
    public int getOrder() {
        return order;
    }
    
    /**
     * 根据显示名称查找状态枚举
     */
    public static OrderStatus fromDisplayName(String displayName) {
        for (OrderStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(displayName)) {
                return status;
            }
        }
        throw new IllegalArgumentException("No enum constant with display name: " + displayName);
    }
    
    /**
     * 检查状态是否可以向前进（按照订单流程）
     */
    public boolean canProgressTo(OrderStatus target) {
        return target.getOrder() > this.order;
    }
    
    /**
     * 检查状态是否可以回退
     */
    public boolean canRollbackTo(OrderStatus target) {
        return target.getOrder() < this.order;
    }
}