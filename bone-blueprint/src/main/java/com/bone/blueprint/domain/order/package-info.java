/**
 * 订单限界上下文（按限界上下文分包，对应 AGENTS.md §5.1 通用树里的 {@code domain/model} 平铺形态）。
 *
 * <p>聚合根 {@link com.bone.blueprint.domain.order.Order} 直接置于本包，领域事件在 {@code event/}、值对象在 {@code
 * valueobject/} 子包。blueprint 选择「按上下文分包」而非「按层分包」，已在 README / TEST_GUIDE 登记。
 */
package com.bone.blueprint.domain.order;
