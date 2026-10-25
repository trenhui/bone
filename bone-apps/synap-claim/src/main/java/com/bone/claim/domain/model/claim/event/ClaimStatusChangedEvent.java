package com.bone.claim.domain.model.claim.event;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 案件状态跃迁（§6.2 合法转换表任一转换即发；编排层据此更新审核任务）。
 *
 * <p>状态用 {@link ClaimStatus} <b>枚举</b>承载，不用数字码：数字码是「对外桥接码」， 落库与域内一律枚举名，两种表示混用正是「按 {@code '50'}
 * 过滤却一行都命中不到」这类静默故障的根因。
 */
public record ClaimStatusChangedEvent(
    Long claimId,
    Long tenantId,
    String claimNo,
    ClaimStatus fromStatus,
    ClaimStatus toStatus,
    String actionCode,
    String operatorId,
    Instant occurredAt)
    implements DomainEvent {}
