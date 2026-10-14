package com.bone.ai.gateway.domain;

import java.util.List;

/**
 * 模型请求。
 *
 * <p>{@code shortCircuited} 是成本红线信号：调用方（编排 / 规则节点）已由确定性规则命中时置 true，门面将拒绝发起真实 LLM 调用——「规则命中不调
 * LLM」由平台强制，不依赖调用方自觉。
 */
public record LlmRequest(
    String model,
    List<LlmMessage> messages,
    Double temperature,
    Integer maxTokens,
    boolean jsonResponse,
    boolean shortCircuited) {

  public LlmRequest {
    messages = (messages == null) ? List.of() : List.copyOf(messages);
  }

  public static LlmRequest of(String model, List<LlmMessage> messages) {
    return new LlmRequest(model, messages, null, null, false, false);
  }

  /** 派生一个带成本红线信号的请求副本（规则命中 ⇒ 禁止调 LLM）。 */
  public LlmRequest withRuleShortCircuit() {
    return new LlmRequest(model, messages, temperature, maxTokens, jsonResponse, true);
  }
}
