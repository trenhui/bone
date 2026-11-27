package com.bone.procurement.governance.domain.service;

/**
 * 人类监督有效性阈值（owner 通过线，仅服务端配置，不接受客户端注入）。
 *
 * <p>缺省未配置即 fail-closed 为 UNDETERMINED；负阈值、非正样本量、比例越界一律拒绝。
 */
public record OversightThresholds(
    Long reviewDurationFloorMs, int minSampleSize, Double fastReviewRatioThreshold) {

  public static final int DEFAULT_MIN_SAMPLE_SIZE = 20;

  public OversightThresholds {
    if (reviewDurationFloorMs != null && reviewDurationFloorMs < 0) {
      throw new IllegalArgumentException("复核耗时下限不得为负");
    }
    if (minSampleSize <= 0) {
      throw new IllegalArgumentException("最小样本量必须为正数");
    }
    if (fastReviewRatioThreshold != null
        && (fastReviewRatioThreshold <= 0.0
            || fastReviewRatioThreshold > 1.0
            || fastReviewRatioThreshold.isNaN())) {
      throw new IllegalArgumentException("橡皮图章比例阈值必须在 (0,1]");
    }
  }

  public static OversightThresholds unconfigured() {
    return new OversightThresholds(null, DEFAULT_MIN_SAMPLE_SIZE, null);
  }

  public boolean configured() {
    return reviewDurationFloorMs != null && fastReviewRatioThreshold != null;
  }
}
