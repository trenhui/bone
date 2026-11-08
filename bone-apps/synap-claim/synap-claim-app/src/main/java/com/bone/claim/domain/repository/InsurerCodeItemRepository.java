package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.mapping.InsurerCodeItem;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 保司码表项仓储。 */
@TenantScope(TenantScopeMode.AUTO)
public interface InsurerCodeItemRepository extends Repository<InsurerCodeItem, Long> {

  default InsurerCodeItem findByCode(
      Long tenantId, String insurerCode, String domain, String itemCode) {
    return findByCriteria(
            Criteria.<InsurerCodeItem>create()
                .eq(InsurerCodeItem::getTenantId, tenantId)
                .eq(InsurerCodeItem::getInsurerCode, insurerCode)
                .eq(InsurerCodeItem::getDomain, domain)
                .eq(InsurerCodeItem::getItemCode, itemCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<InsurerCodeItem> findPage(
      Long tenantId, String insurerCode, String domain, String keyword, int page, int size) {
    Criteria<InsurerCodeItem> criteria = pageCriteria(tenantId, insurerCode, domain, keyword);
    criteria.orderByDesc(InsurerCodeItem::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String insurerCode, String domain, String keyword) {
    return countByCriteria(pageCriteria(tenantId, insurerCode, domain, keyword));
  }

  static Criteria<InsurerCodeItem> pageCriteria(
      Long tenantId, String insurerCode, String domain, String keyword) {
    Criteria<InsurerCodeItem> criteria =
        Criteria.<InsurerCodeItem>create().eq(InsurerCodeItem::getTenantId, tenantId);
    if (insurerCode != null && !insurerCode.isBlank()) {
      criteria.eq(InsurerCodeItem::getInsurerCode, insurerCode);
    }
    if (domain != null && !domain.isBlank()) {
      criteria.eq(InsurerCodeItem::getDomain, domain);
    }
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(InsurerCodeItem::getItemCode, pattern)
                  .like(InsurerCodeItem::getItemName, pattern));
    }
    return criteria;
  }
}
