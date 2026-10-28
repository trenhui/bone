package com.bone.claim.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 评估网关端口（出站，对接 bone-ai C5 LLM 网关 + C6 受控 RAG）。
 *
 * <p>{@link #assess} 在网关不可用时抛 {@link AiGatewayUnavailableException}——决策执行器据此对 LLM 节点
 * 降级纯规则路径（FALLBACK），符合"AI 不可用→降级而非失败"纪律。
 */
public interface AiGatewayPort {

  /** AI 评估：建议拒赔 + 置信度（>=0.85 方可直出，<0.7 强制人工，见 {@code DecisionTriage}）。 */
  AiAssessment assess(ClaimContextPort.ClaimContextView ctx);

  /**
   * AI 评估结果。
   *
   * <p>{@code rejectReasons} 为结构化拒赔原因码（白名单枚举，非自由文本）—— 用于"不利决定须可解释"的合规要求 （保险/PIPL），经 {@code
   * RejectReasonCodes} 校验后落决策轨迹供审计与申诉。 自由文本原因视为不可信，绝不允许模型臆造。
   */
  record AiAssessment(
      boolean available,
      boolean rejectSuggested,
      BigDecimal confidence,
      List<String> rejectReasons) {
    /** 向后兼容：无原因时复用 3 参构造。 */
    public AiAssessment(boolean available, boolean rejectSuggested, BigDecimal confidence) {
      this(available, rejectSuggested, confidence, List.of());
    }
  }
}
