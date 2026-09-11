package com.bone.blueprint.domain.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付成功集成事件（跨边界发布语言 / 契约）。
 *
 * <p><b>为何必须落 Outbox</b>：「支付已成功」是不可容忍丢失的资金事实（P-5.4）。若仅以进程内 Spring 事件表达，
 * 支付事务提交后、订阅器执行前进程崩溃，该事实即永久消失——支付单已是 SUCCESS，而订单永远停在 CREATED，
 * 且无任何记录可供重试或对账。故该事件必须在<strong>支付写事务内</strong>落 Outbox，由中继保证至少一次投递。
 *
 * <p><b>为何在 domain 包</b>：见 {@link OrderPaidIntegrationEvent} 类注释——集成事件是跨边界契约，位于依赖最内侧。
 */
public record PaymentSucceededIntegrationEvent(
    Long paymentId,
    Long tenantId,
    Long orderId,
    BigDecimal amount,
    String channelTradeNo,
    Instant occurredAt,
    String schemaVersion) {

  public static final String SCHEMA_VERSION = "1.0";

  public static PaymentSucceededIntegrationEvent fromDomain(
      Long paymentId,
      Long tenantId,
      Long orderId,
      BigDecimal amount,
      String channelTradeNo,
      Instant occurredAt) {
    return new PaymentSucceededIntegrationEvent(
        paymentId, tenantId, orderId, amount, channelTradeNo, occurredAt, SCHEMA_VERSION);
  }
}
