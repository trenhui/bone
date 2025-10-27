package com.bone.example.extension.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bone.example.extension.common.PaymentRequest;

/**
 * 支付演示服务测试类
 */
class PaymentDemoServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(PaymentDemoServiceTest.class);
    private PaymentDemoService paymentService;
    
    @BeforeEach
    void setUp() {
        paymentService = new PaymentDemoService();
        logger.info("测试准备完成");
    }

    @Test
    void testProcessPaymentWithValidRequest() {
        // 准备测试数据
        PaymentDemoRequest request = new PaymentDemoRequest();
        request.setOrderId("ORDER-2023-01-01-0001");
        request.setUserId("USER001");
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentMethod("ALIPAY");
        String tenantCode = "ECOMMERCE";
        
        // 执行测试
        Object result = paymentService.processPayment(request, tenantCode);
        
        // 验证结果
        assertNotNull(result, "支付结果不应为null");
        assertTrue(result instanceof Map, "支付结果应为Map类型");
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("userId"), "结果应包含userId");
        assertEquals("USER001", resultMap.get("userId"), "userId值应正确");
    }
    
    /**
     * 支付演示请求类
     * <p>
     * 继承自通用PaymentRequest，用于演示服务的支付请求
     */
    static class PaymentDemoRequest extends PaymentRequest {
    }
    
    /**
     * 支付演示服务类
     */
    static class PaymentDemoService {
        private static final Logger logger = LoggerFactory.getLogger(PaymentDemoService.class);
        
        public Object processPayment(final PaymentDemoRequest request, final String tenantCode) {
            // 参数校验
            validateRequest(request, tenantCode);
            
            try {
                // 处理支付逻辑
                logger.info("处理支付请求: 订单号={}, 用户ID={}, 金额={}", 
                        request.getOrderId(), request.getUserId(), request.getAmount());
                
                // 创建默认支付结果
                return createDefaultPaymentResult(request);
            } catch (Exception e) {
                logger.error("支付处理失败", e);
                throw new RuntimeException("支付处理失败", e);
            }
        }
        
        private void validateRequest(final PaymentDemoRequest request, final String tenantCode) {
            if (request == null) {
                throw new IllegalArgumentException("支付请求不能为空");
            }
            if (tenantCode == null || tenantCode.trim().isEmpty()) {
                throw new IllegalArgumentException("租户代码不能为空");
            }
            if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
                throw new IllegalArgumentException("订单号不能为空");
            }
            if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
                throw new IllegalArgumentException("用户ID不能为空");
            }
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("支付金额必须大于0");
            }
            if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
                throw new IllegalArgumentException("支付方式不能为空");
            }
        }
        
        private Object createDefaultPaymentResult(final PaymentDemoRequest request) {
            Map<String, Object> result = new HashMap<>();
            result.put("userId", request.getUserId());
            result.put("orderId", request.getOrderId());
            result.put("amount", request.getAmount());
            result.put("paymentMethod", request.getPaymentMethod());
            result.put("transactionId", "TRX-" + System.currentTimeMillis());
            result.put("success", true);
            return result;
        }
    }
}