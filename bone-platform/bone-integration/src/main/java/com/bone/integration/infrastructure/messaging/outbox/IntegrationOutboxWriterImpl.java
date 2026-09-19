package com.bone.integration.infrastructure.messaging.outbox;

import com.bone.core.domain.DomainEvent;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.application.event.outbox.IntegrationEventCatalog;
import com.bone.integration.application.event.outbox.IntegrationEventEnvelope;
import com.bone.integration.application.event.outbox.IntegrationEventEnvelopeFactory;
import com.bone.integration.application.event.outbox.IntegrationEventRegistration;
import com.bone.integration.application.event.outbox.IntegrationOutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link IntegrationOutboxWriter} 的基础设施实现：在业务事务内写入 Outbox 表。
 *
 * <p><b>端口在 application、实现在 infrastructure</b>：application 不依赖 infrastructure（P0-1 依赖方向），同时 Outbox
 * 这一消息投递机制不侵入 domain 层——与 bone-blueprint {@code OrderOutboxPortAdapter} 同一约定。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationOutboxWriterImpl implements IntegrationOutboxWriter {

  private final IntegrationOutboxProperties properties;
  private final IntegrationEventCatalog catalog;
  private final IntegrationEventEnvelopeFactory envelopeFactory;
  private final IntegrationOutboxRepository outboxRepository;

  @Transactional
  @Override
  public void append(DomainEvent event) {
    if (!properties.isEnabled() || event == null) {
      return;
    }
    catalog
        .resolve(event)
        .ifPresentOrElse(
            registration -> persist(event, registration),
            () -> log.warn("未登记的集成领域事件，跳过 Outbox: {}", event.getClass().getName()));
  }

  private void persist(DomainEvent event, IntegrationEventRegistration registration) {
    IntegrationEventEnvelope envelope = envelopeFactory.create(event, registration);
    String json = envelopeFactory.toJson(envelope);
    Long tenantId = TenantContext.getTenantIdAsLong();
    String partitionKey = String.valueOf(tenantId != null ? tenantId : 0L);
    IntegrationOutboxRecord record =
        IntegrationOutboxRecord.pending(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            envelope.eventId(),
            registration.eventType(),
            registration.topic(),
            partitionKey,
            json);
    outboxRepository.save(record);
    log.debug(
        "Outbox 已写入: eventType={}, topic={}, eventId={}",
        registration.eventType(),
        registration.topic(),
        envelope.eventId());
  }
}
