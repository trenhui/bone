package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.domain.gateway.AggregatePersister;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 支付成功后续处理（AFTER_COMMIT）：确认订单支付 + 写 Outbox 集成事件。
 *
 * <p>跨聚合协作：支付单（写）已提交后，经领域事件订阅把订单从 CREATED 置 PAID。
 *
 * <p>库存扣减**不在本处理器执行**：{@code Order.confirmPaid()} 会发布 {@code OrderPaidEvent}，由 {@link
 * OrderPaidEventHandler} 统一确认库存（单一职责，避免重复扣减）。
 *
 * <p><b>Outbox 原子性</b>：集成事件写入必须**与订单确认在同一事务**（Outbox 模式的核心保证），故在此处 同事务内 {@code
 * appendOrderPaid}，而非放到 AFTER_COMMIT 的 {@link OrderPaidEventHandler}（那时业务事务已提交，会破坏原子性）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventHandler {

  private final OrderRepository orderRepository;
  private final AggregatePersister aggregatePersister;
  private final DomainEventPublisher domainEventPublisher;
  private final OrderOutboxWriter orderOutboxWriter;

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

    // 确认订单（本地聚合写）：在独立事务中完成，已与支付单事务解耦（AFTER_COMMIT）。
    // confirmPaid() 发布 OrderPaidEvent → 由 OrderPaidEventHandler 统一确认库存（单一职责）。
    if (order.getStatus() == OrderStatus.CREATED) {
      boolean paid = order.confirmPaid();
      aggregatePersister.updateAndPublishEvents(orderRepository, domainEventPublisher, order);
      // 同事务写 Outbox：保证「订单已支付」与「集成事件待发」原子提交；
      // 仅真正迁移时写，避免重复事件导致集成事件重复发布。
      if (paid) {
        orderOutboxWriter.appendOrderPaid(
            OrderPaidIntegrationEvent.fromDomain(
                order.getId(),
                order.getTenantId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                Instant.now()));
      }
    }
  }
}
