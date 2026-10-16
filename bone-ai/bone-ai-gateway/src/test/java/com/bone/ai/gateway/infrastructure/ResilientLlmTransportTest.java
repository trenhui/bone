package com.bone.ai.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.infrastructure.ResilientLlmTransport.Config;
import com.bone.ai.gateway.infrastructure.ResilientLlmTransport.RetriableHttpException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ResilientLlmTransport：熔断防重试风暴 + 重试只吸收 5xx/网络瞬时（业界 provider 韧性）。 */
class ResilientLlmTransportTest {

  private CircuitBreakerRegistry registry;
  private Config fastConfig;

  @BeforeEach
  void setUp() {
    registry = CircuitBreakerRegistry.ofDefaults();
    fastConfig = new Config(50.0f, 10, 3, Duration.ofMillis(50), 3, 3, Duration.ofMillis(1));
  }

  @Test
  void retries_transient_5xx_up_to_max_attempts() {
    AtomicInteger calls = new AtomicInteger();
    LlmHttpTransport flaky =
        (url, headers, body) -> {
          if (calls.incrementAndGet() < 3) {
            throw new RetriableHttpException("HTTP 503", null);
          }
          return "ok";
        };
    ResilientLlmTransport transport = new ResilientLlmTransport(flaky, registry, fastConfig);

    String result = transport.post("u", Map.of(), "");

    assertThat(result).isEqualTo("ok");
    assertThat(calls.get()).isEqualTo(3); // 2 次重试 + 1 成功
  }

  @Test
  void does_not_retry_client_4xx() {
    AtomicInteger calls = new AtomicInteger();
    LlmHttpTransport badRequest =
        (url, headers, body) -> {
          calls.incrementAndGet();
          throw new IllegalStateException("HTTP 400");
        };
    ResilientLlmTransport transport = new ResilientLlmTransport(badRequest, registry, fastConfig);

    assertThatThrownBy(() -> transport.post("u", Map.of(), ""))
        .isInstanceOf(IllegalStateException.class);
    assertThat(calls.get()).isEqualTo(1); // 4xx 不重试
  }

  @Test
  void opens_circuit_after_failure_rate_threshold() {
    AtomicInteger calls = new AtomicInteger();
    LlmHttpTransport alwaysDown =
        (url, headers, body) -> {
          calls.incrementAndGet();
          throw new RetriableHttpException("HTTP 500", null);
        };
    ResilientLlmTransport transport = new ResilientLlmTransport(alwaysDown, registry, fastConfig);

    CircuitBreaker cb = registry.circuitBreaker(ResilientLlmTransport.LLM_CIRCUIT);

    // 灌入失败直到断路器 OPEN：minimumNumberOfCalls=3，每次 post 内部已重试耗尽；前 3 次统计失败，达到 50% 阈值后 OPEN
    int guard = 0;
    while (cb.getState() != CircuitBreaker.State.OPEN && guard++ < 10) {
      try {
        transport.post("u", Map.of(), "");
      } catch (RetriableHttpException | CallNotPermittedException ignored) {
        // 重试耗尽或已熔断，均属预期失败，继续灌入
      }
    }
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // OPEN 后：后续调用直接被熔断拒绝，不再触碰 delegate
    int before = calls.get();
    assertThatThrownBy(() -> transport.post("u", Map.of(), ""))
        .isInstanceOf(CallNotPermittedException.class);
    assertThat(calls.get()).isEqualTo(before);
  }

  @Test
  void stays_closed_on_recoverable_success() {
    AtomicInteger calls = new AtomicInteger();
    LlmHttpTransport ok = (url, headers, body) -> "ok-" + calls.incrementAndGet();
    ResilientLlmTransport transport = new ResilientLlmTransport(ok, registry, fastConfig);

    for (int i = 0; i < 6; i++) {
      transport.post("u", Map.of(), "");
    }
    CircuitBreaker cb = registry.circuitBreaker(ResilientLlmTransport.LLM_CIRCUIT);
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    assertThat(calls.get()).isEqualTo(6);
  }
}
