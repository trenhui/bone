package com.bone.system.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * System 模块健康检查占位实现。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：sys_log 归档任务运行状态、sys_config 配置读取一致性、 sys_monitor 数据面 DB 查询耗时、keyMetrics
 *       跨表聚合查询耗时；
 *   <li>未暴露配置中心（Nacos）连接健康；
 *   <li>后续建议拆分成 {@code system-config / system-log / system-monitor} 分组，分别回写最近一次 定时任务的执行结果。
 * </ul>
 */
@Component
public class SystemModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-system")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "SystemModuleHealthIndicator is a placeholder; real log/config/monitor/keyMetrics checks pending")
        .build();
  }
}
