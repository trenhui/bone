package com.bone.integration.application.query.handler;

import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.qry.FlowPageQry;
import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.core.model.PageResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FlowPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<FlowDTO> handle(FlowPageQry qry) {
        return QueryBuilder.from(com.bone.integration.domain.model.flow.IntegrationFlow.class)
                .where(qry.keyword() != null, "name").like(qry.keyword())
                .where(qry.status() != null, "status").eq(qry.status())
                .orderBy("id", "desc")
                .page(qry.pageNum(), qry.pageSize())
                .mapTo(FlowDTO.class);
    }
}