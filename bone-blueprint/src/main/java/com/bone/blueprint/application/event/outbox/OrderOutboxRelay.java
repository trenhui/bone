package com.bone.blueprint.application.event.outbox;

import com.bone.blueprint.application.config.OrderOutboxProperties;
import com.bone.blueprint.application.integration.port.OrderMessageSender;
import com.bone.blueprint.domain.outbox.OrderOutboxRecord;
import com.bone.blueprint.domain.outbox.OutboxStatus;
import com.bone.blueprint.domain.repository.OrderOutboxRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelay {

    private final OrderOutboxProperties properties;
    private final OrderOutboxRepository outboxRepository;
    private final OrderMessageSender messageSender;

    @Transactional
    public int relayPending() {
        if (!properties.isEnabled()) {
            return 0;
        }
        Criteria<OrderOutboxRecord> criteria = Criteria.<OrderOutboxRecord>create()
                .eq(OrderOutboxRecord::getStatus, OutboxStatus.PENDING)
                .page(1, properties.getBatchSize());
        List<OrderOutboxRecord> pending = outboxRepository.findByCriteria(criteria);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (OrderOutboxRecord record : pending) {
            if (relayOne(record)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean relayOne(OrderOutboxRecord record) {
        try {
            messageSender.send(record.getTopic(), record.getPartitionKey(), record.getEnvelopeJson());
            record.markSent();
            outboxRepository.update(record);
            return true;
        } catch (Exception ex) {
            record.incrementRetry();
            if (record.getRetryCount() >= properties.getMaxRetries()) {
                record.markFailed();
                log.error(
                        "Outbox 投递失败并标记 FAILED: eventId={}, topic={}",
                        record.getEventId(),
                        record.getTopic(),
                        ex);
            } else {
                log.warn(
                        "Outbox 投递失败将重试: eventId={}, retry={}",
                        record.getEventId(),
                        record.getRetryCount(),
                        ex);
            }
            outboxRepository.update(record);
            return false;
        }
    }
}
