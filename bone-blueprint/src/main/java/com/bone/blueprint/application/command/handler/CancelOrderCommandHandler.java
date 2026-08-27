package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CancelOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantProvider tenantProvider;
  private final AggregatePersister aggregatePersister;

  @Transactional
  public void handle(CancelOrderCommand cmd) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    cmd.getOrderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + cmd.getOrderId()));
    order.cancel();
    aggregatePersister.updateAndPublishEvents(orderRepository, domainEventPublisher, order);
  }
}
