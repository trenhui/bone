package com.bone.metadata.sdk.support.audit;

import com.bone.metadata.sdk.domain.model.AuditLog;

public interface AuditService {
  void log(AuditLog auditLog);
}
