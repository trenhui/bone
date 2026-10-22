package com.bone.claim.domain.model.claim.event;

import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 人工核赔决策生效（decision = APPROVED / REJECTED / REQUEST_MORE_EVIDENCE）。
 *
 * <p>拒赔必带五要素（{@code comment}），经下游生成拒赔通知书；通过则触发理算与资金链路（A6/A7，后续迭代）。
 */
public record ClaimDecisionMadeEvent(
    Long claimId,
    Long tenantId,
    String claimNo,
    Decision decision,
    String reviewerId,
    String comment,
    Instant occurredAt)
    implements DomainEvent {}
