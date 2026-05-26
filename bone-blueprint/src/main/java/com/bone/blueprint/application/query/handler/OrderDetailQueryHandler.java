package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.support.OrderLookup;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderDetailQueryHandler {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderDto handle(OrderDetailQuery query) {
        Order order = OrderLookup.requireById(orderRepository, query.getOrderId());
        return toOrderDto(order);
    }

    private OrderDto toOrderDto(Order order) {
        return OrderDto.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(order.getItems().stream().map(this::toOrderItemDto).collect(Collectors.toList()))
                .build();
    }

    private OrderDto.OrderItemDto toOrderItemDto(OrderItem item) {
        return OrderDto.OrderItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
