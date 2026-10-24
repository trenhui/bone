package com.bone.aiapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Micrometer 适配器（P2-1）：发射点 → 指标落地，含 null 标签兜底与 token 三维度拆分。
 *
 * <p>锁两条防回归契约：① 每个 {@code LlmMetrics} 方法都有对应计数器 / 计时器（零发射点死缝——网关闭环埋点、 宿主落地指标， 两端都必须有消费方）；② 租户 /
 * 模型缺失时不抛 NPE（Micrometer 禁 null 标签值），以 {@code unknown} 聚合。
 */
class MicrometerLlmMetricsTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerLlmMetrics metrics = new MicrometerLlmMetrics(registry);

  @Test
  void provider_call_records_counter_and_timer() {
    metrics.recordProviderCall("7", "qwen3.5-27b", true, 120);
    metrics.recordProviderCall("7", "qwen3.5-27b", false, 80);

    assertThat(
            registry
                .get("bone.ai.gateway.provider.calls")
                .tag("tenant", "7")
                .tag("success", "true")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(registry.get("bone.ai.gateway.provider.latency").timer().count()).isEqualTo(2);
  }

  @Test
  void tokens_split_by_kind() {
    metrics.recordTokens("7", "m", 100, 20, 120);
    assertThat(registry.get("bone.ai.gateway.tokens").tag("kind", "prompt").counter().count())
        .isEqualTo(100.0);
    assertThat(registry.get("bone.ai.gateway.tokens").tag("kind", "completion").counter().count())
        .isEqualTo(20.0);
    assertThat(registry.get("bone.ai.gateway.tokens").tag("kind", "total").counter().count())
        .isEqualTo(120.0);
  }

  @Test
  void first_token_latency_recorded() {
    metrics.recordFirstToken("7", "m", 45);
    assertThat(registry.get("bone.ai.gateway.first_token.latency").timer().count()).isEqualTo(1);
  }

  @Test
  void governance_hits_recorded() {
    metrics.recordShortCircuited("7", "m");
    metrics.recordCacheHit("7", "m");
    metrics.recordQuotaExceeded("7", "m");
    assertThat(registry.get("bone.ai.gateway.short_circuited").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("bone.ai.gateway.cache.hit").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("bone.ai.gateway.quota.exceeded").counter().count()).isEqualTo(1.0);
  }

  @Test
  void null_tenant_or_model_falls_back_to_unknown() {
    // Micrometer 禁 null 标签值：未解析租户不得让埋点抛 NPE（埋点失败会反噬业务路径）
    metrics.recordProviderCall(null, null, true, 10);
    assertThat(
            registry
                .get("bone.ai.gateway.provider.calls")
                .tag("tenant", "unknown")
                .tag("model", "unknown")
                .counter()
                .count())
        .isEqualTo(1.0);
  }
}
