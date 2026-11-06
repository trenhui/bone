package com.bone.aidoc.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 可信 OCR 文档——七层管线的最终产物（ADR-0046 §三）。
 *
 * <p>相较既有 {@link OcrResult}（单引擎、文档级置信度），本聚合结果承载：字段级证据链、 质量评估报告、逐字段复核状态，以及按复核状态的分布计数。下游理赔业务只消费本对象，
 * 不感知具体 OCR 引擎。
 *
 * @param docId 文档标识（租户内唯一）
 * @param tenantId 租户
 * @param sourceRef 来源引用（文件 key / URL）
 * @param docType 文档类型（驱动识别 schema 与规则）
 * @param primaryEngineId 主识别引擎
 * @param engineIds 参与识别的全部引擎（含交叉校验引擎）
 * @param fields 字段级证据链
 * @param qualityReport 质量评估报告（可为 none）
 * @param rawText 全文文本兜底
 * @param pageCount 页数
 * @param decisionCounts 复核状态分布（AUTO_PASSED / SECONDARY_RECOGNITION / MANUAL_REVIEW）
 */
public record TrustedOcrDocument(
    String docId,
    String tenantId,
    String sourceRef,
    OcrDocType docType,
    String primaryEngineId,
    List<String> engineIds,
    List<OcrFieldEvidence> fields,
    QualityReport qualityReport,
    String rawText,
    int pageCount,
    Map<ReviewStatus, Integer> decisionCounts) {

  /** 自动通过字段占比（0~1），用于「自动通过率」指标。 */
  public double autoPassRate() {
    int total = fields.size();
    if (total == 0) {
      return 0.0;
    }
    int auto = decisionCounts.getOrDefault(ReviewStatus.AUTO_PASSED, 0);
    return (double) auto / total;
  }

  /** 需人工复核的字段数（含二次识别与人工复核）。 */
  public int needsHumanAttention() {
    return decisionCounts.getOrDefault(ReviewStatus.SECONDARY_RECOGNITION, 0)
        + decisionCounts.getOrDefault(ReviewStatus.MANUAL_REVIEW, 0);
  }
}
