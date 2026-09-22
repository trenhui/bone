package com.bone.integration.application.service;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.valueobject.ExecutionStatus;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service("applicationFlowMonitorService")
@RequiredArgsConstructor
public class FlowMonitorService {
  private final IntegrationLogRepository logRepository;

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

  private long countByFlowAndStatus(Long flowId, ExecutionStatus status) {
    return logRepository.countByFlowAndStatus(flowId, status);
  }
}
