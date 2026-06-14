package com.bone.integration.adapter.schedule;

import com.bone.integration.application.service.FlowMonitorService;
import com.bone.integration.domain.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 流程执行统计定时任务（INT-05）：汇总 int_execution_log 成功率并写日志。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlowStatisticsJob {

  private final IntegrationFlowRepository flowRepository;
  private final FlowMonitorService flowMonitorService;

  @Scheduled(cron = "0 0 0 * * ?")
  public void execute() {
    log.info("开始执行流程统计任务");
    List<IntegrationFlow> flows = flowRepository.findByCriteria(Criteria.<IntegrationFlow>create());
    if (flows == null || flows.isEmpty()) {
      log.info("流程统计任务完成：无流程定义");
      return;
    }
    int flowCount = flows.size();
    long totalExecutions = 0;
    long totalSuccess = 0;
    for (IntegrationFlow flow : flows) {
      long count = flowMonitorService.getExecutionCount(flow.getId());
      long success = flowMonitorService.getSuccessCount(flow.getId());
      totalExecutions += count;
      totalSuccess += success;
      if (count > 0) {
        log.info(
            "流程统计 flowId={} name={} executions={} successRate={}%",
            flow.getId(),
            flow.getName(),
            count,
            String.format("%.2f", flowMonitorService.getSuccessRate(flow.getId())));
      }
    }
    log.info(
        "流程统计任务完成 flows={} totalExecutions={} overallSuccessRate={}%",
        flowCount,
        totalExecutions,
        totalExecutions == 0
            ? "N/A"
            : String.format("%.2f", (double) totalSuccess / totalExecutions * 100));
  }
}
