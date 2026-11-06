package com.bone.aidoc.domain.model;

import java.util.Set;

/**
 * 字段级 OCR 证据链（ADR-0046 §三.3——可信度必须是字段级，且每个字段保留可审计证据）。
 *
 * <p>这是可信 OCR 区别于普通文档 OCR 的核心载体：除识别值外，还保存原文字、页码、坐标、 识别引擎、置信度及其来源、规则校验状态与复核状态。OCR
 * 负责忠实还原，复核与业务判断由下游分层完成， 本记录只承载「这条字段从哪来、凭什么可信」的证据。
 *
 * @param fieldName 业务字段名（与统一字段 Schema 对齐，如 total_amount / invoice_no）
 * @param value 识别值（OCR 忠实还原，不得被模型推理修正）
 * @param rawText 原图/原文对应文字（如「合计金额：壹万贰仟伍佰捌拾元整」），可为 {@code null}
 * @param page 所在页码（从 1 计）
 * @param bbox 原图定位框，可为 {@code null}（缺坐标字段不满足 100% 证据定位覆盖率）
 * @param engineId 识别引擎标识（如 paddle-ocr / glm-ocr / minicpm-ocr）
 * @param confidence 置信度数值——其语义由 {@link #confidenceSource()} 标明，禁止跨来源比较
 * @param confidenceSource 置信度来源（OCR 引擎 / 质量模型 / 标定概率）
 * @param validationStatus 规则校验状态（L2）
 * @param reviewStatus 复核状态（路由结果）
 */
public record OcrFieldEvidence(
    String fieldName,
    String value,
    String rawText,
    int page,
    BoundingBox bbox,
    String engineId,
    double confidence,
    ConfidenceSource confidenceSource,
    ValidationStatus validationStatus,
    ReviewStatus reviewStatus) {

  /**
   * 由既有 {@link RecognizedField} 桥接为初始证据：OCR 引擎置信度、待校验、待复核。
   *
   * <p>原图坐标/原文在既有 OCR 结果中未必具备，留空由下游（质检/证据补全）填充。
   */
  public static OcrFieldEvidence from(RecognizedField rf, String engineId, int page) {
    return new OcrFieldEvidence(
        rf.name(),
        rf.value(),
        null,
        page,
        null,
        engineId,
        rf.confidence(),
        ConfidenceSource.OCR_ENGINE,
        ValidationStatus.PENDING,
        ReviewStatus.PENDING);
  }

  /** 带校验状态的新副本（record 不可变，流水线逐步补全）。 */
  public OcrFieldEvidence withValidationStatus(ValidationStatus status) {
    return new OcrFieldEvidence(
        fieldName,
        value,
        rawText,
        page,
        bbox,
        engineId,
        confidence,
        confidenceSource,
        status,
        reviewStatus);
  }

  /** 带复核状态的新副本。 */
  public OcrFieldEvidence withReviewStatus(ReviewStatus status) {
    return new OcrFieldEvidence(
        fieldName,
        value,
        rawText,
        page,
        bbox,
        engineId,
        confidence,
        confidenceSource,
        validationStatus,
        status);
  }

  /** 覆盖置信度及其来源的新副本（质量模型/Jev 定标后调用）。 */
  public OcrFieldEvidence withConfidence(double confidence, ConfidenceSource source) {
    return new OcrFieldEvidence(
        fieldName,
        value,
        rawText,
        page,
        bbox,
        engineId,
        confidence,
        source,
        validationStatus,
        reviewStatus);
  }

  /** 是否已具备原图证据定位（坐标）。 */
  public boolean hasEvidenceLocation() {
    return bbox != null && bbox.valid();
  }

  /** 是否关键字段（验收指标口径，由调用方传入关键字段名集合）。 */
  public boolean isKeyField(Set<String> keyFieldNames) {
    return keyFieldNames.contains(fieldName);
  }
}
