package com.bone.integration.application;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import com.bone.integration.application.service.FlowMonitorService;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 流程统计：flowId 为空时汇总当前可见流程，否则只返回该流程。不走全租户入口。 */
@Component
@RequiredArgsConstructor
public class FlowStatisticsQueryApplicationService {

  private final FlowMonitorService flowMonitorService;
  private final IntegrationFlowRepository flowRepository;

  @Transactional(readOnly = true)
  public List<FlowStatisticsDTO> handle(FlowStatisticsQuery query) {
    if (query.flowId() != null) {
      return List.of(statisticsOf(flowRepository.findById(query.flowId())));
    }
    return flowRepository.findAll().stream().map(this::statisticsOf).collect(Collectors.toList());
  }

  private FlowStatisticsDTO statisticsOf(IntegrationFlow flow) {
    if (flow == null) {
      throw new DomainException("流程不存在");
    }
    long executionCount = flowMonitorService.getExecutionCount(flow.getId());
    long successCount = flowMonitorService.getSuccessCount(flow.getId());
    long failureCount = flowMonitorService.getFailureCount(flow.getId());
    double successRate = flowMonitorService.getSuccessRate(flow.getId());
    return new FlowStatisticsDTO(
        flow.getId(), flow.getName(), executionCount, successCount, failureCount, successRate);
  }
}
