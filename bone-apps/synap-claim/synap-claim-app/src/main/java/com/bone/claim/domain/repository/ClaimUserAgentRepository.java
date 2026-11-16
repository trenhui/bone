package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.inspection.ClaimUserAgent;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * 代理区间仓储（{@code t_claim_user_agent}，append-only）。
 *
 * <p>被代理人的全部代理记录按生效起倒序返回，写入期用 {@link #findActiveByPrincipal} 做事前重叠查重， 数据库生成列 {@code active_key} +
 * 唯一键做并发兜底（恰一条 ACTIVE）。失效走应用层的 {@code deactivate}（改 state 释放唯一名额），不删行、保留审计。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimUserAgentRepository extends Repository<ClaimUserAgent, Long> {

  /** 被代理人的全部代理记录（含失效），按生效起倒序。 */
  List<ClaimUserAgent> findByPrincipal(@Param("principalId") String principalId);

  /** 被代理人当前生效代理（state=ACTIVE，区间重叠由调用方再按时间双判断）。 */
  ClaimUserAgent findActiveByPrincipal(@Param("principalId") String principalId);
}
