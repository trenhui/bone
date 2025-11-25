package com.bone.example.extension.payment;

import com.bone.example.extension.config.TestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

/**
 * 支付服务测试类
 * <p>
 * 测试支付服务的各种功能场景，包括正常支付处理、参数验证等
 */
@SpringBootTest(classes = TestConfig.class)
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@Slf4j
class PaymentServiceTest {

    private final PaymentService paymentService;

    @Autowired
    PaymentServiceTest(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    @Test
    void testProcessPaymentSuccess() {
        log.info("开始测试完整支付流程...");

        PaymentTestRequest ecommerceRequest = createSamplePaymentRequest();

        // 使用Spring注入的服务实例执行完整支付流程
        PaymentResult result = paymentService.processPayment(ecommerceRequest, "ECOMMERCE");

        // 验证结果
        assertNotNull(result, "支付结果不应为null");
        assertTrue(result instanceof PaymentResult, "结果应为PaymentResult类型");

        PaymentResult paymentResult = (PaymentResult) result;
        assertEquals("ECOM123", paymentResult.getOrderId(), "订单ID应匹配");
        assertEquals("USER123", paymentResult.getUserId(), "用户ID应匹配");
        assertNotNull(paymentResult.getTransactionId(), "交易ID不应为null");
        assertNotNull(paymentResult.getStatus(), "支付状态不应为null");

        log.info("完整支付流程测试通过");
    }



    /**
     * 测试基本支付处理流程
     * <p>
     * 验证支付服务能够处理基本的支付请求
     */
    @Test
    void testProcessPaymentWithValidRequest() {
        // 准备测试数据 - 使用PaymentRequest类型
        PaymentTestRequest request = createSamplePaymentRequest();
        String tenantCode = "ECOMMERCE";
        
        // 执行测试
        Object result = paymentService.processPayment(request, tenantCode);
        
        // 基本验证 - 暂时使用Object类型进行简单验证
        assertNotNull(result, "支付结果不应为null");
    }
    
    /**
     * 测试空请求参数验证
     * <p>
     * 验证传入null请求时的行为
     */
    @Test
    void testProcessPaymentWithNullRequest() {
        String tenantCode = "ECOMMERCE";
        
        // 验证异常情况
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            paymentService.processPayment(null, tenantCode);
        });
        
        // 验证异常消息
        assertTrue(exception.getMessage().contains("支付请求不能为空"));
    }
    
    /**
     * 创建示例支付请求对象
     * <p>
     * 提供测试中使用的标准支付请求对象
     * 
     * @return 示例支付请求对象
     */
    private PaymentTestRequest createSamplePaymentRequest() {
        PaymentTestRequest request = new PaymentTestRequest();
        request.setOrderId("ORDER-2023-01-01-0001");
        request.setUserId("USER001");
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentMethod("ALIPAY");
        return request;
    }
}