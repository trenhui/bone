package com.bone.procurement.governance.domain.service;

import com.bone.procurement.governance.domain.model.capability.DriftSignal;
import com.bone.procurement.governance.domain.model.capability.DriftThresholds;
import com.bone.procurement.governance.domain.model.capability.DriftVerdict;
import java.time.Instant;

/**
 * 漂移信号裁决策略（纯函数，零框架依赖）。
 *
 * <p>判定顺序固定：计数一致性 → 阈值配置 → 样本充足 → 触阈。任何不可判定情形都返回 UNDETERMINED，绝不返回"模型健康"。
 */
public final class DriftSignalPolicy {

  public static final DriftSignalPolicy INSTANCE = new DriftSignalPolicy();

  private DriftSignalPolicy() {}

  public DriftSignal evaluate(
      String capabilityCode,
      long sampleSize,
      long overrideCount,
      DriftThresholds thresholds,
      Instant now) {
    // 计数一致性：非法计数直接拒绝，不产生判词。
    if (sampleSize < 0 || overrideCount < 0 || overrideCount > sampleSize) {
      throw new IllegalArgumentException(
          "漂移计数非法: sample=" + sampleSize + ", override=" + overrideCount);
    }

    // 阈值未配置：不可判定。
    if (!thresholds.configured()) {
      return undetermined(
          capabilityCode,
          sampleSize,
          overrideCount,
          thresholds,
          now,
          DriftSignal.REASON_THRESHOLD_NOT_CONFIGURED);
    }

    // 样本不足：不可判定。
    if (sampleSize < thresholds.minSampleSize()) {
      return undetermined(
          capabilityCode,
          sampleSize,
          overrideCount,
          thresholds,
          now,
          DriftSignal.REASON_INSUFFICIENT_SAMPLE);
    }

    double rate = (double) overrideCount / (double) sampleSize;
    if (rate >= thresholds.overrideRateThreshold()) {
      return new DriftSignal(
          capabilityCode,
          DriftVerdict.REVIEW_REQUIRED,
          sampleSize,
          overrideCount,
          rate,
          thresholds.overrideRateThreshold(),
          thresholds.minSampleSize(),
          DriftSignal.REASON_THRESHOLD_EXCEEDED,
          now);
    }
    return new DriftSignal(
        capabilityCode,
        DriftVerdict.NO_ACTIONABLE_SIGNAL,
        sampleSize,
        overrideCount,
        rate,
        thresholds.overrideRateThreshold(),
        thresholds.minSampleSize(),
        DriftSignal.REASON_BELOW_THRESHOLD_NOT_ENDORSEMENT,
        now);
  }

  private DriftSignal undetermined(
      String capabilityCode,
      long sampleSize,
      long overrideCount,
      DriftThresholds thresholds,
      Instant now,
      String reasonCode) {
    return new DriftSignal(
        capabilityCode,
        DriftVerdict.UNDETERMINED,
        sampleSize,
        overrideCount,
        null,
        thresholds.overrideRateThreshold(),
        thresholds.minSampleSize(),
        reasonCode,
        now);
  }
}
