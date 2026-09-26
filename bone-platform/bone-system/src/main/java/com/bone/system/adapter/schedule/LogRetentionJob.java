package com.bone.system.adapter.schedule;

import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.system.domain.repository.SystemLogRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 系统日志保留期清理任务：物理删除超过保留期（详设 §2.3，默认 180 天，可配置）的 {@code sys_log} 行。
 *
 * <p><b>为什么必须有这个 Job</b>：详设声明了「日志保留期 180 天」，但此前没有任何清理实现—— {@code sys_log} 会随写入无限膨胀，声明变成一纸空文。删除走仓储
 * default 方法（SQL 下推， 非逐条 deleteById），并显式 {@code disableTenantFilter()}：保留期是平台级策略，跨租户生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionJob {

  private final SystemLogRepository systemLogRepository;

  @Value("${bone.system.log-retention-days:180}")
  private int retentionDays;

  /** 每日 03:30 执行（与 iam 审计清理 03:00 错峰）。 */
  @Scheduled(cron = "${bone.system.log.cleanup-cron:0 30 3 * * ?}")
  public void purgeExpiredLogs() {
    if (retentionDays <= 0) {
      log.warn("[LogRetention] bone.system.log-retention-days={} 非法，跳过清理", retentionDays);
      return;
    }
    LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
    int deleted =
        TenantContextRunner.callAs(0L, () -> systemLogRepository.purgeOlderThanAllTenants(cutoff));
    if (deleted > 0) {
      log.info("[LogRetention] 清理 {} 天前的系统日志 {} 行（cutoff={}）", retentionDays, deleted, cutoff);
    }
  }
}
