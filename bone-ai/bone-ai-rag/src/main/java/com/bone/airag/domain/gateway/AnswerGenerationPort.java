package com.bone.airag.domain.gateway;

/**
 * 答案生成端口（RAG 闭环中「G」的唯一入口）。
 *
 * <p><b>为什么是端口而不是直接依赖网关</b>：`bone-ai-gateway` 与 `bone-ai-rag` 互不依赖（网关管治理链，rag 管检索与
 * 接地）。把生成抽象成端口后，rag 的「检索 → 装配 → 生成 → 接地校验」闭环可以<b>完整闭环并单测</b>（用桩实现），
 * 真接入只需在组合根写一个适配器把本端口转调网关：
 *
 * <pre>{@code
 * // 组合根（bone-ai-app，已同时依赖 gateway 与 rag）：
 * @Bean
 * AnswerGenerationPort answerGenerationPort(LlmGatewayApplicationService gateway) {
 *   return (system, user) ->
 *       gateway.invoke(LlmRequest.of(model, List.of(
 *               LlmMessage.system(system), LlmMessage.user(user))))
 *           .content();
 * }
 * }</pre>
 *
 * <p><b>实现方职责</b>：只负责「调模型拿原文」。治理链（红线 / 路由 / 配额 / 注入检测 / PII 脱敏 / 缓存）、重试与降级
 * 一律由网关承担——不要在适配器里重做治理，否则两套策略会漂移。
 *
 * <p>返回值是模型<b>原文</b>：接地校验与拒答决策由 {@code RagAnswerService} 统一负责，适配器不得自行裁剪或改写内容
 * （否则守卫校验的就不是模型真实输出了）。
 */
public interface AnswerGenerationPort {

  /**
   * 生成答案。
   *
   * @param systemPrompt 系统提示（角色 + 强制引用 + 不可编造 + 数据/指令分离）
   * @param userPrompt 用户提示（数据围栏包裹的编号上下文 + 原始问题）
   * @return 模型输出原文（未校验、未脱敏）
   */
  String generate(String systemPrompt, String userPrompt);
}
