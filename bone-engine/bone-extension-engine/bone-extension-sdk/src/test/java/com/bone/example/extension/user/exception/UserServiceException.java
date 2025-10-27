package com.bone.example.extension.user.exception;

import com.bone.engine.extension.exception.BusinessException;

/**
 * 用户服务异常类
 * <p>
 * 提供用户服务领域特有的异常功能和业务方法
 */
public class UserServiceException extends BusinessException {
    private static final long serialVersionUID = 1L;
    
    /**
     * 构建用户服务异常
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public UserServiceException(String errorCode, String message) {
        super("USER_SERVICE", errorCode, message);
    }
    
    /**
     * 构建用户服务异常
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常原因
     */
    public UserServiceException(String errorCode, String message, Throwable cause) {
        super("USER_SERVICE", errorCode, message, cause);
    }
    
    /**
     * 创建用户未找到异常
     * @param userId 用户ID
     * @return 用户服务异常实例
     */
    public static UserServiceException userNotFound(String userId) {
        return new UserServiceException("USER_NOT_FOUND", "用户不存在: " + userId);
    }
    
    /**
     * 创建用户验证异常
     * @param message 验证失败消息
     * @return 用户服务异常实例
     */
    public static UserServiceException validationError(String message) {
        return new UserServiceException("USER_VALIDATION_ERROR", message);
    }
}