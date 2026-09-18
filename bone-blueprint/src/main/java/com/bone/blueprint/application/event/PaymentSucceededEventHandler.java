package com.bone.blueprint.application.event;

import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.DomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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
 * <p><b>Outbox 原子性</b>：集成事件写入必须**与订单确认在同一事务**（Outbox 模式的核心保证），故在此处 同事务内 {@code appendOrderPaid} /
 * {@code appendPaymentInconsistent}，而非放到 AFTER_COMMIT 再写（那时业务事务已提交，会 破坏原子性——v4.7 修正禁止该形态）。
 *
 * <p><b>钱货不一致必须留痕</b>：支付单在<strong>独立事务</strong>中已 SUCCESS，本处理器若因订单状态异常而无法
 * 确认支付，即形成「钱已收、货未付」。此时<strong>禁止静默 return</strong>——须在<strong>本事务内</strong>落
 * Outbox（异常判定与告警事件待发必须原子），交由告警 / 自动退款链路消费（资金安全红线）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSucceededEventHandler {

  private final OrderRepository orderRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final OrderOutboxWriter orderOutboxWriter;

  /**
   * 支付成功 → 订单确认（跨聚合两段式）。
   *
   * <p><b>为什么必须用 REQUIRES_NEW</b>：支付单写已在独立事务中提交（AFTER_COMMIT 触发），
   * 订单聚合的确认支付是另一条聚合写路径——两个聚合不能放在同一事务（R9 一事务一聚合）。 REQUIRES_NEW 确保订单确认在全新事务中完成，与支付单事务解耦， 同时 Outbox
   * 写入（本方法内 {@code orderOutboxWriter.*} 调用）也在该独立事务内原子提交。
   *
   * <p>这<strong>不是违规</strong>——禁止的是"在业务主事务内用 REQUIRES_NEW 写幂等表/Outbox"， 这里是 AFTER_COMMIT
   * 后的独立聚合写，属于两段式编排的唯一正确形态。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(PaymentSucceededEvent event) {
    log.info(
        "支付成功回调: paymentId={}, orderId={}, tenantId={}, channelTradeNo={}",
        event.paymentId(),
        event.orderId(),
        event.tenantId(),
        event.channelTradeNo());

    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(event.orderId(), event.tenantId()))
            .orElseThrow(
                () ->
                    new BizException(
                        404, BlueprintErrorCodes.ORDER_NOT_FOUND + ": " + event.orderId()));

    // 状态异常：订单已不处于待支付状态却收到支付成功回调（如已取消/已发货）。属「钱-货不一致」异常路径，
    // 不能静默忽略——至少告警；真实场景应触发告警/自动退款（复用 PaymentRefundedEvent 链路）。
    // 用聚合的意图揭示查询方法判断，不在应用层比较状态枚举。
    // 已支付：渠道重复通知，属**正常幂等路径**，静默跳过。
    // 注意：这条分支必须先于「钱货不一致」判定——若把已支付订单也当成不一致去告警，渠道每重复通知
    // 一次就产生一条异常，真正的问题会被噪声淹没（告警也要防止误报）。
    if (order.isPaid()) {
      log.info("订单已支付，忽略重复的支付成功回调: orderId={}, paymentId={}", order.getId(), event.paymentId());
      return;
    }

    if (!order.isAwaitingPayment()) {
      // 钱货不一致：支付单已在独立事务中置为 SUCCESS（钱已收），而订单处于已取消/已发货/已送达等
      // 无法确认支付的状态（货未付）。不能只打日志后 return——资金与业务状态已偏离，日志会沉没无人
      // 处理。改为发布领域事件 + **本事务内**直接落 Outbox（v4.7 修正：禁止 AFTER_COMMIT 另起事务写
      // Outbox，commit 后崩溃即丢事件），由告警 / 工单 / 自动退款链路消费。
      log.error(
          "钱货不一致：支付成功但订单无法确认支付，待补偿: orderId={}, status={}, paymentId={}",
          order.getId(),
          order.getStatus(),
          event.paymentId());
      order.reportPaymentInconsistency(event.paymentId(), "订单非待支付状态却收到支付成功回调");
      OrderPaymentInconsistentEvent inconsistent =
          extractDomainEvent(order, OrderPaymentInconsistentEvent.class);
      domainEventPublisher.publishFrom(order);
      if (inconsistent != null) {
        orderOutboxWriter.appendPaymentInconsistent(inconsistent);
      }
      return;
    }

    // 确认订单（本地聚合写）：在独立事务中完成，已与支付单事务解耦（AFTER_COMMIT）。
    // confirmPaid() 发布 OrderPaidEvent → 由 OrderPaidEventHandler 统一确认库存（单一职责）。
    boolean paid = order.confirmPaid();
    orderRepository.saveWithVersionCheck(order);
    OrderPaidEvent paidEvent = paid ? extractDomainEvent(order, OrderPaidEvent.class) : null;
    domainEventPublisher.publishFrom(order);

    // 同事务写 Outbox：保证「订单已支付」与「集成事件待发」原子提交；
    // 仅真正迁移时写，避免重复事件导致集成事件重复发布。
    if (paidEvent != null) {
      orderOutboxWriter.appendOrderPaid(paidEvent);
    }
  }

  /** 从聚合已注册的领域事件中提取指定类型（须在 {@code publishFrom} 清空前调用）。 */
  private static <T extends DomainEvent> T extractDomainEvent(Order order, Class<T> type) {
    return order.getDomainEvents().stream()
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElse(null);
  }
}
