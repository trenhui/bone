package com.bone.blueprint.domain.extension.order;

import java.math.BigDecimal;

/**
 * 订单价格计算策略（业务端口，domain 层纯净）。
 *
 * <p>扩展引擎的技术契约（{@code @ExtensionPoint} / {@code @Extension}）下沉到 {@code
 * infrastructure/extension/order/ExtensionOrderPriceCalculator} 子接口——本接口只承载"订单可以有不同的定价策略"
 * 这一业务概念，domain 层不感知扩展引擎框架的存在。
 *
 * <p>入参 {@link OrderPriceRequest} 独立成文件（曾是本接口的内嵌 {@code public static class}）：它由实现方与测试直接构造，本身就是
 * 契约的一部分，内嵌只会让引用点写成 {@code OrderPriceCalculator.OrderPriceRequest} 并掩盖它是值对象这一事实。
 */
public interface OrderPriceCalculator {

  /** 按给定入参计算最终金额。 */
  BigDecimal calculate(OrderPriceRequest request);
}
