package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.util.List;

/**
 * A2 证据仓储。
 *
 * <p>与 {@link ClaimCaseRepository} 同纪律：默认 AUTO 租户过滤（§10.3）；不定义 {@code *AllTenants}
 * 方法——本域无全租户运维扫描需求（ArchUnit {@code all_tenant_entry_points_must_be_named_all_tenants}）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface EvidenceRepository extends Repository<Evidence, Long> {

  Evidence findById(@Param("evidenceId") long evidenceId);

  @TenantScope(TenantScopeMode.AUTO)
  List<Evidence> findByClaimId(@Param("claimId") long claimId);
}
