package com.bone.blueprint.infrastructure.extension;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.blueprint.domain.service.order.OrderPriceCalculator;
import java.math.BigDecimal;

@Extension(
    name = "默认订单价格计算",
    description = "标准价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "standard"
)
public class DefaultOrderPriceExtension implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
        // 标准价格计算逻辑
        return request.getBaseAmount();
    }
}
