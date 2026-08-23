package com.bone.iam.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * IAM 模块健康检查占位实现。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验 DB 连接池活跃数、Redis 令牌黑名单 LATENCY、Minio 审计日志对象存储可达性；
 *   <li>未读取实际签发吞吐与过期 Token 清理任务健康状态；
 *   <li>后续可拆分为独立的 {@code DatabaseHealthIndicator / RedisHealthIndicator /
 *       MinioStorageHealthIndicator}， 通过 {@link
 *       org.springframework.boot.actuate.health.HealthContributorRegistry} 聚合为 {@code iam}
 *       组；如需拆分请勿直接删除本类，改为将其重命名并迁移至分组实现。
 * </ul>
 */
@Component
public class IamModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-iam")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "IamModuleHealthIndicator is a placeholder; real DB/Redis/Minio checks pending")
        .build();
  }
}
