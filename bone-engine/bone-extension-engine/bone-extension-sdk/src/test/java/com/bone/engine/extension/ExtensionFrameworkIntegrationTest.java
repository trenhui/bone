package com.bone.engine.extension;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.example.PaymentService;
import com.bone.engine.extension.proxy.ExtPointProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扩展点框架集成测试
 * <p>
 * 测试整个扩展点框架的功能是否正常工作，包括扩展点的注册、发现、路由和执行
 * </p>
 */
@SpringBootTest
@SpringJUnitConfig
public class ExtensionFrameworkIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ExtPointProxyFactory extPointProxyFactory;

    /**
     * 测试支付宝支付扩展点
     */
    @Test
    public void testAlipayExtension() {
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId("ORDER001");
        request.setAmount(new BigDecimal(100.00));
        request.setCurrency("CNY");
        request.setPaymentMethod("ALIPAY");
        request.setUserId("USER001");

        // 创建业务上下文
        BizContext<PaymentService.PaymentRequest> context = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("ORDER")
                .setTenantCode("TENANT001")
                .setScenario("NORMAL_PAY")
                .setBizData(request)
                .build();

        // 调用扩展点
        PaymentService.PaymentResult result = paymentService.processPayment(context);

        // 验证结果
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getPaymentId().startsWith("ALI"));
        assertEquals(new BigDecimal(100.00), result.getPaidAmount());
    }

    /**
     * 测试微信支付扩展点
     */
    @Test
    public void testWechatPayExtension() {
        // 创建支付请求
        PaymentService.PaymentRequest request = new PaymentService.PaymentRequest();
        request.setOrderId("ORDER002");
        request.setAmount(new BigDecimal(200.00));
        request.setCurrency("CNY");
        request.setPaymentMethod("WECHAT");
        request.setUserId("USER002");

        // 创建业务上下文
        BizContext<PaymentService.PaymentRequest> context = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("MEMBERSHIP")
                .setTenantCode("TENANT002")
                .setScenario("MINI_APP_PAY")
                .setBizData(request)
                .build();

        // 调用扩展点
        PaymentService.PaymentResult result = paymentService.processPayment(context);

        // 验证结果
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertTrue(result.getPaymentId().startsWith("WX"));
        assertEquals(new BigDecimal(200.00), result.getPaidAmount());
    }

    /**
     * 测试扩展点路由机制
     */
    @Test
    public void testExtensionRouting() {
        // 测试不同租户的路由
        PaymentService.PaymentRequest request1 = new PaymentService.PaymentRequest();
        request1.setOrderId("ORDER003");
        request1.setAmount(new BigDecimal(300.00));
        request1.setPaymentMethod("ALIPAY");

        BizContext<PaymentService.PaymentRequest> context1 = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("ORDER")
                .setTenantCode("TENANT001")
                .setBizData(request1)
                .build();

        PaymentService.PaymentResult result1 = paymentService.processPayment(context1);
        assertTrue(result1.getPaymentId().startsWith("ALI"));

        // 测试不同场景的路由
        PaymentService.PaymentRequest request2 = new PaymentService.PaymentRequest();
        request2.setOrderId("ORDER004");
        request2.setAmount(new BigDecimal(400.00));
        request2.setPaymentMethod("WECHAT");

        BizContext<PaymentService.PaymentRequest> context2 = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("ORDER")
                .setTenantCode("TENANT002")
                .setScenario("MINI_APP_PAY")
                .setBizData(request2)
                .build();

        PaymentService.PaymentResult result2 = paymentService.processPayment(context2);
        assertTrue(result2.getPaymentId().startsWith("WX"));
    }

    /**
     * 测试扩展点代理工厂功能
     */
    @Test
    public void testExtPointProxyFactory() {
        // 验证代理工厂能正确创建代理
        assertNotNull(extPointProxyFactory);
        
        // 验证PaymentService已经被代理
        assertNotNull(paymentService);
        assertTrue(paymentService.getClass().getName().contains("$Proxy"));
    }

    /**
     * 测试支付方式路由功能
     */
    @Test
    public void testPaymentMethodRouting() {
        // 相同租户不同支付方式的测试
        PaymentService.PaymentRequest alipayRequest = new PaymentService.PaymentRequest();
        alipayRequest.setOrderId("ORDER005");
        alipayRequest.setAmount(new BigDecimal(500.00));
        alipayRequest.setPaymentMethod("ALIPAY");

        PaymentService.PaymentRequest wechatRequest = new PaymentService.PaymentRequest();
        wechatRequest.setOrderId("ORDER006");
        wechatRequest.setAmount(new BigDecimal(600.00));
        wechatRequest.setPaymentMethod("WECHAT");

        // 使用相同的上下文（除了业务数据）
        BizContext<PaymentService.PaymentRequest> alipayContext = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("ORDER")
                .setTenantCode("DEFAULT")
                .setBizData(alipayRequest)
                .build();

        BizContext<PaymentService.PaymentRequest> wechatContext = new BizContext.Builder<PaymentService.PaymentRequest>()
                .setBizCode("ORDER")
                .setTenantCode("DEFAULT")
                .setBizData(wechatRequest)
                .build();

        // 调用扩展点
        PaymentService.PaymentResult alipayResult = paymentService.processPayment(alipayContext);
        PaymentService.PaymentResult wechatResult = paymentService.processPayment(wechatContext);

        // 验证路由正确性
        assertTrue(alipayResult.getPaymentId().startsWith("ALI"));
        assertTrue(wechatResult.getPaymentId().startsWith("WX"));
    }
}