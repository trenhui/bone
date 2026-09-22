package com.bone.integration.application;

import com.bone.core.model.PageResult;
import com.bone.integration.application.query.dto.ExecutionLogDTO;
import com.bone.integration.application.query.qry.ExecutionLogListQuery;
import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.valueobject.ExecutionStatus;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExecutionLogListQueryApplicationService {

  private final IntegrationLogRepository logRepository;

  @Transactional(readOnly = true)
  public PageResult<ExecutionLogDTO> handle(ExecutionLogListQuery qry) {
    ExecutionStatus status =
        qry.status() != null && !qry.status().isBlank()
            ? ExecutionStatus.valueOf(qry.status())
            : null;
    PageResult<IntegrationLog> result =
        logRepository.findPage(qry.flowId(), status, qry.pageNum(), qry.pageSize());

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
