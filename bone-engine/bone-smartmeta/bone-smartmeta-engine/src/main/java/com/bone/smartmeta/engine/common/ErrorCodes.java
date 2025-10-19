package com.bone.smartmeta.engine.common;

/**
 * 错误代码常量类，用于统一管理系统中的错误码
 * 遵循业界最佳实践，将错误代码集中管理，便于维护和扩展
 */
public final class ErrorCodes {
    
    // 私有构造函数，防止实例化
    private ErrorCodes() {
        throw new UnsupportedOperationException("ErrorCodes cannot be instantiated");
    }
    
    // 通用错误码
    public static final String SUCCESS = "SUCCESS";
    public static final String SYSTEM_ERROR = "SYSTEM_ERROR";
    
    // 参数相关错误码
    public static final String INVALID_PARAMETER = "INVALID_PARAMETER";
    public static final String ILLEGAL_ARGUMENT = "ILLEGAL_ARGUMENT";
    public static final String MISSING_REQUIRED_PARAM = "MISSING_REQUIRED_PARAM";
    
    // 操作相关错误码
    public static final String OPERATION_NOT_FOUND = "OPERATION_NOT_FOUND";
    public static final String OPERATION_FAILED = "OPERATION_FAILED";
    public static final String RUNTIME_ERROR = "RUNTIME_ERROR";
    
    // 业务相关错误码
    public static final String PRECONDITION_FAILED = "PRECONDITION_FAILED";
    public static final String POSTCONDITION_FAILED = "POSTCONDITION_FAILED";
    public static final String STEP_EXECUTION_FAILED = "STEP_EXECUTION_FAILED";
    
    // 数据相关错误码
    public static final String DATA_NOT_FOUND = "DATA_NOT_FOUND";            // 数据未找到
    public static final String DATA_CREATE_FAILED = "DATA_CREATE_FAILED";    // 数据创建失败
    public static final String DATA_UPDATE_FAILED = "DATA_UPDATE_FAILED";    // 数据更新失败
    public static final String DATA_DELETE_FAILED = "DATA_DELETE_FAILED";    // 数据删除失败
    
    // 验证相关错误
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";      // 验证失败
    
    // 外部服务错误
    public static final String EXTERNAL_SERVICE_ERROR = "EXTERNAL_SERVICE_ERROR"; // 外部服务调用失败
    
    // 通知相关错误
    public static final String NOTIFICATION_ERROR = "NOTIFICATION_ERROR";    // 通知发送失败
    
    // 审批相关错误
    public static final String APPROVAL_FAILED = "APPROVAL_FAILED";          // 审批失败
    
    // 计算相关错误
    public static final String CALCULATION_ERROR = "CALCULATION_ERROR";      // 计算失败
    
    // 钩子相关错误
    public static final String HOOK_EXECUTION_FAILED = "HOOK_EXECUTION_FAILED"; // 钩子执行失败
    
    /**
     * 检查是否为成功状态码
     * @param errorCode 错误码
     * @return 是否为成功状态
     */
    public static boolean isSuccess(String errorCode) {
        return SUCCESS.equals(errorCode) || errorCode == null;
    }
    
    /**
     * 检查是否为系统错误
     * @param errorCode 错误码
     * @return 是否为系统错误
     */
    public static boolean isSystemError(String errorCode) {
        return SYSTEM_ERROR.equals(errorCode);
    }
}