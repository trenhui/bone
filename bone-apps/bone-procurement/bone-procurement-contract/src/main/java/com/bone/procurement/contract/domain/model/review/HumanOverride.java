package com.bone.procurement.contract.domain.model.review;

/** 人工推翻记录：即便 AI 高置信，推翻也必须留下 AI 结论与人工终决，不被静默采纳。 */
public record HumanOverride(
    String reviewer,
    String reason,
    double aiConfidence,
    RiskLevel aiRiskLevel,
    RiskLevel finalRiskLevel) {}
