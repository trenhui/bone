package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 执行记录详情查询处理器 */
@Component
public class ExecutionDetailQueryHandler {

  @Transactional(readOnly = true)
  public ExecutionLogDTO handle(ExecutionDetailQuery query) {
    IntegrationLog log = QueryBuilder.from(IntegrationLog.class).where(IntegrationLog::getId).eq(query.id()).first();
    if (log == null) {
      throw new DomainException("执行记录不存在");
    }
    return new ExecutionLogDTO(
        log.getId(),
        log.getFlowId(),
        log.getStatus().name(),
        log.getStartedAt(),
        log.getEndedAt(),
        log.getInputData(),
        log.getOutputData(),
        log.getErrorMessage());
  }
}
