package com.bone.procurement.contract.domain.model.review;

/**
 * Inbox 中持久化的消费结论（tenant_id + review_id 唯一键裁决"只消费一次"）。
 *
 * <p>快照引用只读保留、不改写；租户是消费方上下文权威，绝不取自事件。
 */
public record ContractReviewedConclusion(
    String tenantId,
    String reviewId,
    RiskLevel riskLevel,
    AiParticipation aiAvailable,
    String reviewSnapshotRef,
    boolean quarantined) {}
