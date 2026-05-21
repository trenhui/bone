package com.bone.iam.application.query.handler;

import com.bone.iam.domain.account.AccountRole;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.role.RolePermission;
import com.bone.iam.infrastructure.security.AuthorityCacheEvictionService;
import com.bone.iam.infrastructure.security.DefaultPermissionCodes;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按账号解析 RBAC 权限码（经 {@code iam_account_role} → {@code iam_role_permission} → {@code iam_permission.code}）。
 */
@Component
@RequiredArgsConstructor
public class AccountAuthoritiesQueryHandler {

    private final AuthorityCacheEvictionService authorityCacheEvictionService;

    @Transactional(readOnly = true)
    public List<String> resolvePermissionCodes(Long accountId, boolean adminAccount) {
        if (accountId == null) {
            return List.of();
        }
        return authorityCacheEvictionService
                .get(accountId)
                .orElseGet(() -> {
                    List<String> resolved = resolveFromDatabase(accountId, adminAccount);
                    authorityCacheEvictionService.put(accountId, resolved);
                    return resolved;
                });
    }

    private List<String> resolveFromDatabase(Long accountId, boolean adminAccount) {
        List<AccountRole> accountRoles = QueryBuilder.from(AccountRole.class)
                .where(AccountRole::getAccountId)
                .eq(accountId)
                .list();
        if (accountRoles.isEmpty()) {
            return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
        }
        List<Long> roleIds =
                accountRoles.stream().map(AccountRole::getRoleId).distinct().toList();
        List<RolePermission> rolePermissions = QueryBuilder.from(RolePermission.class)
                .where(RolePermission::getRoleId)
                .in(roleIds)
                .list();
        if (rolePermissions.isEmpty()) {
            return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
        }
        List<Long> permissionIds = rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .toList();
        List<Permission> permissions = QueryBuilder.from(Permission.class)
                .where(Permission::getId)
                .in(permissionIds)
                .list();
        Set<String> codes = new LinkedHashSet<>();
        for (Permission permission : permissions) {
            if (permission.getCode() != null && !permission.getCode().isBlank()) {
                codes.add(permission.getCode());
            }
        }
        if (codes.isEmpty() && adminAccount) {
            return DefaultPermissionCodes.adminFallback();
        }
        return new ArrayList<>(codes);
    }
}
