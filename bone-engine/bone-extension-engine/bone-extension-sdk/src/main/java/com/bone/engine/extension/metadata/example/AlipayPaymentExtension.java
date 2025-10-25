package com.bone.engine.extension.metadata.example;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付宝支付扩展实现
 */
@Component
@Extension(
    tenantCode = "alipay",
    bizCode = "ecommerce",
    scenario = "online",
    condition = "#request.getPaymentMethod() == 'alipay'",
    priority = 100,
    enabled = true
)
@ExtensionDoc(
    description = "基于支付宝开放平台的支付处理扩展",
    applicableScenarios = "适用于电商平台的在线支付场景",
    implementationDetails = "通过支付宝开放平台SDK实现支付功能",
    performanceConsiderations = "平均响应时间：200ms",
    notes = "需要配置支付宝开放平台的appId和私钥",
    author = "payment-team"
)
public class AlipayPaymentExtension implements PaymentExtPoint {
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // 记录支付开始日志
        System.out.println("开始处理支付宝支付请求: 订单号=" + request.getOrderId() + ", 金额=" + request.getAmount());
        
        PaymentResult result = new PaymentResult();
        
        // 模拟支付宝支付处理逻辑
        String paymentId = "ALI" + UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        result.setPaymentId(paymentId);
        result.setStatus("SUCCESS");
        result.setMessage("支付成功");
        result.setPaidAmount(request.getAmount());
        result.setPaidTime(System.currentTimeMillis());
        result.setTransactionId("TP" + System.currentTimeMillis());
        
        System.out.println("支付宝支付处理完成: 支付ID=" + paymentId);
        return result;
    }
    
    @Override
    public ValidationResult validatePayment(PaymentRequest request) {
        ValidationResult result = new ValidationResult();
        Map<String, String> details = new HashMap<>();
        
        // 验证必填字段
        boolean valid = true;
        
        if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
            valid = false;
            details.put("orderId", "订单号不能为空");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            valid = false;
            details.put("amount", "支付金额必须大于0");
        }
        
        if (request.getPaymentMethod() == null || !"alipay".equals(request.getPaymentMethod())) {
            valid = false;
            details.put("paymentMethod", "支付方式必须为alipay");
        }
        
        // 设置验证结果
        result.setValid(valid);
        result.setDetails(details);
        
        if (!valid) {
            result.setErrorCode("INVALID_PARAM");
            result.setErrorMessage("参数验证失败，请检查请求参数");
        }
        
        return result;
    }
    
    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        // 验证支付ID
        if (paymentId == null || paymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("支付ID不能为空");
        }
        
        // 模拟查询支付状态
        PaymentStatus status = new PaymentStatus();
        status.setPaymentId(paymentId);
        status.setStatusCode("SUCCESS");
        status.setStatusDesc("支付成功");
        status.setUpdateTime(System.currentTimeMillis());
        status.setChannel("ALIPAY");
        status.setAmount(new BigDecimal(100.00)); // 模拟金额
        
        return status;
    }
}