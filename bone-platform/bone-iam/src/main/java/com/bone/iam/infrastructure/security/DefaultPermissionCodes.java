package com.bone.iam.infrastructure.security;

import java.util.List;

/**
 * 无角色/权限绑定时管理员 JWT 回退权限码（与历史硬编码及 extension studio 种子一致）。
 * 生产应在 {@code iam_permission} + {@code iam_role_permission} 中显式配置，避免依赖回退。
 */
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
                "iam:roles:write");
    }
}
