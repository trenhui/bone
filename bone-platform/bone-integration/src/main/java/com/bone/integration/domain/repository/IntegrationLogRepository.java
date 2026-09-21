package com.bone.integration.domain.repository;

import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

public interface IntegrationLogRepository extends Repository<IntegrationLog, Long> {

  /** 全租户口径的执行次数（平台运维统计专用；命名与调用面同 {@link IntegrationFlowRepository}）。 */
  default long countByFlowAllTenants(Long flowId) {
    Long count =
        countByCriteria(
            Criteria.<IntegrationLog>create().eq("flowId", flowId).disableTenantFilter());
    return count != null ? count : 0L;
  }

  /** 全租户口径的按状态执行次数（平台运维统计专用）。 */
  default long countByFlowAndStatusAllTenants(Long flowId, ExecutionStatus status) {
    Long count =
        countByCriteria(
            Criteria.<IntegrationLog>create()
                .eq("flowId", flowId)
                .eq("status", status)
                .disableTenantFilter());
    return count != null ? count : 0L;
  }
}
