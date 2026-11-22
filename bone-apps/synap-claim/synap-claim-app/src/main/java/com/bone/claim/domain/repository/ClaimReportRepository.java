package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 报案仓储（1:N 的"一"）。继承 SDK {@link Repository}，写走原生 save/insert，读用 Criteria（无自定义 SQL 模板）。 */
public interface ClaimReportRepository extends Repository<ClaimReport, Long> {

  /** 按报案号查（对外 FNOL 检索键，全局唯一）。 */
  default ClaimReport findByReportNo(Long tenantId, String reportNo) {
    return findOneByCriteria(
        Criteria.<ClaimReport>create()
            .eq(ClaimReport::getTenantId, tenantId)
            .eq(ClaimReport::getReportNo, reportNo));
  }

  /** 按幂等键查（重复登记 → 返回首报案，非 409）。 */
  default ClaimReport findByIdempotencyKey(Long tenantId, String idempotencyKey) {
    return findOneByCriteria(
        Criteria.<ClaimReport>create()
            .eq(ClaimReport::getTenantId, tenantId)
            .eq(ClaimReport::getIdempotencyKey, idempotencyKey));
  }

  /** 按状态列出报案（定时任务/运营视图用），创建时间倒序。 */
  default List<ClaimReport> findByStatus(Long tenantId, String status, int limit) {
    Criteria<ClaimReport> criteria =
        Criteria.<ClaimReport>create()
            .eq(ClaimReport::getTenantId, tenantId)
            .eq(ClaimReport::getStatus, status)
            .orderByDesc(ClaimReport::getCreatedAt)
            .page(0, limit);
    return findByCriteria(criteria);
  }
}
