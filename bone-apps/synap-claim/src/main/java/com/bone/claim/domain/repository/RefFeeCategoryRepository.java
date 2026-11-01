package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reference.RefFeeCategory;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 基础目录——费用类别仓储（费用规则下拉与唯一校验；全量下拉不分页）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface RefFeeCategoryRepository extends Repository<RefFeeCategory, Long> {

  /** 同租户同编码唯一校验（Criteria 走 default 方法，禁抽象 finder 缺 SQL 模板）。 */
  default RefFeeCategory findByFeeCode(Long tenantId, String feeCode) {
    return findByCriteria(
            Criteria.<RefFeeCategory>create()
                .eq(RefFeeCategory::getTenantId, tenantId)
                .eq(RefFeeCategory::getFeeCode, feeCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<RefFeeCategory> findPage(Long tenantId, String keyword, int page, int size) {
    Criteria<RefFeeCategory> criteria = listCriteria(tenantId, keyword);
    criteria.orderByAsc(RefFeeCategory::getSortNo).page(page, size);
    return findByCriteria(criteria);
  }

  default List<RefFeeCategory> listAll(Long tenantId) {
    Criteria<RefFeeCategory> criteria =
        Criteria.<RefFeeCategory>create().eq(RefFeeCategory::getTenantId, tenantId);
    criteria.orderByAsc(RefFeeCategory::getSortNo);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String keyword) {
    return countByCriteria(listCriteria(tenantId, keyword));
  }

  static Criteria<RefFeeCategory> listCriteria(Long tenantId, String keyword) {
    Criteria<RefFeeCategory> criteria =
        Criteria.<RefFeeCategory>create().eq(RefFeeCategory::getTenantId, tenantId);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(RefFeeCategory::getFeeCode, pattern)
                  .like(RefFeeCategory::getFeeName, pattern));
    }
    return criteria;
  }
}
