package com.bone.blueprint.domain.extension.order;

import com.bone.engine.extension.api.ExtensionPointExecutor;
import com.bone.engine.extension.api.ExtensionRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OrderPriceCalculatorExtensionTest {

    @Autowired
    private ExtensionPointExecutor extensionPointExecutor;

    @Autowired
    private ExtensionRouter extensionRouter;

    @Test
    void testStandardScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            ctx.setScenario("standard");
        });

        assertEquals(new BigDecimal("100"), result);
    }

    @Test
    void testVipScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            ctx.setScenario("vip");
            ctx.setTenant("ALI");
        });

        assertEquals(new BigDecimal("90"), result);
    }

    @Test
    void testPromotionScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            ctx.setScenario("promotion");
        });

        assertEquals(new BigDecimal("80"), result);
    }

    @Test
    void testMemberScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            ctx.setScenario("member");
        });

        assertEquals(new BigDecimal("85"), result);
    }

    @Test
    void testEnterpriseScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            ctx.setScenario("enterprise");
        });

        assertEquals(new BigDecimal("70"), result);
    }

    @Test
    void testDefaultScenario() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = extensionPointExecutor.execute(OrderPriceCalculator.class, request, ctx -> {
            // 不设置场景，使用默认实现
        });

        assertEquals(new BigDecimal("100"), result);
    }
}
