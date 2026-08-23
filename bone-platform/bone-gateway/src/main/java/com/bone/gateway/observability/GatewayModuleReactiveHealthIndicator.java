package com.bone.gateway.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * API 网关模块响应式健康检查占位实现。
 *
 * <p>网关为 WebFlux（Reactive）栈，必须实现 {@link ReactiveHealthIndicator} 而非阻塞 HealthIndicator。
 *
 * <p>已知限制（技术债，待后续扩展）：
 *
 * <ul>
 *   <li>当前仅返回静态 UP，未真正校验：所有上游路由（iam/masterdata/system/integration/generator/metadata-server
 *       /extension-studio）HTTP 存活探测、Resilience4j 熔断状态、Redis 限流桶容量；
 *   <li>未聚合白名单路径匹配次数、JWT 签名失败率；
 *   <li>后续应基于 {@code WebClient} 对每个 {@code spring.cloud.gateway.routes} 条目做异步心跳， 并把 5xx 超过阈值的路由标记为
 *       {@code OUT_OF_SERVICE} 聚合到 {@code gateway} 分组。
 * </ul>
 */
@Component
public class GatewayModuleReactiveHealthIndicator implements ReactiveHealthIndicator {

  @Override
  public Mono<Health> health() {
    return Mono.just(
        Health.up()
            .withDetail("module", "bone-gateway")
            .withDetail("status", "STUB")
            .withDetail(
                "limitation",
                "GatewayModuleReactiveHealthIndicator is a placeholder; real upstream/Resilience4j/Redis checks pending")
            .build());
  }
}
