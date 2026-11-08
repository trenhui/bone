package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.stakeholder.ClaimStakeholder;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 赔案干系人仓储（§9.5 A1，可变表）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimStakeholderRepository extends Repository<ClaimStakeholder, Long> {

  /** 按租户 + 案件回溯干系人（报案人/被保人/申请人/领款人）。 */
  default List<ClaimStakeholder> findByClaimId(Long tenantId, long claimId) {
    Criteria<ClaimStakeholder> criteria =
        Criteria.<ClaimStakeholder>create()
            .eq(ClaimStakeholder::getTenantId, tenantId)
            .eq(ClaimStakeholder::getClaimId, claimId);
    return findByCriteria(criteria);
  }
}
