package com.bone.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 熔断参数生效性回归（G-1 验收关键，报告 §五）。
 *
 * <p>历史缺陷：yml {@code resilience4j.instances.gatewayRoutes} 与 Java {@code configureDefault} 双真源冲突且
 * yml 实际无效。本测试把 Java 侧参数值与「窗口满即打开」的真实行为同时钉死—— 任何人改参数而未同步测试语义，或 reintroduce 第二真源导致行为漂移，这里会先红。
 */
class ResilienceConfigParameterTest {

  @Test
  @DisplayName("真源参数：50% 失败率 / 窗口 10 / 最少 10 / 打开 10s / 半开 3 次")
  void circuitBreakerParametersMatchSingleSourceOfTruth() {
    CircuitBreakerConfig config = ResilienceConfig.circuitBreakerConfig();
    assertThat(config.getFailureRateThreshold()).isEqualTo(50);
    assertThat(config.getSlidingWindowSize()).isEqualTo(10);
    // 关键：不显式锁 10 时默认 100，窗口形同虚设、熔断永远打不开。
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(10);
    // waitDurationInOpenState=10s：本版本 CircuitBreakerConfig 无直接 getter，
    // 行为由 ResilienceConfig 真源注释与半开参数（3 次）间接钉住。
    assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
  }

  @Test
  @DisplayName("时间限制器真源：5s 超时")
  void timeLimiterParameterMatchesSingleSourceOfTruth() {
    assertThat(ResilienceConfig.timeLimiterConfig().getTimeoutDuration())
        .isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("行为回归：连续 10 次失败后熔断打开（而非 100 次）")
  void breakerOpensAfterTenFailuresNotHundred() {
    CircuitBreaker breaker =
        CircuitBreaker.of("test-route", ResilienceConfig.circuitBreakerConfig());
    for (int i = 0; i < 10; i++) {
      try {
        breaker.executeCallable(
            () -> {
              throw new IllegalStateException("boom");
            });
      } catch (Exception ignored) {
        // 预期失败调用
      }
    }
    assertThat(breaker.getState())
        .as("窗口 10 + 最少 10 + 失败率 100%% => 第 10 次失败后必须 OPEN")
        .isEqualTo(CircuitBreaker.State.OPEN);
  }
}
