package com.bone.example.extension.payment.exception;

import java.util.Objects;

/**
 * 支付异常类
 * <p>
 * 支付处理过程中抛出的业务异常，包含错误码和错误消息
 */
public class PaymentException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    private final String errorCode;      // 错误码，用于标识具体的错误类型
    private final String errorMessage;   // 错误消息，提供详细的错误描述
    
    /**
     * 构造一个空的支付异常
     */
    public PaymentException() {
        super();
        this.errorCode = null;
        this.errorMessage = null;
    }
    
    /**
     * 构造一个带有错误消息的支付异常
     * 
     * @param message 错误消息
     */
    public PaymentException(String message) {
        super(message);
        this.errorCode = null;
        this.errorMessage = message;
    }
    
    /**
     * 构造一个带有错误消息和原因的支付异常
     * 
     * @param message 错误消息
     * @param cause 异常原因
     */
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.errorMessage = message;
    }
    
    /**
     * 构造一个带有错误码和错误消息的支付异常
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    
    /**
     * 构造一个带有错误码、错误消息和原因的支付异常
     * 
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常原因
     */
    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    
    /**
     * 获取错误码
     * 
     * @return 错误码，用于标识具体的错误类型
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 获取错误消息
     * 
     * @return 详细的错误描述
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PaymentException");
        if (errorCode != null) {
            sb.append("[错误码: ").append(errorCode).append("]");
        }
        sb.append(": ");
        sb.append(errorMessage != null ? errorMessage : super.getMessage());
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentException that = (PaymentException) o;
        return Objects.equals(errorCode, that.errorCode) && 
               Objects.equals(errorMessage, that.errorMessage);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(errorCode, errorMessage);
    }
    
    /**
     * 创建支付验证异常
     * 
     * @param message 验证失败消息
     * @return 支付异常实例
     */
    public static PaymentException validationError(String message) {
        return new PaymentException("PAYMENT_VALIDATION_ERROR", message);
    }
    
    /**
     * 创建支付处理异常
     * 
     * @param message 处理失败消息
     * @param cause 异常原因
     * @return 支付异常实例
     */
    public static PaymentException processingError(String message, Throwable cause) {
        return new PaymentException("PAYMENT_PROCESSING_ERROR", message, cause);
    }
}