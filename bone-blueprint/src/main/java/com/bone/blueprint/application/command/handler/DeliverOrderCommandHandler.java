package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.DeliverOrderCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 订单送达确认用例：加载订单 → 领域方法 {@code deliver()}（仅 SHIPPED）→ 保存。 */
@Capability(
    name = "DeliverOrder",
    description = "订单送达确认（仅已发货订单）",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{}",
    idempotent = false,
    cost = 2,
    retryable = false,
    timeout = 10)
@Component
@RequiredArgsConstructor
public class DeliverOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public void handle(DeliverOrderCommand command) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    command.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + command.orderId()));
    order.deliver();
    orderRepository.save(order);
  }
}
