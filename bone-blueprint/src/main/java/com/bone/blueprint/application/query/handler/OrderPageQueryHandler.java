package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {
    
    private final OrderRepository orderRepository;
    
    @Transactional(readOnly = true)
    public PageResult<OrderDto> handle(OrderPageQuery query) {
        // 这里应该使用查询构建器或SQL执行查询
        // 简化实现，实际应该使用分页查询
        List<Order> orders = getOrdersByQuery(query);
        
        List<OrderDto> orderDtos = orders.stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
        
        // 简化实现，返回空的PageResult
        return PageResult.of(orderDtos, (long) orderDtos.size(), query.getPageNum(), query.getPageSize());
    }
    
    private List<Order> getOrdersByQuery(OrderPageQuery query) {
        // 模拟实现，实际应该从数据库查询
        return List.of();
    }
    
    private OrderDto toOrderDto(Order order) {
        List<OrderDto.OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toOrderItemDto)
                .collect(Collectors.toList());
        
        return OrderDto.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(itemDtos)
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