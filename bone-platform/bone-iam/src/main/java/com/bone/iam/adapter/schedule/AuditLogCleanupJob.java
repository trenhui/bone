package com.bone.iam.adapter.schedule;

import com.bone.iam.domain.audit.AuditSettings;
import com.bone.iam.domain.gateway.AuditSettingsStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按租户 {@code iam_audit_settings.retention_days} 清理过期审计日志（社区版默认 30 天）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupJob {

    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final AuditSettingsStore auditSettingsStore;

    /** 每日 03:00 执行（可通过 {@code spring.task.scheduling} 覆盖）。 */
    @Scheduled(cron = "${bone.iam.audit.cleanup-cron:0 0 3 * * ?}")
    public void purgeExpiredLogs() {
        List<Long> tenantIds =
                jdbcTemplate.queryForList("SELECT DISTINCT tenant_id FROM iam_audit_log", Long.class);
        if (tenantIds.isEmpty()) {
            return;
        }
        int totalDeleted = 0;
        for (Long tenantId : tenantIds) {
            AuditSettings settings = auditSettingsStore.findByTenantId(tenantId);
            int retention = settings.getRetentionDays() > 0 ? settings.getRetentionDays() : DEFAULT_RETENTION_DAYS;
            int deleted =
                    jdbcTemplate.update(
                            """
                            DELETE FROM iam_audit_log
                            WHERE tenant_id = ? AND created_at < DATE_SUB(NOW(3), INTERVAL ? DAY)
                            """,
                            tenantId,
                            retention);
            totalDeleted += deleted;
        }
        if (totalDeleted > 0) {
            log.info("iam audit log cleanup: deleted {} rows across {} tenants", totalDeleted, tenantIds.size());
        }
    }
}
