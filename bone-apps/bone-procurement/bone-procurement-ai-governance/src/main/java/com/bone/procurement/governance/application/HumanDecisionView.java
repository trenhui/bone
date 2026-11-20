package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;

/** 人工终决只读投影：不含 AI output 原文（PII 禁入）。 */
public record HumanDecisionView(
    String decisionId,
    String capabilityCode,
    String subjectRef,
    String finalConclusion,
    String reviewer,
    boolean override,
    boolean evaluationFeedbackRaised,
    boolean humanHasFinalAuthority) {

  public static HumanDecisionView from(HumanFinalDecision decision) {
    return new HumanDecisionView(
        decision.decisionId(),
        decision.capabilityCode().value(),
        decision.subjectRef(),
        decision.finalConclusion(),
        decision.reviewer(),
        decision.isOverride(),
        decision.evaluationFeedbackRaised(),
        decision.humanHasFinalAuthority());
  }
}
