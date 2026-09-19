package com.bone.blueprint.infrastructure.extension.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PromotionOrderPriceCalculatorTest {

  private final PromotionOrderPriceCalculator calculator = new PromotionOrderPriceCalculator();

  @Test
  void testCalculateWithDiscount() {
    OrderPriceRequest request = OrderPriceRequest.of(new BigDecimal("100"));

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("80").compareTo(result));
  }

  @Test
  void testCalculateWithShippingFee() {
    OrderPriceRequest request = OrderPriceRequest.of(new BigDecimal("100"), new BigDecimal("10"));

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("88").compareTo(result));
  }

  @Test
  void testCalculateWithLargeAmount() {
    OrderPriceRequest request =
        OrderPriceRequest.of(new BigDecimal("100000"), new BigDecimal("500"));

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("80400").compareTo(result));
  }

  @Test
  void testCalculateWithZeroAmount() {
    OrderPriceRequest request = OrderPriceRequest.of(new BigDecimal("0"));

    BigDecimal result = calculator.calculate(request);

    assertEquals(0, new BigDecimal("0").compareTo(result));
  }
}
