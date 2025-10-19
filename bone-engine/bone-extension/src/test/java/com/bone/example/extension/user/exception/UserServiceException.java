package com.bone.example.extension.user.exception;

/**
 * 用户服务模块的自定义异常类
 * 用于用户服务处理过程中的业务异常
 */
public class UserServiceException extends RuntimeException {
    
    private String errorCode;
    private String userId;
    
    public UserServiceException(String message) {
        super(message);
    }
    
    public UserServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public UserServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public UserServiceException(String message, String errorCode, String userId) {
        super(message);
        this.errorCode = errorCode;
        this.userId = userId;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getUserId() {
        return userId;
    }
}