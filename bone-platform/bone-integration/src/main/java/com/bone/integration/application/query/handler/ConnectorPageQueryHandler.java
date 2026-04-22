package com.bone.integration.application.query.handler;

import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.core.model.PageResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConnectorPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<ConnectorDTO> handle(ConnectorPageQry qry) {
        return QueryBuilder.from(com.bone.integration.domain.model.connector.Connector.class)
                .where(qry.keyword() != null, "name").like(qry.keyword())
                .where(qry.type() != null, "type").eq(qry.type())
                .where(qry.status() != null, "status").eq(qry.status())
                .orderBy("id", "desc")
                .page(qry.pageNum(), qry.pageSize())
                .mapTo(ConnectorDTO.class);
    }
}