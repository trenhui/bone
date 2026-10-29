package com.bone.workflow.starter;

import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.starter.metrics.WorkflowMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 卡死实例看门狗（§11.4 决策 #2 落地）：周期扫描「仍处于 RUNNING、但当前节点已停留超阈值、且无任何 PENDING 任务」的实例， 更新 {@code
 * instance.stuck} Gauge 并逐条告警。
 *
 * <p><b>为什么需要它</b>：正常实例要么有 PENDING 任务在等人审，要么节点已推进。若某实例 RUNNING、节点停留很久、却<b>没有任何待办任务</b>，
 * 说明节点推进后派单失败（任务 CAS 后崩溃）、或 {@code assignTask} 异常导致节点永远等不齐——这类实例<b>不会自愈</b>，
 * 会一直占着活动指针、挡住同一单据重新提交（陈旧指针把门焊死）。仅靠人工翻「问题单据」列表无法保证及时发现，看门狗把它变成可告警信号。
 *
 * <p><b>判据是「无 PENDING 任务」而非「有 PENDING 但超时」</b>：后者是「人迟迟不审」的正常业务等待（SLA 范畴，由挂起/催办处理），看门狗不应把慢审批当成死实例告警。
 *
 * <p><b>opt-in</b>：由 {@code bone.workflow.instance-watchdog.enabled=true}
 * 开启（默认关）。跨租户扫描（与中继同口径，调度线程无 TenantContext），仅做计数 + 告警，不触达任何业务动作。
 */
public class WorkflowInstanceWatchdog {

  private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceWatchdog.class);

  /** 节点停留超此时长仍无待办即判为卡死（默认 1 小时）。可由构造参数覆盖。 */
  private final Duration stuckThreshold;

  private final WorkflowInstanceRepository instances;
  private final WorkflowMetrics metrics;

  public WorkflowInstanceWatchdog(
      WorkflowInstanceRepository instances, WorkflowMetrics metrics, Duration stuckThreshold) {
    this.instances = instances;
    this.metrics = metrics == null ? WorkflowMetrics.NOOP : metrics;
    this.stuckThreshold = stuckThreshold == null ? Duration.ofHours(1) : stuckThreshold;
  }

  @Scheduled(
      fixedDelayString = "${bone.workflow.instance-watchdog.fixed-delay:300000}",
      initialDelayString = "${bone.workflow.instance-watchdog.initial-delay:60000}")
  public void run() {
    try {
      Instant cutoff = Instant.now().minus(stuckThreshold);
      List<String> stuck = instances.findStuckInstances(cutoff);
      metrics.stuckInstances(stuck.size());
      if (!stuck.isEmpty()) {
        log.warn(
            "[workflow] 检测到 {} 个卡死流程实例（RUNNING 且当前节点停留超 {} 且无待办任务），需人工介入：{}",
            stuck.size(),
            stuckThreshold,
            stuck);
      }
    } catch (RuntimeException ex) {
      // 看门狗自身异常不得冒泡到调度器；下一轮自然重试
      log.error("[workflow] 卡死实例巡检本轮异常（下轮重试）", ex);
    }
  }
}
