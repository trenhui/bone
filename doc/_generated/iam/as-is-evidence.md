# IAM 模块 As-Is 证据（CI 派生）

> **生成时间**：2026-09-17T04:49:06Z（UTC）  
> **勿手改**：由 `tools/iam-compliance-collector/collect.py` 生成。

| ID | 能力 | 证据摘要 |
|----|------|----------|
| `iam-ddl-tables` | IAM 10 张表全部入 bone-init.sql（含 iam_audit_settings） | DDL：10/10 表 |
| `iam-openapi-single-source` | iam-v1.yaml 作为 HTTP 契约单源 + bearer JWT 安全声明 | OpenAPI：33 paths；Bearer JWT |
| `iam-method-security` | @EnableMethodSecurity + @PreAuthorize 方法级鉴权 | @PreAuthorize：8 文件 / 16 权限码；`@EnableMethodSecurity` |
| `iam-tenant-context-from-jwt` | JwtAuthenticationFilter 写入 TenantContext + finally 清理 | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/JwtAuthenticationFilter.java` |
| `iam-tenant-query-filter` | Query Handler 强制按 TenantContext 过滤（非平台租户） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/AssignPermissionCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateDeptCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateMenuCommandHandler.java`；… +13 |
| `iam-jwt-scopes-claim` | JWT claim `scopes`（权限码）+ refresh token + 黑名单 | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuthController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/IamJwtTokenService.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/JwtAuthenticationFilter.java`；… +2 |
| `iam-audit-settings` | 审计设置 GET/PUT 经 Handler + AuditSettingsStore | DDL 有表；源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/schedule/AuditLogCleanupJob.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuditController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateAuditSettingsCommandHandler.java`；… +3 |
| `iam-password-policy` | 弱口令策略 + 登录 requirePasswordChange | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/converter/AuthWebConverter.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/dto/response/LoginResp.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java`；… +5 |
| `iam-refresh-reuse` | Refresh Token 复用检测（replaced_by） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/RefreshTokenService.java` |
| `iam-role-tenant-guard` | 角色权限分配租户一致性校验 | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/AssignPermissionCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/RevokeSessionCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java` |
| `iam-audit-cleanup-job` | 审计日志按保留天数清理 Job | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/schedule/AuditLogCleanupJob.java` |
| `iam-tenant-controller` | TenantController CRUD + 启停 + DELETE + /quota | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/TenantController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/DeleteTenantCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateTenantQuotaCommandHandler.java` |
| `iam-tenant-quota` | 租户配额列 + TenantQuotaEnforcer | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateAccountCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateRoleCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/TenantQuotaEnforcer.java` |
| `iam-init-demo-password-gate` | init 演示弱口令 CI ACK 门禁 | — |
| `iam-login-lockout` | 登录失败锁定（threshold + lockMinutes 可配，含 LOCKED 状态校验） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/LoginCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/config/IamPasswordProperties.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java`；… +1 |
| `iam-role-hierarchy` | 角色继承闭包求值（parent_role_id，最大深度 5，环检测） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AccountAuthoritiesQueryHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/RoleHierarchyResolver.java` |
| `iam-sessions-api` | 会话管理 API（在线会话 + 强制下线，iam:sessions:* 权限码） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/SessionController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/RevokeSessionCommandHandler.java`；… +4 |
| `iam-me-self-service` | 个人信息自助 API（/me + /me/change-password，仅认证不需权限码） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/MeController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateMyProfileCommandHandler.java`；… +2 |
| `iam-password-expiry` | 密码到期策略（max-age-days → requirePasswordChange） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/dto/response/MeResp.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/LoginCommandHandler.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/application/config/IamPasswordProperties.java`；… +2 |
| `iam-audit-csv-export` | 审计日志 CSV 导出（UTF-8 BOM + Content-Disposition） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuditController.java` |
| `iam-mfa-sso-501-contract` | MFA / SSO 未启用 501 契约（社区版） | 源码：`bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuthController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/MfaController.java`, `bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java` |
| `iam-archunit` | ArchUnit 分层 + 仓储白名单 | 源码：`bone-platform/bone-iam/src/test/java/com/bone/iam/architecture/ArchitectureTest.java` |
| `iam-gateway-route-it` | Gateway → bone-iam 路由 IT | 源码：`bone-platform/bone-gateway/src/test/java/com/bone/gateway/IamGatewayRouteIT.java` |
| `iam-permission-codes-catalog` | 跨模块权限码注册表（iam:*, metadata:*, extension:*） | `DefaultPermissionCodes`；`bonePermissionCodes.ts` |

## 明细

### `iam-ddl-tables` — IAM 10 张表全部入 bone-init.sql（含 iam_audit_settings）

```json
{
  "expected": [
    "iam_tenant",
    "iam_account",
    "iam_role",
    "iam_permission",
    "iam_account_role",
    "iam_role_permission",
    "iam_audit_log",
    "iam_audit_settings",
    "iam_policy",
    "iam_refresh_token"
  ],
  "present": [
    "iam_account",
    "iam_account_role",
    "iam_audit_log",
    "iam_audit_settings",
    "iam_permission",
    "iam_policy",
    "iam_refresh_token",
    "iam_role",
    "iam_role_permission",
    "iam_tenant"
  ],
  "missing": []
}
```

### `iam-openapi-single-source` — iam-v1.yaml 作为 HTTP 契约单源 + bearer JWT 安全声明

```json
{
  "openapi_file": "doc/architecture/openapi/iam-v1.yaml",
  "path_count": 33,
  "paths": [
    "/login",
    "/logout",
    "/refresh",
    "/accounts",
    "/accounts/{id}",
    "/accounts/{id}/enable",
    "/accounts/{id}/disable",
    "/accounts/{id}/reset-password",
    "/me",
    "/me/change-password",
    "/accounts/{accountId}/sessions",
    "/sessions/{id}",
    "/accounts/import",
    "/accounts/export",
    "/sso/config",
    "/sso/callback",
    "/mfa/status",
    "/mfa/enroll",
    "/mfa/verify",
    "/roles",
    "/roles/{id}",
    "/roles/{id}/permissions",
    "/permissions",
    "/permissions/tree",
    "/permissions/{id}",
    "/audit/logs",
    "/audit/logs/export",
    "/audit/settings",
    "/tenants",
    "/tenants/{id}",
    "/tenants/{id}/enable",
    "/tenants/{id}/disable",
    "/tenants/{id}/quota"
  ],
  "security_bearer_jwt": true
}
```

### `iam-method-security` — @EnableMethodSecurity + @PreAuthorize 方法级鉴权

```json
{
  "security_config": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/config/SecurityConfig.java"
  ],
  "preauthorize": {
    "files": [
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AccountController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuditController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/DeptController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/MenuController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/PermissionController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/RoleController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/SessionController.java",
      "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/TenantController.java"
    ],
    "scopes": [
      "iam:accounts:read",
      "iam:accounts:write",
      "iam:audit:read",
      "iam:audit:write",
      "iam:depts:read",
      "iam:depts:write",
      "iam:menus:read",
      "iam:menus:write",
      "iam:permissions:read",
      "iam:permissions:write",
      "iam:roles:read",
      "iam:roles:write",
      "iam:sessions:read",
      "iam:sessions:write",
      "iam:tenants:read",
      "iam:tenants:write"
    ]
  }
}
```

### `iam-tenant-context-from-jwt` — JwtAuthenticationFilter 写入 TenantContext + finally 清理

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/JwtAuthenticationFilter.java"
  ]
}
```

### `iam-tenant-query-filter` — Query Handler 强制按 TenantContext 过滤（非平台租户）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/AssignPermissionCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateDeptCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateMenuCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateRoleCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/RevokeSessionCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateAuditSettingsCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AccountDetailQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AccountPageQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AuditLogListQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/DeptTreeQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/GetAuditSettingsQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/MenuCurrentQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/MenuTreeQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/RoleDetailQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/RolePageQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/SessionListQueryHandler.java"
  ]
}
```

### `iam-jwt-scopes-claim` — JWT claim `scopes`（权限码）+ refresh token + 黑名单

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuthController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/IamJwtTokenService.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/JwtAuthenticationFilter.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/RefreshTokenService.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/TokenBlacklistService.java"
  ]
}
```

### `iam-audit-settings` — 审计设置 GET/PUT 经 Handler + AuditSettingsStore

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/schedule/AuditLogCleanupJob.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuditController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateAuditSettingsCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/GetAuditSettingsQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/gateway/AuditSettingsStore.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/persistence/AuditSettingsStoreImpl.java"
  ],
  "ddl_has_table": true
}
```

### `iam-password-policy` — 弱口令策略 + 登录 requirePasswordChange

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/converter/AuthWebConverter.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/dto/response/LoginResp.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateAccountCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/LoginCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ResetPasswordCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/config/IamPasswordProperties.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/PasswordPolicyValidator.java"
  ]
}
```

### `iam-refresh-reuse` — Refresh Token 复用检测（replaced_by）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/RefreshTokenService.java"
  ]
}
```

### `iam-role-tenant-guard` — 角色权限分配租户一致性校验

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/AssignPermissionCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/RevokeSessionCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java"
  ]
}
```

### `iam-audit-cleanup-job` — 审计日志按保留天数清理 Job

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/schedule/AuditLogCleanupJob.java"
  ]
}
```

### `iam-tenant-controller` — TenantController CRUD + 启停 + DELETE + /quota

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/TenantController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/DeleteTenantCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateTenantQuotaCommandHandler.java"
  ]
}
```

### `iam-tenant-quota` — 租户配额列 + TenantQuotaEnforcer

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateAccountCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/CreateRoleCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/TenantQuotaEnforcer.java"
  ],
  "ddl": true
}
```

### `iam-init-demo-password-gate` — init 演示弱口令 CI ACK 门禁

```json
{
  "script": [
    "scripts/ci/check-iam-init-demo-password.sh"
  ],
  "marker": true
}
```

### `iam-login-lockout` — 登录失败锁定（threshold + lockMinutes 可配，含 LOCKED 状态校验）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/LoginCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/config/IamPasswordProperties.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/account/Account.java"
  ]
}
```

### `iam-role-hierarchy` — 角色继承闭包求值（parent_role_id，最大深度 5，环检测）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AccountAuthoritiesQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/RoleHierarchyResolver.java"
  ]
}
```

### `iam-sessions-api` — 会话管理 API（在线会话 + 强制下线，iam:sessions:* 权限码）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/SessionController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/RevokeSessionCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/SessionListQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/gateway/RefreshTokenSessionStore.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/permission/DefaultPermissionCodes.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/persistence/RefreshTokenSessionStoreImpl.java"
  ]
}
```

### `iam-me-self-service` — 个人信息自助 API（/me + /me/change-password，仅认证不需权限码）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/MeController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/ChangeMyPasswordCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/UpdateMyProfileCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/MenuCurrentQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/IamCurrentAccountResolver.java"
  ]
}
```

### `iam-password-expiry` — 密码到期策略（max-age-days → requirePasswordChange）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/dto/response/MeResp.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/LoginCommandHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/config/IamPasswordProperties.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/dto/AccountDTO.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/account/Account.java"
  ]
}
```

### `iam-audit-csv-export` — 审计日志 CSV 导出（UTF-8 BOM + Content-Disposition）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuditController.java"
  ]
}
```

### `iam-mfa-sso-501-contract` — MFA / SSO 未启用 501 契约（社区版）

```json
{
  "java": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuthController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/MfaController.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/common/IamErrorCodes.java"
  ]
}
```

### `iam-archunit` — ArchUnit 分层 + 仓储白名单

```json
{
  "java": [
    "bone-platform/bone-iam/src/test/java/com/bone/iam/architecture/ArchitectureTest.java"
  ]
}
```

### `iam-gateway-route-it` — Gateway → bone-iam 路由 IT

```json
{
  "java": [
    "bone-platform/bone-gateway/src/test/java/com/bone/gateway/IamGatewayRouteIT.java"
  ]
}
```

### `iam-permission-codes-catalog` — 跨模块权限码注册表（iam:*, metadata:*, extension:*）

```json
{
  "java_default": [
    "bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/handler/AccountAuthoritiesQueryHandler.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/domain/permission/DefaultPermissionCodes.java",
    "bone-platform/bone-iam/src/main/java/com/bone/iam/infrastructure/security/IamJwtTokenService.java"
  ],
  "frontend": [
    "bone-frontend/packages/shared-types/src/bonePermissionCodes.ts"
  ]
}
```
