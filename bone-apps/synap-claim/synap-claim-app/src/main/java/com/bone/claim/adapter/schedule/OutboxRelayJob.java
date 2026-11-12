package com.bone.claim.adapter.schedule;

import com.bone.claim.application.OutboxApplicationService;
import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import com.bone.claim.infrastructure.schedule.ScheduleLockService;
import com.bone.core.tenant.context.TenantContextRunner;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 领域事件发件箱中继（ADR-008「资金事件零丢失」兜底，ADR-0030 平台运维旁路）。
 *
 * <p>镜像 {@code CloseExpiredClaimJob}：全租户扫描 {@link
 * DomainEventOutboxRepository#findPendingOutboxAllTenants} （{@code @TenantScope(ALL)} 方法），逐笔 {@link
 * TenantContextRunner#runAs} 显式携带租户进派发，再交给 {@link OutboxApplicationService#dispatchAndAck} 落库。调度包只调
 * {@code *AllTenants} 仓储方法 + 应用服务，符合 ADR-0030 守卫。
 *
 * <p><b>E-2 显式租户</b>：扫描行的 {@code tenantId} 必须显式带入派发动作（异步分支不依赖线程上下文），否则非平台租户记录会被静默漏发。
 *
 * <p><b>调度单写者（P1-2，2026-11-12）</b>：本任务经 {@link #relayPending} 由 {@link ScheduleLockService} 包裹，
 * 多副本下仅一个实例执行中继，杜绝重复派发（配合 outbox 自身幂等，保证资金事件零丢失且不重投）。业务逻辑本体见 {@link #relayPendingBody}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  /** 调度单写者锁名（P1-2）。 */
  public static final String LOCK_NAME = "schedule:outbox-relay";

  private final DomainEventOutboxRepository outboxRepository;
  private final OutboxApplicationService outboxApplicationService;
  private final ScheduleLockService scheduleLockService;

  /** 扫描窗口（分钟）：早于此刻仍未 SENT 的 PENDING 记录纳入本次中继。 */
  @Value("${bone.claim.schedule.outbox-older-than-minutes:60}")
  private long olderThanMinutes;

  /** 单批上限。 */
  @Value("${bone.claim.schedule.outbox-batch:200}")
  private int batch;

  /** 卡死占用态回收窗口（分钟）：DISPATCHING 且更新时间早于此刻即视为派发中崩溃，回收重投（B3）。 */
  @Value("${bone.claim.schedule.outbox-stuck-minutes:5}")
  private long stuckMinutes;

  /** 周期可配置（默认每分钟一轮）。 */
  @Scheduled(cron = "${bone.claim.schedule.outbox-relay-cron:0 */1 * * * ?}")
  public void relayPending() {
    // outbox 中继每分钟一轮，租约用 5 分钟：正常单次派发远小于此，崩溃未释放时 5 分钟内由他副本接管。
    scheduleLockService.execute(LOCK_NAME, 5L, this::relayPendingBody);
  }

  /** outbox 中继业务逻辑本体（无锁；单测直接调用，锁由 {@link #relayPending} 的调度单写者包裹）。 */
  void relayPendingBody() {
    Timestamp before = Timestamp.from(Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES));
    List<DomainEventOutbox> pending = outboxRepository.findPendingOutboxAllTenants(before, batch);
    int dispatched = 0;
    int failed = 0;
    for (DomainEventOutbox outbox : pending) {
      DispatchOutcome r = dispatchOne(outbox);
      dispatched += r.dispatched();
      failed += r.failed();
    }
    // 卡死 DISPATCHING 回收：派发中崩溃导致占用态未释放，更新时间早于 stuckBefore 即回收重投（B3）
    Timestamp stuckBefore = Timestamp.from(Instant.now().minus(stuckMinutes, ChronoUnit.MINUTES));
    List<DomainEventOutbox> stuck =
        outboxRepository.findStuckDispatchingOutboxAllTenants(stuckBefore, batch);
    for (DomainEventOutbox outbox : stuck) {
      DispatchOutcome r = dispatchOne(outbox);
      dispatched += r.dispatched();
      failed += r.failed();
    }
    // 死信告警：FAILED（耗尽重试）记录是零丢失的兜底，绝不静默丢弃，必须人工介入（B2）
    List<DomainEventOutbox> dead = outboxRepository.findFailedOutboxAllTenants(batch);
    if (!dead.isEmpty()) {
      log.error(
          "[全租户扫描] outbox 死信告警: 待人工介入 {} 条 (ids={})（E-2 平台运维入口）",
          dead.size(),
          dead.stream().map(o -> String.valueOf(o.getId())).collect(Collectors.joining(",")));
    }
    log.info(
        "[全租户扫描] outbox 中继完成: 窗口={}min, 待发={}, 卡死回收={}, 成功={}, 失败={}, 死信={}",
        olderThanMinutes,
        pending.size(),
        stuck.size(),
        dispatched,
        failed,
        dead.size());
  }

  private record DispatchOutcome(int dispatched, int failed) {}

  /** 单笔派发（在显式租户上下文内执行；返回成功/失败计数供日志聚合）。 */
  private DispatchOutcome dispatchOne(DomainEventOutbox outbox) {
    try {
      boolean[] ok = {false};
      TenantContextRunner.runAs(
          outbox.getTenantId(),
          () ->
              ok[0] =
                  outboxApplicationService.dispatchAndAck(outbox.getTenantId(), outbox.getId()));
      return ok[0] ? new DispatchOutcome(1, 0) : new DispatchOutcome(0, 1);
    } catch (Exception e) {
      log.error("outbox 中继异常: id={}, tenantId={}", outbox.getId(), outbox.getTenantId(), e);
      return new DispatchOutcome(0, 1);
    }
  }
}
