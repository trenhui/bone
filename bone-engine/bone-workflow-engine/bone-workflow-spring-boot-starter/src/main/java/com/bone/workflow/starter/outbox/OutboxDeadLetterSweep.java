package com.bone.workflow.starter.outbox;

import com.bone.workflow.starter.metrics.WorkflowMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 死信积压巡检（P0-2 完善）：周期统计 {@code wf_status_outbox} 中终态 FAILED 的存量意图，更新 {@code dead_letter_depth} Gauge
 * 并在有积压时告警。
 *
 * <p><b>为什么需要它</b>：{@link StatusProjectionRelay} 在动作未声明幂等（或非可重试根因）时会把意图落终态 FAILED 并「转人工」。 但若只靠单次
 * {@code terminally_failed} 计数器，积压会随时间累积而无人察觉——运营不知道「此刻还堆着多少条没处置的死信」。 Gauge 让「存量」可见，本作业把存量持续刷给监控。
 *
 * <p><b>opt-in</b>：由 {@code bone.workflow.outbox.dead-letter-sweep.enabled=true} 开启（默认关）。它用
 * {@code @Scheduled}， 需宿主开启 {@code @EnableScheduling}；未开启时该注解静默忽略。
 */
public class OutboxDeadLetterSweep {

  private static final Logger log = LoggerFactory.getLogger(OutboxDeadLetterSweep.class);

  private final WorkflowStatusOutboxRepository outbox;
  private final WorkflowMetrics metrics;

  public OutboxDeadLetterSweep(WorkflowStatusOutboxRepository outbox, WorkflowMetrics metrics) {
    this.outbox = outbox;
    this.metrics = metrics == null ? WorkflowMetrics.NOOP : metrics;
  }

  @Scheduled(
      fixedDelayString = "${bone.workflow.outbox.dead-letter-sweep.fixed-delay:60000}",
      initialDelayString = "${bone.workflow.outbox.dead-letter-sweep.initial-delay:30000}")
  public void run() {
    try {
      long n = outbox.countTerminallyFailed();
      metrics.deadLetterDepth((int) n);
      if (n > 0) {
        log.warn("[workflow] 状态投影死信积压：{} 条意图已落终态 FAILED（非幂等动作被拒 / 不可重试根因），需人工接入幂等后重投或手动处置", n);
      }
    } catch (RuntimeException ex) {
      // 巡检自身异常不得冒泡到调度器；下一轮自然重试
      log.error("[workflow] 死信积压巡检本轮异常（下轮重试）", ex);
    }
  }
}
