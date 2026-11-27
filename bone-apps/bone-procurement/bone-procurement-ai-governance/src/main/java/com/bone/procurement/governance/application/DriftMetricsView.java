package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.DriftSignal;
import java.time.Instant;

/**
 * 漂移指标只读投影：对外只暴露计数、判词与原因码，不暴露内部可变对象；任何判词都不声明模型效果达标。
 *
 * @param capabilityCode 能力码
 * @param verdict 三态判词
 * @param sampleSize 人工终决样本数
 * @param overrideCount 推翻次数
 * @param overrideRate 推翻率（不可判定时为 null）
 * @param threshold 生效阈值（未配置时为 null）
 * @param minSampleSize 最小样本量
 * @param reasonCode 判词原因码
 * @param modelHealthClaim 恒为 NONE——明确不声明模型健康
 * @param requiresOwnerReview 是否需 owner 复核（仅触阈为真）
 * @param computedAt 计算时刻
 */
public record DriftMetricsView(
    String capabilityCode,
    String verdict,
    long sampleSize,
    long overrideCount,
    Double overrideRate,
    Double threshold,
    int minSampleSize,
    String reasonCode,
    String modelHealthClaim,
    boolean requiresOwnerReview,
    Instant computedAt) {

  public static final String NO_MODEL_HEALTH_CLAIM = "NONE";

  public static DriftMetricsView from(DriftSignal signal) {
    return new DriftMetricsView(
        signal.capabilityCode(),
        signal.verdict().name(),
        signal.sampleSize(),
        signal.overrideCount(),
        signal.overrideRate(),
        signal.threshold(),
        signal.minSampleSize(),
        signal.reasonCode(),
        NO_MODEL_HEALTH_CLAIM,
        signal.verdict().isActionable(),
        signal.computedAt());
  }
}
