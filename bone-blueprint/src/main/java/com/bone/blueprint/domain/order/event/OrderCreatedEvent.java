package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

public record OrderCreatedEvent(Long orderId, Long tenantId, Long customerId, Instant occurredAt)
        implements DomainEvent {
}
