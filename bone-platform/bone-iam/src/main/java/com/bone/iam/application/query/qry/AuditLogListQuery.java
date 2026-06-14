package com.bone.iam.application.query.qry;

import com.bone.core.model.PageParam;
import com.bone.iam.domain.audit.vo.OperationType;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuditLogListQuery extends PageParam {
  private Long userId;
  private OperationType operation;
  private String resourceType;
  private String result;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private Long tenantId;
}
