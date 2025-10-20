package com.bone.example.extension.payment.service;

import com.bone.example.extension.payment.PaymentResult;

/**
 * 支付日志服务接口
 * 提供支付记录日志功能
 */
public interface PaymentLogService {
    
    /**
     * 记录支付结果日志
     * @param result 支付结果
     */
    void logPayment(PaymentResult result);
}