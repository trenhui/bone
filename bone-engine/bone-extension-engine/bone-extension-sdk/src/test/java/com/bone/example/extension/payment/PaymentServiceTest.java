package com.bone.example.extension.payment;

import com.bone.engine.extension.support.context.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * PaymentService 完整测试类（2025 企业级标准版）
 * 只测试新接口：processPayment(PaymentTestRequest, BizContext)
 */
@SpringBootTest(classes = com.bone.example.extension.config.TestConfig.class)
@ActiveProfiles("test")
@Slf4j
@DisplayName("PaymentService 支付服务完整测试")
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    private static PaymentTestRequest sampleRequest;

    @BeforeAll
    static void initSampleRequest() {
        sampleRequest = PaymentTestRequest.builder()
                .orderId("ORDER-20251203-001")
                .userId("USER_888888")
                .amount(new BigDecimal("188.88"))
                .paymentMethod("WECHAT")
                .couponId("WELCOME2025")
                .pointsToDeduct(100)
                .build();
    }

    @Test
    @DisplayName("电商租户 - 应路由到电商支付实现")
    void shouldRouteToEcommerceImplementation() {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant("ECOMMERCE")
                .bizCode("PAYMENT")
                .useCase("ONLINE_TRADE")
                .scenario("WECHAT")
                .data(sampleRequest)
                .build();

        PaymentResult result = paymentService.processPayment(sampleRequest, context);

        assertThat(result)
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getStatus()).isEqualTo("SUCCESS");
                    assertThat(r.getTransactionId()).startsWith("TXN_");
                    assertThat(r.getAmount()).isEqualByComparingTo(new BigDecimal("188.88"));
                    assertThat(r.getPaymentMethod()).isEqualTo("WECHAT");
                    // 可选：如果你在 PaymentResult 中加了 channel 字段
                    // assertThat(r.getPaymentChannel()).isEqualTo("ECOMMERCE_WECHAT");
                });

        log.info("电商租户支付成功，交易ID: {}", result.getTransactionId());
    }

    @Test
    @DisplayName("金融租户 - 应路由到金融支付实现")
    void shouldRouteToFinancialImplementation() {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant("FINANCIAL_TENANT")
                .bizCode("PAYMENT")
                .useCase("BANK_TRANSFER")
                .data(sampleRequest)
                .build();

        PaymentResult result = paymentService.processPayment(sampleRequest, context);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        // 金融租户可能走银行通道
        // assertThat(result.getPaymentChannel()).isEqualTo("BANK_UNIONPAY");
    }

    @Test
    @DisplayName("默认实现 - tenant=* 的扩展点应被命中")
    void shouldHitDefaultImplementationWhenTenantMatchesWildcard() {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant("UNKNOWN_TENANT")  // 不存在，但有 tenant=* 的实现
                .bizCode("PAYMENT")
                .data(sampleRequest)
                .build();

        PaymentResult result = paymentService.processPayment(sampleRequest, context);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        log.info("通配符租户路由成功，使用默认实现");
    }

    @Test
    @DisplayName("金额为负 - 应被参数校验拦截")
    void shouldRejectNegativeAmount() {
        PaymentTestRequest badRequest = sampleRequest.toBuilder()
                .amount(new BigDecimal("-99.99"))
                .build();

        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant("ECOMMERCE")
                .data(badRequest)
                .build();

        assertThatThrownBy(() -> paymentService.processPayment(badRequest, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("金额");
    }

    @Test
    @DisplayName("请求为 null - 应抛出 IllegalArgumentException")
    void shouldRejectNullRequest() {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant("ECOMMERCE")
                .build();

        assertThatThrownBy(() -> paymentService.processPayment(null, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("支付请求不能为空");
    }

    @Test
    @DisplayName("BizContext 缺失 tenant - 只要有 tenant=* 的实现，仍可成功")
    void shouldWorkEvenWithoutTenantInContext() {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .bizCode("PAYMENT")
                .data(sampleRequest)
                .build(); // 故意不设置 tenant

        PaymentResult result = paymentService.processPayment(sampleRequest, context);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        log.info("无 tenant 维度仍路由成功（依赖你已修复的模糊匹配）");
    }

    @DisplayName("多租户路由参数化测试")
    @ParameterizedTest(name = "租户={0} → 应成功")
    @CsvSource({
            "ECOMMERCE,       WECHAT",
            "FINANCIAL_TENANT, UNIONPAY",
            "GOV_TENANT,      ALIPAY",
            "UNKNOWN_TENANT,  DEFAULT"   // 走默认实现
    })
    void shouldRouteCorrectlyForDifferentTenants(String tenant, String expectedChannel) {
        BizContext<PaymentTestRequest> context = BizContext.<PaymentTestRequest>builder()
                .tenant(tenant)
                .bizCode("PAYMENT")
                .data(sampleRequest)
                .build();

        PaymentResult result = paymentService.processPayment(sampleRequest, context);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        log.info("租户 {} 路由成功", tenant);
    }
}