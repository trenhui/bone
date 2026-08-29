package com.bone.blueprint.domain.payment.event;

import com.bone.core.domain.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;

/** 支付退款领域事件（瘦载荷，过去式命名，§23.2）。 */
public record PaymentRefundedEvent(
    Long paymentId,
    Long tenantId,
    Long orderId,
    BigDecimal refundAmount,
    String channelTradeNo,
    Instant occurredAt)
    implements DomainEvent {}
