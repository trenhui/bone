package com.bone.blueprint.domain.order.event;

public class OrderPaidEvent {
    private final Long orderId;
    private final java.math.BigDecimal amount;

    public OrderPaidEvent(Long orderId, java.math.BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }
}
