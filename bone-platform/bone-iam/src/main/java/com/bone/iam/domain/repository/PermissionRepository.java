package com.bone.iam.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.vo.PermissionType;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.Optional;

/**
 * 权限聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>树 / 详情 / 分页三类读都只涉及 {@link Permission} 自身，属「本聚合读」，DSL 驻留此处而不进 {@code application}（E-4.2）。
 */
public interface PermissionRepository extends Repository<Permission, Long> {

  /** 全量权限（权限树与详情映射的公共取数，仍受租户过滤）。 */
  default List<Permission> listAll() {
    return QueryBuilder.from(Permission.class).list();
  }

  /** 按 id 取权限；不存在返回 {@code Optional.empty()}（不抛 {@code MultipleResultsException}）。 */
  default Optional<Permission> findPermissionById(Long id) {
    if (id == null) {
      return Optional.empty();
    }
    return QueryBuilder.from(Permission.class).where(Permission::getId).eq(id).first();
  }

  /** 按 id 批量取权限（权限码解析等场景，避免 N+1）。 */
  default List<Permission> findByIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    return QueryBuilder.from(Permission.class).where(Permission::getId).in(ids).list();
  }

  /**
   * 权限分页（本聚合读）。
   *
   * <p>关键字跨 {@code name} / {@code code} / {@code description} 三列 OR，须用 {@link QueryBuilder}。
   */
  default PageResult<Permission> findPermissionPage(
      String keyword, PermissionType type, Long parentId, int pageNo, int pageSize) {
    FluentQuery<Permission> query = QueryBuilder.from(Permission.class);
    if (keyword != null && !keyword.isEmpty()) {
      query
          .where(Permission::getName)
          .like(keyword)
          .or(Permission::getCode)
          .like(keyword)
          .or(Permission::getDescription)
          .like(keyword);
    }
    if (type != null) {
      query.where(Permission::getType).eq(type);
    }
    if (parentId != null) {
      query.where(Permission::getParentId).eq(parentId);
    }
    return query.orderByDesc(Permission::getCreatedAt).page(pageNo, pageSize);
  }
}
