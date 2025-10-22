package com.bone.engine.extension.example;

import com.bone.engine.extension.context.BizContext;
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
        BizContext<PaymentService.PaymentRequest> context = BizContext.createEmpty();
        context.setBizCode("ORDER");
        context.setTenantCode("TENANT001");
        context.setScenario("NORMAL_PAY");
        context.setData(request);
        
        // 调用扩展点 - 会自动路由到AlipayServiceImpl
        return paymentService.processPayment(context);
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
        
        // 创建微信支付业务上下文
        BizContext<PaymentService.PaymentRequest> context = BizContext.createEmpty();
        context.setBizCode("MEMBERSHIP");
        context.setTenantCode("TENANT002");
        context.setScenario("MINI_APP_PAY");
        context.setData(request);
        
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
        BizContext<PaymentService.PaymentRequest> context = BizContext.createEmpty();
        context.setBizCode("ORDER");
        context.setTenantCode(tenantCode);
        context.setScenario(scenario);
        context.setData(request);
        
        // 扩展点框架会根据上下文自动选择合适的实现类
        return paymentService.processPayment(context);
    }
    
    /**
     * 演示嵌套调用场景
     */
    public void nestedPaymentDemo() {
        // 外部调用创建上下文
        BizContext<Void> outerContext = BizContext.createEmpty();
        outerContext.setBizCode("BATCH");
        outerContext.setTenantCode("TENANT001");
        
        // 处理批量支付 - 直接传递上下文
        processBatchPayments(outerContext);
    }
    
    private void processBatchPayments(BizContext<?> context) {
        // 使用传递的上下文
        System.out.println("Current tenant in batch: " + context.getTenantCode());
        
        // 这里可以批量处理多个支付请求
        // ...
    }
}