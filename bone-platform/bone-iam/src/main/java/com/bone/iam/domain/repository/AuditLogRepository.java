package com.bone.iam.domain.repository;

import com.bone.iam.domain.audit.AuditLog;
import com.bone.iam.application.query.qry.AuditLogListQry;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface AuditLogRepository extends Repository<AuditLog, Long> {

    /**
     * 根据条件查询审计日志
     */
    List<AuditLog> findByConditions(AuditLogListQry qry);

    /**
     * 根据条件统计审计日志数量
     */
    long countByConditions(AuditLogListQry qry);
}
