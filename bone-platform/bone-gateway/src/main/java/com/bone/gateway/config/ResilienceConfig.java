package com.bone.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关熔断配置：Resilience4j CircuitBreaker + TimeLimiter。
 *
 * <p><b>单一真源（G-1，设计稿 §5.2 裁决）</b>：本类是熔断参数的<b>唯一</b>真源—— {@code configureDefault} 对所有 {@code
 * create(id)}（breakerId=routeId）生效，Spring Cloud CircuitBreaker 不会再回落 Resilience4j 原生 {@code
 * instances} 配置。原 {@code application.yml} 的 {@code resilience4j:*} 整段（实例名 {@code gatewayRoutes} 与
 * routeId 不匹配、调参实际无效）已随本裁决删除。 运维调参改此处；参数生效性由 {@code ResilienceConfigParameterTest} 钉住。
 */
@Configuration
public class ResilienceConfig {

  @Bean
  public Customizer<ReactiveResilience4JCircuitBreakerFactory> gatewayCircuitBreakerCustomizer() {
    return factory ->
        factory.configureDefault(
            id ->
                new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(circuitBreakerConfig())
                    .timeLimiterConfig(timeLimiterConfig())
                    .build());
  }

  /** 熔断参数真源：50% 失败率、窗口 10 次、最少 10 次评估、打开 10s、半开探 3 次。 */
  static CircuitBreakerConfig circuitBreakerConfig() {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        // 不显式设置时 resilience4j 默认 minimumNumberOfCalls=100：窗口只有 10 却要 100 次才评估，
        // 熔断几乎永远打不开——必须与窗口同值锁死（原 yml 的 minimum-number-of-calls: 10 语义并入此处）。
        .minimumNumberOfCalls(10)
        .slidingWindowSize(10)
        .waitDurationInOpenState(Duration.ofSeconds(10))
        .permittedNumberOfCallsInHalfOpenState(3)
        .build();
  }

  /** 时间限制器真源：单次下游调用超过 5s 视为超时（计入熔断统计）。 */
  static TimeLimiterConfig timeLimiterConfig() {
    return TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(5)).build();
  }
}
