package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.service.OrderLookup;
import com.bone.core.domain.event.DomainEventPublisher;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PayOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final OrderOutboxWriter orderOutboxWriter;
  private final TenantProvider tenantProvider;
  private final AggregatePersister aggregatePersister;

  @Transactional
  public void handle(PayOrderCommand cmd) {
    Order order =
        OrderLookup.requireById(
            orderRepository, cmd.getOrderId(), tenantProvider.currentTenantId());
    order.pay();
    aggregatePersister.updateAndPublishEvents(orderRepository, domainEventPublisher, order);

    orderOutboxWriter.appendOrderPaid(
        OrderPaidIntegrationEvent.fromDomain(
            order.getId(),
            order.getTenantId(),
            order.getCustomerId(),
            order.getTotalAmount(),
            Instant.now()));
  }
}
