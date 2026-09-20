package com.bone.iam.domain.repository;

import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.role.RolePermission;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;

/**
 * 角色-权限绑定聚合的域仓储（写 + 本聚合读，ADR-0030）。
 *
 * <p>角色已授予的权限列表横跨 {@code iam_role_permission} 与 {@code iam_permission}，属「本聚合读」的展开：DSL 驻留此处，{@code
 * application} 层只调本方法，不直接依赖读侧 DSL（E-4.2）。
 */
public interface RolePermissionRepository extends Repository<RolePermission, Long> {

  /** 某角色下的全部绑定关系。 */
  default List<RolePermission> findByRole(Long roleId) {
    if (roleId == null) {
      return List.of();
    }
    return QueryBuilder.from(RolePermission.class)
        .where(RolePermission::getRoleId)
        .eq(roleId)
        .list();
  }

  /** 多个角色下的全部绑定关系（登录时按角色闭包批量取权限，避免逐角色查询）。 */
  default List<RolePermission> findByRoles(List<Long> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return List.of();
    }
    return QueryBuilder.from(RolePermission.class)
        .where(RolePermission::getRoleId)
        .in(roleIds)
        .list();
  }

  /**
   * 某角色已授予的权限对象列表（经 {@code iam_role_permission} 关联 {@code iam_permission}）。
   *
   * <p>返回领域对象而非 id 列表：写侧仓储的返回类型白名单只允许聚合 / 领域类型 / 标量，返回 {@code List<Long>} 会判违规。
   */
  default List<Permission> findPermissionsOfRole(Long roleId) {
    List<RolePermission> links = findByRole(roleId);
    if (links.isEmpty()) {
      return List.of();
    }
    List<Long> permissionIds =
        links.stream().map(RolePermission::getPermissionId).distinct().toList();
    return QueryBuilder.from(Permission.class).where(Permission::getId).in(permissionIds).list();
  }
}
