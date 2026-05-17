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

        assertEquals(0, new BigDecimal("85").compareTo(result));
    }

    @Test
    void testCalculateWithShippingFee() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .shippingFee(new BigDecimal("10"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(0, new BigDecimal("93.5").compareTo(result));
    }

    @Test
    void testCalculateWithLargeAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100000"))
                .shippingFee(new BigDecimal("500"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(0, new BigDecimal("85425").compareTo(result));
    }

    @Test
    void testCalculateWithZeroAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("0"))
                .build();

        BigDecimal result = calculator.calculate(request);

        assertEquals(0, new BigDecimal("0").compareTo(result));
    }
}