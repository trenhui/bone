package com.bone.procurement.governance.adapter.web.controller;

import com.bone.procurement.governance.application.RecordHumanDecisionCommand;
import jakarta.validation.constraints.NotBlank;

/** 人工终决请求体：复核人不接受自报，由登录态注入。 */
public record HumanDecisionRequest(
    String decisionId,
    @NotBlank String capabilityCode,
    @NotBlank String subjectRef,
    String aiConclusion,
    double aiConfidence,
    @NotBlank String finalConclusion,
    String reason,
    long reviewDurationMs) {

  public RecordHumanDecisionCommand toCommand() {
    return new RecordHumanDecisionCommand(
        decisionId,
        capabilityCode,
        subjectRef,
        aiConclusion,
        aiConfidence,
        finalConclusion,
        reason,
        reviewDurationMs);
  }
}
