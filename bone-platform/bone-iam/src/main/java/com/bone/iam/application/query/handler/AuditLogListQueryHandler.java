package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.AuditLogDTO;
import com.bone.iam.application.query.qry.AuditLogListQry;
import com.bone.iam.domain.model.audit.AuditLog;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogListQueryHandler {
    @Transactional(readOnly = true)
    public PageResult<AuditLogDTO> handle(AuditLogListQry qry) {
        QueryBuilder<AuditLog> queryBuilder = QueryBuilder.from(AuditLog.class);
        
        if (qry.getUserId() != null) {
            queryBuilder.where("userId").eq(qry.getUserId());
        }
        
        if (qry.getOperation() != null) {
            queryBuilder.where("operation").eq(qry.getOperation());
        }
        
        if (qry.getResourceType() != null && !qry.getResourceType().isEmpty()) {
            queryBuilder.where("resourceType").eq(qry.getResourceType());
        }
        
        if (qry.getResult() != null && !qry.getResult().isEmpty()) {
            queryBuilder.where("result").eq(qry.getResult());
        }
        
        if (qry.getStartTime() != null) {
            queryBuilder.where("createTime").ge(qry.getStartTime());
        }
        
        if (qry.getEndTime() != null) {
            queryBuilder.where("createTime").le(qry.getEndTime());
        }
        
        if (qry.getTenantId() != null) {
            queryBuilder.where("tenantId").eq(qry.getTenantId());
        }
        
        return queryBuilder.orderBy("createTime", "desc")
                          .page(qry.getPageNum(), qry.getPageSize())
                          .mapTo(AuditLogDTO.class);
    }
}