package com.bone.procurement.governance.domain.model.capability;

import com.bone.procurement.governance.domain.service.DriftSignalPolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 漂移信号判词结果（只读投影产物）。
 *
 * <p>刻意不保存 AI output 原文（PII 禁入），只存计数、比率与判词。任何判词都不得对外声明模型效果达标。
 *
 * @param capabilityCode 能力码
 * @param verdict 三态判词
 * @param sampleSize 观测窗口内人工终决样本数
 * @param overrideCount 其中推翻 AI 结论的次数
 * @param overrideRate 推翻率，仅样本充足且阈值配置时非 null
 * @param threshold 生效阈值，未配置时为 null
 * @param minSampleSize 生效最小样本量
 * @param reasonCode 判词原因码
 * @param computedAt 计算时刻
 */
public record DriftSignal(
    String capabilityCode,
    DriftVerdict verdict,
    long sampleSize,
    long overrideCount,
    Double overrideRate,
    Double threshold,
    int minSampleSize,
    String reasonCode,
    Instant computedAt) {

  public static final String REASON_THRESHOLD_NOT_CONFIGURED = "DRIFT_THRESHOLD_NOT_CONFIGURED";
  public static final String REASON_INSUFFICIENT_SAMPLE = "DRIFT_INSUFFICIENT_SAMPLE";
  public static final String REASON_THRESHOLD_EXCEEDED = "DRIFT_OVERRIDE_RATE_THRESHOLD_EXCEEDED";
  public static final String REASON_BELOW_THRESHOLD_NOT_ENDORSEMENT =
      "DRIFT_BELOW_THRESHOLD_NOT_MODEL_ENDORSEMENT";

  public DriftSignal {
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    Objects.requireNonNull(verdict, "verdict");
    Objects.requireNonNull(reasonCode, "reasonCode");
    Objects.requireNonNull(computedAt, "computedAt");
  }

  /** 终局护栏：漂移信号永远不构成模型效果背书。 */
  public boolean claimsModelHealthy() {
    return false;
  }

  /** 领域入口：纯函数裁决，由 DriftSignalPolicy 调用。 */
  public static DriftSignal evaluate(
      String capabilityCode,
      long sampleSize,
      long overrideCount,
      DriftThresholds thresholds,
      Instant now) {
    return DriftSignalPolicy.INSTANCE.evaluate(
        capabilityCode, sampleSize, overrideCount, thresholds, now);
  }
}
