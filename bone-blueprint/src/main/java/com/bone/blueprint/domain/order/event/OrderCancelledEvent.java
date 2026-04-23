package com.bone.blueprint.domain.order.event;

public class OrderCancelledEvent {
    private final Long orderId;

    public OrderCancelledEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
