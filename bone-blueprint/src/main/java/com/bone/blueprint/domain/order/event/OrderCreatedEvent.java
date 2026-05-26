package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;

import java.time.Instant;

/**
 * 订单已创建（领域事实，瘦载荷）。
 */
public record OrderCreatedEvent(Long orderId, Long customerId, Instant occurredAt) implements DomainEvent {
}
