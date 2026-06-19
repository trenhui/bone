package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderPaidEvent(
    Long orderId, Long tenantId, Long customerId, BigDecimal amount, Instant occurredAt)
    implements DomainEvent {}
