package com.bone.iam.domain.permission;

import java.util.List;

/** 无角色/权限绑定时管理员 JWT 回退权限码。生产应在 {@code iam_permission} 中显式配置。 */
public final class DefaultPermissionCodes {

  private DefaultPermissionCodes() {}

  public static List<String> adminFallback() {
    return List.of(
        "extension:points:read",
        "extension:points:write",
        "extension:plugins:deploy",
        "metadata:read",
        "metadata:write",
        "iam:accounts:read",
        "iam:accounts:write",
        "iam:roles:read",
        "iam:roles:write",
        "iam:permissions:read",
        "iam:permissions:write",
        "iam:audit:read",
        "iam:audit:write",
        "iam:tenants:read",
        "iam:tenants:write",
        "iam:sessions:read",
        "iam:sessions:write",
        "iam:depts:read",
        "iam:depts:write",
        "iam:menus:read",
        "iam:menus:write",
        "order:orders:read",
        "order:orders:write",
        "sys:console:read");
  }
}
