package com.bone.iam.infrastructure.persistence;

import com.bone.iam.application.port.out.AuditLogRetentionPort;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link AuditLogRetentionPort} 的 JDBC 实现。
 *
 * <p>落 {@code infrastructure/persistence}（E-10.2：持久化技术能力的落点）。保留原生 SQL 是刻意选择：定时任务没有租户 上下文，走 SDK 的
 * {@code Criteria} 通道会被 ADR-0029 失败关闭拦下；清理语句按设计物理删除过期日志，不做软删过滤。
 *
 * <p>SQL 只出现在本类，{@code adapter} 层（{@code AuditLogCleanupJob}）只做保留期编排。
 *
 * <p><b>受控例外（HC-006 登记）</b>：定时清理属全租户运维任务，无请求级租户上下文，故不走 SDK 的 {@code Criteria} 通道；但每条清理语句均显式按 {@code
 * tenant_id} 作用域（{@code PURGE_SQL} 的 {@code WHERE tenant_id = ?}），不存在跨租户越权。登记依据见 {@code
 * sdk-persistence-bypass-baseline.json}。
 */
@Component
public class AuditLogRetentionPortAdapter implements AuditLogRetentionPort {

  private static final String LIST_TENANTS_SQL = "SELECT DISTINCT tenant_id FROM iam_audit_log";

  private static final String PURGE_SQL =
      """
      DELETE FROM iam_audit_log
      WHERE tenant_id = ? AND created_at < DATE_SUB(NOW(3), INTERVAL ? DAY)
      """;

  private final JdbcTemplate jdbcTemplate;

  public AuditLogRetentionPortAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public List<Long> listTenantsWithLogs() {
    return jdbcTemplate.queryForList(LIST_TENANTS_SQL, Long.class);
  }

  @Override
  public int purgeExpired(Long tenantId, int retentionDays) {
    return jdbcTemplate.update(PURGE_SQL, tenantId, retentionDays);
  }
}
