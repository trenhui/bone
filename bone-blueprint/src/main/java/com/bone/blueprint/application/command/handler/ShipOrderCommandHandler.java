package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.ShipOrderCommand;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 订单发货用例：加载订单 → 领域方法 {@code ship()}（仅 PAID）→ 保存。 */
@Capability(
    name = "ShipOrder",
    description = "订单发货（仅已支付订单）",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{}",
    idempotent = false,
    cost = 2,
    retryable = false,
    timeout = 10)
@Component
@RequiredArgsConstructor
public class ShipOrderCommandHandler {

  private final OrderRepository orderRepository;
  private final TenantProvider tenantProvider;

  @Transactional
  public void handle(ShipOrderCommand cmd) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(cmd.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + cmd.orderId()));
    order.ship();
    orderRepository.save(order);
  }
}
