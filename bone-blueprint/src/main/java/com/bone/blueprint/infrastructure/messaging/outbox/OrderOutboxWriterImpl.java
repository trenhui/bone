package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.config.OrderOutboxProperties;
import com.bone.blueprint.application.event.outbox.OrderOutboxWriter;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.integration.event.OrderPaymentInconsistentIntegrationEvent;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderOutboxWriter} 的基础设施实现：在业务事务内写入 Outbox 表。
 *
 * <p><b>端口在 application、实现在 infrastructure</b>：application 不依赖 infrastructure（P0-1 依赖方向）， 同时 Outbox
 * 这一消息投递机制不侵入 domain 层——三方职责因此分明。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxWriterImpl implements OrderOutboxWriter {

  private static final String EVENT_TYPE_ORDER_PAID = "OrderPaidIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_INCONSISTENT =
      "OrderPaymentInconsistentIntegrationEvent";

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderOutboxEnvelopeFactory envelopeFactory;
  private final TenantProvider tenantProvider;

  @Transactional
  @Override
  public void appendOrderPaid(OrderPaidIntegrationEvent event) {
    append(
        EVENT_TYPE_ORDER_PAID,
        properties.getOrderPaidTopic(),
        event,
        event == null ? null : event.orderId());
  }

  @Transactional
  @Override
  public void appendPaymentInconsistent(OrderPaymentInconsistentIntegrationEvent event) {
    append(
        EVENT_TYPE_PAYMENT_INCONSISTENT,
        properties.getPaymentInconsistentTopic(),
        event,
        event == null ? null : event.orderId());
  }

  /**
   * 写入一条 PENDING 记录。
   *
   * <p><b>为何与业务写同事务</b>：Outbox 模式的全部价值就在「业务状态变更」与「事件待发」的原子性—— 二者同事务提交，才不会出现「业务成功而事件丢失」或「事件已发而业务回滚」。
   */
  private void append(String eventType, String topic, Object event, Long orderId) {
    if (!properties.isEnabled() || event == null) {
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
            envelopeFactory.toJson(event));
    outboxRepository.save(record);
    log.debug("Outbox 已写入: eventId={}, eventType={}, orderId={}", eventId, eventType, orderId);
  }

  /** 事件自带租户则优先用事件携带值（避免跨租户误写），否则回落当前上下文租户。 */
  private long resolveTenantId(Object event) {
    if (event instanceof OrderPaidIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    if (event instanceof OrderPaymentInconsistentIntegrationEvent e && e.tenantId() != null) {
      return e.tenantId();
    }
    return tenantProvider.currentTenantId();
  }
}
