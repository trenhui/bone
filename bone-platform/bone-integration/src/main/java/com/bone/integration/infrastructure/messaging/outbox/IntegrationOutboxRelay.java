package com.bone.integration.infrastructure.messaging.outbox;

import com.bone.core.tenant.context.TenantContext;
import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.application.event.port.IntegrationMessageSender;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 轮询 PENDING Outbox 并投递 MQ（INT-10 §7）。
 *
 * <p>Outbox 中继是消息投递侧技术设施，无业务规则，故随记录/仓储一并下沉基础设施层（adapter 的调度壳 {@code IntegrationOutboxRelayJob}
 * 直接编排之，与 bone-blueprint 同约定）。
 */
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
            .disableTenantFilter() // 中继跨租户全量扫描 PENDING（每条消息自带 tenantId 进入消费逻辑）
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
    // 调度线程无租户会话；更新 int_outbox（租户作用域表）须以记录自身租户执行，否则 fail-closed 抛
    // MissingTenantContextException，且异常发生在 catch 内会导致整批中继中断、SENT 状态永不落库。
    TenantContext.setTenantId(record.getTenantId());
    try {
      doRelayOne(record);
    } finally {
      TenantContext.clear();
    }
    return record.getStatus() == OutboxStatus.SENT;
  }

  private void doRelayOne(IntegrationOutboxRecord record) {
    try {
      messageSender.send(record.getTopic(), record.getPartitionKey(), record.getEnvelopeJson());
      record.markSent();
      outboxRepository.update(record);
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
    }
  }
}
