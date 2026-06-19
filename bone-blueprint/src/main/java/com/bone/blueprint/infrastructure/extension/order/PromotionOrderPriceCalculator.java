package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "促销订单价格计算",
    description = "促销活动期间的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "promotion")
public class PromotionOrderPriceCalculator implements OrderPriceCalculator {
  @Override
  public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
    BigDecimal total = request.getBaseAmount().add(request.getShippingFee());
    // 促销期间全场8折
    return total.multiply(new BigDecimal("0.8"));
  }
}
