package com.bone.blueprint.infrastructure.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Blueprint（DDD 参考实现）模块健康检查占位实现。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：订单 Outbox 中继积压、RocketMQ 消费者 lag、库存 Feign 网关 超时率、扩展引擎调用熔断；
 *   <li>未与业务侧订单聚合一致性探测结合；
 *   <li>Blueprint 作为参考模板，后续模块（如 procurement）请基于该模板派生同类占位类。
 * </ul>
 */
@Component
public class BlueprintModuleHealthIndicator implements HealthIndicator {

  @Override
  public Health health() {
    return Health.up()
        .withDetail("module", "bone-blueprint")
        .withDetail("status", "STUB")
        .withDetail(
            "limitation",
            "BlueprintModuleHealthIndicator is a placeholder; real Outbox/MQ/Feign/Extension checks pending")
        .build();
  }
}
