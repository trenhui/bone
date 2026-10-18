package com.bone.ai.gateway.domain;

import java.util.Set;

/**
 * 对话消息（role + content），对应 OpenAI chat 协议的一条 message。
 *
 * <p><b>fail-fast 不变量（2026-10-18 补）</b>：role 限定在 OpenAI 协议受控集合内、content 不得为空—— 非法值在构造期即拒绝， 而不是带到
 * provider 才拿回一个语义模糊的 400（既浪费一次网络往返，错误信息也指不回调用点）。
 */
public record LlmMessage(String role, String content) {

  /** OpenAI chat 协议受控角色集合（多轮对话的助手回包也走此契约）。 */
  private static final Set<String> ROLES = Set.of("system", "user", "assistant");

  public LlmMessage {
    if (role == null || !ROLES.contains(role)) {
      throw new IllegalArgumentException("message role 须为 system/user/assistant 之一: " + role);
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("message content 不得为空（role=" + role + "）");
    }
  }

  public static LlmMessage system(String content) {
    return new LlmMessage("system", content);
  }

  public static LlmMessage user(String content) {
    return new LlmMessage("user", content);
  }

  /** 助手回包（多轮对话上下文回填用）。 */
  public static LlmMessage assistant(String content) {
    return new LlmMessage("assistant", content);
  }
}
