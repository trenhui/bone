package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 承保主体主档仓储。 */
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimInsurerRepository extends Repository<ClaimInsurer, Long> {

  default ClaimInsurer findByInsurerCode(Long tenantId, String insurerCode) {
    return findByCriteria(
            Criteria.<ClaimInsurer>create()
                .eq(ClaimInsurer::getTenantId, tenantId)
                .eq(ClaimInsurer::getInsurerCode, insurerCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<ClaimInsurer> findPage(Long tenantId, String keyword, int page, int size) {
    Criteria<ClaimInsurer> criteria = pageCriteria(tenantId, keyword);
    criteria.orderByDesc(ClaimInsurer::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String keyword) {
    return countByCriteria(pageCriteria(tenantId, keyword));
  }

  static Criteria<ClaimInsurer> pageCriteria(Long tenantId, String keyword) {
    Criteria<ClaimInsurer> criteria =
        Criteria.<ClaimInsurer>create().eq(ClaimInsurer::getTenantId, tenantId);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(ClaimInsurer::getInsurerCode, pattern)
                  .like(ClaimInsurer::getInsurerName, pattern));
    }
    return criteria;
  }
}
