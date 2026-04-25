package com.bone.iam.application.usecase.standard;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.qry.AuditLogListQry;
import com.bone.iam.domain.repository.AuditLogRepository;
import com.bone.iam.domain.audit.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 审计日志查询用例
 */
@Component
@RequiredArgsConstructor
public class AuditLogQueryUseCase {

    private final AuditLogRepository auditLogRepository;

    public PageResult<AuditLogDTO> execute(AuditLogListQry qry) {
        List<AuditLog> logs = auditLogRepository.findByConditions(qry);
        long total = auditLogRepository.countByConditions(qry);

        List<AuditLogDTO> dtoList = logs.stream()
                .map(log -> {
                    AuditLogDTO dto = new AuditLogDTO();
                    dto.setId(log.getId());
                    dto.setTenantId(log.getTenantId());
                    dto.setUserId(log.getUserId());
                    dto.setOperation(log.getOperation());
                    dto.setResourceId(log.getResourceId());
                    dto.setResourceType(log.getResourceType());
                    dto.setIp(log.getIp());
                    dto.setUserAgent(log.getUserAgent());
                    dto.setParameters(log.getParameters());
                    dto.setResult(log.getResult());
                    dto.setDuration(log.getDuration());
                    dto.setCreateTime(log.getCreateTime());
                    return dto;
                })
                .toList();

        return PageResult.of(dtoList, total, qry.getPage(), qry.getPageSize());
    }
}
