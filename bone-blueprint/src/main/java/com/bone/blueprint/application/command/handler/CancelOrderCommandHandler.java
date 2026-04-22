
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CancelOrderCommandHandler {

    private final OrderRepository orderRepository;

    @Transactional
    public void handle(CancelOrderCommand cmd) {
        OrderId orderId = OrderId.of(cmd.getOrderId());
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + cmd.getOrderId());
        }
        order.cancel(cmd.getReason());
        orderRepository.update(order);
    }
}

