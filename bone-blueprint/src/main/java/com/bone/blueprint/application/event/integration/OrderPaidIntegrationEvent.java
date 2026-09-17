package com.bone.blueprint.application.event.integration;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单已支付集成事件（跨边界发布语言 / 契约）。
 *
 * <p><b>为何在 domain 包</b>：集成事件是跨限界上下文的<strong>契约</strong>，位于依赖最内侧的 domain， 既能被 infrastructure 的
 * Outbox 写侧构造（ACL 职责），也能被 adapter 的 MQ 消费端依赖， 避免 infrastructure 反向依赖 application（E-3
 * R1）。它不含任何领域业务行为，仅承载跨边界载荷。
 */
public record OrderPaidIntegrationEvent(
    Long orderId,
    Long tenantId,
    Long customerId,
    BigDecimal amount,
    Instant occurredAt,
    String schemaVersion) {

  public static final String SCHEMA_VERSION = "1.0";

  public static OrderPaidIntegrationEvent fromDomain(
      Long orderId, Long tenantId, Long customerId, BigDecimal amount, Instant occurredAt) {
    return new OrderPaidIntegrationEvent(
        orderId, tenantId, customerId, amount, occurredAt, SCHEMA_VERSION);
  }
}
