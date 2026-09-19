/**
 * 领域事件处理器（{@code *EventHandler}）：订阅本模块领域事件（{@code AFTER_COMMIT} / {@code REQUIRES_NEW}），
 * 做跨聚合编排（如支付成功 → 订单确认 PAID）。
 *
 * <p><b>与 {@code application/integration} 区分</b>：本包处理「模块内领域事件」（聚合协作）；{@code integration} 包处理
 * 「发往其它上下文的集成事件」。两者概念不同，勿混。
 */
package com.bone.blueprint.application.event;
