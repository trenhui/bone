package com.bone.iam.domain.repository;

import com.bone.iam.domain.model.audit.AuditLog;
import com.bone.iam.domain.model.audit.vo.AuditLogId;
import com.bone.metadata.sdk.Repository;

public interface AuditLogRepository extends Repository<AuditLog, AuditLogId> {
    // 审计日志查询方法由 QueryBuilder 处理
}