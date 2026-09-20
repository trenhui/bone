package com.bone.iam.adapter.schedule;

import com.bone.iam.application.port.out.AuditLogRetentionPort;
import com.bone.iam.domain.audit.AuditSettings;
import com.bone.iam.domain.gateway.AuditSettingsGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按租户 {@code iam_audit_settings.retention_days} 清理过期审计日志（社区版默认 30 天）。
 *
 * <p>本类只做保留期编排：跨租户扫描与物理删除经 application 出站端口 {@link AuditLogRetentionPort} 下沉到 {@code
 * infrastructure/persistence}（原实现在本类直接用 {@code JdbcTemplate} 拼 SQL，属"豁免了分层、没豁免持久化方式"）。 保留在 {@code
 * adapter/schedule}：它是 ADR-0030 明确授权可直连出站端口的定时入口形态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupJob {

  private static final int DEFAULT_RETENTION_DAYS = 30;

  private final AuditLogRetentionPort auditLogRetentionPort;
  private final AuditSettingsGateway auditSettingsGateway;

  /** 每日 03:00 执行（可通过 {@code spring.task.scheduling} 覆盖）。 */
  @Scheduled(cron = "${bone.iam.audit.cleanup-cron:0 0 3 * * ?}")
  public void purgeExpiredLogs() {
    List<Long> tenantIds = auditLogRetentionPort.listTenantsWithLogs();
    if (tenantIds.isEmpty()) {
      return;
    }
    int totalDeleted = 0;
    for (Long tenantId : tenantIds) {
      AuditSettings settings = auditSettingsGateway.findByTenantId(tenantId);
      int retention =
          settings.getRetentionDays() > 0 ? settings.getRetentionDays() : DEFAULT_RETENTION_DAYS;
      totalDeleted += auditLogRetentionPort.purgeExpired(tenantId, retention);
    }
    if (totalDeleted > 0) {
      log.info(
          "iam audit log cleanup: deleted {} rows across {} tenants",
          totalDeleted,
          tenantIds.size());
    }
  }
}
