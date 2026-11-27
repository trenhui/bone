package com.bone.procurement.governance.domain.model.capability;

import com.bone.procurement.governance.domain.service.OversightSignalPolicy;
import com.bone.procurement.governance.domain.service.OversightThresholds;
import java.time.Instant;

/**
 * 人类监督有效性信号：基于人工终决台账真实计数的 fail-closed 判词。
 *
 * <p>刻意不回传 reason / aiConclusion 原文（PII 禁入），只返回计数与原因码；任何判词都不声明 模型或监督达标，不自动改变能力状态。
 */
public record OversightSignal(
    String capabilityCode,
    OversightVerdict verdict,
    long sampleSize,
    long fastReviewCount,
    long unreasonedOverrideCount,
    long feedbackNotRaisedCount,
    String reasonCode,
    Instant computedAt) {

  public static final String REASON_THRESHOLD_NOT_CONFIGURED = "OVERSIGHT_THRESHOLD_NOT_CONFIGURED";
  public static final String REASON_INSUFFICIENT_SAMPLE = "OVERSIGHT_INSUFFICIENT_SAMPLE";
  public static final String REASON_FAST_REVIEW_RATIO = "OVERSIGHT_FAST_REVIEW_RATIO";
  public static final String REASON_UNREASONED_OVERRIDE = "OVERSIGHT_UNREASONED_OVERRIDE";
  public static final String REASON_FEEDBACK_NOT_RAISED = "OVERSIGHT_FEEDBACK_NOT_RAISED";
  public static final String REASON_NO_ACTIONABLE_SIGNAL = "OVERSIGHT_NO_ACTIONABLE_SIGNAL";

  /** 终局护栏：任何判词都不得被解读为模型效果或监督充分性的背书。 */
  public boolean claimsModelHealthy() {
    return false;
  }

  public static OversightSignal evaluate(
      String capabilityCode,
      long sampleSize,
      long fastReviewCount,
      long unreasonedOverrideCount,
      long feedbackNotRaisedCount,
      OversightThresholds thresholds,
      Instant now) {
    return OversightSignalPolicy.INSTANCE.evaluate(
        capabilityCode,
        sampleSize,
        fastReviewCount,
        unreasonedOverrideCount,
        feedbackNotRaisedCount,
        thresholds,
        now);
  }
}
