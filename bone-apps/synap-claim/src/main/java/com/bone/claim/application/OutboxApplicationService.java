package com.bone.claim.application;

import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.port.out.OutboxPort;
import com.bone.claim.domain.port.out.OutboxRecord;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发件箱派发与确认（ADR-008）。
 *
 * <p>被 {@code OutboxRelayJob}（adapter.schedule）逐租户 {@code runAs} 后调用：加载 PENDING 记录 → 经 {@link
 * OutboxPort} 派发 → 成功刷 SENT，失败按退避策略累加重试或置 FAILED。写路径落在应用层，使调度包（ADR-0030 守卫）只调 {@code *AllTenants}
 * 扫描方法 + 本服务，不直接碰仓储。
 *
 * <p><b>E-5.4 豁免</b>：本服务 {@code save()} 仅写发件箱投递台账（outbox 即投递机制本身，不是领域事件源），不再向外派发领域事件。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxApplicationService {

  private final DomainEventOutboxRepository outboxRepository;
  private final OutboxPort outboxPort;

  private static final int MAX_RETRY = 5;
  private static final Duration RETRY_BACKOFF = Duration.ofMinutes(10);

  /**
   * 派发一笔发件箱事件并确认。
   *
   * @return true=已成功派发并确认；false=派发失败（已记录重试/置 FAILED）或记录不存在
   */
  @Transactional
  public boolean dispatchAndAck(Long tenantId, Long outboxId) {
    DomainEventOutbox outbox = outboxRepository.load(tenantId, outboxId);
    if (outbox == null) {
      return false;
    }
    if (!DomainEventOutbox.STATUS_PENDING.equals(outbox.getStatus())) {
      return true;
    }
    try {
      outboxPort.dispatch(
          new OutboxRecord(
              outbox.getTenantId(),
              outbox.getAggregate(),
              outbox.getAggregateId(),
              outbox.getEventType(),
              outbox.getPayload()));
      outbox.markSent();
      outboxRepository.save(outbox);
      return true;
    } catch (Exception e) {
      outbox.markFailed(MAX_RETRY, Instant.now().plus(RETRY_BACKOFF));
      outboxRepository.save(outbox);
      log.error("outbox 派发失败: id={}, tenantId={}", outboxId, tenantId, e);
      return false;
    }
  }
}
