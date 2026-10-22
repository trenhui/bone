package com.bone.claim.domain.model.claim.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/** 报案受理成功（DRAFT → 7；触发材料预检与保单校验流水）。 */
public record ClaimSubmittedEvent(Long claimId, Long tenantId, String claimNo, Instant occurredAt)
    implements DomainEvent {}
