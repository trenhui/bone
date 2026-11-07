package com.bone.aidoc.domain.model;

/**
 * 阈值标定样本（一行 = 一个字段的一次识别 + 人工标注结论）。
 *
 * <p>来源应为<b>脱敏的真实标注数据集</b>；本记录不存原始影像与个人信息，只存判定所需的数值信号。 「错误放行」的定义是：策略判为 {@link
 * ReviewDecision#AUTO_PASS} 且 {@code correct=false}。
 *
 * @param riskLevel 该字段的风险等级（与运行时 {@code ClaimFieldRisk} 口径一致）
 * @param confidence OCR 引擎自报置信度（L1）
 * @param qualityScore 质量评估模型打分（L3）；{@link Double#NaN} 表示该样本缺质量分
 * @param correct 识别值是否与人工标注一致
 * @param keyField 是否为关键字段（金额/证件号等，用于分组核对覆盖率）
 */
public record CalibrationSample(
    OcrDocType docType,
    String fieldName,
    RiskLevel riskLevel,
    double confidence,
    double qualityScore,
    boolean correct,
    boolean keyField) {

  public CalibrationSample {
    if (riskLevel == null) {
      throw new IllegalArgumentException("标定样本风险等级不可为空");
    }
    if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
      throw new IllegalArgumentException("标定样本 OCR 置信度须在 [0,1]: " + confidence);
    }
    if (!Double.isNaN(qualityScore) && (qualityScore < 0.0 || qualityScore > 1.0)) {
      throw new IllegalArgumentException("标定样本质量分须在 [0,1] 或 NaN: " + qualityScore);
    }
  }

  /** 便捷构造：非关键字段、有质量分。 */
  public static CalibrationSample of(
      RiskLevel level, double confidence, double qualityScore, boolean correct) {
    return new CalibrationSample(
        OcrDocType.GENERAL, "field", level, confidence, qualityScore, correct, false);
  }
}
