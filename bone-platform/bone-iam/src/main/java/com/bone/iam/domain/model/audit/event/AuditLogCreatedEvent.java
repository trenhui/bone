package com.bone.iam.domain.model.audit.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.audit.AuditLog;
import com.bone.iam.domain.model.audit.vo.OperationType;

public record AuditLogCreatedEvent(Long auditLogId, Long tenantId, Long userId, OperationType operation, 
                                  String resourceType, String result) implements DomainEvent {
    public AuditLogCreatedEvent(AuditLog auditLog) {
        this(auditLog.getId().getValue(), auditLog.getTenantId(), auditLog.getUserId(), 
             auditLog.getOperation(), auditLog.getResourceType(), auditLog.getResult());
    }
}