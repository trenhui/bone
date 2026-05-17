package com.bone.iam.domain.audit;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.audit.event.AuditLogCreatedEvent;
import com.bone.iam.domain.audit.vo.OperationType;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_audit_log")
public class AuditLog extends AggregateRoot<Long> {
    private Long id;
    private Long tenantId;
    private Long userId;
    private OperationType operation;
    private String resourceId;
    private String resourceType;
    private String ip;
    private String userAgent;
    private String parameters;
    private String result;
    private Integer duration;
    private LocalDateTime createdAt;

    public static AuditLog create(Long tenantId, Long userId, OperationType operation, String resourceId,
                                  String resourceType, String ip, String userAgent,
                                  String parameters, String result, Integer duration) {
        AuditLog auditLog = new AuditLog();
        auditLog.tenantId = tenantId;
        auditLog.userId = userId;
        auditLog.operation = operation;
        auditLog.resourceId = resourceId;
        auditLog.resourceType = resourceType;
        auditLog.ip = ip;
        auditLog.userAgent = userAgent;
        auditLog.parameters = parameters;
        auditLog.result = result;
        auditLog.duration = duration;
        auditLog.createdAt = LocalDateTime.now();
        auditLog.addDomainEvent(new AuditLogCreatedEvent(auditLog));
        return auditLog;
    }
}
