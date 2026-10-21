package com.bone.ai.gateway.domain.gateway;

/**
 * 输出内容安全护栏端口（OWASP LLM05 / LLM09：模型产出不得含不可接受内容）。
 *
 * <p>与输入侧 {@link PromptInjectionGuard}（LLM01，防指令被劫持）形成「输入 / 输出治理对称」：本端口检查<b>模型产出</b>——
 * 典型命中是提示注入被绕过、模型遵从了恶意指令（越狱成功标志），或外溢危险能力内容。在门面这个唯一出站边界统一拦截， 所有场景无需各自记得校验模型说了什么。
 *
 * <p>与 {@link
 * com.bone.ai.gateway.domain.gateway.PiiRedactor}（LLM02，数据不出域）正交：脱敏管「不含个人信息」，本端口管「不含不可接受内容」。
 *
 * <p><b>默认启发式且默认关闭</b>：关键词规则有误伤风险，装配层默认不启用；需要语义级 / 云端内容安全时以 {@code @Bean} 覆盖（属外部依赖，L3）。
 */
public interface ContentSafetyGuard {

  /**
   * 检测模型产出是否含不可接受内容；命中抛 {@code AI_GATEWAY_CONTENT_UNSAFE}。
   *
   * @param modelOutput 模型原始产出（出站前检测）
   * @param traceId 全链追踪 ID（用于错误定位）
   */
  void check(String modelOutput, String traceId);
}
