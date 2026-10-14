package com.bone.ai.gateway.domain;

/** 模型响应：内容 + 元信息 + 用量。 */
public record LlmResponse(String content, String model, String finishReason, LlmUsage usage) {}
