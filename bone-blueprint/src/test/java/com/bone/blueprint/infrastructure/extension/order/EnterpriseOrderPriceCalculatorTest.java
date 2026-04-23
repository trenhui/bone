package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnterpriseOrderPriceCalculatorTest {

    private final EnterpriseOrderPriceCalculator calculator = new EnterpriseOrderPriceCalculator();

    @Test
    void testCalculateWithDiscount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("70"), result);
    }

    @Test
    void testCalculateWithShippingFee() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .shippingFee(new BigDecimal("10"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("77"), result);
    }

    @Test
    void testCalculateWithLargeAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("500"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("70350"), result);
    }

    @Test
    void testCalculateWithZeroAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("0"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("0"), result);
    }
}