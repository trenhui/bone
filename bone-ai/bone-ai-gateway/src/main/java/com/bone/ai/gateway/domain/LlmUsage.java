package com.bone.ai.gateway.domain;

/** 一次调用的 token 用量（计量与成本核算的地基）。 */
public record LlmUsage(int promptTokens, int completionTokens, int totalTokens) {

  public static LlmUsage empty() {
    return new LlmUsage(0, 0, 0);
  }
}
