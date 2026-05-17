package com.bone.integration.application.event.outbox;

import com.bone.core.domain.DomainEvent;
import com.bone.core.tenant.context.TenantContext;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.integration.domain.outbox.IntegrationOutboxRecord;
import com.bone.integration.domain.repository.IntegrationOutboxRepository;
import com.bone.integration.infrastructure.config.IntegrationOutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在业务事务内写入 Outbox（INT-10）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationOutboxWriter {

    private final IntegrationOutboxProperties properties;
    private final IntegrationEventCatalog catalog;
    private final IntegrationEventEnvelopeFactory envelopeFactory;
    private final IntegrationOutboxRepository outboxRepository;

    @Transactional
    public void append(DomainEvent event) {
        if (!properties.isEnabled() || event == null) {
            return;
        }
        catalog.resolve(event)
                .ifPresentOrElse(
                        registration -> persist(event, registration),
                        () -> log.warn("未登记的集成领域事件，跳过 Outbox: {}", event.getClass().getName()));
    }

    private void persist(DomainEvent event, IntegrationEventRegistration registration) {
        IntegrationEventEnvelope envelope = envelopeFactory.create(event, registration);
        String json = envelopeFactory.toJson(envelope);
        Long tenantId = TenantContext.getTenantId();
        String partitionKey = String.valueOf(tenantId != null ? tenantId : 0L);
        IntegrationOutboxRecord record = IntegrationOutboxRecord.pending(
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
