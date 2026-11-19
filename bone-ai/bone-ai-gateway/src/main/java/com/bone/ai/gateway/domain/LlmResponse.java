package com.bone.ai.gateway.domain;

/** 模型响应：内容 + 元信息 + 用量 + provider 原始 requestId（同一次调用的计费/审计凭据）。 */
public record LlmResponse(
    String content, String model, String finishReason, LlmUsage usage, String requestId) {

  /**
   * 兼容构造器：历史调用点与替身/缓存对象没有 provider requestId，显式置 null，绝不伪造一个。 保留它让全仓既有的 4
   * 参构造调用点零改动（也避免把「没取到」误报成「取到了」）。
   */
  public LlmResponse(String content, String model, String finishReason, LlmUsage usage) {
    this(content, model, finishReason, usage, null);
  }
}
