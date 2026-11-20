package com.bone.procurement.contract.domain.model.review;

import java.util.List;

/**
 * AI 节点给出的审查建议（仅建议，不直接采纳）。
 *
 * <p>{@code aiAvailable} 由 LLM 节点自报，缺省必须为 {@link AiParticipation#UNKNOWN}（fail-closed）； {@code
 * modelVersionSnapshot} 仅作版本快照，不构成 AI 参与证据。
 */
public record ReviewSuggestion(
    AiParticipation aiAvailable,
    RiskLevel riskLevel,
    List<RiskItem> riskItems,
    double confidence,
    boolean negativeHit,
    String modelVersionSnapshot) {

  public ReviewSuggestion {
    if (aiAvailable == null) {
      aiAvailable = AiParticipation.UNKNOWN;
    }
    if (riskLevel == null) {
      riskLevel = RiskLevel.UNKNOWN;
    }
    riskItems = riskItems == null ? List.of() : List.copyOf(riskItems);
  }
}
