package com.bone.procurement.contract.domain.model;

/**
 * ContractReviewed 事件 payload（D3-v2 §2 唯一真源，严格 7 字段）。
 *
 * <p>风险明细不进事件：仅以 {@code riskItemsHash} 摘要表达；审查快照只存 {@code reviewSnapshotRef} 引用。 本记录绝不承载 output
 * 原文、合同正文全文或任何 PII 字段。
 */
public record ContractReviewedEvent(
    String reviewId,
    String contractNo,
    RiskLevel riskLevel,
    String riskItemsHash,
    String reviewSnapshotRef,
    AiParticipation aiAvailable,
    String modelVersionSnapshot) {}
