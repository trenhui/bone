package com.bone.ai.gateway.domain.gateway;

/**
 * 提示注入防护端口（OWASP LLM01:2025，业界护栏第一关）。
 *
 * <p><b>为什么在网关做</b>：提示注入是 LLM 应用头号风险（OWASP Top 10 for LLM 第 1 位），攻击在用户侧 prompt 里夹带 「忽略之前的指令 /
 * 你现在扮演…」这类指令，企图劫持 system 语境。编排层把判定文案直接喂给模型，天然是注入载体； 在门面这个唯一入站边界统一拦截，所有场景无需各自记得防护。
 *
 * <p><b>与 PII 脱敏的分工</b>：脱敏管「数据不出域」（LLM02），本端口管「指令不被劫持」（LLM01），两者正交。
 *
 * <p><b>默认实现是启发式、且默认关闭</b>：关键词规则必然有误伤（保险条款文本里偶含「忽略 / 系统提示」等词）， 故装配层默认不启用；需要语义级检测时以 {@code @Bean}
 * 覆盖为本端口的模型分类器 / 云端护栏实现（属外部依赖，L3）。
 *
 * <p>只应对 <b>user</b> 角色消息检测（system / assistant 是本地可信内容，不应被当注入拦）。
 */
public interface PromptInjectionGuard {

  /**
   * 检测单次用户输入是否疑似提示注入；疑似抛 {@code AI_GATEWAY_INJECTION_SUSPECTED}。
   *
   * @param userMessage 用户消息原文（应在脱敏之前检测，脱敏可能改写指令特征）
   * @param traceId 全链追踪 ID（用于错误定位）
   */
  void check(String userMessage, String traceId);
}
