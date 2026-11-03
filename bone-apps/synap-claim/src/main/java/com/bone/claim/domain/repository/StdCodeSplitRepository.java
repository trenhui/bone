package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.StdCodeSplit;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 标准码拆分仓储（1:N）。解析层只用它判断"是不是拆分父码"。 */
@TenantScope(TenantScopeMode.AUTO)
public interface StdCodeSplitRepository extends Repository<StdCodeSplit, Long> {

  default List<StdCodeSplit> findByParent(Long tenantId, String domain, String parentCode) {
    return findByCriteria(
        Criteria.<StdCodeSplit>create()
            .eq(StdCodeSplit::getTenantId, tenantId)
            .eq(StdCodeSplit::getDomain, domain)
            .eq(StdCodeSplit::getParentCode, parentCode)
            .eq(StdCodeSplit::getStatus, "ACTIVE"));
  }
}
