package com.bone.example.extension.payment;

/**
 * 支付服务
 */
public class PaymentService {

    /**
     * 处理支付请求
     */
    public Object processPayment(Object request, String tenantCode) {
        // 简化实现，直接返回空对象
        return null;
    }
    
    /**
     * 设置支付扩展点（用于测试）
     */
    public void setPaymentExtPoint(PaymentExtPoint paymentExtPoint) {
        // 用于测试的设置方法
    }
}