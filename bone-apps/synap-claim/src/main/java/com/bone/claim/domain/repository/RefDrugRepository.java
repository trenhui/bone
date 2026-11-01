package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reference.RefDrug;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 基础目录——药品仓储（配置面分页/模糊查/编码唯一校验）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface RefDrugRepository extends Repository<RefDrug, Long> {

  /** 同租户同编码唯一校验（Criteria 走 default 方法，禁抽象 finder 缺 SQL 模板）。 */
  default RefDrug findByDrugCode(Long tenantId, String drugCode) {
    return findByCriteria(
            Criteria.<RefDrug>create()
                .eq(RefDrug::getTenantId, tenantId)
                .eq(RefDrug::getDrugCode, drugCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<RefDrug> findPage(Long tenantId, String keyword, int page, int size) {
    Criteria<RefDrug> criteria = listCriteria(tenantId, keyword);
    criteria.orderByDesc(RefDrug::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String keyword) {
    return countByCriteria(listCriteria(tenantId, keyword));
  }

  static Criteria<RefDrug> listCriteria(Long tenantId, String keyword) {
    Criteria<RefDrug> criteria = Criteria.<RefDrug>create().eq(RefDrug::getTenantId, tenantId);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub -> sub.like(RefDrug::getDrugCode, pattern).like(RefDrug::getDrugName, pattern));
    }
    return criteria;
  }
}
