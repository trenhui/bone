package com.bone.example.extension.payment.service;

import com.bone.example.extension.payment.PaymentResult;

/**
 * 支付日志服务接口
 * <p>
 * 提供支付记录的日志记录功能，用于追踪和审计支付操作
 */
public interface PaymentLogService {
    
    /**
     * 记录支付结果日志
     * <p>
     * 记录完整的支付结果信息，包括交易ID、金额、状态等关键信息
     * 
     * @param result 支付结果对象，包含完整的支付信息
     * @throws IllegalArgumentException 当支付结果为空时抛出
     */
    void logPayment(PaymentResult result);
    
    /**
     * 记录支付异常日志
     * <p>
     * 专门用于记录支付过程中发生的异常情况
     * 
     * @param transactionId 交易ID，如果有
     * @param orderId 订单ID
     * @param errorMessage 错误消息
     * @param errorCode 错误码
     */
    void logPaymentException(String transactionId, String orderId, String errorMessage, String errorCode);
    
    /**
     * 记录支付请求日志
     * <p>
     * 记录支付请求的详细信息，用于请求追踪和问题排查
     * 
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param amount 支付金额
     * @param paymentMethod 支付方式
     * @param requestTime 请求时间戳
     */
    void logPaymentRequest(String orderId, String userId, String amount, String paymentMethod, long requestTime);
}