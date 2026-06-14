package com.bone.iam.application.query.handler;

import com.bone.iam.application.service.RoleHierarchyResolver;
import com.bone.iam.domain.account.AccountRole;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.permission.DefaultPermissionCodes;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.role.RolePermission;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按账号解析 RBAC 权限码（经 {@code iam_account_role} → {@code iam_role_permission} → {@code
 * iam_permission.code}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountAuthoritiesQueryHandler {

  private final AccountAuthorityCache accountAuthorityCache;
  private final RoleHierarchyResolver roleHierarchyResolver;

  @Transactional(readOnly = true)
  public List<String> resolvePermissionCodes(Long accountId, boolean adminAccount) {
    if (accountId == null) {
      return List.of();
    }
    return accountAuthorityCache
        .get(accountId)
        .orElseGet(
            () -> {
              List<String> resolved = resolveFromDatabase(accountId, adminAccount);
              accountAuthorityCache.put(accountId, resolved);
              return resolved;
            });
  }

  private List<String> resolveFromDatabase(Long accountId, boolean adminAccount) {
    try {
      List<AccountRole> accountRoles =
          QueryBuilder.from(AccountRole.class)
              .where(AccountRole::getAccountId)
              .eq(accountId)
              .list();
      log.debug(
          "Found {} account-role associations for account {}", accountRoles.size(), accountId);
      if (accountRoles.isEmpty()) {
        return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
      }
      List<Long> directRoleIds =
          accountRoles.stream().map(AccountRole::getRoleId).distinct().toList();
      // 展开 parent_role_id 闭包，使继承角色的权限码自动并入（详设 §3.2 / IAM-22）。
      Set<Long> closure = roleHierarchyResolver.resolveClosure(directRoleIds);
      List<Long> closureRoleIds = List.copyOf(closure);
      List<RolePermission> rolePermissions =
          QueryBuilder.from(RolePermission.class)
              .where(RolePermission::getRoleId)
              .in(closureRoleIds)
              .list();
      if (rolePermissions.isEmpty()) {
        return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
      }
      List<Long> permissionIds =
          rolePermissions.stream().map(RolePermission::getPermissionId).distinct().toList();
      List<Permission> permissions =
          QueryBuilder.from(Permission.class).where(Permission::getId).in(permissionIds).list();
      Set<String> codes = new LinkedHashSet<>();
      for (Permission permission : permissions) {
        if (permission.getCode() != null && !permission.getCode().isBlank()) {
          codes.add(permission.getCode());
        }
      }
      // 管理员账户始终合并 fallback 权限，避免因数据库数据不完整导致权限缺失
      if (adminAccount) {
        codes.addAll(DefaultPermissionCodes.adminFallback());
      }
      return new ArrayList<>(codes);
    } catch (Exception e) {
      log.error(
          "Failed to query account roles for account {}, using admin fallback: {}",
          accountId,
          e.getMessage());
      // 数据库数据异常时，管理员账户使用 fallback 权限
      return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
    }
  }
}
