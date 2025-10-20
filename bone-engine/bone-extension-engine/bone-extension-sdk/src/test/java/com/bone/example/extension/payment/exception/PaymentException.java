package com.bone.example.extension.payment.exception;

/**
 * 支付相关业务异常
 * 用于表示支付处理过程中的各种业务异常情况
 */
public class PaymentException extends RuntimeException {
    
    private String errorCode;
    
    /**
     * 构造函数
     * @param errorCode 错误代码
     * @param message 错误消息
     */
    public PaymentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * 构造函数
     * @param errorCode 错误代码
     * @param message 错误消息
     * @param cause 根本原因
     */
    public PaymentException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * 获取错误代码
     * @return 错误代码
     */
    public String getErrorCode() {
        return errorCode;
    }
}