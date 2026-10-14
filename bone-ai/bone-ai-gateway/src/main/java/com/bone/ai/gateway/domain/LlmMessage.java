package com.bone.ai.gateway.domain;

/** 对话消息（role + content），对应 OpenAI chat 协议的一条 message。 */
public record LlmMessage(String role, String content) {

  public static LlmMessage system(String content) {
    return new LlmMessage("system", content);
  }

  public static LlmMessage user(String content) {
    return new LlmMessage("user", content);
  }
}
