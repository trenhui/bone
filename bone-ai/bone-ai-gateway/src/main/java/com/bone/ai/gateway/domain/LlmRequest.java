package com.bone.ai.gateway.domain;

import java.util.List;
import java.util.UUID;

/**
 * 模型请求。
 *
 * <p>{@code shortCircuited} 是成本红线信号：调用方（编排 / 规则节点）已由确定性规则命中时置 true，门面将拒绝发起真实 LLM 调用——「规则命中不调
 * LLM」由平台强制，不依赖调用方自觉。
 *
 * <p>{@code traceId} 贯穿一次业务动作全链（回放关联键，2026-10-17 复核修复）：由调用方提供，缺省才由网关生成—— 否则每次调用各生成一个
 * UUID，证据快照无法按业务动作拼回全链（详设 §2.1 演进项①）。
 *
 * <p>{@code scenario} 是业务场景标签（详设 §2.1 演进项：模型档位路由）：调用方声明场景，网关经 {@code ModelRouter} 把场景映射到模型档位——
 * 简单场景走廉价模型、复杂场景走强模型，把「该用多贵的模型」从调用方硬编码收敛为平台可配置策略。缺省（null / 空串）= 不参与路由，按请求模型原样调用。
 */
public record LlmRequest(
    String model,
    List<LlmMessage> messages,
    Double temperature,
    Integer maxTokens,
    boolean jsonResponse,
    boolean shortCircuited,
    String traceId,
    String scenario) {

  /**
   * 兼容旧调用点：未声明业务场景（{@code scenario = null} = 不参与模型档位路由）。
   *
   * <p>2026-10-21 补齐：{@code scenario} 是后加的路由维度，若只保留 8 参规范构造器，全部既有调用点都要跟着改签名——用重载承接，
   * 新增路由维度不再波及其它调用点。
   */
  public LlmRequest(
      String model,
      List<LlmMessage> messages,
      Double temperature,
      Integer maxTokens,
      boolean jsonResponse,
      boolean shortCircuited,
      String traceId) {
    this(model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId, null);
  }

  public LlmRequest {
    messages = (messages == null) ? List.of() : List.copyOf(messages);
    // fail-fast：参数不变量在边界校验，而非带到 provider 才拿回语义模糊的 400
    if (temperature != null && (temperature < 0 || temperature > 2)) {
      throw new IllegalArgumentException("temperature 须在 [0,2]（OpenAI 兼容协议范围）: " + temperature);
    }
    if (maxTokens != null && maxTokens <= 0) {
      throw new IllegalArgumentException("maxTokens 须为正整数: " + maxTokens);
    }
  }

  public static LlmRequest of(String model, List<LlmMessage> messages) {
    return new LlmRequest(model, messages, null, null, false, false, null, null);
  }

  /** 带全链追踪 ID 的构造（编排 / 产品侧优先使用）。 */
  public static LlmRequest of(String model, List<LlmMessage> messages, String traceId) {
    return new LlmRequest(model, messages, null, null, false, false, traceId, null);
  }

  /** 派生一个带成本红线信号的请求副本（规则命中 ⇒ 禁止调 LLM）。 */
  public LlmRequest withRuleShortCircuit() {
    return new LlmRequest(
        model, messages, temperature, maxTokens, jsonResponse, true, traceId, scenario);
  }

  /** 派生一个绑定全链追踪 ID 的请求副本（保留其余参数）。 */
  public LlmRequest withTraceId(String traceId) {
    return new LlmRequest(
        model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId, scenario);
  }

  /**
   * 派生一个改写模型的请求副本（级联升档 / 降备援换模型，保留其余参数）。
   *
   * <p>2026-10-21 补齐：CascadeLlmClient（级联升档）与 FallbackLlmClient（降备援）的换模型调用点已就位， 仅缺本派生方法——记录（record）无
   * setter，缺此方法则两处调用无法编译。
   */
  public LlmRequest withModel(String model) {
    return new LlmRequest(
        model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId, scenario);
  }

  /**
   * 派生一个声明业务场景的请求副本（模型档位路由，保留其余参数）。
   *
   * <p>2026-10-21 补齐：{@code ModelRouter} 端口与 {@code ConfigDrivenModelRouter} 实现已就位，仅缺本派生方法与 {@code
   * scenario} 维度——缺此方法则路由场景无从声明，配置驱动的档位路由形同虚设。
   */
  public LlmRequest withScenario(String scenario) {
    return new LlmRequest(
        model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId, scenario);
  }

  /** 生效的追踪 ID：调用方提供优先，缺省生成（单次调用的最低可追溯性）。 */
  public String effectiveTraceId() {
    return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
  }
}
