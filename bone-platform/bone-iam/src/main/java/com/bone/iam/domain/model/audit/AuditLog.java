package com.bone.iam.domain.model.audit;

import com.bone.core.annotation.Id;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.model.audit.event.AuditLogCreatedEvent;
import com.bone.iam.domain.model.audit.vo.OperationType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_audit_log")
public class AuditLog extends TenantAggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

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

  public static AuditLog create(
      Long tenantId,
      Long userId,
      OperationType operation,
      String resourceId,
      String resourceType,
      String ip,
      String userAgent,
      String parameters,
      String result,
      Integer duration) {
    AuditLog auditLog = new AuditLog();
    auditLog.setTenantId(tenantId);
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
