package com.bone.iam.domain.model.permission;

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
        // G5 元数据权限码拆分（2a §4.3）：建模 / 运行时 / 平台模板；旧三码保留为 deprecated 别名
        "metadata:model:read",
        "metadata:model:write",
        "metadata:runtime:read",
        "metadata:runtime:write",
        "metadata:template:read",
        "metadata:template:write",
        "iam:accounts:read",
        "iam:accounts:write",
        "iam:apps:read",
        "iam:apps:write",
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
        "sys:console:read",
        // system 平台域写门禁（全局表写端点 @PreAuthorize；租户不应写全局配置/字典/调度任务）
        "sys:config:write",
        "sys:dict:write",
        "sys:schedule:write",
        // 主数据（G6 落地：3a 设计 §4.4 权限码全集）
        "masterdata:entities:read",
        "masterdata:entities:write",
        "masterdata:records:read",
        "masterdata:records:write",
        "masterdata:records:approve",
        "masterdata:categories:read",
        "masterdata:categories:write",
        "masterdata:templates:read",
        "masterdata:templates:write",
        "masterdata:templates:instantiate",
        "masterdata:subscriptions:write",
        "masterdata:quality:write",
        "masterdata:reference:read",
        "masterdata:reference:write",
        "masterdata:governance:write");
  }
}
