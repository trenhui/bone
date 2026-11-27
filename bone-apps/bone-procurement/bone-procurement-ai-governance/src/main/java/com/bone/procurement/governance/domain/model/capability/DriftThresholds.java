package com.bone.procurement.governance.domain.model.capability;

/**
 * 漂移判定阈值配置。
 *
 * <p>缺省为未配置（阈值 null），此时任何评估都 fail-closed 到 UNDETERMINED。阈值属 owner 通过线（D-10 未闭合）， 本代码不硬编码默认阈值，避免冒充
 * owner 结论。
 *
 * @param overrideRateThreshold 人工终决推翻率阈值，必须在 (0,1]；null 表示未配置
 * @param minSampleSize 最小样本量，必须为正数
 */
public record DriftThresholds(Double overrideRateThreshold, int minSampleSize) {

  public static final int DEFAULT_MIN_SAMPLE_SIZE = 20;

  public DriftThresholds {
    if (overrideRateThreshold != null
        && (overrideRateThreshold <= 0.0
            || overrideRateThreshold > 1.0
            || overrideRateThreshold.isNaN())) {
      throw new IllegalArgumentException("override 率阈值必须在 (0,1]");
    }
    if (minSampleSize <= 0) {
      throw new IllegalArgumentException("最小样本量必须为正数");
    }
  }

  /** 未配置阈值：任何评估都不可判定。 */
  public static DriftThresholds unconfigured() {
    return new DriftThresholds(null, DEFAULT_MIN_SAMPLE_SIZE);
  }

  public boolean configured() {
    return overrideRateThreshold != null;
  }
}
