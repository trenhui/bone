package com.bone.aidoc.domain.model;

import java.util.Collections;
import java.util.Map;

/**
 * OCR 质量评估报告（ADR-0046 五层可信度 L3 模型质检 / Jev 类打分模型产物）。
 *
 * <p>由 {@link OcrQualityEvaluator} 产出：对 OCR 候选结果逐字段打可信分（不重写原值）。 {@link #scoreFor(String)} 取不到时返回
 * {@link Double#NaN}，表示「质量分不可用」—— 此时 {@link ReviewPolicy} 不得仅凭 OCR 置信度自动放行高风险字段。
 */
public record QualityReport(String evaluatorId, Map<String, Double> fieldScores, double overall) {

  /** 字段质量分；缺省返回 NaN（不可用）。 */
  public double scoreFor(String fieldName) {
    Double v = fieldScores.get(fieldName);
    return v == null ? Double.NaN : v;
  }

  /** 无质量评估时的空报告（默认 NoOp 实现）。 */
  public static QualityReport none(String evaluatorId) {
    return new QualityReport(evaluatorId, Collections.emptyMap(), Double.NaN);
  }
}
