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
 */
public record LlmRequest(
    String model,
    List<LlmMessage> messages,
    Double temperature,
    Integer maxTokens,
    boolean jsonResponse,
    boolean shortCircuited,
    String traceId) {

  public LlmRequest {
    messages = (messages == null) ? List.of() : List.copyOf(messages);
  }

  public static LlmRequest of(String model, List<LlmMessage> messages) {
    return new LlmRequest(model, messages, null, null, false, false, null);
  }

  /** 带全链追踪 ID 的构造（编排 / 产品侧优先使用）。 */
  public static LlmRequest of(String model, List<LlmMessage> messages, String traceId) {
    return new LlmRequest(model, messages, null, null, false, false, traceId);
  }

  /** 派生一个带成本红线信号的请求副本（规则命中 ⇒ 禁止调 LLM）。 */
  public LlmRequest withRuleShortCircuit() {
    return new LlmRequest(model, messages, temperature, maxTokens, jsonResponse, true, traceId);
  }

  /** 派生一个绑定全链追踪 ID 的请求副本（保留其余参数）。 */
  public LlmRequest withTraceId(String traceId) {
    return new LlmRequest(
        model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId);
  }

  /** 生效的追踪 ID：调用方提供优先，缺省生成（单次调用的最低可追溯性）。 */
  public String effectiveTraceId() {
    return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
  }
}
