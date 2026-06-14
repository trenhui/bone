package com.bone.integration.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExecutionLogListQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<ExecutionLogDTO> handle(ExecutionLogListQuery qry) {
    FluentQuery<IntegrationLog> query = QueryBuilder.from(IntegrationLog.class);

    if (qry.flowId() != null) {
      query.where(IntegrationLog::getFlowId).eq(qry.flowId());
    }
    if (qry.status() != null && !qry.status().isBlank()) {
      query.where(IntegrationLog::getStatus).eq(ExecutionStatus.valueOf(qry.status()));
    }

    PageResult<IntegrationLog> result =
        query.orderByDesc(IntegrationLog::getId).page(qry.pageNum(), qry.pageSize());

    List<ExecutionLogDTO> records =
        result.getRecords().stream()
            .map(
                log ->
                    new ExecutionLogDTO(
                        log.getId(),
                        log.getFlowId(),
                        log.getStatus().name(),
                        log.getStartedAt(),
                        log.getEndedAt(),
                        log.getInputData(),
                        log.getOutputData(),
                        log.getErrorMessage()))
            .collect(Collectors.toList());

    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }
}
