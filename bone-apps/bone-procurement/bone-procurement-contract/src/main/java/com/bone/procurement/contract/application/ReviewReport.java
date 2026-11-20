package com.bone.procurement.contract.application;

import com.bone.procurement.contract.domain.model.review.AiParticipation;
import com.bone.procurement.contract.domain.model.review.RiskLevel;

/** 审查报告视图（查询用）：只含状态与终决所需摘要，<b>不返回 output 原文、不返回风险明细正文/PII</b>。 */
public record ReviewReport(
    String reviewId,
    String contractNo,
    String status,
    AiParticipation aiAvailable,
    RiskLevel riskLevel,
    boolean humanFinalized,
    String humanReason) {}
