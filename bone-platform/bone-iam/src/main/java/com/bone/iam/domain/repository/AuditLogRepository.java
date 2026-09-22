package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.model.audit.AuditLog;
import com.bone.iam.domain.model.audit.valueobject.OperationType;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.time.LocalDateTime;

/**
 * 审计日志聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>日志分页检索属「本聚合读」：DSL 驻留此处，{@code application} 层只做租户解析与 DTO 装配（E-4.2）。
 *
 * <p>参数刻意使用标量而非 application 查询对象——{@code domain} 不得依赖 {@code ..application..}（P0-1）。
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

  /** 审计日志分页检索（多条件可选，按发生时间倒序）。 */
  default PageResult<AuditLog> findAuditLogPage(
      Long userId,
      OperationType operation,
      String resourceType,
      String result,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      Long tenantId,
      int pageNo,
      int pageSize) {
    FluentQuery<AuditLog> query = QueryBuilder.from(AuditLog.class);
    if (userId != null) {
      query.where(AuditLog::getUserId).eq(userId);
    }
    if (operation != null) {
      query.where(AuditLog::getOperation).eq(operation);
    }
    if (resourceType != null && !resourceType.isEmpty()) {
      query.where(AuditLog::getResourceType).eq(resourceType);
    }
    if (result != null && !result.isEmpty()) {
      query.where(AuditLog::getResult).eq(result);
    }
    if (startedAt != null) {
      query.where(AuditLog::getCreatedAt).gte(startedAt);
    }
    if (endedAt != null) {
      query.where(AuditLog::getCreatedAt).lte(endedAt);
    }
    if (tenantId != null) {
      query.where(AuditLog::getTenantId).eq(tenantId);
    }
    return query.orderByDesc(AuditLog::getCreatedAt).page(pageNo, pageSize);
  }
}
