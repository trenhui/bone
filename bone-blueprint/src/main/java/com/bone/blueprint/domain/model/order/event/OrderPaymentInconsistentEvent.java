package com.bone.blueprint.domain.model.order.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 「钱货不一致」领域事件：支付单已成功（钱已收），但订单当前状态无法确认支付（货未付）。
 *
 * <p><b>为何必须发事件，而不是只打日志</b>：支付回调在<strong>独立事务</strong>中已把支付单置为 SUCCESS，
 * 此时若订单处于已取消/已发货/已送达等状态，就形成资金与业务状态的偏离。日志会沉没在海量输出中无人 处理；发布事件则可被订阅方落到 Outbox，交由告警 / 工单 /
 * 自动退款链路消费，保证异常<strong>可观测、 可补偿</strong>——这是资金安全红线，不允许静默吞掉。
 *
 * @param orderId 订单号
 * @param tenantId 租户号
 * @param paymentId 已成功的支付单号（钱已收）
 * @param orderStatus 订单当前状态（快照，便于下游判断如何补偿）
 * @param reason 不一致原因（人类可读）
 * @param occurredAt 发生时间
 */
public record OrderPaymentInconsistentEvent(
    Long orderId,
    Long tenantId,
    Long paymentId,
    String orderStatus,
    String reason,
    Instant occurredAt)
    implements DomainEvent {}
