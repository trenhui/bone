package com.bone.ai.gateway.domain.gateway;

/**
 * 结构化输出校验端口（{@code jsonResponse=true} 时的响应契约兜底）。
 *
 * <p><b>为什么网关要校验而不是信 provider</b>：即便请求带 {@code response_format=json_object}，主流 provider 也不保证 100%
 * 合法 JSON（截断于 max_tokens、偶发夹带 markdown 代码块围栏都是真实故障）。下游判定逻辑按 JSON 解析，一旦格式坏了就是
 * 「下游解析异常」这种无归因故障；在网关挡住并给出稳定码，故障定位从「某业务方法 NPE」变成「模型输出不合规」。
 *
 * <p>业界对位：OpenAI Structured Outputs / LiteLLM 的 response_format 校验、Portkey 的 output guardrails。
 *
 * <p>默认实现只校验「合法 JSON」；需要 JSON Schema 强校验时以 {@code @Bean} 覆盖（schema 校验库属新增依赖，AGENTS L3）。
 */
public interface StructuredOutputValidator {

  /**
   * 校验模型输出；不合规抛业务异常（由调用方转重试 / HITL / 确定性路径）。
   *
   * @param content 模型返回正文
   * @param traceId 全链追踪 ID（用于错误定位）
   */
  void validate(String content, String traceId);
}
