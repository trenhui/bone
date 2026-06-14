package com.bone.iam.domain.audit.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.audit.AuditLog;
import lombok.Getter;

@Getter
public class AuditLogCreatedEvent implements DomainEvent {
  private final Long auditLogId;
  private final String operation;

  public AuditLogCreatedEvent(AuditLog auditLog) {
    this.auditLogId = auditLog.getId();
    this.operation = auditLog.getOperation().name();
  }
}
