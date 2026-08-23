package com.bone.gateway.filter;

import com.bone.gateway.config.GatewayResilienceProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 网关熔断：包裹下游转发，失败率超阈值进入 OPEN 快速失败返 503。 order 置于 JWT 鉴权与限流之后、实际路由转发之前。 */
@Component
public class CircuitBreakerGatewayFilter implements GlobalFilter, Ordered {

  public static final int ORDER = JwtAuthGlobalFilter.ORDER + 20;

  private final GatewayResilienceProperties properties;
  private final ReactiveCircuitBreaker breaker;

  public CircuitBreakerGatewayFilter(
      ReactiveResilience4JCircuitBreakerFactory breakerFactory,
      GatewayResilienceProperties properties) {
    this.properties = properties;
    this.breaker = breakerFactory.create("gateway-routing");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }
    return breaker.run(
        chain.filter(exchange),
        throwable ->
            GatewayErrorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "下游服务暂不可用（熔断）"));
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
