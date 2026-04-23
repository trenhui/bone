package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberOrderPriceCalculatorTest {

    private final MemberOrderPriceCalculator calculator = new MemberOrderPriceCalculator();

    @Test
    void testCalculateWithDiscount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("85"), result);
    }

    @Test
    void testCalculateWithShippingFee() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .shippingFee(new BigDecimal("10"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("93.5"), result);
    }

    @Test
    void testCalculateWithLargeAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("500"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(new BigDecimal("85425"), result);
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