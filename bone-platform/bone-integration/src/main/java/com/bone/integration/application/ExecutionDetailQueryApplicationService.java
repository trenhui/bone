package com.bone.integration.application;

import com.bone.integration.application.query.dto.ExecutionLogDto;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 执行记录详情。 */
@Component
@RequiredArgsConstructor
public class ExecutionDetailQueryApplicationService {

  private final IntegrationLogRepository logRepository;

  @Transactional(readOnly = true)
  public ExecutionLogDto handle(ExecutionDetailQuery query) {
    IntegrationLog log = logRepository.findById(query.id());
    if (log == null) {
      throw IntegrationErrors.of(IntegrationErrorCodes.EXECUTION_NOT_FOUND, query.id());
    }
    return new ExecutionLogDto(
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
