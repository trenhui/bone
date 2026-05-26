package com.bone.blueprint.application.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单已支付集成事件（跨上下文契约，与领域事件分离）。
 */
public record OrderPaidIntegrationEvent(
        Long orderId, Long customerId, BigDecimal amount, Instant occurredAt, String schemaVersion) {
    public static final String SCHEMA_VERSION = "1.0";

    public static OrderPaidIntegrationEvent fromDomain(
            Long orderId, Long customerId, BigDecimal amount, Instant occurredAt) {
        return new OrderPaidIntegrationEvent(orderId, customerId, amount, occurredAt, SCHEMA_VERSION);
    }
}
