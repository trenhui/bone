package com.bone.integration.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

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

  /** 本聚合执行记录分页。 */
  default PageResult<IntegrationLog> findPage(
      Long flowId, ExecutionStatus status, int pageNum, int pageSize) {
    Criteria<IntegrationLog> criteria =
        Criteria.<IntegrationLog>create()
            .eq(flowId != null, IntegrationLog::getFlowId, flowId)
            .eq(status != null, IntegrationLog::getStatus, status)
            .orderByDesc(IntegrationLog::getId)
            .page(pageNum, pageSize);
    return pageByCriteria(criteria);
  }

  default List<IntegrationLog> findByFlowId(Long flowId) {
    return findByCriteria(Criteria.<IntegrationLog>create().eq(IntegrationLog::getFlowId, flowId));
  }

  default long countByFlow(Long flowId) {
    Long count =
        countByCriteria(Criteria.<IntegrationLog>create().eq(IntegrationLog::getFlowId, flowId));
    return count != null ? count : 0L;
  }

  default long countByFlowAndStatus(Long flowId, ExecutionStatus status) {
    Long count =
        countByCriteria(
            Criteria.<IntegrationLog>create()
                .eq(IntegrationLog::getFlowId, flowId)
                .eq(IntegrationLog::getStatus, status));
    return count != null ? count : 0L;
  }
}
