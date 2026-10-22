package com.bone.claim.domain.model.claim.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/** 案件状态跃迁（§6.2 合法转换表任一转换即发；编排层据此更新审核任务）。 */
public record ClaimStatusChangedEvent(
    Long claimId,
    Long tenantId,
    String claimNo,
    String fromStatus,
    String toStatus,
    String actionCode,
    String operatorId,
    Instant occurredAt)
    implements DomainEvent {}
