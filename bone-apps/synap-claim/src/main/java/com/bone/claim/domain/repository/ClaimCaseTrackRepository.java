package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimCaseTrack;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 案件状态流转流水仓储（ADR-003）。
 *
 * <p>{@code save} 为 append-only 写入（由 {@code ClaimCaseTrackRecorder} 在 {@code
 * ClaimStatusChangedEvent} 后落库）； {@link #findByClaimId} 供 SLA 物化（{@code
 * RefreshLimitHourJob}）从流水派生阶段停留与挂起占用（§6.6）。
 */
public interface ClaimCaseTrackRepository extends Repository<ClaimCaseTrack, Long> {

  /** 按租户 + 案件回溯流水（按事件时间升序），SLA 物化的事实源。 */
  default List<ClaimCaseTrack> findByClaimId(Long tenantId, long claimId) {
    Criteria<ClaimCaseTrack> criteria =
        Criteria.<ClaimCaseTrack>create()
            .eq(ClaimCaseTrack::getTenantId, tenantId)
            .eq(ClaimCaseTrack::getClaimId, claimId)
            .orderByAsc(ClaimCaseTrack::getCreatedAt);
    return findByCriteria(criteria);
  }
}
