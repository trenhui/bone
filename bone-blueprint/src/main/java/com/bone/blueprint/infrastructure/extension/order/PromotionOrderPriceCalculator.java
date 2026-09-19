package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "促销订单价格计算",
    description = "促销活动期间的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "promotion")
public class PromotionOrderPriceCalculator implements ExtensionOrderPriceCalculator {
  @Override
  public BigDecimal calculate(OrderPriceRequest request) {
    BigDecimal total = request.totalBeforeDiscount();
    // 促销期间全场8折
    return total.multiply(new BigDecimal("0.8"));
  }
}
