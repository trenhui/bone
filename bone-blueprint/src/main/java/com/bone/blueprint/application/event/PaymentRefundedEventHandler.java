package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
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
 * 退款成功后续处理（AFTER_COMMIT）：确认订单退款 + 释放库存。
 *
 * <p>跨聚合协作：支付单（写）已提交后，经领域事件订阅把订单置 REFUNDED，并对每个明细释放库存。 订单确认在独立事务，释放库存为远程调用，依赖最终一致（§5.3）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundedEventHandler {

  private final OrderRepository orderRepository;
  private final InventoryGateway inventoryGateway;
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional
  public void handle(PaymentRefundedEvent event) {
    log.info(
        "退款成功回调: paymentId={}, orderId={}, tenantId={}, refundAmount={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.refundAmount());

    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(event.orderId(), event.tenantId()))
            .orElseThrow(() -> new NotFoundException("订单不存在: " + event.orderId()));

    // 确认订单退款（本地聚合写，独立事务）
    if (order.getStatus() != OrderStatus.REFUNDED) {
      order.refund();
      aggregatePersister.updateAndPublishEvents(orderRepository, domainEventPublisher, order);
    }

    // 释放库存（远程调用，最终一致；失败由补偿/重试处理，不回滚已提交的订单退款）。
    // 现有 InventoryGateway 为整单释放预留语义，退款场景复用 releaseStock(orderId)。
    inventoryGateway.releaseStock(event.orderId());
  }
}
