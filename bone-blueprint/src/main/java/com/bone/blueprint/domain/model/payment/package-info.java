/**
 * 支付限界上下文（按限界上下文分包，对应 AGENTS.md §5.1 通用树里的 {@code domain/model} 平铺形态）。
 *
 * <p>聚合根 {@link com.bone.blueprint.domain.model.payment.Payment} 直接置于本包，领域事件在 {@code event/}、值对象在
 * {@code valueobject/} 子包。支付聚合独立于订单聚合，仅以 {@code orderId} 引用（聚合间仅以 ID 引用）。
 */
package com.bone.blueprint.domain.model.payment;
