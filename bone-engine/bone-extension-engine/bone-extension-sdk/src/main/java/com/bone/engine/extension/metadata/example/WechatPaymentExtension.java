package com.bone.engine.extension.metadata.example;

import com.bone.engine.extension.Extension;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付扩展实现
 * <p>
 * 演示如何使用新的元数据属性和不同的优先级
 * </p>
 * 
 * @since 1.0.0
 */
@Component
@Extension(
    // 路由配置
    tenantCode = "wechat-tenant",
    bizCode = "ecommerce",
    useCase = "payment",
    scenario = "online",
    
    // 新添加的元数据属性
    description = "微信在线支付实现，支持微信支付和小程序支付",
    author = "payment-team@example.com",
    isDefault = true,
    isRecommended = false,
    priority = 20, // 优先级低于支付宝实现
    
    // 依赖的其他扩展实现
    dependencies = {"com.example.extension.log.LoggingExtension", "com.example.extension.security.SignatureExtension"},
    
    // 配置属性
    properties = {
        "wechat.api.url=https://api.mch.weixin.qq.com",
        "wechat.app.id=wx1234567890123456",
        "wechat.mch.id=1234567890",
        "wechat.notify.url=https://example.com/payment/notify/wechat",
        "wechat.timeout=1800",
        "wechat.cert.path=/config/certs/wechat/"
    }
)
public class WechatPaymentExtension implements PaymentExtPoint {
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        PaymentResult result = new PaymentResult();
        
        // 模拟微信支付处理逻辑
        result.setPaymentId("WX" + UUID.randomUUID().toString().substring(0, 16).toUpperCase());
        result.setStatus("SUCCESS");
        result.setTransactionTime(LocalDateTime.now());
        result.setThirdPartyTransactionId("WX" + System.currentTimeMillis());
        
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("paymentMethod", "wechat");
        extraInfo.put("orderId", request.getOrderId());
        extraInfo.put("appId", "wx1234567890123456");
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
        if (request.getPaymentMethod() == null || !"wechat".equals(request.getPaymentMethod())) {
            result.addErrorMessage("支付方式必须为wechat");
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
        status.setCompletedTime(LocalDateTime.now().minusMinutes(3));
        
        return status;
    }
}