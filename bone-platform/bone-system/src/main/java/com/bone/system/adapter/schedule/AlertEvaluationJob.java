package com.bone.system.adapter.schedule;

import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.system.application.AlertApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 告警规则评估定时任务：周期性把启用的规则与实测指标比对，触发 / 更新 / 自动恢复告警事件。
 *
 * <p><b>为什么必须有这个 Job</b>：规则 + 事件两张表只是存储形态；没有周期评估器，规则永远不会被自动计算， 告警只能靠外部手动 POST {@code
 * /alert/events}——监控告警在语义上是「假绿」的。本 Job 是补齐 「监控引擎收集指标并触发告警」（详设 §3.2）的最小闭环。
 *
 * <p><b>为什么包 {@code TenantContextRunner}</b>：SDK 写侧以可信 TenantContext 解析 {@code tenant_id}
 * （ADR-0029）；调度线程无 HTTP 上下文，不显式落平台租户（0）会导致插入缺租户上下文而失败。 规则与事件的跨租户扫描由仓储 default 方法显式 {@code
 * disableTenantFilter()}。
 *
 * <p>保留在 {@code adapter/schedule}：ADR-0030 授权定时入口可直连应用服务的形态（对齐 iam {@code AuditLogCleanupJob}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluationJob {

  private final AlertApplicationService alertApplicationService;

  @Value("${bone.system.alert.evaluation-enabled:true}")
  private boolean enabled;

  /** 评估周期默认 60s，可通过 {@code bone.system.alert.evaluation-interval-ms} 调整。 */
  @Scheduled(fixedDelayString = "${bone.system.alert.evaluation-interval-ms:60000}")
  public void evaluate() {
    if (!enabled) {
      return;
    }
    var summary = TenantContextRunner.callAs(0L, alertApplicationService::evaluateAllRules);
    if (summary.triggered() > 0 || summary.resolved() > 0) {
      log.info(
          "[AlertEval] evaluated={} triggered={} updated={} resolved={} skipped={}",
          summary.evaluated(),
          summary.triggered(),
          summary.updated(),
          summary.resolved(),
          summary.skipped());
    }
  }
}
