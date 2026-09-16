package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.port.out.OrderOutboxWriter;
import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.blueprint.domain.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.domain.integration.event.OrderPaymentInconsistentIntegrationEvent;
import com.bone.blueprint.domain.integration.event.PaymentFailedIntegrationEvent;
import com.bone.blueprint.domain.integration.event.PaymentRefundedIntegrationEvent;
import com.bone.blueprint.domain.integration.event.PaymentSucceededIntegrationEvent;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderOutboxWriter} 的基础设施实现：在业务事务内写入 Outbox 表。
 *
 * <p><b>端口在 domain、实现在 infrastructure</b>：E-10 出站端口声明于 domain，application 依赖领域接口、 不依赖
 * infrastructure（P0-1 依赖方向），同时 Outbox 这一消息投递机制不侵入领域业务类型——三方职责因此分明。 载荷先由本实现把领域事件转换为跨边界集成事件（ACL
 * 职责），再序列化为信封。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxWriterImpl implements OrderOutboxWriter {

  private static final String EVENT_TYPE_ORDER_PAID = "OrderPaidIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_INCONSISTENT =
      "OrderPaymentInconsistentIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_SUCCEEDED = "PaymentSucceededIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_REFUNDED = "PaymentRefundedIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_FAILED = "PaymentFailedIntegrationEvent";

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderOutboxEnvelopeFactory envelopeFactory;
  private final TenantProvider tenantProvider;

  /**
   * 支付成功事实入 Outbox（MANDATORY：强制调用方已有事务）。
   *
   * <p><b>为何用 MANDATORY</b>：「事件与业务状态同事务」是 Outbox 的唯一价值来源。用默认 REQUIRED 时，
   * 若调用方没有事务，容器会<strong>悄悄新起一个事务</strong>，原子性被破坏却不报错。MANDATORY 把这条 约束从注释升级为容器级保证：无事务调用直接抛
   * IllegalTransactionStateException。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentSucceeded(PaymentSucceededEvent event) {
    append(
        EVENT_TYPE_PAYMENT_SUCCEEDED,
        properties.getPaymentSucceededTopic(),
        event == null
            ? null
            : PaymentSucceededIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.amount(),
                event.channelTradeNo(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendOrderPaid(OrderPaidEvent event) {
    append(
        EVENT_TYPE_ORDER_PAID,
        properties.getOrderPaidTopic(),
        event == null
            ? null
            : OrderPaidIntegrationEvent.fromDomain(
                event.orderId(),
                event.tenantId(),
                event.customerId(),
                event.amount(),
                event.occurredAt()),
        event == null ? null : event.orderId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentInconsistent(OrderPaymentInconsistentEvent event) {
    append(
        EVENT_TYPE_PAYMENT_INCONSISTENT,
        properties.getPaymentInconsistentTopic(),
        event == null
            ? null
            : OrderPaymentInconsistentIntegrationEvent.fromDomain(
                event.orderId(),
                event.tenantId(),
                event.paymentId(),
                event.orderStatus(),
                event.reason(),
                event.occurredAt()),
        event == null ? null : event.orderId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentRefunded(PaymentRefundedEvent event) {
    append(
        EVENT_TYPE_PAYMENT_REFUNDED,
        properties.getPaymentRefundedTopic(),
        event == null
            ? null
            : PaymentRefundedIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.refundAmount(),
                event.channelTradeNo(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentFailed(PaymentFailedEvent event) {
    append(
        EVENT_TYPE_PAYMENT_FAILED,
        properties.getPaymentFailedTopic(),
        event == null
            ? null
            : PaymentFailedIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.amount(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  /**
   * 写入一条 PENDING 记录。
   *
   * <p><b>为何与业务写同事务</b>：Outbox 模式的全部价值就在「业务状态变更」与「事件待发」的原子性—— 二者同事务提交，才不会出现「业务成功而事件丢失」或「事件已发而业务回滚」。
   */
  private void append(String eventType, String topic, Object event, Long bizId) {
    if (event == null) {
      return;
    }
    if (!properties.isEnabled()) {
      // 关闭 Outbox 等于声明「这些事件可容忍丢失」，必须留痕：静默 return 会让资金/状态事实凭空消失且无人察觉。
      log.warn(
          "Outbox 已关闭，集成事件未落库（下游将收不到该事实）: eventType={}, bizId={}。"
              + "若为生产环境请检查 bone.blueprint.outbox.enabled",
          eventType,
          bizId);
      return;
    }
    long tenantId = resolveTenantId(event);
    String eventId = envelopeFactory.newEventId();
    OrderOutboxRecord record =
        OrderOutboxRecord.pending(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            eventId,
            eventType,
            topic,
            String.valueOf(tenantId),
            envelopeFactory.toJson(eventId, event));
    outboxRepository.save(record);
    log.debug("Outbox 已写入: eventId={}, eventType={}, bizId={}", eventId, eventType, bizId);
  }

  /** 事件自带租户则优先用事件携带值（避免跨租户误写），否则回落当前上下文租户。 */
  private long resolveTenantId(Object event) {
    if (event instanceof PaymentSucceededIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    if (event instanceof OrderPaidIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    if (event instanceof OrderPaymentInconsistentIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    if (event instanceof PaymentRefundedIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    if (event instanceof PaymentFailedIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    return tenantProvider.currentTenantId();
  }
}
