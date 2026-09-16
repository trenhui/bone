package com.bone.blueprint.domain.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付已失败集成事件（跨边界发布语言 / 契约）。
 *
 * <p>下游（通知、告警）需感知"支付未完成"。订单可能仍处于 CREATED 可重新发起支付， 但通知用户支付失败是体验必需。必须与支付单置 FAILED 同事务落 Outbox。
 */
public record PaymentFailedIntegrationEvent(
    Long paymentId,
    Long tenantId,
    Long orderId,
    BigDecimal amount,
    Instant occurredAt,
    String schemaVersion) {

  public static final String SCHEMA_VERSION = "1.0";

  public static PaymentFailedIntegrationEvent fromDomain(
      Long paymentId, Long tenantId, Long orderId, BigDecimal amount, Instant occurredAt) {
    return new PaymentFailedIntegrationEvent(
        paymentId, tenantId, orderId, amount, occurredAt, SCHEMA_VERSION);
  }
}
