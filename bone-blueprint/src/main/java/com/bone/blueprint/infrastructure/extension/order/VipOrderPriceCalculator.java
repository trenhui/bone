package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "VIP订单价格计算",
    description = "VIP客户享受9折优惠",
    tenant = "ALI",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip")
public class VipOrderPriceCalculator implements OrderPriceCalculator {
  @Override
  public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
    BigDecimal total = request.getBaseAmount().add(request.getShippingFee());
    return total.multiply(new BigDecimal("0.9"));
  }
}
