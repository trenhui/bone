package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.role.Role;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;

/**
 * 角色聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>读侧 DSL（{@link QueryBuilder}）只出现在本接口的 {@code default} 方法内：模块内联规则 {@code
 * domain_no_query_builder} 豁免 {@code ..domain.repository..}，而 {@code
 * read_side_dsl_only_in_query_adapter} 禁止 {@code application} 依赖读侧 DSL——两条门禁共同把「本聚合读」的 DSL
 * 唯一落点收敛到此处（E-4.2 / ADR-0030）。
 */
public interface RoleRepository extends Repository<Role, Long> {

  /**
   * 角色分页（本聚合读）。
   *
   * <p>关键字同时匹配 {@code name} 与 {@code description}（OR 分支，须用 {@link QueryBuilder}——{@code
   * Criteria.or(Consumer)} 有生成坏 SQL 的已知缺陷）；租户过滤值由调用方解析后显式传入。
   *
   * @param tenantId 已解析的租户过滤值；{@code null} 表示不加租户条件（平台租户未显式选择租户时）
   */
  default PageResult<Role> findRolePage(String keyword, Long tenantId, int pageNo, int pageSize) {
    FluentQuery<Role> query = QueryBuilder.from(Role.class);
    if (keyword != null && !keyword.isEmpty()) {
      query.where(Role::getName).like(keyword).or(Role::getDescription).like(keyword);
    }
    if (tenantId != null) {
      query.where(Role::getTenantId).eq(tenantId);
    }
    return query.orderByDesc(Role::getCreatedAt).page(pageNo, pageSize);
  }
}
