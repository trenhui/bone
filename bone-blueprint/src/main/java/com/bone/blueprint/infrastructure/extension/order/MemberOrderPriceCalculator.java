package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.annotation.Extension;
import java.math.BigDecimal;

@Extension(
    name = "会员订单价格计算",
    description = "会员专享的价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "member")
public class MemberOrderPriceCalculator implements ExtensionOrderPriceCalculator {
  @Override
  public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
    BigDecimal total = request.getBaseAmount().add(request.getShippingFee());
    // 会员享受85折优惠
    return total.multiply(new BigDecimal("0.85"));
  }
}
