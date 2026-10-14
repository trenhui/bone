package com.bone.ai.gateway.domain;

/**
 * LLM 调用门面：模型运行时的唯一出口契约。
 *
 * <p>Phase 0 只冻结契约——所有产品对模型的调用都必须经此接口，由 {@code bone-ai-gateway} 的应用服务统一收口；
 * 成本红线、审计、脱敏、配额等治理语义后续落在门面之后，调用方不感知具体 provider。
 */
public interface LlmClient {

  /**
   * 发起一次对话补全。
   *
   * @param request 模型请求（模型档位、消息、采样参数、成本红线信号）
   * @return 模型响应（内容、模型、结束原因、用量）
   */
  LlmResponse chat(LlmRequest request);
}
