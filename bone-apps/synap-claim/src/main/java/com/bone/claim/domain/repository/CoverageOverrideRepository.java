package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.liability.ClaimCoverageOverride;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 保司覆盖项仓储（两段式；行级状态迁移，无物理删除）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface CoverageOverrideRepository extends Repository<ClaimCoverageOverride, Long> {

  @TenantScope(TenantScopeMode.AUTO)
  ClaimCoverageOverride findById(@Param("id") long id);

  /** 主体×责任的全部覆盖行（含未发布，合并预览用）。 */
  default List<ClaimCoverageOverride> findByBizAndCoverage(
      Long tenantId, String bizIdentityCode, String coverageUuid) {
    return findByCriteria(
        Criteria.<ClaimCoverageOverride>create()
            .eq(ClaimCoverageOverride::getTenantId, tenantId)
            .eq(ClaimCoverageOverride::getBizIdentityCode, bizIdentityCode)
            .eq(ClaimCoverageOverride::getCoverageUuid, coverageUuid)
            .orderByAsc(ClaimCoverageOverride::getOverrideField));
  }

  /** 已发布的覆盖行（理算前合并查询用）。 */
  default List<ClaimCoverageOverride> findActiveByBizAndCoverage(
      Long tenantId, String bizIdentityCode, String coverageUuid) {
    return findByCriteria(
        Criteria.<ClaimCoverageOverride>create()
            .eq(ClaimCoverageOverride::getTenantId, tenantId)
            .eq(ClaimCoverageOverride::getBizIdentityCode, bizIdentityCode)
            .eq(ClaimCoverageOverride::getCoverageUuid, coverageUuid)
            .eq(ClaimCoverageOverride::getStatus, ClaimCoverageOverride.STATUS_ACTIVE));
  }

  /** 主体×责任×维度唯一定位（UK 语义的读侧）。 */
  default ClaimCoverageOverride findRow(
      Long tenantId, String bizIdentityCode, String coverageUuid, String overrideField) {
    List<ClaimCoverageOverride> rows =
        findByCriteria(
            Criteria.<ClaimCoverageOverride>create()
                .eq(ClaimCoverageOverride::getTenantId, tenantId)
                .eq(ClaimCoverageOverride::getBizIdentityCode, bizIdentityCode)
                .eq(ClaimCoverageOverride::getCoverageUuid, coverageUuid)
                .eq(ClaimCoverageOverride::getOverrideField, overrideField));
    return rows.isEmpty() ? null : rows.get(0);
  }
}
