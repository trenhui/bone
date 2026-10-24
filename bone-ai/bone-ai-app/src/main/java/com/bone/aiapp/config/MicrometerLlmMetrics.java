package com.bone.aiapp.config;

import com.bone.ai.gateway.domain.gateway.LlmMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;

/**
 * 网关指标 Micrometer 适配器（2026-10-24 企业复杂场景诊断 P2-1 落地）：把 {@link LlmMetrics} 发射点接到度量框架。
 *
 * <p><b>为什么接在这里而不是 bone-ai-gateway 内</b>：库形态模块零度量依赖（Noop 默认）；宿主 {@code bone-ai-app} 已依赖 {@code
 * spring-boot-starter-actuator}（Micrometer 随附），以一个 {@code @Bean} 覆盖即接通全部发射点——热路径埋点早在 网关闭环，此处只做「发射 →
 * 指标」的落地，零新增必选依赖（AGENTS 依赖审批面无新增）。
 *
 * <p><b>指标口径</b>（与 OTel {@code gen_ai.*} 映射表版本化 v1 对齐，见详设 §3.2；名称用点分层，勿硬编码进告警规则常量）：
 *
 * <ul>
 *   <li>{@code bone.ai.gateway.provider.calls}（counter，tenant/model/success）——错误率与调用量；
 *   <li>{@code bone.ai.gateway.provider.latency}（timer）——P99 延迟（§4 #15 网关延时增量）；
 *   <li>{@code bone.ai.gateway.first_token.latency}（timer）——TTFT，流式体验核心；
 *   <li>{@code bone.ai.gateway.tokens}（counter，kind=prompt/completion/total）——成本核算核心；
 *   <li>{@code bone.ai.gateway.short_circuited} / {@code cache.hit} / {@code
 *       quota.exceeded}（counter）——治理面命中率。
 * </ul>
 *
 * <p>提示词 / 响应内容默认不采集（合规口径不变）——本适配器只承载计数与时长，无内容标签。
 */
public final class MicrometerLlmMetrics implements LlmMetrics {

  private static final String PREFIX = "bone.ai.gateway.";

  private final MeterRegistry registry;

  public MicrometerLlmMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordShortCircuited(String tenantId, String model) {
    registry.counter(PREFIX + "short_circuited", tenantModel(tenantId, model)).increment();
  }

  @Override
  public void recordCacheHit(String tenantId, String model) {
    registry.counter(PREFIX + "cache.hit", tenantModel(tenantId, model)).increment();
  }

  @Override
  public void recordQuotaExceeded(String tenantId, String model) {
    registry.counter(PREFIX + "quota.exceeded", tenantModel(tenantId, model)).increment();
  }

  @Override
  public void recordProviderCall(String tenantId, String model, boolean success, long latencyMs) {
    Tags tags = tenantModel(tenantId, model).and("success", String.valueOf(success));
    registry.counter(PREFIX + "provider.calls", tags).increment();
    registry
        .timer(PREFIX + "provider.latency", tenantModel(tenantId, model))
        .record(Duration.ofMillis(latencyMs));
  }

  @Override
  public void recordTokens(
      String tenantId, String model, long promptTokens, long completionTokens, long totalTokens) {
    Tags base = tenantModel(tenantId, model);
    registry.counter(PREFIX + "tokens", base.and("kind", "prompt")).increment(promptTokens);
    registry.counter(PREFIX + "tokens", base.and("kind", "completion")).increment(completionTokens);
    registry.counter(PREFIX + "tokens", base.and("kind", "total")).increment(totalTokens);
  }

  @Override
  public void recordFirstToken(String tenantId, String model, long latencyMs) {
    registry
        .timer(PREFIX + "first_token.latency", tenantModel(tenantId, model))
        .record(Duration.ofMillis(latencyMs));
  }

  /** 标签兜底：Micrometer 禁 null 标签值（未解析租户 / 未声明模型时以 unknown 聚合，不静默丢维度）。 */
  private static Tags tenantModel(String tenantId, String model) {
    return Tags.of(
        "tenant", tenantId == null ? "unknown" : tenantId,
        "model", model == null ? "unknown" : model);
  }
}
