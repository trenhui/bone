package com.bone.integration.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.integration.application.query.qry.FlowPageQry;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.model.flow.vo.FlowStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FlowPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<FlowDTO> handle(FlowPageQry qry) {
        FluentQuery<IntegrationFlow> query = QueryBuilder.from(IntegrationFlow.class);

        if (qry.keyword() != null && !qry.keyword().isBlank()) {
            query.where(IntegrationFlow::getName).like(qry.keyword());
        }
        if (qry.status() != null && !qry.status().isBlank()) {
            query.where(IntegrationFlow::getStatus).eq(FlowStatus.valueOf(qry.status()));
        }

        PageResult<IntegrationFlow> result =
                query.orderByDesc(IntegrationFlow::getId).page(qry.pageNum(), qry.pageSize());

        List<FlowDTO> records = result.getRecords().stream()
                .map(flow -> new FlowDTO(
                        flow.getId(),
                        flow.getName(),
                        flow.getDescription(),
                        flow.getStatus().name(),
                        Collections.emptyList(),
                        Collections.emptyList()))
                .collect(Collectors.toList());

        return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
    }
}
