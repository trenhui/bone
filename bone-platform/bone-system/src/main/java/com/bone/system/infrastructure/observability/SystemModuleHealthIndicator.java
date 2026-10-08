package com.bone.system.infrastructure.observability;

import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.repository.SystemLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * System 模块健康检查。
 *
 * <p>真检查内容：
 *
 * <ul>
 *   <li>轻量 DB 可达探测：对 sys_log 表执行一次跨租户 count（disableTenantFilter）， 验证 metadata-sdk
 *       读路径与数据库连通性。探测本身极低开销（COUNT 走索引）， 异常时整体健康度降为 DOWN。
 * </ul>
 *
 * <p>后续可扩展：Nacos ConfigService 探测、调度器线程池活跃数暴露。
 */
@Component
public class SystemModuleHealthIndicator implements HealthIndicator {

  private final SystemLogRepository systemLogRepository;

  public SystemModuleHealthIndicator(SystemLogRepository systemLogRepository) {
    this.systemLogRepository = systemLogRepository;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "bone-system");

    long logCount;
    try {
      logCount =
          systemLogRepository.countByCriteria(
              com.bone.metadata.sdk.query.criteria.Criteria.<SystemLog>create()
                  .disableTenantFilter());
    } catch (Exception ex) {
      details.put("db-error", ex.getMessage());
      return Health.down().withDetails(details).build();
    }
    details.put("sys_log-total", logCount);

    return Health.up().withDetails(details).build();
  }
}
