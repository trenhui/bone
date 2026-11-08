package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.policy.ClaimPlan;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 保障计划仓储（{@code t_claim_plan}）。
 *
 * <p>计划是"保单 → 计划 → 险种 → 责任"三层树的顶层容器：责任挂在计划下， 因此"按计划取责任"（理算路径）与"按保单取计划"（配置面路径）都是高频查询。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimPlanRepository extends Repository<ClaimPlan, Long> {

  /** 按保单号取全部计划（配置面：保单详情页的计划列表）。 */
  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimPlan> findByPolicyNo(@Param("policyNo") String policyNo);

  /** 按计划编码查（同保单下编码唯一；跨保单可复用，故须带 policyNo）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPlan findByPolicyNoAndCode(
      @Param("policyNo") String policyNo, @Param("planCode") String planCode);

  /** 按计划 ID 取（租户内可见）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPlan findPlanById(@Param("planId") long planId);
}
