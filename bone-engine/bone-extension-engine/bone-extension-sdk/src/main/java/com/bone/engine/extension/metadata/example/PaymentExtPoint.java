package com.bone.engine.extension.metadata.example;

import com.bone.engine.extension.ExtPoint;

/**
 * 支付扩展点示例接口
 * <p>
 * 演示如何使用新的元数据属性来描述扩展点
 * </p>
 * 
 * @since 1.0.0
 */
@ExtPoint(
    name = "支付扩展点",
    description = "支付处理扩展点，支持多种支付方式的实现",
    category = "payment"
)
public interface PaymentExtPoint {
    
    /**
     * 处理支付请求
     * 
     * @param request 支付请求参数
     * @return 支付结果
     */
    PaymentResult processPayment(PaymentRequest request);
    
    /**
     * 验证支付参数
     * 
     * @param request 支付请求参数
     * @return 验证结果
     */
    ValidationResult validatePayment(PaymentRequest request);
    
    /**
     * 获取支付状态
     * 
     * @param paymentId 支付ID
     * @return 支付状态信息
     */
    PaymentStatus getPaymentStatus(String paymentId);
}