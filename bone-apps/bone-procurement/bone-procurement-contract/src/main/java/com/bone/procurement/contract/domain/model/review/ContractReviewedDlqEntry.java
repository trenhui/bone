package com.bone.procurement.contract.domain.model.review;

/** 死信条目：以 review_id 重放，outboxRef 指向原始 payload（tenant_id + review_id）。 */
public record ContractReviewedDlqEntry(
    String tenantId, String reviewId, String reason, String outboxRef, boolean resolved) {}
