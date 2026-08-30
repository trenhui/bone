package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 订单取消用例：加载订单 → 领域方法 {@code cancel()}（发布 OrderCancelledEvent）→ 保存发布。 */
@Capability(
    name = "CancelOrder",
    description = "取消订单（CREATED/PAID 可取消）",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{}",
    idempotent = false,
    cost = 2,
    retryable = false,
    timeout = 10)
@Component
@RequiredArgsConstructor
public class CancelOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantProvider tenantProvider;

  @Transactional
  public void handle(CancelOrderCommand cmd) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(cmd.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + cmd.orderId()));
    order.cancel();
    orderRepository.save(order);
    domainEventPublisher.publishFrom(order);
  }
}
