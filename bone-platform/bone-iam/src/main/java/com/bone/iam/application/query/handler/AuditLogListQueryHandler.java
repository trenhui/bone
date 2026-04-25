package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.qry.AuditLogListQry;
import com.bone.iam.domain.audit.AuditLog;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AuditLogListQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<AuditLogDTO> handle(AuditLogListQry qry) {
        FluentQuery<AuditLog> query = QueryBuilder.from(AuditLog.class);

        if (qry.getUserId() != null) {
            query.where(AuditLog::getUserId).eq(qry.getUserId());
        }

        if (qry.getOperation() != null) {
            query.where(AuditLog::getOperation).eq(qry.getOperation());
        }

        if (qry.getResourceType() != null && !qry.getResourceType().isEmpty()) {
            query.where(AuditLog::getResourceType).eq(qry.getResourceType());
        }

        if (qry.getResult() != null && !qry.getResult().isEmpty()) {
            query.where(AuditLog::getResult).eq(qry.getResult());
        }

        if (qry.getStartTime() != null) {
            query.where(AuditLog::getCreateTime).gte(qry.getStartTime());
        }

        if (qry.getEndTime() != null) {
            query.where(AuditLog::getCreateTime).lte(qry.getEndTime());
        }

        if (qry.getTenantId() != null) {
            query.where(AuditLog::getTenantId).eq(qry.getTenantId());
        }

        PageResult<AuditLog> result = query.orderByDesc(AuditLog::getCreateTime)
                          .page(qry.getPage(), qry.getSize());

        List<AuditLogDTO> dtoList = result.getRecords().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());

        return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
    }

    private AuditLogDTO convertToDto(AuditLog auditLog) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(auditLog.getId());
        dto.setTenantId(auditLog.getTenantId());
        dto.setUserId(auditLog.getUserId());
        dto.setOperation(auditLog.getOperation());
        dto.setResourceId(auditLog.getResourceId());
        dto.setResourceType(auditLog.getResourceType());
        dto.setIp(auditLog.getIp());
        dto.setUserAgent(auditLog.getUserAgent());
        dto.setParameters(auditLog.getParameters());
        dto.setResult(auditLog.getResult());
        dto.setDuration(auditLog.getDuration());
        dto.setCreateTime(auditLog.getCreateTime());
        return dto;
    }
}
