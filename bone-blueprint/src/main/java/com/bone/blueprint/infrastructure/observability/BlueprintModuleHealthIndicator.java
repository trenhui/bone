package com.bone.blueprint.infrastructure.observability;

import com.bone.blueprint.infrastructure.messaging.outbox.OrderOutboxRecord;
import com.bone.blueprint.infrastructure.messaging.outbox.OrderOutboxRepository;
import com.bone.blueprint.infrastructure.messaging.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Blueprint 模块健康检查：Outbox 积压 + 整体模块状态。
 *
 * <p>Outbox 积压判定：
 *
 * <ul>
 *   <li>PENDING 记录数 ≤ 10 → UP（健康）
 *   <li>PENDING 记录数 11~100 → UP（但 WARN detail 标记）
 *   <li>PENDING 记录数 > 100 → DOWN（严重积压，须人工介入）
 * </ul>
 *
 * <p>真实部署环境应额外检查：RocketMQ 消费者 lag、库存网关超时率、扩展引擎熔断状态。 Blueprint 作为参考模板，其他模块可基于此派生自己的健康检查。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlueprintModuleHealthIndicator implements HealthIndicator {

  /** PENDING 积压阈值：超过此数量判定为严重积压。 */
  private static final int PENDING_CRITICAL_THRESHOLD = 100;

  private final OrderOutboxRepository orderOutboxRepository;

  @Override
  public Health health() {
    try {
      long pendingCount =
          orderOutboxRepository
              .query()
              .where(OrderOutboxRecord::getStatus)
              .eq(OutboxStatus.PENDING)
              .count();

      Health.Builder builder = Health.up().withDetail("module", "bone-blueprint");

      if (pendingCount > PENDING_CRITICAL_THRESHOLD) {
        log.warn(
            "Outbox PENDING 记录积压严重: count={}, threshold={}",
            pendingCount,
            PENDING_CRITICAL_THRESHOLD);
        builder = Health.down();
      }

      builder
          .withDetail("outboxPendingCount", pendingCount)
          .withDetail("outboxCriticalThreshold", PENDING_CRITICAL_THRESHOLD);

      return builder.build();
    } catch (Exception ex) {
      log.error("Outbox 健康检查执行失败", ex);
      return Health.down()
          .withDetail("error", ex.getMessage())
          .withDetail("module", "bone-blueprint")
          .build();
    }
  }
}
