package com.bone.smartmeta.engine.exception;

import java.util.Map;

/**
 * 基础业务异常类
 * 提供统一的异常处理机制，支持错误码、上下文信息和异常级别
 */
public abstract class BaseBusinessException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private final String errorCode;
    
    /**
     * 异常级别
     */
    private final ExceptionLevel level;
    
    /**
     * 上下文信息
     */
    private final Map<String, Object> context;
    
    /**
     * 构造函数
     * @param message 错误消息
     */
    protected BaseBusinessException(String message) {
        this(message, null, ExceptionLevel.ERROR, null);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     */
    protected BaseBusinessException(String message, String errorCode) {
        this(message, errorCode, ExceptionLevel.ERROR, null);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param level 异常级别
     */
    protected BaseBusinessException(String message, String errorCode, ExceptionLevel level) {
        this(message, errorCode, level, null);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param level 异常级别
     * @param context 上下文信息
     */
    protected BaseBusinessException(String message, String errorCode, ExceptionLevel level, Map<String, Object> context) {
        super(message);
        this.errorCode = errorCode;
        this.level = level != null ? level : ExceptionLevel.ERROR;
        this.context = context;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 根异常
     */
    protected BaseBusinessException(String message, Throwable cause) {
        this(message, null, ExceptionLevel.ERROR, cause, null);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param cause 根异常
     */
    protected BaseBusinessException(String message, String errorCode, Throwable cause) {
        this(message, errorCode, ExceptionLevel.ERROR, cause, null);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param level 异常级别
     * @param cause 根异常
     * @param context 上下文信息
     */
    protected BaseBusinessException(String message, String errorCode, ExceptionLevel level, Throwable cause, Map<String, Object> context) {
        super(message, cause);
        this.errorCode = errorCode;
        this.level = level != null ? level : ExceptionLevel.ERROR;
        this.context = context;
    }
    
    /**
     * 获取错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取异常级别
     */
    public ExceptionLevel getLevel() {
        return level;
    }
    
    /**
     * 获取上下文信息
     */
    public Map<String, Object> getContext() {
        return context;
    }
    
    /**
     * 异常级别枚举
     */
    public enum ExceptionLevel {
        INFO,      // 信息级别
        WARNING,   // 警告级别
        ERROR      // 错误级别
    }
}