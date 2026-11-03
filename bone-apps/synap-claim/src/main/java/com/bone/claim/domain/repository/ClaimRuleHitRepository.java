package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.rule.ClaimRuleHit;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 规则命中记录仓储（§9.5 A3，append-only）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimRuleHitRepository extends Repository<ClaimRuleHit, Long> {

  /** 按租户 + 案件回溯全部规则命中（推翻率 / 命中分析的事实源）。 */
  default List<ClaimRuleHit> findByClaimId(Long tenantId, long claimId) {
    Criteria<ClaimRuleHit> criteria =
        Criteria.<ClaimRuleHit>create()
            .eq(ClaimRuleHit::getTenantId, tenantId)
            .eq(ClaimRuleHit::getClaimId, claimId)
            .orderByAsc(ClaimRuleHit::getHitAt);
    return findByCriteria(criteria);
  }

  /** 按租户 + 决策执行回溯命中（一次决策执行命中的全部规则）。 */
  default List<ClaimRuleHit> findByExecutionId(Long tenantId, long executionId) {
    Criteria<ClaimRuleHit> criteria =
        Criteria.<ClaimRuleHit>create()
            .eq(ClaimRuleHit::getTenantId, tenantId)
            .eq(ClaimRuleHit::getExecutionId, executionId)
            .orderByAsc(ClaimRuleHit::getHitAt);
    return findByCriteria(criteria);
  }
}
