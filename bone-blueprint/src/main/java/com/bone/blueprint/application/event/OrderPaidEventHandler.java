package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单支付成功后续处理（AFTER_COMMIT）：确认库存扣减。集成事件经 Outbox 异步中继。
 *
 * <p>「明细为空显式留痕」「单笔失败不中断」「明细取自读侧投影而非重载聚合」三条规则及其实现，与 {@link OrderCreatedEventHandler} 收敛在同一份骨架 {@link
 * OrderItemInventoryExecutor}：两条链路在此处<strong>必须</strong>一致，否则会出现「预留静默失败、扣减正常」 这类只在单侧暴露的形态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

  private final OrderQueryPort orderQueryPort;
  private final InventoryGateway inventoryGateway;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderPaidEvent event) {
    log.info("订单支付成功: orderId={}, tenantId={}", event.orderId(), event.tenantId());

    OrderItemInventoryExecutor.forEachItem(
        orderQueryPort, event.tenantId(), event.orderId(), "确认扣减", inventoryGateway::confirmStock);
  }
}
