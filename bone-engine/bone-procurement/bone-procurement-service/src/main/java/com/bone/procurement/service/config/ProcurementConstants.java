package com.bone.procurement.service.config;

/**
 * 采购模块常量类<br>
 * 集中管理采购模块中使用的所有业务常量，遵循统一的命名和组织结构规范。<br>
 * <strong>注意：</strong>请使用有意义的分组注释来组织常量，便于维护和查找。
 */
public final class ProcurementConstants {
    
    /**
     * 私有构造函数，防止实例化<br>
     * 常量类应该是final的，并且不允许创建实例
     */
    private ProcurementConstants() {
        throw new AssertionError("ProcurementConstants 类不允许实例化");
    }
    
    // =============================== 订单状态常量 ===============================
    /** 订单状态：草稿 - 订单创建但尚未提交 */
    public static final String ORDER_STATUS_DRAFT = "DRAFT";
    
    /** 订单状态：待审批 - 订单已提交等待审批 */
    public static final String ORDER_STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    
    /** 订单状态：已审批 - 订单审批通过 */
    public static final String ORDER_STATUS_APPROVED = "APPROVED";
    
    /** 订单状态：已拒绝 - 订单审批被拒绝 */
    public static final String ORDER_STATUS_REJECTED = "REJECTED";
    
    /** 订单状态：已取消 - 订单被取消 */
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    
    /** 订单状态：已关闭 - 订单执行完成或终止 */
    public static final String ORDER_STATUS_CLOSED = "CLOSED";
    
    // =============================== 审批状态常量 ===============================
    /** 审批状态：待处理 - 审批任务创建但尚未处理 */
    public static final String APPROVAL_STATUS_PENDING = "PENDING";
    
    /** 审批状态：处理中 - 审批任务正在处理 */
    public static final String APPROVAL_STATUS_IN_PROGRESS = "IN_PROGRESS";
    
    /** 审批状态：已通过 - 审批任务审批通过 */
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    
    /** 审批状态：已拒绝 - 审批任务被拒绝 */
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    
    /** 审批状态：已取消 - 审批任务被取消 */
    public static final String APPROVAL_STATUS_CANCELLED = "CANCELLED";
    
    // =============================== 审批动作常量 ===============================
    /** 审批动作：批准 */
    public static final String APPROVAL_ACTION_APPROVE = "approve";
    
    /** 审批动作：拒绝 */
    public static final String APPROVAL_ACTION_REJECT = "reject";
    
    // =============================== 订单类型常量 ===============================
    /** 订单类型：常规订单 */
    public static final String ORDER_TYPE_REGULAR = "REGULAR";
    
    /** 订单类型：紧急订单 */
    public static final String ORDER_TYPE_EMERGENCY = "EMERGENCY";
    
    // =============================== 验证结果严重程度 ===============================
    /** 验证严重程度：信息 - 仅提供提示信息 */
    public static final String VALIDATION_SEVERITY_INFO = "INFO";
    
    /** 验证严重程度：警告 - 提示潜在问题，但不阻止操作 */
    public static final String VALIDATION_SEVERITY_WARNING = "WARNING";
    
    /** 验证严重程度：错误 - 发现严重问题，阻止操作继续 */
    public static final String VALIDATION_SEVERITY_ERROR = "ERROR";
    
    // =============================== 规则ID常量 ===============================
    /** 规则ID：基本信息验证规则 */
    public static final String RULE_ID_BASIC_INFO_VALIDATION = "basic_info_validation";
    
    /** 规则ID：金额验证规则 */
    public static final String RULE_ID_AMOUNT_VALIDATION = "amount_validation";
    
    /** 规则ID：采购项目验证规则 */
    public static final String RULE_ID_ITEM_VALIDATION = "item_validation";
    
    /** 规则ID：供应商验证规则 */
    public static final String RULE_ID_SUPPLIER_VALIDATION = "supplier_validation";
    
    /** 规则ID：交货日期验证规则 */
    public static final String RULE_ID_DELIVERY_DATE_VALIDATION = "delivery_date_validation";
    
    /** 规则ID：紧急采购处理规则 */
    public static final String RULE_ID_EMERGENCY_PURCHASE_RULE = "emergency_purchase_rule";
    
    // =============================== 响应消息常量 ===============================
    /** 响应消息：订单创建成功 */
    public static final String MESSAGE_ORDER_CREATED_SUCCESS = "订单创建成功";
    
    /** 响应消息：订单更新成功 */
    public static final String MESSAGE_ORDER_UPDATED_SUCCESS = "订单更新成功";
    
    /** 响应消息：订单已提交审批 */
    public static final String MESSAGE_ORDER_SUBMITTED_SUCCESS = "订单已提交审批";
    
    /** 响应消息：订单审批通过 */
    public static final String MESSAGE_ORDER_APPROVED_SUCCESS = "订单审批通过";
    
    /** 响应消息：订单审批拒绝 */
    public static final String MESSAGE_ORDER_REJECTED_SUCCESS = "订单审批拒绝";
    
    /** 响应消息：订单已取消 */
    public static final String MESSAGE_ORDER_CANCELLED_SUCCESS = "订单已取消";
    
    /** 响应消息：订单已关闭 */
    public static final String MESSAGE_ORDER_CLOSED_SUCCESS = "订单已关闭";
    
    // =============================== 错误消息常量 ===============================
    /** 错误消息：采购订单不存在 */
    public static final String ERROR_ORDER_NOT_FOUND = "采购订单不存在";
    
    /** 错误消息：当前订单状态不允许执行此操作 */
    public static final String ERROR_STATUS_NOT_ALLOWED = "当前订单状态不允许执行此操作";
    
    /** 错误消息：订单验证失败 */
    public static final String ERROR_VALIDATION_FAILED = "订单验证失败";
    
    /** 错误消息：订单需要审批 */
    public static final String ERROR_APPROVAL_REQUIRED = "订单需要审批";
    
    /** 错误消息：无效的操作 */
    public static final String ERROR_INVALID_ACTION = "无效的操作";
}