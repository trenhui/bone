package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.model.RiskLevel;

/** 人工推翻输入：终决风险等级由人给出，AI 结论不被静默采纳。 */
public record HumanOverrideInput(String reviewer, String reason, RiskLevel finalRiskLevel) {}
