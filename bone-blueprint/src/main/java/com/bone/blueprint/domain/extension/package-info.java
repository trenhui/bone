/**
 * domain 层扩展点：以业务语言声明「同一业务动作可以有多种策略」的领域端口。
 *
 * <p><b>为什么业务端口在 domain，而技术契约在 infrastructure</b>：{@code OrderPriceCalculator}
 * 只表达"订单可以有不同的定价策略"这一业务概念， domain 不感知扩展引擎（{@code @ExtensionPoint} / {@code @Extension} / {@code
 * BizContext}）。带引擎注解的子接口 {@code ExtensionOrderPriceCalculator} 与各实现落在 {@code
 * infrastructure/extension/order}，由启动类的 {@code @EnableExtensionPoints} 扫描。这样换掉扩展引擎（或改用 Spring 多实现 +
 * 策略工厂）时，domain 一行不用动。
 *
 * <p><b>放什么</b>：纯策略接口 + 其入参值对象（如 {@link
 * com.bone.blueprint.domain.extension.order.OrderPriceRequest}，不可变
 * record）。入参、出参一律用领域类型或基础类型，不得出现引擎类型。
 *
 * <p><b>不放什么</b>：引擎注解、运行期上下文（{@code BizContext} / {@code ExtensionContextManager}）、扩展点注册与路由——这些是
 * infrastructure 的职责（见 {@code PricingPortAdapter}：应用层只依赖 {@code application/port/out/PricingPort}）。
 *
 * <p><b>不做什么</b>：领域层不主动调用扩展点。是否使用扩展结果由应用用例决定（{@code Order#applyPricing(Money)}），聚合不持有、不调用本包接口。
 */
package com.bone.blueprint.domain.extension;
