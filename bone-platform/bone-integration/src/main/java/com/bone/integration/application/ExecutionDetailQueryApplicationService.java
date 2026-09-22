package com.bone.integration.application;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.qry.ExecutionDetailQuery;
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
  public ExecutionLogDTO handle(ExecutionDetailQuery query) {
    IntegrationLog log = logRepository.findById(query.id());
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
