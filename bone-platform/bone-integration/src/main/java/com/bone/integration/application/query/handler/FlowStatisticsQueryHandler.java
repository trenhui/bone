package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.query.dto.FlowStatisticsDTO;
import com.bone.integration.application.query.qry.FlowStatisticsQuery;
import com.bone.integration.application.service.FlowMonitorService;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 流程统计查询处理器 */
@Component
public class FlowStatisticsQueryHandler {

  private final FlowMonitorService flowMonitorService;

  public FlowStatisticsQueryHandler(FlowMonitorService flowMonitorService) {
    this.flowMonitorService = flowMonitorService;
  }

  @Transactional(readOnly = true)
  public FlowStatisticsDTO handle(FlowStatisticsQuery query) {
    IntegrationFlow flow =
        QueryBuilder.from(IntegrationFlow.class)
            .where(IntegrationFlow::getId)
            .eq(query.flowId())
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
