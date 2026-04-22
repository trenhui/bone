package com.bone.iam.domain.model.audit;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.model.audit.event.AuditLogCreatedEvent;
import com.bone.iam.domain.model.audit.vo.AuditLogId;
import com.bone.iam.domain.model.audit.vo.OperationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog extends AggregateRoot<Long> {
    private AuditLogId id;
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
    private LocalDateTime createTime;

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
        auditLog.createTime = LocalDateTime.now();
        auditLog.addDomainEvent(new AuditLogCreatedEvent(auditLog));
        return auditLog;
    }

    void setId(Long id) {
        this.id = AuditLogId.of(id);
    }
}