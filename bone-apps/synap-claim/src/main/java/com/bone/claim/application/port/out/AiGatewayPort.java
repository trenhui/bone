package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * AI 评估网关端口（出站，对接 bone-ai C5 LLM 网关 + C6 受控 RAG）。
 *
 * <p>{@link #assess} 在网关不可用时抛 {@link AiGatewayUnavailableException}——决策执行器据此对 LLM 节点
 * 降级纯规则路径（FALLBACK），符合"AI 不可用→降级而非失败"纪律。
 */
public interface AiGatewayPort {

  /** AI 评估：建议拒赔 + 置信度（>=0.85 方可直出，<0.7 强制人工，见 {@code DecisionTriage}）。 */
  AiAssessment assess(ClaimContextPort.ClaimContextView ctx);

  record AiAssessment(boolean available, boolean rejectSuggested, BigDecimal confidence) {}
}
