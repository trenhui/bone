package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "促销订单价格计算",
    description = "促销活动期间的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "promotion")
public class PromotionOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 促销期间全场8折 */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.8");
  }
}
