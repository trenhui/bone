package com.bone.engine.extension.metadata.example;

import com.bone.engine.extension.Extension;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付宝支付扩展实现
 * <p>
 * 演示如何使用新的元数据属性来描述扩展实现
 * </p>
 * 
 * @since 1.0.0
 */
@Component
@Extension(
    // 路由配置
    tenantCode = "alipay-tenant",
    bizCode = "ecommerce",
    useCase = "payment",
    scenario = "online",
    
    // 新添加的元数据属性
    description = "支付宝在线支付实现，支持标准支付流程",
    author = "payment-team@example.com",
    isDefault = false,
    isRecommended = true,
    priority = 10,
    
    // 依赖的其他扩展实现
    dependencies = {"com.example.extension.log.LoggingExtension"},
    
    // 配置属性
    properties = {
        "alipay.gateway.url=https://openapi.alipay.com/gateway.do",
        "alipay.app.id=2021000000000000",
        "alipay.notify.url=https://example.com/payment/notify/alipay",
        "alipay.timeout.express=30m",
        "alipay.use.sandbox=false"
    }
)
public class AlipayPaymentExtension implements PaymentExtPoint {
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        PaymentResult result = new PaymentResult();
        
        // 模拟支付宝支付处理逻辑
        result.setPaymentId("ALI" + UUID.randomUUID().toString().substring(0, 16).toUpperCase());
        result.setStatus("SUCCESS");
        result.setTransactionTime(LocalDateTime.now());
        result.setThirdPartyTransactionId("TP" + System.currentTimeMillis());
        
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("paymentMethod", "alipay");
        extraInfo.put("orderId", request.getOrderId());
        result.setExtraInfo(extraInfo);
        
        return result;
    }
    
    @Override
    public ValidationResult validatePayment(PaymentRequest request) {
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        
        // 验证支付金额
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            result.addErrorMessage("支付金额必须大于0");
        }
        
        // 验证支付方式
        if (request.getPaymentMethod() == null || !"alipay".equals(request.getPaymentMethod())) {
            result.addErrorMessage("支付方式必须为alipay");
        }
        
        // 验证订单号
        if (request.getOrderId() == null || request.getOrderId().isEmpty()) {
            result.addErrorMessage("订单号不能为空");
        }
        
        return result;
    }
    
    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        PaymentStatus status = new PaymentStatus();
        status.setPaymentId(paymentId);
        status.setStatusCode("SUCCESS");
        status.setStatusDescription("支付成功");
        status.setLastUpdatedTime(LocalDateTime.now());
        status.setCompletedTime(LocalDateTime.now().minusMinutes(5));
        
        return status;
    }
}