package com.bone.blueprint.domain.order.event;

import com.bone.core.domain.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单已支付（领域事实，瘦载荷）。
 */
public record OrderPaidEvent(Long orderId, Long customerId, BigDecimal amount, Instant occurredAt)
        implements DomainEvent {
}
