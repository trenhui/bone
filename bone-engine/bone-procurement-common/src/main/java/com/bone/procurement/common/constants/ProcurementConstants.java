package com.bone.procurement.common.constants;

/**
 * 采购模块通用常量类
 * 集中管理采购模块中使用的各类常量，确保常量定义的一致性
 */
public final class ProcurementConstants {
    
    // 私有构造函数，防止实例化
    private ProcurementConstants() {
        throw new AssertionError("Cannot instantiate utility class");
    }
    
    //=========================== 订单状态 ===========================
    
    /**
     * 草稿状态
     */
    public static final String ORDER_STATUS_DRAFT = "DRAFT";
    
    /**
     * 待审批状态
     */
    public static final String ORDER_STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    
    /**
     * 已审批状态
     */
    public static final String ORDER_STATUS_APPROVED = "APPROVED";
    
    /**
     * 已拒绝状态
     */
    public static final String ORDER_STATUS_REJECTED = "REJECTED";
    
    /**
     * 执行中状态
     */
    public static final String ORDER_STATUS_IN_PROGRESS = "IN_PROGRESS";
    
    /**
     * 已完成状态
     */
    public static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    
    /**
     * 已取消状态
     */
    public static final String ORDER_STATUS_CANCELLED = "CANCELLED";
    
    //=========================== 审批状态 ===========================
    
    /**
     * 待审批
     */
    public static final String APPROVAL_STATUS_PENDING = "PENDING";
    
    /**
     * 已审批
     */
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    
    /**
     * 已拒绝
     */
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    
    /**
     * 已撤销
     */
    public static final String APPROVAL_STATUS_WITHDRAWN = "WITHDRAWN";
    
    //=========================== 付款状态 ===========================
    
    /**
     * 未付款
     */
    public static final String PAYMENT_STATUS_UNPAID = "UNPAID";
    
    /**
     * 部分付款
     */
    public static final String PAYMENT_STATUS_PARTIALLY_PAID = "PARTIALLY_PAID";
    
    /**
     * 已付款
     */
    public static final String PAYMENT_STATUS_PAID = "PAID";
    
    //=========================== 交货状态 ===========================
    
    /**
     * 待交货
     */
    public static final String DELIVERY_STATUS_PENDING = "PENDING";
    
    /**
     * 部分交货
     */
    public static final String DELIVERY_STATUS_PARTIALLY_DELIVERED = "PARTIALLY_DELIVERED";
    
    /**
     * 已交货
     */
    public static final String DELIVERY_STATUS_DELIVERED = "DELIVERED";
    
    //=========================== 订单类型 ===========================
    
    /**
     * 标准采购
     */
    public static final String ORDER_TYPE_STANDARD = "标准采购";
    
    /**
     * 紧急采购
     */
    public static final String ORDER_TYPE_EMERGENCY = "紧急采购";
    
    /**
     * 日常采购
     */
    public static final String ORDER_TYPE_ROUTINE = "日常采购";
    
    /**
     * 常规采购
     */
    public static final String ORDER_TYPE_REGULAR = "常规采购";
    
    //=========================== 审批流程 ===========================
    
    /**
     * 简单采购流程
     */
    public static final String PURCHASE_FLOW_SIMPLE = "SIMPLE_PURCHASE_FLOW";
    
    /**
     * 标准采购流程
     */
    public static final String PURCHASE_FLOW_STANDARD = "STANDARD_PURCHASE_FLOW";
    
    /**
     * 复杂采购流程
     */
    public static final String PURCHASE_FLOW_COMPLEX = "COMPLEX_PURCHASE_FLOW";
    
    /**
     * VIP采购流程
     */
    public static final String PURCHASE_FLOW_VIP = "VIP_PURCHASE_FLOW";
    
    /**
     * 紧急采购流程
     */
    public static final String PURCHASE_FLOW_EMERGENCY = "EMERGENCY_PURCHASE_FLOW";
    
    //=========================== 审批级别 ===========================
    
    /**
     * 部门经理审批
     */
    public static final String APPROVAL_LEVEL_1 = "LEVEL_1";
    public static final int APPROVAL_LEVEL_1_VALUE = 1;
    
    /**
     * 总监审批
     */
    public static final String APPROVAL_LEVEL_2 = "LEVEL_2";
    public static final int APPROVAL_LEVEL_2_VALUE = 2;
    
    /**
     * 副总经理审批
     */
    public static final String APPROVAL_LEVEL_3 = "LEVEL_3";
    public static final int APPROVAL_LEVEL_3_VALUE = 3;
    
    /**
     * 总经理审批
     */
    public static final String APPROVAL_LEVEL_4 = "LEVEL_4";
    public static final int APPROVAL_LEVEL_4_VALUE = 4;
    
    //=========================== 错误代码 ===========================
    
    /**
     * 订单验证失败
     */
    public static final String ERROR_ORDER_VALIDATION_FAILED = "PROC_ORDER_001";
    
    /**
     * 订单项验证失败
     */
    public static final String ERROR_ORDER_ITEM_VALIDATION_FAILED = "PROC_ORDER_ITEM_001";
    
    /**
     * 订单不存在
     */
    public static final String ERROR_ORDER_NOT_FOUND = "PROC_ORDER_002";
    
    /**
     * 状态变更失败
     */
    public static final String ERROR_STATUS_CHANGE_FAILED = "PROC_STATUS_001";
    
    //=========================== 其他常量 ===========================
    
    /**
     * 默认币种
     */
    public static final String DEFAULT_CURRENCY = "CNY";
    
    /**
     * 默认税率
     */
    public static final String DEFAULT_TAX_RATE = "0.13";
    
    /**
     * 最大批处理数量
     */
    public static final int MAX_BATCH_SIZE = 1000;
}