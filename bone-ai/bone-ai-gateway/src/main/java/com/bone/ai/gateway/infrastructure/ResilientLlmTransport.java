package com.bone.ai.gateway.infrastructure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 出站 LLM 传输韧性装饰器（AI 中台 §4.1 网关治理面）：把「发请求」包进 熔断 + 重试。
 *
 * <p>业界对位：LiteLLM / Portkey / Kong AI Gateway 的标准 provider 韧性——熔断防"provider 退化时的重试风暴"，重试防瞬时故障。
 * 刻意实现：
 *
 * <ul>
 *   <li><b>重试语义化</b>：仅对 5xx / 网络异常重试（4xx 为请求方错误，重试无意义且放大成本）；用 {@link Retry} 的 {@code
 *       retryOnException} 谓词表达，而非盲目重试。
 *   <li><b>熔断只看后端</b>：断路器放内层（先重试后熔断），只统计最终失败，不因可重试的瞬时抖动误开。
 *   <li><b>可回放</b>：被熔断拒绝（{@link CallNotPermittedException}）原样抛出，由上层按合规路径降级（BONE 刻意分歧——绝不 fail
 *       到直连模型，见总案 §4.1）。
 * </ul>
 *
 * <p>装配：gateway 配置出现 {@code CircuitBreakerRegistry} Bean 时启用；否则回落直连（POC 零依赖）。单测经 fake registry
 * 覆盖真实熔断状态机。
 */
public class ResilientLlmTransport implements LlmHttpTransport {

  /** 断路器名（指标/观测聚合键）。 */
  public static final String LLM_CIRCUIT = "llmProvider";

  private final LlmHttpTransport delegate;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public ResilientLlmTransport(
      LlmHttpTransport delegate, CircuitBreakerRegistry circuitBreakerRegistry) {
    this(delegate, circuitBreakerRegistry, defaultConfig());
  }

  public ResilientLlmTransport(
      LlmHttpTransport delegate, CircuitBreakerRegistry circuitBreakerRegistry, Config config) {
    this.delegate = delegate;
    this.circuitBreaker =
        circuitBreakerRegistry.circuitBreaker(
            LLM_CIRCUIT,
            CircuitBreakerConfig.custom()
                .failureRateThreshold(config.failureRateThreshold)
                .slidingWindowSize(config.slidingWindowSize)
                .minimumNumberOfCalls(config.minimumNumberOfCalls)
                .waitDurationInOpenState(config.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(config.permittedCallsInHalfOpen)
                .build());
    this.retry =
        Retry.of(
            "llmProviderRetry",
            RetryConfig.custom()
                .maxAttempts(config.maxAttempts)
                .waitDuration(config.retryInterval)
                .retryOnException(this::isRetryable)
                .build());
  }

  /** 默认阈值：滑动窗口 10、失败率 50%、开路等待 10s、半开放行 3、最多 3 次尝试、500ms 退避。 */
  static Config defaultConfig() {
    return new Config(50.0f, 10, 5, Duration.ofSeconds(10), 3, 3, Duration.ofMillis(500));
  }

  @Override
  public String post(String url, Map<String, String> headers, String jsonBody) {
    // 内层重试（只重试瞬时/5xx），外层熔断（只统计最终失败）——重试风暴被熔断封顶。
    Supplier<String> retried =
        Retry.decorateSupplier(retry, () -> delegate.post(url, headers, jsonBody));
    return circuitBreaker.executeSupplier(retried);
  }

  private boolean isRetryable(Throwable t) {
    if (t instanceof CallNotPermittedException) {
      return false; // 被熔断拒绝不重试，交给上层降级
    }
    if (t instanceof RetriableHttpException) {
      return true; // 5xx / 网络瞬时，重试合理
    }
    return false;
  }

  /** 可由重试吸收的传输失败（5xx / 网络瞬时）。被 {@code ResilientLlmTransport} 之外的直连路径判定、包装后抛出。 */
  public static class RetriableHttpException extends RuntimeException {
    public RetriableHttpException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** 韧性阈值配置（默认值满足常见 provider；可按实例覆盖）。 */
  public record Config(
      float failureRateThreshold,
      int slidingWindowSize,
      int minimumNumberOfCalls,
      Duration waitDurationInOpenState,
      int permittedCallsInHalfOpen,
      int maxAttempts,
      Duration retryInterval) {}
}
