package com.bone.blueprint.infrastructure.extension.order;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import java.math.BigDecimal;

@Extension(
    name = "标准订单价格计算",
    description = "标准价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "standard"
)
public class DefaultOrderPriceCalculator implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceCalculator.OrderPriceRequest request) {
        return request.getBaseAmount().add(request.getShippingFee());
    }
}