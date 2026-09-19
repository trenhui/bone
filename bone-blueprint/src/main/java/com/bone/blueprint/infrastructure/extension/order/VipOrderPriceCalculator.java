package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "VIP订单价格计算",
    description = "VIP客户享受9折优惠",
    tenant = "ALI",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip")
public class VipOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** VIP客户享受9折优惠 */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.9");
  }
}
