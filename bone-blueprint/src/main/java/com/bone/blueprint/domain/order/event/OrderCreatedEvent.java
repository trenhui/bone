package com.bone.blueprint.domain.order.event;

import com.bone.blueprint.domain.order.Order;

public class OrderCreatedEvent {
    private final Order order;

    public OrderCreatedEvent(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
