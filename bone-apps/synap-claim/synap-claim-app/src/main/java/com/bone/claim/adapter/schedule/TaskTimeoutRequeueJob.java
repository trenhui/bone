package com.bone.claim.adapter.schedule;

import com.bone.claim.application.TaskRequeueService;
import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import com.bone.claim.infrastructure.schedule.ScheduleLockService;
import com.bone.core.tenant.context.TenantContextRunner;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
 * 超时任务回池定时 Job（批 4 任务 3.3）。
 *
 * <p>扫描 {@code ASSIGNED + claimed_at IS NULL + due_at < now} 的任务并幂等回池。受 {@code
 * all_tenants_scan_only_by_schedule} 约束：定时线程无请求上下文，全租户扫描只能在本包调用；每行显式 {@link
 * TenantContextRunner#runAs} 后写，否则租户写路径失败关闭会被 catch 静默吞掉。单写者由 {@link ScheduleLockService} 保证。
 *
 * <p><b>监控计数</b>：命中/实际回池/幂等跳过/失败分开计数（tag=outcome），避免「命中 10 条全失败」与「全部成功」在日志里同形； 重复扫描不回池时 requeued
 * 不增加，计数器本身即幂等可观测证据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutRequeueJob {

  public static final String LOCK_NAME = "schedule:task-timeout-requeue";
  private static final String COUNTER_NAME = "claim.task.timeout.requeue.total";

  private final ClaimTaskRepository taskRepository;
  private final TaskRequeueService taskRequeueService;
  private final ScheduleLockService scheduleLockService;
  private final MeterRegistry meterRegistry;

  /** 超时阈值（小时）：due_at 早于 now−threshold 才算超时（默认 48h）。 */
  @Value("${bone.claim.schedule.task-timeout-hours:48}")
  private long timeoutHours;

  /** 单批扫描上界（防止一次扫全表；运维判据数据源，非全库扫描器）。 */
  @Value("${bone.claim.schedule.task-timeout-batch:500}")
  private int batchSize;

  @Scheduled(cron = "${bone.claim.schedule.task-timeout-requeue-cron:0 0/30 * * * ?}")
  public void requeueTimedOutTasks() {
    scheduleLockService.execute(LOCK_NAME, this::requeueTimedOutTasksBody);
  }

  /** 业务逻辑本体（无锁；单测直接调用）。 */
  void requeueTimedOutTasksBody() {
    Instant now = Instant.now();
    Timestamp before = Timestamp.from(now.minus(timeoutHours, ChronoUnit.HOURS));
    List<ClaimTask> expired = taskRepository.findExpiredAssignedAllTenants(before, batchSize);
    int requeued = 0;
    int skipped = 0;
    int failed = 0;
    for (ClaimTask task : expired) {
      try {
        boolean changed =
            TenantContextRunner.callAs(
                task.getTenantId(), () -> taskRequeueService.requeueExpired(task.getId(), now));
        if (Boolean.TRUE.equals(changed)) {
          requeued++;
        } else {
          skipped++;
        }
      } catch (Exception e) {
        failed++;
        log.error("超时回池失败: taskId={}, tenantId={}", task.getId(), task.getTenantId(), e);
      }
    }
    counter("hit").increment(expired.size());
    counter("requeued").increment(requeued);
    counter("skipped").increment(skipped);
    counter("failed").increment(failed);
    log.info(
        "[全租户扫描] 超时任务回池完成: 阈值={}h, 命中={}, 回池={}, 幂等跳过={}, 失败={}",
        timeoutHours,
        expired.size(),
        requeued,
        skipped,
        failed);
  }

  private Counter counter(String outcome) {
    return Counter.builder(COUNTER_NAME).tag("outcome", outcome).register(meterRegistry);
  }
}
