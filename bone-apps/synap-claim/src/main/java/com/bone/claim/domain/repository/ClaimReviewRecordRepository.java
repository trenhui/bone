package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.review.ClaimReviewRecord;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 复核 / 审核留痕仓储（§9.5 A1，append-only）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimReviewRecordRepository extends Repository<ClaimReviewRecord, Long> {

  /** 按租户 + 案件回溯全部复核留痕（双人复核不变量校验的事实源）。 */
  default List<ClaimReviewRecord> findByClaimId(Long tenantId, long claimId) {
    Criteria<ClaimReviewRecord> criteria =
        Criteria.<ClaimReviewRecord>create()
            .eq(ClaimReviewRecord::getTenantId, tenantId)
            .eq(ClaimReviewRecord::getClaimId, claimId)
            .orderByAsc(ClaimReviewRecord::getCreatedAt);
    return findByCriteria(criteria);
  }
}
