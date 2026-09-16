package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "企业订单价格计算",
    description = "企业客户专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "enterprise")
public class EnterpriseOrderPriceCalculator implements ExtensionOrderPriceCalculator {
  @Override
  public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
    BigDecimal total = request.getBaseAmount().add(request.getShippingFee());
    // 企业客户享受7折优惠
    return total.multiply(new BigDecimal("0.7"));
  }
}
