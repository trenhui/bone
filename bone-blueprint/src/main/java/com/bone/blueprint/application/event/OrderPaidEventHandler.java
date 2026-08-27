package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订单支付成功后续处理（AFTER_COMMIT）：确认库存扣减。集成事件经 Outbox 异步中继。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderPaidEvent event) {
    log.info("订单支付成功: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(event.orderId(), event.tenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + event.orderId()));
    for (OrderItem item : order.getItems()) {
      inventoryGateway.confirmStock(event.orderId(), item.getProductId(), item.getQuantity());
    }
  }
}
