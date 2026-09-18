package com.bone.blueprint.application.event.integration;

import java.time.Instant;

/**
 * 「钱货不一致」集成事件：支付单已成功（钱已收），但订单无法确认支付（货未付）。
 *
 * <p><b>为何必须有此事件</b>：支付回调在独立事务中已把支付单置为 SUCCESS，此时若订单处于已取消/已发货等
 * 无法确认支付的状态，就形成「钱已收、订单未支付」的资金与业务状态偏离。若仅打日志后 return，异常将 永久沉没、无人可感知。落 Outbox
 * 后由下游（告警/工单/自动退款）消费，保证异常<strong>可观测、可补偿</strong>。
 *
 * <p><b>为何在 domain 包</b>：见 {@link OrderPaidIntegrationEvent} 类注释——集成事件是跨边界契约，位于依赖最内侧。
 */
public record OrderPaymentInconsistentIntegrationEvent(
    Long orderId,
    Long tenantId,
    Long paymentId,
    String orderStatus,
    String reason,
    Instant occurredAt,
    String schemaVersion)
    implements IntegrationEnvelope {

  public static final String SCHEMA_VERSION = "1.0";

  public static OrderPaymentInconsistentIntegrationEvent fromDomain(
      Long orderId,
      Long tenantId,
      Long paymentId,
      String orderStatus,
      String reason,
      Instant occurredAt) {
    return new OrderPaymentInconsistentIntegrationEvent(
        orderId, tenantId, paymentId, orderStatus, reason, occurredAt, SCHEMA_VERSION);
  }
}
