# bone-iam

身份与访问管理（端口 8081）。

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md) 全量 P0。
- 多租户聚合根（`TenantAggregateRoot` + 域内 `LocalDateTime` 审计）：`Account`、`Role`、`AuditLog`。
- 单租户聚合根：`Tenant`、`Permission` 使用 `AggregateRoot` + `setId`（`Permission` 无 `tenantId` 字段）。
- 出站端口：`domain/gateway/`（`AccessTokenIssuer`、`RefreshTokenIssuer`、`AccountAuthorityCache`、`AuditSettingsGateway`、`RefreshTokenSessionGateway`、`TenantProvider`）；技术性端口（密码编码）位于 `application/port/out`（`PasswordEncoderPort`）。端口实现统一在 `infrastructure/gateway/`（命名 `*GatewayAdapter`），密码编码适配在 `infrastructure/security/`。
- 审计字段：[ADR-0018](../../doc/architecture/adr/0018-iam-localdatetime-audit.md)（域内 `LocalDateTime`）。
- 应用入口：语义化 `{X}ApplicationService` 平铺 `application/` 根目录（Application Service First，见 ADR-0028）；`application/app/`（应用/模块/应用权限聚合）已并入主包，不再保留平行 CQRS 树。
- ArchUnit：仅扫描 `src/main`（`ImportOption.DoNotIncludeTests`）；`application_no_infra` / 仓储白名单 **直接门禁**。

## 本上下文拥有的表（E-1.2 数据所有权声明）

bone-iam 是下列表的唯一写方与 Schema _owner；其他模块只读须经本模块出站端口，不得直连这些表：

| 表 | 语义 | 聚合 |
|---|---|---|
| `iam_account` | 账号（含密码哈希、状态、锁定、登录审计） | Account |
| `iam_role` | 角色 | Role |
| `iam_permission` | 权限 | Permission |
| `iam_menu` | 菜单 | Menu |
| `iam_dept` | 部门 | Dept |
| `iam_audit_log` | 审计日志 | AuditLog |
| `iam_tenant` | 租户 | Tenant |
| `iam_session` | 会话（refresh token 关联） | Session |
| `bone_application` | 应用 | Application |
| `bone_module` | 模块 | Module |
| `bone_app_permission` | 应用-权限授权 | Application/Permission |
