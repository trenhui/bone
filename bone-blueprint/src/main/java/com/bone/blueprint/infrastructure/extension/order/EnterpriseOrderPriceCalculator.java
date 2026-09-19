package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "企业订单价格计算",
    description = "企业客户专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "enterprise")
public class EnterpriseOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 企业客户享受7折优惠 */
  @Override
  protected BigDecimal discountRate() {
    return new BigDecimal("0.7");
  }
}
