package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付成功后续处理（AFTER_COMMIT）：确认订单支付 + 确认库存扣减。
 *
 * <p>跨聚合协作：支付单（写）已提交后，经领域事件订阅把订单从 CREATED 置 PAID，并对每个明细确认库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void handle(PaymentSucceededEvent event) {
    log.info(
        "支付成功回调: paymentId={}, orderId={}, tenantId={}, channelTradeNo={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.channelTradeNo());

    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(event.orderId(), event.tenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + event.orderId()));

    // 确认订单（本地聚合写）：在独立事务中完成，已与支付单事务解耦（AFTER_COMMIT）
    if (order.getStatus() == OrderStatus.CREATED) {
      order.confirmPaid();
      aggregatePersister.updateAndPublishEvents(orderRepository, domainEventPublisher, order);
    }

    // 库存确认（跨上下文远程调用）：依赖最终一致。若 confirmStock 失败，应由补偿/重试处理，
    // 不回滚已提交的订单确认事务——这是跨聚合最终一致的既定语义（§5.3）。
    for (OrderItem item : order.getItems()) {
      inventoryGateway.confirmStock(event.orderId(), item.getProductId(), item.getQuantity());
    }
  }
}
