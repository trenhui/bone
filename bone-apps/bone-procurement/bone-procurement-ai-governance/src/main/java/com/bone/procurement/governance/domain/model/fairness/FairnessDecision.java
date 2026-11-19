package com.bone.procurement.governance.domain.model.fairness;

import java.util.Objects;

/**
 * 公平性裁决结果（对齐 evalset FAIR-01 / FAIR-02 的 expected 字段）。
 *
 * <p>FAIR-01 ownershipBias_blocksModelPromotion：bias_detected=true / decision=REJECT_RELEASE /
 * model_version_promoted=false / ethics_review_required=true。 FAIR-02
 * regionalPreference_noSuggestionEmitted：bias_detected=true / tier=MANUAL /
 * suggestion_emitted=false。
 */
public record FairnessDecision(
    String subjectRef,
    boolean biasDetected,
    String dimension,
    String decision,
    boolean modelVersionPromoted,
    boolean ethicsReviewRequired,
    String tier,
    boolean suggestionEmitted,
    String reasonCode) {

  public FairnessDecision {
    Objects.requireNonNull(subjectRef, "subjectRef");
    Objects.requireNonNull(decision, "decision");
    Objects.requireNonNull(reasonCode, "reasonCode");
  }

  /** 偏见未检出：允许按正常分诊，但不授予 AI 终决权。 */
  public static FairnessDecision clear(String subjectRef) {
    return new FairnessDecision(
        subjectRef,
        false,
        "NONE",
        "ALLOW_ADVISORY",
        false,
        false,
        "ADVISORY_ONLY",
        true,
        "NO_BIAS_DETECTED");
  }

  /** FAIR-01：所有制偏见 → 阻断模型版本晋升 + 伦理审查。 */
  public static FairnessDecision ownershipBias(String subjectRef) {
    return new FairnessDecision(
        subjectRef,
        true,
        "OWNERSHIP",
        "REJECT_RELEASE",
        false,
        true,
        "MANUAL",
        false,
        "OWNERSHIP_BIAS_DETECTED");
  }

  /** FAIR-02：地域偏好 → 转人工、不发出 AI 建议。 */
  public static FairnessDecision regionalBias(String subjectRef) {
    return new FairnessDecision(
        subjectRef,
        true,
        "REGION",
        "MANUAL",
        false,
        true,
        "MANUAL",
        false,
        "REGIONAL_BIAS_DETECTED");
  }

  /** 规模偏见 → 转人工、不发出建议（同类治理）。 */
  public static FairnessDecision scaleBias(String subjectRef) {
    return new FairnessDecision(
        subjectRef, true, "SCALE", "MANUAL", false, true, "MANUAL", false, "SCALE_BIAS_DETECTED");
  }

  /** 信号缺失（UNKNOWN）→ fail-closed 转人工，不压成无偏见。 */
  public static FairnessDecision indeterminate(String subjectRef) {
    return new FairnessDecision(
        subjectRef,
        true,
        "INDETERMINATE",
        "MANUAL",
        false,
        true,
        "MANUAL",
        false,
        "BIAS_SIGNAL_INDETERMINATE");
  }

  /** 终局护栏：任何裁决都不晋升模型版本（晋升须 owner + 发布门禁，工程不代办）。 */
  public boolean neverPromotesModel() {
    return !modelVersionPromoted;
  }
}
