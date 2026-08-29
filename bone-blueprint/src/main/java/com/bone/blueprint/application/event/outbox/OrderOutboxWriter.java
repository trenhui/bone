package com.bone.blueprint.application.event.outbox;

import com.bone.blueprint.application.config.OrderOutboxProperties;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.outbox.OrderOutboxRecord;
import com.bone.blueprint.domain.repository.OrderOutboxRepository;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 在业务事务内写入 Outbox（与订单支付同事务提交）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxWriter {

  private static final String EVENT_TYPE_ORDER_PAID = "OrderPaidIntegrationEvent";

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderOutboxEnvelopeFactory envelopeFactory;
  private final TenantProvider tenantProvider;

  @Transactional
  public void appendOrderPaid(OrderPaidIntegrationEvent event) {
    if (!properties.isEnabled() || event == null) {
      return;
    }
    long tenantId = event.tenantId() != null ? event.tenantId() : tenantProvider.currentTenantId();
    String eventId = envelopeFactory.newEventId();
    String json = envelopeFactory.toJson(event);
    String partitionKey = String.valueOf(tenantId);
    OrderOutboxRecord record =
        OrderOutboxRecord.pending(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            eventId,
            EVENT_TYPE_ORDER_PAID,
            properties.getOrderPaidTopic(),
            partitionKey,
            json);
    outboxRepository.save(record);
    log.debug("Outbox 已写入: eventId={}, orderId={}", eventId, event.orderId());
  }
}
