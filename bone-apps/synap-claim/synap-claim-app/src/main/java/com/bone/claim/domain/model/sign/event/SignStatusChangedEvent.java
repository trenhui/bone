package com.bone.claim.domain.model.sign.event;

import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/** 签收批次状态跃迁事件，轨迹记录器据此追加不可变轨迹。 */
public record SignStatusChangedEvent(
    Long batchId,
    Long tenantId,
    String batchNo,
    SignStatus fromStatus,
    SignStatus toStatus,
    String actionCode,
    String operatorId,
    Instant occurredAt)
    implements DomainEvent {}
