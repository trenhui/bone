package com.bone.blueprint.application.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

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
        return new OrderPaidIntegrationEvent(orderId, tenantId, customerId, amount, occurredAt, SCHEMA_VERSION);
    }
}
