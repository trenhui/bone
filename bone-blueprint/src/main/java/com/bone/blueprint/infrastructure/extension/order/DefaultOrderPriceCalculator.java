package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "标准订单价格计算",
    description = "标准价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "standard")
public class DefaultOrderPriceCalculator extends AbstractRateOrderPriceCalculator {
  /** 标准价：不打折（{@link BigDecimal#ONE} 使计算式与折扣型实现保持同一口径）。 */
  @Override
  protected BigDecimal discountRate() {
    return BigDecimal.ONE;
  }
}
