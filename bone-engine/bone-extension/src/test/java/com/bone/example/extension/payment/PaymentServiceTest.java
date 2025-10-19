package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.example.extension.result.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 支付服务测试类
 */
class PaymentServiceTest {

    private PaymentService paymentService;
    private PaymentExtPoint paymentExtPoint;

    @BeforeEach
    void setUp() {
        // 创建mock对象
        paymentExtPoint = Mockito.mock(PaymentExtPoint.class);
        paymentService = new PaymentService();
        
        // 注入mock对象（通过反射或提供setter方法）
        paymentService.setPaymentExtPoint(paymentExtPoint);
    }

    /**
     * 测试成功的支付处理流程
     */
    @Test
    void testProcessPaymentSuccess() {
        // 准备测试数据
        PaymentRequest request = new PaymentRequest();
        request.setUserId("user123");
        String tenantCode = "ECOMMERCE";
        
        // 配置mock行为
        ValidationResult validationResult = ValidationResult.success();
        when(paymentExtPoint.prePayValidate(any(BizContext.class))).thenReturn(validationResult);
        
        Map<String, BigDecimal> deductionDetails = new HashMap<>();
        PaymentCalculationResult calculationResult = PaymentCalculationResult.builder()
                .originalAmount(new BigDecimal("100.0"))
                .finalAmount(new BigDecimal("100.0"))
                .deductionDetails(deductionDetails)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .currency("CNY")
                .build();
        when(paymentExtPoint.calculatePayment(any(BizContext.class))).thenReturn(calculationResult);
        
        // 执行测试
        PaymentResult result = paymentService.processPayment(request, tenantCode);
        
        // 验证结果
        assertNotNull(result);
        assertNotNull(result.getTransactionId());
        assertTrue(result.isSuccess());
    }

    /**
     * 测试验证失败的支付处理
     */
    @Test
    void testProcessPaymentValidationFailed() {
        // 准备测试数据
        PaymentRequest request = new PaymentRequest();
        request.setUserId("user123");
        String tenantCode = "ECOMMERCE";
        
        // 配置mock行为 - 验证失败
        ValidationResult validationResult = ValidationResult.fail("INVALID_USER", "用户无效");
        when(paymentExtPoint.prePayValidate(any(BizContext.class))).thenReturn(validationResult);
        
        // 执行测试
        PaymentResult result = paymentService.processPayment(request, tenantCode);
        
        // 验证结果
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("INVALID_USER", result.getErrorCode());
    }
}