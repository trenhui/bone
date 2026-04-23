package com.bone.blueprint.infrastructure.extension;

import com.bone.blueprint.domain.service.order.OrderPriceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class VipOrderPriceCalculatorTest {

    private final VipOrderPriceCalculator calculator = new VipOrderPriceCalculator();

    @Test
    void testCalculateWithBaseAmountOnly() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .shippingFee(BigDecimal.ZERO)
                .build();
        
        BigDecimal result = calculator.calculate(request);
        
        assertEquals(new BigDecimal("90"), result);
    }

    @Test
    void testCalculateWithShippingFee() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("100"))
                .shippingFee(new BigDecimal("10"))
                .build();
        
        BigDecimal result = calculator.calculate(request);
        
        assertEquals(new BigDecimal("99"), result);
    }

    @Test
    void testCalculateWithLargeAmount() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("9999.99"))
                .shippingFee(new BigDecimal("0.01"))
                .build();
        
        BigDecimal result = calculator.calculate(request);
        
        assertEquals(new BigDecimal("9000.00"), result);
    }

    @Test
    void testCalculateDiscountIs10Percent() {
        OrderPriceCalculator.OrderPriceRequest request = OrderPriceCalculator.OrderPriceRequest.builder()
                .baseAmount(new BigDecimal("1000"))
                .shippingFee(new BigDecimal("50"))
                .build();
        
        BigDecimal result = calculator.calculate(request);
        
        BigDecimal expected = new BigDecimal("1050").multiply(new BigDecimal("0.9"));
        assertEquals(expected, result);
    }
}
