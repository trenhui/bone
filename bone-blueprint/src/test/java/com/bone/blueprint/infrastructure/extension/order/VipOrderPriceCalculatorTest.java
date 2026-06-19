package com.bone.blueprint.infrastructure.extension.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VipOrderPriceCalculatorTest {

  private final VipOrderPriceCalculator calculator = new VipOrderPriceCalculator();

  @Test
  void testCalculateWithDiscount() {
    OrderPriceCalculator.OrderPriceRequest request =
        OrderPriceCalculator.OrderPriceRequest.builder().baseAmount(new BigDecimal("100")).build();

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("90").compareTo(result));
  }

  @Test
  void testCalculateWithShippingFee() {
    OrderPriceCalculator.OrderPriceRequest request =
        OrderPriceCalculator.OrderPriceRequest.builder()
            .baseAmount(new BigDecimal("100"))
            .shippingFee(new BigDecimal("10"))
            .build();

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("99").compareTo(result));
  }

  @Test
  void testCalculateWithLargeAmount() {
    OrderPriceCalculator.OrderPriceRequest request =
        OrderPriceCalculator.OrderPriceRequest.builder()
            .baseAmount(new BigDecimal("100000"))
            .shippingFee(new BigDecimal("500"))
            .build();

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("90450").compareTo(result));
  }

  @Test
  void testCalculateWithZeroAmount() {
    OrderPriceCalculator.OrderPriceRequest request =
        OrderPriceCalculator.OrderPriceRequest.builder().baseAmount(new BigDecimal("0")).build();

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("0").compareTo(result));
  }
}
