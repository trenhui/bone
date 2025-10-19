package com.bone.example.extension.payment.service;

import com.bone.example.extension.payment.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 默认支付日志服务实现
 */
@Service
@Slf4j
public class DefaultPaymentLogService implements PaymentLogService {
    
    @Override
    public void logPayment(PaymentResult result) {
        // 记录支付日志
        // 实际应用中可能需要写入数据库、消息队列等
        log.info("Logging payment: {}", result.getTransactionId());
    }
}