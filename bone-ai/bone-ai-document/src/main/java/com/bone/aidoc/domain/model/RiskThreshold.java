package com.bone.aidoc.domain.model;

/**
 * 单风险等级的复核阈值（ADR-0046 §四：阈值须按票种/字段/风险分别标定，禁全局 0.95）。
 *
 * <p>所有阈值均为<b>闭合下界</b>。{@code DefaultReviewPolicy} 按字段的 {@link RiskLevel} 取对应阈值：
 *
 * <ul>
 *   <li>{@code autoPassConfidence}：自动通过要求的最低 OCR 置信度。
 *   <li>{@code autoPassQuality}：自动通过要求的最低质量分（质量分可用时生效）。
 *   <li>{@code manualConfidence}：低于此置信度 → 人工复核。
 *   <li>{@code manualQuality}：质量分低于此 → 人工复核。
 * </ul>
 *
 * <p>默认骨架值仅用于开发自测，<b>生产阈值必须由真实标注样本按风险等级标定后覆盖</b>。
 */
public record RiskThreshold(
    double autoPassConfidence,
    double autoPassQuality,
    double manualConfidence,
    double manualQuality) {}
