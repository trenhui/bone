package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.StdCodeAlias;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 标准码别名仓储（1:1 归并/更名）。解析时一次性取该域全部别名，供内核 canonicalize。 */
@TenantScope(TenantScopeMode.AUTO)
public interface StdCodeAliasRepository extends Repository<StdCodeAlias, Long> {

  default List<StdCodeAlias> findByDomain(Long tenantId, String domain) {
    return findByCriteria(
        Criteria.<StdCodeAlias>create()
            .eq(StdCodeAlias::getTenantId, tenantId)
            .eq(StdCodeAlias::getDomain, domain));
  }

  default StdCodeAlias findByAliasCode(Long tenantId, String domain, String aliasCode) {
    return findByCriteria(
            Criteria.<StdCodeAlias>create()
                .eq(StdCodeAlias::getTenantId, tenantId)
                .eq(StdCodeAlias::getDomain, domain)
                .eq(StdCodeAlias::getAliasCode, aliasCode)
                .eq(StdCodeAlias::getStatus, "ACTIVE"))
        .stream()
        .findFirst()
        .orElse(null);
  }
}
