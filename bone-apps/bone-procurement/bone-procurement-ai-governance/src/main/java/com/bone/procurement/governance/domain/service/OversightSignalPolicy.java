package com.bone.procurement.governance.domain.service;

import com.bone.procurement.governance.domain.model.capability.OversightSignal;
import com.bone.procurement.governance.domain.model.capability.OversightVerdict;
import java.time.Instant;

/** 人类监督有效性纯函数裁决：非法输入拒绝 → 未配置/样本不足 → 三类风险 → 无信号。 */
public enum OversightSignalPolicy {
  INSTANCE;

  public OversightSignal evaluate(
      String capabilityCode,
      long sampleSize,
      long fastReviewCount,
      long unreasonedOverrideCount,
      long feedbackNotRaisedCount,
      OversightThresholds thresholds,
      Instant now) {
    if (sampleSize < 0
        || fastReviewCount < 0
        || unreasonedOverrideCount < 0
        || feedbackNotRaisedCount < 0
        || fastReviewCount > sampleSize
        || unreasonedOverrideCount > sampleSize
        || feedbackNotRaisedCount > sampleSize) {
      throw new IllegalArgumentException(
          "监督计数非法: sample="
              + sampleSize
              + ", fast="
              + fastReviewCount
              + ", unreasoned="
              + unreasonedOverrideCount
              + ", noFeedback="
              + feedbackNotRaisedCount);
    }

    if (!thresholds.configured()) {
      return new OversightSignal(
          capabilityCode,
          OversightVerdict.UNDETERMINED,
          sampleSize,
          fastReviewCount,
          unreasonedOverrideCount,
          feedbackNotRaisedCount,
          OversightSignal.REASON_THRESHOLD_NOT_CONFIGURED,
          now);
    }

    if (sampleSize < thresholds.minSampleSize()) {
      return new OversightSignal(
          capabilityCode,
          OversightVerdict.UNDETERMINED,
          sampleSize,
          fastReviewCount,
          unreasonedOverrideCount,
          feedbackNotRaisedCount,
          OversightSignal.REASON_INSUFFICIENT_SAMPLE,
          now);
    }

    if (unreasonedOverrideCount > 0) {
      return signal(
          capabilityCode,
          OversightVerdict.REVIEW_REQUIRED,
          sampleSize,
          fastReviewCount,
          unreasonedOverrideCount,
          feedbackNotRaisedCount,
          OversightSignal.REASON_UNREASONED_OVERRIDE,
          now);
    }
    if (feedbackNotRaisedCount > 0) {
      return signal(
          capabilityCode,
          OversightVerdict.REVIEW_REQUIRED,
          sampleSize,
          fastReviewCount,
          unreasonedOverrideCount,
          feedbackNotRaisedCount,
          OversightSignal.REASON_FEEDBACK_NOT_RAISED,
          now);
    }
    double ratio = (double) fastReviewCount / (double) sampleSize;
    if (ratio >= thresholds.fastReviewRatioThreshold()) {
      return signal(
          capabilityCode,
          OversightVerdict.REVIEW_REQUIRED,
          sampleSize,
          fastReviewCount,
          unreasonedOverrideCount,
          feedbackNotRaisedCount,
          OversightSignal.REASON_FAST_REVIEW_RATIO,
          now);
    }
    return signal(
        capabilityCode,
        OversightVerdict.NO_ACTIONABLE_SIGNAL,
        sampleSize,
        fastReviewCount,
        unreasonedOverrideCount,
        feedbackNotRaisedCount,
        OversightSignal.REASON_NO_ACTIONABLE_SIGNAL,
        now);
  }

  private static OversightSignal signal(
      String capabilityCode,
      OversightVerdict verdict,
      long sampleSize,
      long fastReviewCount,
      long unreasonedOverrideCount,
      long feedbackNotRaisedCount,
      String reasonCode,
      Instant now) {
    return new OversightSignal(
        capabilityCode,
        verdict,
        sampleSize,
        fastReviewCount,
        unreasonedOverrideCount,
        feedbackNotRaisedCount,
        reasonCode,
        now);
  }
}
