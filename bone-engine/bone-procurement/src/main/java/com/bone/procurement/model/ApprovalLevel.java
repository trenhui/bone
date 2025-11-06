package com.bone.procurement.model;

/**
 * 审批级别枚举
 * 定义多级审批流程中的不同级别
 */
public enum ApprovalLevel {
    
    /**
     * 一级审批
     * 适用于小额订单，通常由部门经理审批
     */
    LEVEL_1("一级审批", 1, "部门经理"),
    
    /**
     * 二级审批
     * 适用于中额订单，通常由总监审批
     */
    LEVEL_2("二级审批", 2, "总监"),
    
    /**
     * 三级审批
     * 适用于大额订单，通常由副总经理或总经理审批
     */
    LEVEL_3("三级审批", 3, "副总经理/总经理"),
    
    /**
     * 四级审批
     * 适用于特大额订单，通常需要高级管理层审批
     */
    LEVEL_4("四级审批", 4, "高级管理层");
    
    private final String displayName;
    private final int value;
    private final String approverRole;
    
    ApprovalLevel(String displayName, int value, String approverRole) {
        this.displayName = displayName;
        this.value = value;
        this.approverRole = approverRole;
    }
    
    /**
     * 获取审批级别的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取审批级别的数值
     */
    public int getValue() {
        return value;
    }
    
    /**
     * 获取审批人的角色描述
     */
    public String getApproverRole() {
        return approverRole;
    }
    
    /**
     * 根据数值获取审批级别枚举
     */
    public static ApprovalLevel fromValue(int value) {
        for (ApprovalLevel level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        throw new IllegalArgumentException("No enum constant with value: " + value);
    }
    
    /**
     * 根据显示名称查找审批级别枚举
     */
    public static ApprovalLevel fromDisplayName(String displayName) {
        for (ApprovalLevel level : values()) {
            if (level.displayName.equalsIgnoreCase(displayName)) {
                return level;
            }
        }
        throw new IllegalArgumentException("No enum constant with display name: " + displayName);
    }
    
    /**
     * 获取下一级审批级别
     */
    public ApprovalLevel getNextLevel() {
        if (this == LEVEL_4) {
            return null; // 已经是最高级别
        }
        return fromValue(this.value + 1);
    }
    
    /**
     * 获取上一级审批级别
     */
    public ApprovalLevel getPreviousLevel() {
        if (this == LEVEL_1) {
            return null; // 已经是最低级别
        }
        return fromValue(this.value - 1);
    }
    
    /**
     * 检查是否是更高级别的审批
     */
    public boolean isHigherThan(ApprovalLevel other) {
        return this.value > other.value;
    }
    
    /**
     * 检查是否是更低级别的审批
     */
    public boolean isLowerThan(ApprovalLevel other) {
        return this.value < other.value;
    }
    
    /**
     * 获取最高审批级别
     */
    public static ApprovalLevel getHighestLevel() {
        return LEVEL_4;
    }
    
    /**
     * 获取最低审批级别
     */
    public static ApprovalLevel getLowestLevel() {
        return LEVEL_1;
    }
}