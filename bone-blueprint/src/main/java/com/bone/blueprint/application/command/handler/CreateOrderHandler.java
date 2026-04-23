package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.infrastructure.annotation.Capability;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Capability(
    name = "CreateOrder",
    description = "创建订单并保存到数据库",
    inputSchema = "{\"orderId\": \"long\", \"customerId\": \"long\", \"items\": [{\"id\": \"long\", \"productId\": \"long\", \"quantity\": \"int\", \"unitPrice\": \"decimal\"}]}",
    outputSchema = "{\"orderId\": \"long\", \"status\": \"string\"}",
    idempotent = false,
    cost = 3,
    retryable = false,
    timeout = 30
)
@Component
@RequiredArgsConstructor
public class CreateOrderHandler {
    private final OrderRepository orderRepository;
    
    public Order handle(Long orderId, Long customerId, List<OrderItem> items) {
        Order order = Order.create(orderId, customerId, items);
        orderRepository.save(order);
        return order;
    }
}