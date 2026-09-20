package com.bone.iam.application.port.out;

import java.util.List;

/**
 * 审计日志保留期维护端口（按租户清理过期日志）。
 *
 * <p>按 E-10.2「应用流程需要通知、时钟、文件、幂等等技术能力 → {@code application/port/out} → {@code
 * infrastructure/<具体能力>}」放置：保留期清理是跨租户的维护型数据操作，定时任务没有 JWT / 租户上下文，走 SDK 的 {@code Criteria} 通道会被
 * ADR-0029 失败关闭拦下，故作为受控的技术能力端口暴露，SQL 细节留在 {@code infrastructure}。
 */
public interface AuditLogRetentionPort {

  /** 存在审计日志的租户 id 清单（跨租户扫描，仅维护任务使用）。 */
  List<Long> listTenantsWithLogs();

  /**
   * 清理某租户在"当前时间 - {@code retentionDays} 天"之前的审计日志。
   *
   * <p>截止时间由数据库侧计算（{@code NOW(3)}），避免应用与数据库时钟偏差影响清理边界。
   *
   * @return 实际删除行数
   */
  int purgeExpired(Long tenantId, int retentionDays);
}
