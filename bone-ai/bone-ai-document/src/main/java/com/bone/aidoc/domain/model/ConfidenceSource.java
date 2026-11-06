package com.bone.aidoc.domain.model;

/**
 * 置信度来源——必须显式区分，禁止混为一谈（ADR-0046 §三.3）。
 *
 * <p>OCR 引擎自报的字符置信度、质量模型（如 Jev）打的可信分、以及经过真实样本标定的字段正确概率， 三者语义完全不同，不能直接比较或互相替代。 {@link
 * OcrFieldEvidence#confidence()} 的取值含义由本枚举标明。
 */
public enum ConfidenceSource {
  /** OCR 引擎自报的字符/字段识别置信度。 */
  OCR_ENGINE,
  /** 独立质量模型（如 Jev）对 OCR 候选结果打出的可信分，不改写原值。 */
  QUALITY_MODEL,
  /** 经过真实业务样本标定的字段正确概率（最可信，但需标定数据支撑）。 */
  CALIBRATED
}
