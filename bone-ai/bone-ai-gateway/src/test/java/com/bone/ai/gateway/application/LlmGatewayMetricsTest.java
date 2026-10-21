package com.bone.ai.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.ai.gateway.domain.LlmClient;
import com.bone.ai.gateway.domain.LlmGatewayPolicies;
import com.bone.ai.gateway.domain.LlmMessage;
import com.bone.ai.gateway.domain.LlmRequest;
import com.bone.ai.gateway.domain.LlmResponse;
import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import com.bone.ai.gateway.domain.gateway.LlmResponseCache;
import com.bone.ai.gateway.domain.gateway.TenantProvider;
import com.bone.ai.gateway.domain.gateway.TenantQuotaGuard;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 网关可观测性 seam：{@link LlmMetrics} 在热路径各发射点的埋点验证（零依赖，Noop 默认不影响行为）。 */
class LlmGatewayMetricsTest {

  private final LlmClient client = mock(LlmClient.class);
  private final DomainEventPublisher publisher = mock(DomainEventPublisher.class);
  private final TenantProvider tenantProvider = mock(TenantProvider.class);
  private final RecordingMetrics metrics = new RecordingMetrics();

  @BeforeEach
  void setUp() {
    when(tenantProvider.currentTenantId()).thenReturn("42");
  }

  private LlmGatewayApplicationService service(LlmGatewayPolicies policies) {
    return new LlmGatewayApplicationService(client, publisher, tenantProvider, policies);
  }

  private LlmGatewayPolicies withMetrics(LlmGatewayPolicies base) {
    return new LlmGatewayPolicies(
        base.piiRedactor(),
        base.outputValidator(),
        base.responseCache(),
        base.quotaGuard(),
        base.injectionGuard(),
        metrics);
  }

  @Test
  void records_provider_success_latency_and_tokens() {
    when(client.chat(any()))
        .thenReturn(new LlmResponse("ok", "m", "stop", new LlmUsage(10, 5, 15)));

    service(withMetrics(LlmGatewayPolicies.permissive()))
        .invoke(LlmRequest.of("m", List.of(LlmMessage.user("hi")), "trace-1"));

    assertThat(metrics.providerCalls).hasSize(1);
    assertThat(metrics.providerCalls.get(0).tenantId()).isEqualTo("42");
    assertThat(metrics.providerCalls.get(0).model()).isEqualTo("m");
    assertThat(metrics.providerCalls.get(0).success()).isTrue();
    assertThat(metrics.providerCalls.get(0).latencyMs()).isGreaterThanOrEqualTo(0);
    assertThat(metrics.tokenCalls).hasSize(1);
    assertThat(metrics.tokenCalls.get(0).prompt()).isEqualTo(10);
    assertThat(metrics.tokenCalls.get(0).completion()).isEqualTo(5);
    assertThat(metrics.tokenCalls.get(0).total()).isEqualTo(15);
  }

  @Test
  void records_provider_failure_without_tokens() {
    when(client.chat(any())).thenThrow(new IllegalStateException("down"));

    assertThatThrownBy(
            () ->
                service(withMetrics(LlmGatewayPolicies.permissive()))
                    .invoke(LlmRequest.of("m", List.of(LlmMessage.user("hi")), "t")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(metrics.providerCalls).hasSize(1);
    assertThat(metrics.providerCalls.get(0).success()).isFalse();
    assertThat(metrics.tokenCalls).isEmpty();
  }

  @Test
  void records_short_circuited() {
    assertThatThrownBy(
            () ->
                service(withMetrics(LlmGatewayPolicies.permissive()))
                    .invoke(
                        LlmRequest.of("m", List.of(LlmMessage.user("hi"))).withRuleShortCircuit()))
        .isInstanceOf(BizException.class);

    assertThat(metrics.shortCircuited).containsExactly("42|m");
    verify(client, never()).chat(any());
  }

  @Test
  void records_cache_hit() {
    LlmResponseCache cache = mock(LlmResponseCache.class);
    when(cache.get(any()))
        .thenReturn(Optional.of(new LlmResponse("cached", "m", "stop", LlmUsage.empty())));
    LlmGatewayPolicies base = new LlmGatewayPolicies(null, null, cache, null, null);

    LlmResponse out =
        service(withMetrics(base)).invoke(LlmRequest.of("m", List.of(LlmMessage.user("hi")), "t"));

    assertThat(out.content()).isEqualTo("cached");
    assertThat(metrics.cacheHits).containsExactly("42|m");
    verify(client, never()).chat(any()); // 命中不触达模型
  }

  @Test
  void records_quota_exceeded() {
    TenantQuotaGuard quota = mock(TenantQuotaGuard.class);
    doThrow(new BizException(429, "quota exceeded", "AI_GATEWAY_QUOTA")).when(quota).acquire("42");
    LlmGatewayPolicies base = new LlmGatewayPolicies(null, null, null, quota, null);

    assertThatThrownBy(
            () ->
                service(withMetrics(base))
                    .invoke(LlmRequest.of("m", List.of(LlmMessage.user("hi")), "t")))
        .isInstanceOf(BizException.class);

    assertThat(metrics.quotaExceeded).containsExactly("42|m");
    verify(client, never()).chat(any()); // 超限不触达模型
  }

  /** 捕获式指标实现（测试替身）：记录各发射点调用。 */
  static class RecordingMetrics implements LlmMetrics {
    record Call(String tenantId, String model, boolean success, long latencyMs) {}

    record Tokens(String tenantId, String model, long prompt, long completion, long total) {}

    final List<Call> providerCalls = new ArrayList<>();
    final List<Tokens> tokenCalls = new ArrayList<>();
    final List<String> shortCircuited = new ArrayList<>();
    final List<String> cacheHits = new ArrayList<>();
    final List<String> quotaExceeded = new ArrayList<>();

    @Override
    public void recordShortCircuited(String tenantId, String model) {
      shortCircuited.add(tenantId + "|" + model);
    }

    @Override
    public void recordCacheHit(String tenantId, String model) {
      cacheHits.add(tenantId + "|" + model);
    }

    @Override
    public void recordQuotaExceeded(String tenantId, String model) {
      quotaExceeded.add(tenantId + "|" + model);
    }

    @Override
    public void recordProviderCall(String tenantId, String model, boolean success, long latencyMs) {
      providerCalls.add(new Call(tenantId, model, success, latencyMs));
    }

    @Override
    public void recordTokens(
        String tenantId, String model, long prompt, long completion, long total) {
      tokenCalls.add(new Tokens(tenantId, model, prompt, completion, total));
    }
  }
}
