package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.support.AggregatePersistence;
import com.bone.blueprint.application.support.OrderLookup;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PayOrderCommandHandler {

    private final OrderRepository orderRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public void handle(PayOrderCommand cmd) {
        Order order = OrderLookup.requireById(orderRepository, cmd.getOrderId());
        order.pay();
        AggregatePersistence.updateAndPublishEvents(orderRepository, domainEventPublisher, order);
    }
}
