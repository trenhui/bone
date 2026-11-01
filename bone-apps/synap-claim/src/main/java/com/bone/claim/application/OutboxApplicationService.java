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
import org.springframework.dao.OptimisticLockingFailureException;
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
    // 终态短路：SENT（已成功）或 FAILED（耗尽重试，由死信告警处理）都不在派发路径内
    if (DomainEventOutbox.STATUS_SENT.equals(outbox.getStatus())
        || DomainEventOutbox.STATUS_FAILED.equals(outbox.getStatus())) {
      return true;
    }
    // 抢占式占用（乐观锁）：PENDING/DISPATCHING 都先转 DISPATCHING 并落库。
    // 只有抢到版本锁（@Version）的实例能让这次状态转换落库；并发实例的 save 会因版本冲突抛
    // OptimisticLockingFailureException，被下方捕获后视为「已被其他实例接管」，从而杜绝同一事件被重复派发。
    try {
      outbox.markDispatching();
      outboxRepository.save(outbox);
    } catch (OptimisticLockingFailureException ex) {
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
