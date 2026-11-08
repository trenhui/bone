package com.bone.claim.domain.model.evidence.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/** 证据锁定（§7.2：`EvidenceCollection.locked()` 后触发，理算的前置条件）。 */
public record EvidenceLockedEvent(
    Long claimId, Long tenantId, int evidenceCount, String operatorId, Instant occurredAt)
    implements DomainEvent {}
