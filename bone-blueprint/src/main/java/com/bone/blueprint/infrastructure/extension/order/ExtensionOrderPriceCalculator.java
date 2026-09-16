package com.bone.blueprint.infrastructure.extension.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.annotation.ExtensionPoint;

/**
 * {@link OrderPriceCalculator} 的扩展引擎子接口——承载技术契约。
 *
 * <p>domain 层的 {@link OrderPriceCalculator} 只表达业务策略；本接口在 infrastructure 层声明
 * {@code @ExtensionPoint}，让扩展引擎能扫描到这个扩展点，同时不把 extension-sdk 泄漏进 domain。 所有扩展点实现类应当 {@code
 * implements ExtensionOrderPriceCalculator}。
 */
@ExtensionPoint(
    name = "订单价格计算扩展点",
    description = "不同租户和场景下的订单价格计算",
    version = "1.0.0",
    transactional = false,
    timeout = 10,
    singleton = true)
public interface ExtensionOrderPriceCalculator extends OrderPriceCalculator {}
