package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.OversightSignal;
import java.time.Instant;

/** 人类监督有效性只读投影：只含判词、原因码与真实计数，不回传 reason/aiConclusion 原文（PII 禁入）。 */
public record OversightMetricsView(
    String capabilityCode,
    String verdict,
    long sampleSize,
    long fastReviewCount,
    long unreasonedOverrideCount,
    long feedbackNotRaisedCount,
    String reasonCode,
    String modelHealthClaim,
    boolean requiresOwnerReview,
    Instant computedAt) {

  public static final String NO_MODEL_HEALTH_CLAIM = "NONE";

  public static OversightMetricsView from(OversightSignal signal) {
    return new OversightMetricsView(
        signal.capabilityCode(),
        signal.verdict().name(),
        signal.sampleSize(),
        signal.fastReviewCount(),
        signal.unreasonedOverrideCount(),
        signal.feedbackNotRaisedCount(),
        signal.reasonCode(),
        NO_MODEL_HEALTH_CLAIM,
        signal.verdict().isActionable(),
        signal.computedAt());
  }
}
