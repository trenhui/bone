package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

public record OrderCancelledEvent(Long orderId, Long tenantId, Instant occurredAt) implements DomainEvent {
}
