package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.model.tenant.Tenant;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;

/**
 * 租户聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>租户编码查重与租户分页都只涉及 {@link Tenant} 自身，属「本聚合读」：DSL 驻留此处而不进 {@code application}（E-4.2）。
 */
public interface TenantRepository extends Repository<Tenant, Long> {

  /** 按租户编码计数（建租户查重）；写侧仓储方法名禁用 {@code exists} 词汇，故用计数表达存在性。 */
  default Long countByCode(String code) {
    return countByCriteria(Criteria.<Tenant>create().eq("code", code));
  }

  /** 租户分页（本聚合读）：{@code code} / {@code name} 模糊匹配，按创建时间倒序。 */
  default PageResult<Tenant> findTenantPage(String code, String name, int pageNo, int pageSize) {
    var query = QueryBuilder.from(Tenant.class);
    if (code != null && !code.isBlank()) {
      query.where(Tenant::getCode).like("%" + code + "%");
    }
    if (name != null && !name.isBlank()) {
      query.where(Tenant::getName).like("%" + name + "%");
    }
    return query.orderByDesc(Tenant::getCreatedAt).page(pageNo, pageSize);
  }
}
