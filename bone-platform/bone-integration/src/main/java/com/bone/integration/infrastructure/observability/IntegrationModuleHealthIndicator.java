package com.bone.integration.infrastructure.observability;

import com.bone.integration.infrastructure.messaging.outbox.IntegrationOutboxRecord;
import com.bone.integration.infrastructure.messaging.outbox.IntegrationOutboxRepository;
import com.bone.integration.infrastructure.messaging.outbox.OutboxStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 集成引擎模块健康检查。
 *
 * <p>真检查内容：
 *
 * <ul>
 *   <li>Resilience4j {@code connectorHttp} / {@code flowExecution} 断路器状态聚合。 任一断路器为 OPEN 时整体健康度降为
 *       DOWN。
 *   <li>Outbox PENDING 积压计数（跨租户）。超过 1000 条视为异常积压降为 WARN； 超过 5000 条视为 DOWN（中继可能挂了或 MQ 不可达）。
 * </ul>
 */
@Slf4j
@Component
public class IntegrationModuleHealthIndicator implements HealthIndicator {

  /** Outbox PENDING 积压 WARN 阈值 */
  private static final int OUTBOX_WARN_THRESHOLD = 1_000;

  /** Outbox PENDING 积压 DOWN 阈值 */
  private static final int OUTBOX_DOWN_THRESHOLD = 5_000;

  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final IntegrationOutboxRepository outboxRepository;

  public IntegrationModuleHealthIndicator(
      CircuitBreakerRegistry circuitBreakerRegistry, IntegrationOutboxRepository outboxRepository) {
    this.circuitBreakerRegistry = circuitBreakerRegistry;
    this.outboxRepository = outboxRepository;
  }

  @Override
  public Health health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "bone-integration");

    boolean anyBreakerOpen = false;
    Map<String, String> breakerStates = new LinkedHashMap<>();
    for (String name : new String[] {"connectorHttp", "flowExecution"}) {
      CircuitBreaker.State state =
          circuitBreakerRegistry.find(name).map(CircuitBreaker::getState).orElse(null);
      String stateStr = state != null ? state.name() : "NOT_REGISTERED";
      breakerStates.put(name, stateStr);
      if ("OPEN".equals(stateStr)) {
        anyBreakerOpen = true;
      }
    }
    details.put("resilience4j-breakers", breakerStates);
    details.put("breaker-open", anyBreakerOpen);

    // Outbox PENDING 积压探测（跨租户，disableTenantFilter）
    long pendingCount = -1;
    try {
      pendingCount =
          outboxRepository.countByCriteria(
              com.bone.metadata.sdk.query.criteria.Criteria.<IntegrationOutboxRecord>create()
                  .eq(IntegrationOutboxRecord::getStatus, OutboxStatus.PENDING)
                  .disableTenantFilter());
    } catch (Exception ex) {
      log.warn("Outbox PENDING 计数探测失败（DB 可能不可达）", ex);
    }
    details.put("outbox-pending", pendingCount);

    Health.Builder builder;
    if (anyBreakerOpen) {
      builder = Health.down();
    } else if (pendingCount >= OUTBOX_DOWN_THRESHOLD) {
      builder = Health.down();
      details.put("outbox-overflow", true);
    } else if (pendingCount >= OUTBOX_WARN_THRESHOLD) {
      builder = Health.status("WARN");
    } else if (pendingCount < 0) {
      builder = Health.status("UNKNOWN");
    } else {
      builder = Health.up();
    }
    return builder.withDetails(details).build();
  }
}
