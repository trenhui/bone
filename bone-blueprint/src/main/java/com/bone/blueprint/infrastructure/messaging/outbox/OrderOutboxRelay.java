package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.domain.gateway.OrderMessageSender;
import com.bone.blueprint.domain.gateway.OrderOutboxRelayPort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 中继：扫描 PENDING 记录投递至 MQ，成功标记 SENT，失败累计重试次数，超限标记 FAILED。
 *
 * <p>由定时任务驱动（见 {@code adapter/schedule}）。投递是<strong>至少一次</strong>语义——MQ 已收到但 标记 SENT
 * 前宕机会重复投递，消费方须幂等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelay implements OrderOutboxRelayPort {

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderMessageSender messageSender;

  @Transactional
  public int relayPending() {
    if (!properties.isEnabled()) {
      return 0;
    }
    Criteria<OrderOutboxRecord> criteria =
        Criteria.<OrderOutboxRecord>create()
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
