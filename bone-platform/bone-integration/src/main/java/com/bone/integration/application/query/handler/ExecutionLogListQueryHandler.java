package com.bone.integration.application.query.handler;

import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.qry.ExecutionLogListQry;
import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.core.model.PageResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExecutionLogListQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<ExecutionLogDTO> handle(ExecutionLogListQry qry) {
        return QueryBuilder.from(com.bone.integration.domain.execution.IntegrationLog.class)
                .where(qry.flowId() != null, "flowId").eq(qry.flowId())
                .where(qry.status() != null, "status").eq(qry.status())
                .orderBy("id", "desc")
                .page(qry.pageNum(), qry.pageSize())
                .mapTo(ExecutionLogDTO.class);
    }
}