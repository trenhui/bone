package com.bone.blueprint.infrastructure.extension;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.blueprint.domain.service.order.OrderPriceCalculator;
import java.math.BigDecimal;

@Extension(
    name = "VIP订单价格计算",
    description = "VIP客户享受9折优惠",
    tenant = "ALI",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip"
)
public class VipOrderPriceExtension implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
        // VIP价格计算逻辑（9折）
        return request.getBaseAmount().multiply(new BigDecimal("0.9"));
    }
}
