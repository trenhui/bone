package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler {

  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderCancelledEvent event) {
    log.info("订单已取消，释放库存预留: orderId={}", event.orderId());
    inventoryGateway.releaseStock(event.orderId());
  }
}
