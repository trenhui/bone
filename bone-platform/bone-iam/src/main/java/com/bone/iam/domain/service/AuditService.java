package com.bone.iam.domain.service;

import com.bone.iam.domain.audit.AuditLog;
import com.bone.iam.domain.audit.vo.OperationType;
import com.bone.iam.domain.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public void log(Long tenantId, Long userId, OperationType operation, String resourceId,
                    String resourceType, String ip, String userAgent,
                    String parameters, String result, Integer duration) {
        AuditLog auditLog = AuditLog.create(tenantId, userId, operation, resourceId,
                                           resourceType, ip, userAgent, parameters, result, duration);
        auditLogRepository.save(auditLog);
    }
}
