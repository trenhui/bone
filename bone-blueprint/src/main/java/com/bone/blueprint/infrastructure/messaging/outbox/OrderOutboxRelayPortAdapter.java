package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.port.out.OrderMessagePort;
import com.bone.blueprint.application.port.out.OrderOutboxRelayPort;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.metadata.sdk.query.criteria.Criteria;
import io.micrometer.core.instrument.MeterRegistry;
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
public class OrderOutboxRelayPortAdapter implements OrderOutboxRelayPort {

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderMessagePort messageSender;
  private final MeterRegistry meterRegistry;

  @Transactional
  public int relayPending() {
    if (!properties.isEnabled()) {
      return 0;
    }
    Criteria<OrderOutboxRecord> criteria =
        Criteria.<OrderOutboxRecord>create()
            .eq(OrderOutboxRecord::getStatus, OutboxStatus.PENDING)
            .disableTenantFilter() // 中继跨租户全量扫描 PENDING（每条消息自带 tenantId 进入消费逻辑）
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
      countSend(record, "sent");
      return true;
    } catch (Exception ex) {
      record.incrementRetry();
      if (record.getRetryCount() >= properties.getMaxRetries()) {
        record.markFailed();
        moveToDeadLetter(record, ex);
        countSend(record, "failed");
        log.error(
            "Outbox 投递失败并标记 FAILED（已转投死信）: eventId={}, topic={}",
            record.getEventId(),
            record.getTopic(),
            ex);
      } else {
        countSend(record, "retry");
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

  /**
   * 终端失败转投死信主题（消息与事件规范 §6）。
   *
   * <p><b>为何不是直接丢弃或无限重试</b>：无限重试会堵住中继批次、让后续事件一起饿死；静默丢弃则让资金/状态事实消失。 死信保留原信封（其中已含 {@code eventId} /
   * {@code topic} / {@code traceId}），运维可按 topic 重放； 记录本身也已置 {@code FAILED}，重放前可查表核对。
   *
   * <p>死信投递失败只记日志：事实仍在 {@code bp_outbox} 表中（status=FAILED），不会因死信通道故障而丢失。
   */
  private void moveToDeadLetter(OrderOutboxRecord record, Exception cause) {
    try {
      messageSender.send(
          properties.getDeadLetterTopic(), record.getPartitionKey(), record.getEnvelopeJson());
    } catch (Exception dlqEx) {
      log.error(
          "Outbox 死信转投失败（记录已标记 FAILED，可查 bp_outbox 人工重放）: eventId={}, dlqTopic={}",
          record.getEventId(),
          properties.getDeadLetterTopic(),
          dlqEx);
    }
  }

  /** 投递计数指标（消息与事件规范 §9）：{@code bone_mq_send_total{topic,status}}。 */
  private void countSend(OrderOutboxRecord record, String status) {
    meterRegistry
        .counter("bone_mq_send_total", "topic", record.getTopic(), "status", status)
        .increment();
  }
}
