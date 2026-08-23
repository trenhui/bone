package com.bone.integration.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 集成引擎模块健康检查占位实现。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：死信队列(DLQ)堆积、Connector 外连端点心跳、Camel 路由编译失败次数、RocketMQ 发送成功率、Outbox Relay
 *       消费滞后；
 *   <li>未将 Resilience4j 熔断状态（connectorHttp / flowExecution）聚合进 health；
 *   <li>后续应结合 {@link IntegrationExecutionMetrics} 与 {@link DeadLetterMetricsRefresher}
 *       把阈值判定下沉到实际查询，再替换本类为 {@code UNKNOWN/DOWN} 分支返回。
 * </ul>
 */
@Component
public class IntegrationModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-integration")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "IntegrationModuleHealthIndicator is a placeholder; real DLQ/Connector/Outbox/Resilience4j checks pending")
        .build();
  }
}
