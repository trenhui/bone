package com.bone.claim.adapter.schedule;

import com.bone.claim.application.OutboxApplicationService;
import com.bone.claim.domain.model.event.DomainEventOutbox;
import com.bone.claim.domain.repository.DomainEventOutboxRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final DomainEventOutboxRepository outboxRepository;
  private final OutboxApplicationService outboxApplicationService;

  /** 扫描窗口（分钟）：早于此刻仍未 SENT 的 PENDING 记录纳入本次中继。 */
  @Value("${bone.claim.schedule.outbox-older-than-minutes:60}")
  private long olderThanMinutes;

  /** 单批上限。 */
  @Value("${bone.claim.schedule.outbox-batch:200}")
  private int batch;

  /** 周期可配置（默认每分钟一轮）。 */
  @Scheduled(cron = "${bone.claim.schedule.outbox-relay-cron:0 */1 * * * ?}")
  public void relayPending() {
    Timestamp before = Timestamp.from(Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES));
    List<DomainEventOutbox> pending = outboxRepository.findPendingOutboxAllTenants(before, batch);
    int dispatched = 0;
    int failed = 0;
    for (DomainEventOutbox outbox : pending) {
      try {
        boolean[] ok = {false};
        TenantContextRunner.runAs(
            outbox.getTenantId(),
            () ->
                ok[0] =
                    outboxApplicationService.dispatchAndAck(outbox.getTenantId(), outbox.getId()));
        if (ok[0]) {
          dispatched++;
        } else {
          failed++;
        }
      } catch (Exception e) {
        failed++;
        log.error("outbox 中继异常: id={}, tenantId={}", outbox.getId(), outbox.getTenantId(), e);
      }
    }

    // 区分「命中」与「实际完成」：只打命中数会掩盖派发失败。
    log.info(
        "[全租户扫描] outbox 中继完成: 窗口={}min, 待发={}, 成功={}, 失败={}（E-2 平台运维入口）",
        olderThanMinutes,
        pending.size(),
        dispatched,
        failed);
  }
}
