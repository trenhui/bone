package com.bone.procurement.governance.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 人工终决 + override 回流台账条目（EU AI Act Art.14 / ISO 42001 A.6.2.4 / NIST AI RMF MANAGE）。
 *
 * <p>「有意义的监督」需三要素同时成立：能力（reviewer 有权限）、时间（记录耗时）、权威（override 被接受并被回流为评测增补标记）。本记录把 AI 建议与人工终决 分开存储：AI
 * 结论仅作输入，finalDecision 由人工给出；当二者不一致时 {@link #isOverride()} 为真并触发评测增补标记。
 *
 * <p>刻意不保存 AI 的 output 原文（PII 禁入），只存结论摘要与置信度。
 */
public record HumanFinalDecision(
    String decisionId,
    CapabilityCode capabilityCode,
    String subjectRef,
    String aiConclusion,
    double aiConfidence,
    String finalConclusion,
    String reviewer,
    String reason,
    long reviewDurationMs,
    boolean overrideAccepted,
    boolean evaluationFeedbackRaised,
    Instant decidedAt) {

  public HumanFinalDecision {
    Objects.requireNonNull(decisionId, "decisionId");
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    Objects.requireNonNull(subjectRef, "subjectRef");
    Objects.requireNonNull(finalConclusion, "finalConclusion");
    Objects.requireNonNull(reviewer, "reviewer");
    if (reviewer.isBlank()) {
      throw new IllegalArgumentException("人工终决必须有具名复核人");
    }
    if (finalConclusion.isBlank()) {
      throw new IllegalArgumentException("人工终决结论不得为空");
    }
    if (reason == null) {
      reason = "";
    }
    if (reviewDurationMs < 0) {
      throw new IllegalArgumentException("复核耗时不得为负");
    }
    Objects.requireNonNull(decidedAt, "decidedAt");
  }

  /** AI 结论与人工终决不一致即为一次推翻，必须留痕并回流评测。 */
  public boolean isOverride() {
    return aiConclusion != null && !aiConclusion.equals(finalConclusion);
  }

  /** 终局护栏：任何决策的终决权都在人工侧，AI 只有建议。 */
  public boolean humanHasFinalAuthority() {
    return true;
  }
}
