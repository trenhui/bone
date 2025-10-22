package com.bone.engine.extension.example;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.context.BizContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 支付服务使用示例
 * <p>
 * 演示如何在实际业务中使用扩展点框架
 * </p>
 */
@Service
public class PaymentServiceDemo {

    @Autowired
    private PaymentService paymentService;

    /**
     * 使用支付宝进行订单支付
     */
    public PaymentService.PaymentResult alipayOrderPayment(String orderId, BigDecimal amount, String userId) {
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(amount);
        request.setCurrency("CNY");
        request.setPaymentMethod("ALIPAY");
        request.setUserId(userId);
        
        // 创建业务上下文
        BizContext<PaymentService.PaymentRequest> context = BizContext.<PaymentService.PaymentRequest>builder()
            .bizCode("ORDER")
            .tenantCode("TENANT001")
            .scenario("NORMAL_PAY")
            .data(request)
            .build();
        
        // 设置上下文到ThreadLocal（可选，如果在其他地方需要访问）
        BizContextHolder.set(context);
        
        try {
            // 调用扩展点 - 会自动路由到AlipayServiceImpl
            return paymentService.processPayment(context);
        } finally {
            // 清理ThreadLocal（推荐在finally块中执行）
            BizContextHolder.clear();
        }
    }

    /**
     * 使用微信支付进行会员支付
     */
    public PaymentService.PaymentResult wechatMembershipPayment(String orderId, BigDecimal amount, String userId) {
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(amount);
        request.setCurrency("CNY");
        request.setPaymentMethod("WECHAT");
        request.setUserId(userId);
        
        // 创建业务上下文
        BizContext<PaymentService.PaymentRequest> context = BizContext.<PaymentService.PaymentRequest>builder()
            .bizCode("MEMBERSHIP")
            .tenantCode("TENANT002")
            .scenario("MINI_APP_PAY")
            .data(request)
            .build();
        
        // 调用扩展点 - 会自动路由到WechatPayServiceImpl
        return paymentService.processPayment(context);
    }

    /**
     * 演示根据不同租户和业务场景自动路由
     */
    public PaymentService.PaymentResult processPaymentByScenario(
            String orderId, 
            BigDecimal amount, 
            String userId, 
            String tenantCode, 
            String scenario) {
        
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId(orderId);
        request.setAmount(amount);
        request.setCurrency("CNY");
        request.setUserId(userId);
        
        // 根据场景设置支付方式
        if ("MINI_APP_PAY".equals(scenario)) {
            request.setPaymentMethod("WECHAT");
        } else {
            request.setPaymentMethod("ALIPAY");
        }
        
        // 创建业务上下文
        BizContext<PaymentService.PaymentRequest> context = BizContext.<PaymentService.PaymentRequest>builder()
            .bizCode("ORDER")
            .tenantCode(tenantCode)
            .scenario(scenario)
            .data(request)
            .build();
        
        // 扩展点框架会根据上下文自动选择合适的实现类
        return paymentService.processPayment(context);
    }
    
    /**
     * 演示嵌套调用场景
     */
    public void nestedPaymentDemo() {
        // 外部调用设置上下文
        BizContext<Void> outerContext = BizContext.<Void>builder()
            .bizCode("BATCH")
            .tenantCode("TENANT001")
            .build();
        
        BizContextHolder.set(outerContext);
        
        try {
            // 处理批量支付
            processBatchPayments();
        } finally {
            BizContextHolder.clear();
        }
    }
    
    private void processBatchPayments() {
        // 在内部方法中可以访问外部设置的上下文
        BizContext<?> currentContext = BizContextHolder.getCurrentContext();
        System.out.println("Current tenant in batch: "); // 移除getTenantCode()方法调用
        
        // 这里可以批量处理多个支付请求
        // ...
    }
}