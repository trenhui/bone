package com.bone.ai.gateway.domain;

import java.util.function.Consumer;

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

  /**
   * 流式对话补全（默认缓冲兜底：无真流式能力的客户端把整段响应作为单个 {@link LlmStreamEvent.Token} 下发后接 {@link
   * LlmStreamEvent.Done}）。
   *
   * <p>真实流式由支持 {@code StreamingLlmTransport} 的客户端覆盖（如 {@code OpenAiCompatibleClient} 解析 SSE）。
   * 调用方统一用流式 API，是否真流式取决于底层传输——切换 provider / 传输零改动。
   *
   * @param request 模型请求
   * @param sink 事件接收器（Token / Done / Error）
   */
  default void streamChat(LlmRequest request, Consumer<LlmStreamEvent> sink) {
    LlmResponse r = chat(request);
    sink.accept(LlmStreamEvent.token(r.content()));
    sink.accept(LlmStreamEvent.done(r.model(), r.finishReason(), r.usage()));
  }
}
