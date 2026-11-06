package com.bone.aidoc.domain.model;

/**
 * 单字段复核上下文——{@link ReviewPolicy} 的判定输入。
 *
 * <p>汇聚字段证据、质量分、风险等级与多模型交叉结论四层信号，供策略做差异化路由。 设计铁律（ADR-0046 §四）：
 *
 * <ul>
 *   <li>多模型一致 ≠ 正确：上下文同时携带 OCR 置信度与质量分，策略可要求二者同时达标才放行高风险字段。
 *   <li>多模型<b>不一致</b>必须升级：{@link #crossCheck()} 为 {@link CrossCheck#MISMATCH} 时策略直接转人工复核。
 * </ul>
 *
 * @param field 字段级证据链
 * @param qualityScore 质量分（NaN 表示质量模型未覆盖该字段）
 * @param riskLevel 字段风险等级
 * @param crossCheck 多模型交叉结论（默认 {@link CrossCheck#NOT_RUN}）
 */
public record FieldReviewContext(
    OcrFieldEvidence field, double qualityScore, RiskLevel riskLevel, CrossCheck crossCheck) {

  /** 兼容三参构造（无交叉信号）。 */
  public FieldReviewContext(OcrFieldEvidence field, double qualityScore, RiskLevel riskLevel) {
    this(field, qualityScore, riskLevel, CrossCheck.NOT_RUN);
  }
}
