package com.bone.blueprint.domain.payment.event;

import com.bone.core.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;

/** 支付失败领域事件（瘦载荷，过去式命名，E-13.2）。 */
public record PaymentFailedEvent(
    Long paymentId, Long tenantId, Long orderId, BigDecimal amount, Instant occurredAt)
    implements DomainEvent {}
