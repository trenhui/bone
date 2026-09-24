package com.bone.gateway.filter;

import com.bone.gateway.config.GatewayResilienceProperties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关熔断：按路由（routeId）隔离的 Resilience4j 熔断器，包裹下游转发。
 *
 * <p>早期实现使用单一全局 breaker（id="gateway-routing"），任一消费服务（如 extension / generator）未启动返回 503 时， 会把全局
 * breaker 打开放纵到其它健康路由（如 metadata），导致全栈联调被误伤。改为按 routeId 各自维护独立 breaker 后，
 * 单服务的故障域被限制在其自身路由内，不再影响其它微服务的可用性。
 *
 * <p>order 置于 JWT 鉴权与限流之后、实际路由转发之前。
 */
@Component
public class CircuitBreakerGatewayFilter implements GlobalFilter, Ordered {

  public static final int ORDER = JwtAuthGlobalFilter.ORDER + 20;

  private final GatewayResilienceProperties properties;
  private final ReactiveResilience4JCircuitBreakerFactory breakerFactory;
  private final ConcurrentMap<String, ReactiveCircuitBreaker> breakers = new ConcurrentHashMap<>();

  public CircuitBreakerGatewayFilter(
      ReactiveResilience4JCircuitBreakerFactory breakerFactory,
      GatewayResilienceProperties properties) {
    this.breakerFactory = breakerFactory;
    this.properties = properties;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    String breakerId = (route != null && route.getId() != null) ? route.getId() : "gateway-routing";
    ReactiveCircuitBreaker breaker = breakers.computeIfAbsent(breakerId, breakerFactory::create);
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
