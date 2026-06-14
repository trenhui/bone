package com.bone.iam.domain.audit.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class AuditLogId {
  private final Long value;
}
