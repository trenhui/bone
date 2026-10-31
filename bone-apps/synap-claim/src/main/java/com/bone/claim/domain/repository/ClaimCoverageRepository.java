package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.policy.ClaimCoverage;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/** 保障项/险种仓储（{@code t_claim_coverage}）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimCoverageRepository extends Repository<ClaimCoverage, Long> {

  /** 按计划取全部险种（配置面：计划下的险种列表）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimCoverage> findByPlanId(@Param("planId") long planId);

  /** 按险种 ID 取（租户内可见）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimCoverage findCoverageById(@Param("coverageId") long coverageId);

  /** 按计划 + 险种编码查（同计划下编码唯一）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimCoverage findByPlanIdAndCode(
      @Param("planId") long planId, @Param("coverageCode") String coverageCode);
}
