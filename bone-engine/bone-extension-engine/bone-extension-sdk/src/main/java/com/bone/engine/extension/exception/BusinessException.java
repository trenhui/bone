package com.bone.engine.extension.exception;

import java.util.Objects;

/**
 * 业务异常基类
 * <p>
 * 提供业务异常的通用功能，包含错误码和错误消息等通用字段和方法
 * 各业务领域的异常类可以继承此类，避免重复代码
 * 继承自ExtensionException以保持框架异常体系的一致性
 * </p>
 * 
 * <p>
 * <strong>使用建议：</strong>
 * <ul>
 * <li>对于简单的业务场景，可直接使用此类并通过错误码区分不同业务模块</li>
 * <li>对于需要附加业务字段的场景，可继承此类并添加必要的字段</li>
 * <li>推荐使用静态工厂方法创建异常实例，提高代码可读性</li>
 * </ul>
 * </p>
 */
public class BusinessException extends ExtensionException {
    private static final long serialVersionUID = 1L;
    
    // 业务模块标识，用于区分不同模块的异常
    private final String module;
    
    // 默认构造函数
    protected BusinessException() {
        super("BUSINESS_ERROR"); // 提供默认错误码
        this.module = "DEFAULT";
    }
    
    // 带错误码的构造函数
    protected BusinessException(String errorCode) {
        super(errorCode);
        this.module = "DEFAULT";
    }
    
    // 带错误码和错误消息的构造函数
    protected BusinessException(String errorCode, String message) {
        super(errorCode, message);
        this.module = "DEFAULT";
    }
    
    // 带错误码、错误消息和异常原因的构造函数
    protected BusinessException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.module = "DEFAULT";
    }
    
    // 带模块标识、错误码和错误消息的构造函数
    public BusinessException(String module, String errorCode, String message) {
        super(errorCode, message);
        this.module = module;
    }
    
    // 带模块标识、错误码、错误消息和异常原因的构造函数
    public BusinessException(String module, String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
        this.module = module;
    }
    
    /**
     * 获取业务模块标识
     * 
     * @return 业务模块标识
     */
    public String getModule() {
        return module;
    }
    
    /**
     * 检查是否包含错误码
     * 
     * @return 如果包含有效的错误码则返回true，否则返回false
     */
    public boolean hasErrorCode() {
        String errorCode = getErrorCode();
        return errorCode != null && !errorCode.trim().isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessException that = (BusinessException) o;
        return Objects.equals(getErrorCode(), that.getErrorCode()) && 
               Objects.equals(getMessage(), that.getMessage()) &&
               Objects.equals(module, that.module);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getErrorCode(), getMessage(), module);
    }
    
    @Override
    public String toString() {
        return "BusinessException{" +
                "errorCode='" + getErrorCode() + "'" +
                ", module='" + module + "'" +
                ", message='" + getMessage() + "'" +
                '}';
    }
    
    /**
     * 创建通用业务异常实例
     * 
     * @param module 业务模块标识
     * @param errorCode 错误码
     * @param message 错误消息
     * @return 业务异常实例
     */
    public static BusinessException of(String module, String errorCode, String message) {
        return new BusinessException(module, errorCode, message);
    }
    
    /**
     * 创建包含异常原因的业务异常实例
     * 
     * @param module 业务模块标识
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常原因
     * @return 业务异常实例
     */
    public static BusinessException of(String module, String errorCode, String message, Throwable cause) {
        return new BusinessException(module, errorCode, message, cause);
    }
    
    /**
     * 创建用户服务异常
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     * @return 业务异常实例
     */
    public static BusinessException userServiceException(String errorCode, String message) {
        return new BusinessException("USER_SERVICE", errorCode, message);
    }
    
    /**
     * 创建风控服务异常
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     * @return 业务异常实例
     */
    public static BusinessException riskControlException(String errorCode, String message) {
        return new BusinessException("RISK_CONTROL", errorCode, message);
    }
}