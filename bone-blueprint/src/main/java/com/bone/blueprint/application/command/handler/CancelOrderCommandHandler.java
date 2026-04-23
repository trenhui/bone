package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.order.Order;
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
        Order order = orderRepository.findById(cmd.getOrderId());
        if (order == null) {
            throw new com.bone.core.exception.DomainException("订单不存在");
        }
        order.cancel();
        orderRepository.save(order);
    }
}
