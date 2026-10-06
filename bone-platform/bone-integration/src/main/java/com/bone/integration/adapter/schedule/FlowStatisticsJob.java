package com.bone.integration.adapter.schedule;

import com.bone.integration.application.support.FlowMonitorSupport;
import com.bone.integration.application.support.FlowMonitorSupport.FlowStat;
import com.bone.integration.application.support.FlowMonitorSupport.FlowStatisticsSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 流程执行统计定时任务（INT-05）：汇总 int_execution_log 成功率并写日志。
 *
 * <p><b>P2-7 修复</b>：原先本Job 直接注入 {@code IntegrationFlowRepository}（domain 端口）， adapter 层越过
 * application 取数据；且循环里每个 flow 打 3 次 monitor 方法，而 {@code getSuccessRateAllTenants} 内部又把
 * count/success 各查一遍 ⇒ 实际<b>每流程 4 次</b>查询。 现改为：取数与聚合全部收进 {@link
 * FlowMonitorSupport#getStatisticsForAllTenants()}， 成功率由 count/success 现算 ⇒ 每流程 2 次查询，且本类不再依赖
 * domain 端口。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlowStatisticsJob {

  private final FlowMonitorSupport flowMonitorSupport;

  @Scheduled(cron = "0 0 0 * * ?")
  public void execute() {
    log.info("开始执行流程统计任务");
    FlowStatisticsSnapshot snapshot = flowMonitorSupport.getStatisticsForAllTenants();
    if (snapshot.flowCount() == 0) {
      log.info("流程统计任务完成：无流程定义");
      return;
    }
    for (FlowStat stat : snapshot.stats()) {
      if (stat.executions() > 0) {
        log.info(
            "流程统计 flowId={} name={} executions={} successRate={}%",
            stat.flowId(),
            stat.name(),
            stat.executions(),
            String.format("%.2f", stat.successRate()));
      }
    }
    log.info(
        "流程统计任务完成 flows={} totalExecutions={} overallSuccessRate={}%",
        snapshot.flowCount(),
        snapshot.totalExecutions(),
        snapshot.totalExecutions() == 0
            ? "N/A"
            : String.format("%.2f", snapshot.overallSuccessRate()));
  }
}
