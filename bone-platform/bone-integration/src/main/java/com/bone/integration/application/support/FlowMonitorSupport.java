package com.bone.integration.application.support;

import com.bone.integration.domain.model.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.valueobject.ExecutionStatus;
import com.bone.integration.domain.model.flow.IntegrationFlow;
import com.bone.integration.domain.repository.IntegrationFlowRepository;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationFlowMonitorSupport")
@RequiredArgsConstructor
public class FlowMonitorSupport {
  private final IntegrationLogRepository logRepository;
  private final IntegrationFlowRepository flowRepository;

  public List<IntegrationLog> getExecutionLogs(Long flowId) {
    return logRepository.findByFlowId(flowId);
  }

  public long getExecutionCount(Long flowId) {
    return logRepository.countByFlow(flowId);
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

  // ===== 平台运维统计口径（跨租户）=====
  //
  // 为什么与上面那组并列而不是复用：上面那组服务租户可见路径（MonitorController 的监控页），
  // 必须保持租户内口径；下面这组只服务 FlowStatisticsJob 的平台汇总（定时线程无租户上下文）。
  // 两组分开命名，调用方从签名就能看出跨租户，避免"某次顺手复用"把跨租户数据带进租户页面。
  // 命名与调用面由共享门禁双向绑定（ArchitectureTest#all_tenant_entry_points_must_be_named_all_tenants
  // / #all_tenants_scan_only_by_registered_callers，本类已登记为允许调用方）。

  /** 全租户口径的执行次数（平台运维统计专用，勿用于租户可见路径）。 */
  public long getExecutionCountAllTenants(Long flowId) {
    return logRepository.countByFlowAllTenants(flowId);
  }

  /** 全租户口径的成功次数（平台运维统计专用）。 */
  public long getSuccessCountAllTenants(Long flowId) {
    return logRepository.countByFlowAndStatusAllTenants(flowId, ExecutionStatus.SUCCESS);
  }

  /** 全租户口径的成功率（平台运维统计专用）。 */
  public double getSuccessRateAllTenants(Long flowId) {
    long total = getExecutionCountAllTenants(flowId);
    if (total == 0) {
      return 0.0;
    }
    return (double) getSuccessCountAllTenants(flowId) / total * 100;
  }

  /**
   * 全租户口径的流程统计快照（P2-7）。
   *
   * <p><b>为什么必须有这个方法</b>：原先 {@code FlowStatisticsJob} 在循环里对每个 flow 调 {@code getExecutionCount} +
   * {@code getSuccessCount} + {@code getSuccessRate}，而 {@code getSuccessRateAllTenants} **内部又把
   * count 与 success 各查了一遍** ⇒ 实际是<b>每流程 4 次</b>查询 （不是报告说的 3 次）。100 个流程 = 400 次查询，只为打印一行汇总日志。
   *
   * <p>这里改为：每个 flow 只查两次（count + success），成功率由这两个数现算，不再单独查。 真正的 {@code GROUP BY flow_id} 一次取回仍做不了
   * —— SDK 的 {@code Criteria} 不支持 groupBy （实测 0 处），要批量化需扩展 SDK 或写原生 SQL，属架构决策，未在本次改动中臆定。
   *
   * <p>顺带把「取流程定义」也收进 application 层：{@code FlowStatisticsJob} 原先直接注入了 {@code
   * IntegrationFlowRepository}（domain 端口），adapter 层越过 application 取数据。
   */
  public FlowStatisticsSnapshot getStatisticsForAllTenants() {
    List<IntegrationFlow> flows = flowRepository.findForStatisticsAllTenants();
    if (flows == null || flows.isEmpty()) {
      return new FlowStatisticsSnapshot(List.of(), 0L, 0L);
    }
    List<FlowStat> stats = new ArrayList<>(flows.size());
    long totalExecutions = 0;
    long totalSuccess = 0;
    for (IntegrationFlow flow : flows) {
      long executions = getExecutionCountAllTenants(flow.getId());
      long success = getSuccessCountAllTenants(flow.getId());
      totalExecutions += executions;
      totalSuccess += success;
      stats.add(new FlowStat(flow.getId(), flow.getName(), executions, success));
    }
    return new FlowStatisticsSnapshot(stats, totalExecutions, totalSuccess);
  }

  /** 单个流程的统计快照；成功率由 executions / success 现算，不再额外查库。 */
  public record FlowStat(Long flowId, String name, long executions, long success) {
    /** 0 ≤ rate ≤ 100；executions 为 0 时返回 0.0（与旧 getSuccessRateAllTenants 语义一致）。 */
    public double successRate() {
      return executions == 0 ? 0.0 : (double) success / executions * 100;
    }
  }

  /** 统计快照汇总。 */
  public record FlowStatisticsSnapshot(
      List<FlowStat> stats, long totalExecutions, long totalSuccess) {
    public long flowCount() {
      return stats.size();
    }

    public double overallSuccessRate() {
      return totalExecutions == 0 ? 0.0 : (double) totalSuccess / totalExecutions * 100;
    }
  }

  private long countByFlowAndStatus(Long flowId, ExecutionStatus status) {
    return logRepository.countByFlowAndStatus(flowId, status);
  }
}
