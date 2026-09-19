package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "会员订单价格计算",
    description = "会员专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "member")
public class MemberOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 会员享受85折优惠 */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.85");
  }
}
