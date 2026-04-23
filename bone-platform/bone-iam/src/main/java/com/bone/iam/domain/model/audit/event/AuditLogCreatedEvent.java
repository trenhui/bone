package com.bone.iam.domain.model.audit.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.audit.AuditLog;
import com.bone.iam.domain.model.audit.vo.AuditLogId;
import com.bone.iam.domain.model.audit.vo.OperationType;

public record AuditLogCreatedEvent(AuditLogId auditLogId, Long tenantId, Long userId, OperationType operation) implements DomainEvent {
    public AuditLogCreatedEvent(AuditLog auditLog) {
        this(auditLog.getId(), auditLog.getTenantId(), auditLog.getUserId(), auditLog.getOperation());
    }
}
