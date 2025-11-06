package com.bone.smartmeta.engine.constant;

/**
 * 错误代码常量类
 * <p>
 * <strong>已废弃：</strong>请使用 {@link com.bone.smartmeta.engine.common.ErrorCodes} 替代
 * </p>
 * <p>
 * 所有常量已迁移至 ErrorCodes 类中，此类仅为保持向后兼容性而保留。
 * 建议在所有新代码中直接使用 ErrorCodes 类。
 * </p>
 * @deprecated 请使用 {@link com.bone.smartmeta.engine.common.ErrorCodes} 替代
 */
@Deprecated
public final class ErrorCodeConstants {
    
    // 私有构造函数，防止实例化
    private ErrorCodeConstants() {
        throw new AssertionError("不能实例化ErrorConstants类");
    }
    
    // 业务规则错误代码
    public static final String HIGH_VALUE_ORDER_REQUIRES_APPROVAL = "HIGH_VALUE_ORDER_REQUIRES_APPROVAL";
    public static final String RULE_EVALUATION_FAILED = "RULE_EVALUATION_FAILED";
    public static final String RULE_EXECUTION_FAILED = "RULE_EXECUTION_FAILED";
    public static final String UNSUPPORTED_RULE_TYPE = "UNSUPPORTED_RULE_TYPE";
    public static final String INVALID_ENTITY_TYPE = "INVALID_ENTITY_TYPE";
    
    // 工作流错误代码
    public static final String WORKFLOW_START_FAILED = "WORKFLOW_START_FAILED";
    public static final String TRANSITION_EXECUTION_FAILED = "TRANSITION_EXECUTION_FAILED";
    public static final String TASK_COMPLETION_FAILED = "TASK_COMPLETION_FAILED";
    public static final String INVALID_WORKFLOW_PARAMETER = "INVALID_WORKFLOW_PARAMETER";
    
    // 通用错误代码
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    
    // 动态模型错误代码
    public static final String MODEL_LOAD_FAILED = "MODEL_LOAD_FAILED";
    public static final String MODEL_REGISTER_FAILED = "MODEL_REGISTER_FAILED";
    public static final String MODEL_DUPLICATE_FAILED = "MODEL_DUPLICATE_FAILED";
}