package com.bone.procurement.model;

/**
 * 通知类型枚举
 * 定义系统中所有可能的通知类型
 */
public enum NotificationType {
    
    /**
     * 审批通知
     * 需要用户进行审批操作的通知
     */
    APPROVAL_REQUEST("审批请求", "需要您审批的采购订单"),
    
    /**
     * 审批结果通知
     * 告知用户其提交的订单审批结果
     */
    APPROVAL_RESULT("审批结果", "订单审批状态更新"),
    
    /**
     * 状态变更通知
     * 订单状态发生变化的通知
     */
    STATUS_CHANGE("状态变更", "订单状态已更新"),
    
    /**
     * 提醒通知
     * 各种提醒类通知（如订单超时提醒）
     */
    REMINDER("提醒", "系统提醒"),
    
    /**
     * 系统通知
     * 系统级别的通知
     */
    SYSTEM("系统通知", "系统消息"),
    
    /**
     * 错误通知
     * 系统错误或异常的通知
     */
    ERROR("错误通知", "系统错误"),
    
    /**
     * 警告通知
     * 需要注意但不严重的警告信息
     */
    WARNING("警告通知", "警告信息"),
    
    /**
     * 成功通知
     * 操作成功的通知
     */
    SUCCESS("成功通知", "操作成功"),
    
    /**
     * 信息通知
     * 一般信息性通知
     */
    INFO("信息通知", "通知信息"),
    
    /**
     * 自定义通知
     * 自定义内容的通知
     */
    CUSTOM("自定义通知", "自定义消息");
    
    private final String displayName;
    private final String defaultTitle;
    
    NotificationType(String displayName, String defaultTitle) {
        this.displayName = displayName;
        this.defaultTitle = defaultTitle;
    }
    
    /**
     * 获取通知类型的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取通知类型的默认标题
     */
    public String getDefaultTitle() {
        return defaultTitle;
    }
    
    /**
     * 根据显示名称查找通知类型枚举
     */
    public static NotificationType fromDisplayName(String displayName) {
        for (NotificationType type : values()) {
            if (type.displayName.equalsIgnoreCase(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant with display name: " + displayName);
    }
    
    /**
     * 检查通知类型是否需要用户操作
     */
    public boolean requiresAction() {
        return this == APPROVAL_REQUEST || this == REMINDER;
    }
    
    /**
     * 检查通知类型是否为系统级别的通知
     */
    public boolean isSystemLevel() {
        return this == SYSTEM || this == ERROR || this == WARNING;
    }
}