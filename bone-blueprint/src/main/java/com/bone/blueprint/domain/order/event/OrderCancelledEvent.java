package com.bone.blueprint.domain.order.event;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.domain.DomainEvent;

public class OrderCancelledEvent implements DomainEvent {
    private final Order order;
    
    public OrderCancelledEvent(Order order) {
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public Long getOrderId() {
        return order.getId();
    }
}