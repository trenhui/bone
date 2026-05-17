package com.bone.iam.application.usecase.standard;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.handler.AuditLogListQueryHandler;
import com.bone.iam.application.query.qry.AuditLogListQry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审计日志查询用例
 */
@Component
@RequiredArgsConstructor
public class AuditLogQueryUseCase {

    private final AuditLogListQueryHandler auditLogListQueryHandler;

    public PageResult<AuditLogDTO> execute(AuditLogListQry qry) {
        return auditLogListQueryHandler.handle(qry);
    }
}
