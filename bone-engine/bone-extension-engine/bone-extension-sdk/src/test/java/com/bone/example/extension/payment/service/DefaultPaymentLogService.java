package com.bone.example.extension.payment.service;

import com.bone.example.extension.payment.PaymentResult;
import org.springframework.stereotype.Service;

/**
 * 默认支付日志服务实现
 */
@Service
public class DefaultPaymentLogService implements PaymentLogService {
    
    @Override
    public void logPayment(PaymentResult result) {
        // 简化实现，移除日志依赖
    }
}