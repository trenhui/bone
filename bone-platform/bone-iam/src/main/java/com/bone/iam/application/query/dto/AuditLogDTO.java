package com.bone.iam.application.query.dto;

import com.bone.iam.domain.model.audit.vo.OperationType;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AuditLogDTO {
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
}
