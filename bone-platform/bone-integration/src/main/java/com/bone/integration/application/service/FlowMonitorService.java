package com.bone.integration.application.service;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationFlowMonitorService")
@RequiredArgsConstructor
public class FlowMonitorService {
  private final IntegrationLogRepository logRepository;

  public List<IntegrationLog> getExecutionLogs(Long flowId) {
    return logRepository.findByCriteria(Criteria.<IntegrationLog>create().eq("flowId", flowId));
  }

  public long getExecutionCount(Long flowId) {
    Long count =
        logRepository.countByCriteria(Criteria.<IntegrationLog>create().eq("flowId", flowId));
    return count != null ? count : 0L;
  }

  public long getSuccessCount(Long flowId) {
    return countByFlowAndStatus(flowId, ExecutionStatus.SUCCESS);
  }

  public long getFailureCount(Long flowId) {
    return countByFlowAndStatus(flowId, ExecutionStatus.FAILED)
        + countByFlowAndStatus(flowId, ExecutionStatus.TIMEOUT);
  }

  public double getSuccessRate(Long flowId) {
    long total = getExecutionCount(flowId);
    if (total == 0) {
      return 0.0;
    }
    return (double) getSuccessCount(flowId) / total * 100;
  }

  private long countByFlowAndStatus(Long flowId, ExecutionStatus status) {
    Long count =
        logRepository.countByCriteria(
            Criteria.<IntegrationLog>create().eq("flowId", flowId).eq("status", status));
    return count != null ? count : 0L;
  }
}
