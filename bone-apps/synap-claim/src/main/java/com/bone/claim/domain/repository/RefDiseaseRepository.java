package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reference.RefDisease;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/** 基础目录——疾病仓储（配置面分页/模糊查/编码唯一校验）。 */
@TenantScope(TenantScopeMode.AUTO)
public interface RefDiseaseRepository extends Repository<RefDisease, Long> {

  /** 同租户同编码唯一校验（Criteria 走 default 方法，禁抽象 finder 缺 SQL 模板）。 */
  default RefDisease findByDiseaseCode(Long tenantId, String diseaseCode) {
    return findByCriteria(
            Criteria.<RefDisease>create()
                .eq(RefDisease::getTenantId, tenantId)
                .eq(RefDisease::getDiseaseCode, diseaseCode))
        .stream()
        .findFirst()
        .orElse(null);
  }

  default List<RefDisease> findPage(Long tenantId, String keyword, int page, int size) {
    Criteria<RefDisease> criteria = listCriteria(tenantId, keyword);
    criteria.orderByDesc(RefDisease::getCreatedAt).page(page, size);
    return findByCriteria(criteria);
  }

  default long countPage(Long tenantId, String keyword) {
    return countByCriteria(listCriteria(tenantId, keyword));
  }

  /** 目录总数（映射健康度覆盖率的分母）。 */
  default long countAll(Long tenantId) {
    return countByCriteria(Criteria.<RefDisease>create().eq(RefDisease::getTenantId, tenantId));
  }

  /** 按编码批量取目录项（映射列表回填标准名称，避免逐行 N+1）。 */
  default List<RefDisease> findByCodes(Long tenantId, List<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return List.of();
    }
    return findByCriteria(
        Criteria.<RefDisease>create()
            .eq(RefDisease::getTenantId, tenantId)
            .in(RefDisease::getDiseaseCode, codes.toArray()));
  }

  static Criteria<RefDisease> listCriteria(Long tenantId, String keyword) {
    Criteria<RefDisease> criteria =
        Criteria.<RefDisease>create().eq(RefDisease::getTenantId, tenantId);
    if (keyword != null && !keyword.isBlank()) {
      String pattern = "%" + keyword.trim() + "%";
      criteria.or(
          sub ->
              sub.like(RefDisease::getDiseaseCode, pattern)
                  .like(RefDisease::getDiseaseName, pattern));
    }
    return criteria;
  }
}
