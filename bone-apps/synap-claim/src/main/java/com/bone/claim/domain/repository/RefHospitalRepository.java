package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reference.RefHospital;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 基础目录——医院仓储（配置面分页/模糊查/编码唯一校验）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface RefHospitalRepository extends Repository<RefHospital, Long> {

  /** 同租户同编码唯一校验（Criteria 走 default 方法，禁抽象 finder 缺 SQL 模板）。 */
  default RefHospital findByHospitalCode(Long tenantId, String hospitalCode) {
    return findByCriteria(
            Criteria.<RefHospital>create()
                .eq(RefHospital::getTenantId, tenantId)
                .eq(RefHospital::getHospitalCode, hospitalCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<RefHospital> findPage(Long tenantId, String keyword, int page, int size) {
    Criteria<RefHospital> criteria = listCriteria(tenantId, keyword);
    criteria.orderByDesc(RefHospital::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String keyword) {
    return countByCriteria(listCriteria(tenantId, keyword));
  }

  static Criteria<RefHospital> listCriteria(Long tenantId, String keyword) {
    Criteria<RefHospital> criteria =
        Criteria.<RefHospital>create().eq(RefHospital::getTenantId, tenantId);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(RefHospital::getHospitalCode, pattern)
                  .like(RefHospital::getHospitalName, pattern));
    }
    return criteria;
  }
}
