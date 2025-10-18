package com.bone.procurement.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常类
 * 用于处理业务逻辑中的错误和异常情况
 * 可以指定错误码、错误消息和HTTP状态码
 */
@Getter
@Setter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private String errorCode;
    
    /**
     * HTTP状态码
     */
    private HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
    
    /**
     * 是否记录详细日志
     */
    private boolean logDetail = true;
    
    /**
     * 构造函数
     * @param message 错误消息
     */
    public BusinessException(String message) {
        super(message);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 异常原因
     */
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     */
    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param cause 异常原因
     */
    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param httpStatus HTTP状态码
     */
    public BusinessException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param errorCode 错误码
     * @param httpStatus HTTP状态码
     * @param cause 异常原因
     */
    public BusinessException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    /**
     * 构造函数
     * @param message 错误消息
     * @param cause 异常原因
     * @param logDetail 是否记录详细日志
     */
    public BusinessException(String message, Throwable cause, boolean logDetail) {
        super(message, cause);
        this.logDetail = logDetail;
    }
}