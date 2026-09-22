package com.bone.blueprint.domain.model.payment.event;

import com.bone.core.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;

/** 支付成功领域事件（瘦载荷，过去式命名，E-13.2）。 */
public record PaymentSucceededEvent(
    Long paymentId,
    Long tenantId,
    Long orderId,
    BigDecimal amount,
    String channelTradeNo,
    Instant occurredAt)
    implements DomainEvent {}
