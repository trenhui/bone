package com.bone.procurement.service.config;

/**
 * 采购模块常量类
 * 定义采购模块中使用的常量
 */
public class ProcurementConstants {
    
    // 订单状态常量
    public static final String ORDER_STATUS_DRAFT = "DRAFT";
    public static final String ORDER_STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String ORDER_STATUS_APPROVED = "APPROVED";
    public static final String ORDER_STATUS_REJECTED = "REJECTED";
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    public static final String ORDER_STATUS_CLOSED = "CLOSED";
    
    // 审批状态常量
    public static final String APPROVAL_STATUS_PENDING = "PENDING";
    public static final String APPROVAL_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    public static final String APPROVAL_STATUS_CANCELLED = "CANCELLED";
    
    // 审批动作常量
    public static final String APPROVAL_ACTION_APPROVE = "approve";
    public static final String APPROVAL_ACTION_REJECT = "reject";
    
    // 订单类型常量
    public static final String ORDER_TYPE_REGULAR = "REGULAR";
    public static final String ORDER_TYPE_EMERGENCY = "EMERGENCY";
    
    // 验证结果严重程度
    public static final String VALIDATION_SEVERITY_INFO = "INFO";
    public static final String VALIDATION_SEVERITY_WARNING = "WARNING";
    public static final String VALIDATION_SEVERITY_ERROR = "ERROR";
    
    // 规则ID常量
    public static final String RULE_ID_BASIC_INFO_VALIDATION = "basic_info_validation";
    public static final String RULE_ID_AMOUNT_VALIDATION = "amount_validation";
    public static final String RULE_ID_ITEM_VALIDATION = "item_validation";
    public static final String RULE_ID_SUPPLIER_VALIDATION = "supplier_validation";
    public static final String RULE_ID_DELIVERY_DATE_VALIDATION = "delivery_date_validation";
    public static final String RULE_ID_EMERGENCY_PURCHASE_RULE = "emergency_purchase_rule";
    
    // 响应消息常量
    public static final String MESSAGE_ORDER_CREATED_SUCCESS = "订单创建成功";
    public static final String MESSAGE_ORDER_UPDATED_SUCCESS = "订单更新成功";
    public static final String MESSAGE_ORDER_SUBMITTED_SUCCESS = "订单已提交审批";
    public static final String MESSAGE_ORDER_APPROVED_SUCCESS = "订单审批通过";
    public static final String MESSAGE_ORDER_REJECTED_SUCCESS = "订单审批拒绝";
    public static final String MESSAGE_ORDER_CANCELLED_SUCCESS = "订单已取消";
    public static final String MESSAGE_ORDER_CLOSED_SUCCESS = "订单已关闭";
    
    // 错误消息常量
    public static final String ERROR_ORDER_NOT_FOUND = "采购订单不存在";
    public static final String ERROR_STATUS_NOT_ALLOWED = "当前订单状态不允许执行此操作";
    public static final String ERROR_VALIDATION_FAILED = "订单验证失败";
    public static final String ERROR_APPROVAL_REQUIRED = "订单需要审批";
    public static final String ERROR_INVALID_ACTION = "无效的操作";
}