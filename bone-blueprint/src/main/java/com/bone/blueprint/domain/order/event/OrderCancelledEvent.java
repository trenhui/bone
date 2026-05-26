package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;

import java.time.Instant;

/**
 * 订单已取消（领域事实，瘦载荷）。
 */
public record OrderCancelledEvent(Long orderId, Instant occurredAt) implements DomainEvent {
}
