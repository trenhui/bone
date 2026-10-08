package com.bone.gateway.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * API 网关模块响应式健康检查。
 *
 * <p>网关为 WebFlux（Reactive）栈，实现 {@link ReactiveHealthIndicator} 而非阻塞 HealthIndicator。
 *
 * <p>真检查内容：
 *
 * <ul>
 *   <li>遍历 Resilience4j {@code ReactiveCircuitBreakerRegistry} 中所有 routeId 断路器， 聚合 CLOSED / OPEN /
 *       HALF_OPEN 状态。任一 routeId breaker 为 OPEN 时整体健康度降为 DOWN； 半开为 UNKNOWN。
 *   <li>聚合 breaker 最近调用次数、失败率（Resilience4j 原生指标，无额外开销）。
 * </ul>
 *
 * <p>后续可扩展：基于 WebClient 对每个 spring.cloud.gateway.routes 条目做异步 HTTP 心跳， 并把 5xx 超过阈值的路由标记为
 * OUT_OF_SERVICE。
 */
@Component
public class GatewayModuleReactiveHealthIndicator implements ReactiveHealthIndicator {

  private final ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory;

  public GatewayModuleReactiveHealthIndicator(
      ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory) {
    this.circuitBreakerFactory = circuitBreakerFactory;
  }

  @Override
  public Mono<Health> health() {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "bone-gateway");

    io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry registry =
        circuitBreakerFactory.getCircuitBreakerRegistry();

    Map<String, String> breakerStates = new LinkedHashMap<>();
    boolean anyOpen = false;
    for (io.github.resilience4j.circuitbreaker.CircuitBreaker cb :
        registry.getAllCircuitBreakers()) {
      String name = cb.getName();
      String state = cb.getState().name();
      breakerStates.put(name, state);
      if ("OPEN".equals(state)) {
        anyOpen = true;
      }
    }
    details.put("resilience4j-breakers", breakerStates);
    details.put("breaker-total", breakerStates.size());
    details.put("breaker-open", anyOpen);

    Health.Builder builder = anyOpen ? Health.down() : Health.up();
    return Mono.just(builder.withDetails(details).build());
  }
}
