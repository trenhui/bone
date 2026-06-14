package com.bone.integration.application.event.outbox;

import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.application.event.port.IntegrationMessageSender;
import com.bone.integration.domain.outbox.IntegrationOutboxRecord;
import com.bone.integration.domain.outbox.OutboxStatus;
import com.bone.integration.domain.repository.IntegrationOutboxRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 轮询 PENDING Outbox 并投递 MQ（INT-10 §7）。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegrationOutboxRelay {

  private final IntegrationOutboxProperties properties;
  private final IntegrationOutboxRepository outboxRepository;
  private final IntegrationMessageSender messageSender;

  @Transactional
  public int relayBatch() {
    if (!properties.isEnabled()) {
      return 0;
    }
    Criteria<IntegrationOutboxRecord> criteria =
        Criteria.<IntegrationOutboxRecord>create()
            .eq(IntegrationOutboxRecord::getStatus, OutboxStatus.PENDING)
            .page(1, properties.getBatchSize());
    List<IntegrationOutboxRecord> pending = outboxRepository.findByCriteria(criteria);
    if (pending == null || pending.isEmpty()) {
      return 0;
    }
    int sent = 0;
    for (IntegrationOutboxRecord record : pending) {
      if (relayOne(record)) {
        sent++;
      }
    }
    return sent;
  }

  private boolean relayOne(IntegrationOutboxRecord record) {
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
