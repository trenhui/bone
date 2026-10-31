package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.policy.ClaimPolicy;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 保单仓储（{@code t_claim_policy}）。
 *
 * <p>读侧分两类：
 *
 * <ul>
 *   <li><b>配置面</b>（分页/模糊查/按 ID 查）：走 SDK {@link Criteria} DSL + 领域投影，供运营后台使用；
 *   <li><b>业务面</b>（按保单号查生效中的保单）：走 SQL 模板，供报案前置校验与额度年度裁决使用。
 * </ul>
 *
 * <p><b>为什么业务面不用 Criteria</b>：报案前置校验要"此刻生效中"这个时间相关判据， 而"生效起 ≤ now ≤ 生效止 + 规则启用 + 未过期"三条要一起判；写成 SQL
 * 模板比用 DSL 拼更直白， 且与既有的 {@code PolicyQueryPort} 语义一一对应（该端口是防腐层，未来切真实保单系统时只动适配器）。
 *
 * <p>注意：本仓储方法均返回聚合根或聚合根列表，符合 ArchUnit {@code domainRepositoriesShouldOnlyDeclareWhitelistedMethods}
 * 的返回类型白名单。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimPolicyRepository extends Repository<ClaimPolicy, Long> {

  /** 按保单号查（不按"此刻生效中"过滤）。 */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPolicy findByPolicyNo(@Param("policyNo") String policyNo);

  /**
   * 按保单号查<b>此刻可用</b>的保单（生效期内 + 规则启用 + 未过期）。
   *
   * <p>报案前置校验用。查不到即拒收（{@code SC_CLAIM_POLICY_NOT_ACTIVE}）。
   */
  @TenantScope(TenantScopeMode.AUTO)
  ClaimPolicy findUsablePolicy(@Param("policyNo") String policyNo);

  /**
   * 分页查保单（配置面）。
   *
   * @param tenantId 租户
   * @param keyword 保单号/名称模糊匹配（可空）
   * @param configStatus 规则开关过滤（可空）
   * @param page 页码（从 1 起）
   * @param size 每页条数
   */
  default List<ClaimPolicy> findPolicyPage(
      Long tenantId, String keyword, String configStatus, int page, int size) {
    Criteria<ClaimPolicy> criteria =
        Criteria.<ClaimPolicy>create()
            .eq(ClaimPolicy::getTenantId, tenantId)
            .like(
                keyword != null && !keyword.isBlank(),
                ClaimPolicy::getPolicyNo,
                keyword == null ? null : "%" + keyword.trim() + "%")
            .eq(
                configStatus != null && !configStatus.isBlank(),
                ClaimPolicy::getConfigStatus,
                configStatus)
            .orderByDesc(ClaimPolicy::getCreatedAt)
            .page(page, size);
    return findByCriteria(criteria);
  }

  /** 分页总数（与 {@link #findPolicyPage} 同口径）。 */
  default long countPolicyPage(Long tenantId, String keyword, String configStatus) {
    Criteria<ClaimPolicy> criteria =
        Criteria.<ClaimPolicy>create()
            .eq(ClaimPolicy::getTenantId, tenantId)
            .like(
                keyword != null && !keyword.isBlank(),
                ClaimPolicy::getPolicyNo,
                keyword == null ? null : "%" + keyword.trim() + "%")
            .eq(
                configStatus != null && !configStatus.isBlank(),
                ClaimPolicy::getConfigStatus,
                configStatus);
    return countByCriteria(criteria);
  }
}
