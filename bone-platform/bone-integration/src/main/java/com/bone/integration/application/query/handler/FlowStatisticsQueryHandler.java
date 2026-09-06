package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import com.bone.integration.application.service.FlowMonitorService;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 流程统计查询处理器：flowId 为空时汇总全部流程，否则只返回该流程的统计。 */
@Component
public class FlowStatisticsQueryHandler {

  private final FlowMonitorService flowMonitorService;

  public FlowStatisticsQueryHandler(FlowMonitorService flowMonitorService) {
    this.flowMonitorService = flowMonitorService;
  }

  @Transactional(readOnly = true)
  public List<FlowStatisticsDTO> handle(FlowStatisticsQuery query) {
    if (query.flowId() != null) {
      return List.of(statisticsOf(query.flowId()));
    }
    return QueryBuilder.from(IntegrationFlow.class).list().stream()
        .map(flow -> statisticsOf(flow.getId()))
        .collect(Collectors.toList());
  }

  private FlowStatisticsDTO statisticsOf(Long flowId) {
    IntegrationFlow flow =
        QueryBuilder.from(IntegrationFlow.class)
            .where(IntegrationFlow::getId)
            .eq(flowId)
            .first()
            .orElse(null);
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
