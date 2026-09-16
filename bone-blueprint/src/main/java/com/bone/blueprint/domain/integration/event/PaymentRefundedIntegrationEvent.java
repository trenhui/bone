package com.bone.blueprint.domain.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付已退款集成事件（跨边界发布语言 / 契约）。
 *
 * <p>下游（通知、财务对账、发票）需感知"资金已退回"的事实。同支付成功事件一致， 必须与支付单退款确认在同一事务落 Outbox，避免崩溃窗口内退款事实永久丢失。
 */
public record PaymentRefundedIntegrationEvent(
    Long paymentId,
    Long tenantId,
    Long orderId,
    BigDecimal refundAmount,
    String channelTradeNo,
    Instant occurredAt,
    String schemaVersion) {

  public static final String SCHEMA_VERSION = "1.0";

  public static PaymentRefundedIntegrationEvent fromDomain(
      Long paymentId,
      Long tenantId,
      Long orderId,
      BigDecimal refundAmount,
      String channelTradeNo,
      Instant occurredAt) {
    return new PaymentRefundedIntegrationEvent(
        paymentId, tenantId, orderId, refundAmount, channelTradeNo, occurredAt, SCHEMA_VERSION);
  }
}
