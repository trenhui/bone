package com.bone.example.extension.payment.exception;

import com.bone.engine.extension.api.exception.ExtensionBizException;

/**
 * 支付异常类
 * <p>
 * 提供支付领域特有的异常功能和业务方法
 * 推荐使用静态工厂方法创建异常实例，提高代码可读性
 */
public class PaymentException extends ExtensionBizException {
    private static final long serialVersionUID = 1L;
    
    /**
     * 构建支付异常
     * @param errorCode 错误码
     * @param message 错误消息
     */
    public PaymentException(String errorCode, String message) {
        super("PAYMENT", errorCode, message);
    }
    
    /**
     * 构建支付异常
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 异常原因
     */
    public PaymentException(String errorCode, String message, Throwable cause) {
        super("PAYMENT", errorCode, message, cause);
    }
    
    /**
     * 创建支付验证异常
     * @param message 验证失败消息
     * @return 支付异常实例
     */
    public static PaymentException validationError(String message) {
        return new PaymentException("VALIDATION_ERROR", message);
    }
    
    /**
     * 创建支付处理异常
     * @param message 处理失败消息
     * @param cause 异常原因
     * @return 支付异常实例
     */
    public static PaymentException processingError(String message, Throwable cause) {
        return new PaymentException("PROCESSING_ERROR", message, cause);
    }
    
    /**
     * 创建余额不足异常
     * @param balance 可用余额
     * @param required 所需金额
     * @return 支付异常实例
     */
    public static PaymentException insufficientBalance(double balance, double required) {
        return new PaymentException("INSUFFICIENT_BALANCE", 
                "余额不足: 可用=" + balance + ", 所需=" + required);
    }
    
    /**
     * 创建支付超时异常
     * @param orderId 订单ID
     * @param timeoutMs 超时时间(毫秒)
     * @return 支付异常实例
     */
    public static PaymentException paymentTimeout(String orderId, long timeoutMs) {
        return new PaymentException("PAYMENT_TIMEOUT", 
                "支付超时: 订单ID=" + orderId + ", 超时时间=" + timeoutMs + "ms");
    }
}