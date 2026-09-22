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

## 应用层结构（无 service 子包）

应用层入口构件只有语义化 `*ApplicationService`（平铺 `application/` 根目录，见 ADR-0028）。**不另设 `application/service/`「服务层」**——它曾制造与 `*ApplicationService` 同层竞争的「第二编排层」（[ADR-0033](../../doc/architecture/adr/0033-application-collaboration-service.md) 已撤销，详见 Bone-DDD 5.5.16）。跨切面复用逻辑按语义化子包归位：

- `application/binding/`：账号-角色、角色-权限绑定的替换与回读（含权限缓存失效）。
- `application/policy/`：密码强度策略、租户配额校验。
- 其余助手（如纯算法）以独立 `*Resolver` 等形式置于 `application/` 根，命名不与用例入口混淆。

本模块现行构件：

| 类 | 包 | 用途 | 命中判据 | 备注 |
|---|---|---|---|---|
| `AccountRoleBindingService` | `binding` | 账号-角色绑定替换与回读（含权限缓存失效） | — | 条件构造下沉 `AccountRoleRepository#replaceBindingsForAccount`（E-4.2 已收敛，ADR-0030） |
| `TenantQuotaEnforcer` | `policy` | 租户配额校验（账号 / 角色上限，跨 2 个仓储） | — | 计数下沉 `AccountRepository#countByTenant` / `RoleRepository#countByTenant`（E-4.2 已收敛） |
| `PasswordPolicyValidator` | `policy` | 密码强度策略（长度 + 弱口令拒绝） | — | 纯规则，失败语义为 `IamErrors` 业务码→HTTP 状态配对（`BizException`）；迁 `domain/service` 须先改 `DomainException` + 应用层转换（E-5.3.1），按触达即收敛 |
| `RoleHierarchyResolver` | `application`（根，独立 `*Resolver` 助手） | 角色祖先闭包解析（按层批量查询、最多 5 层、含环检测） | — | 取数已下沉 `RoleRepository#findByIds`（E-4.2 已收敛），仅保留纯图算法（BFS + 环检测 + 深度截断） |

> 原 `application/service/` 下的 `AuthService`（登录定位 / 密码校验）已内联进 `AuthApplicationService`、`AuditService`（审计落库）已并入 `AuditApplicationService`、`AuditUtils`（静态审计工具）已随审计落库归口删除；三者不再作为独立类存在。角色-权限绑定赋值用例（`assignPermission`）已归位 `RoleApplicationService`（含跨租户守卫），不再经独立的 `RolePermissionBindingService`。

> **E-4.2 收口已完成**：原 4 个在 application 内直写 `Criteria` / `QueryBuilder` 的类（`AccountRoleBindingService`、`RolePermissionBindingService`、`TenantQuotaEnforcer`、`RoleHierarchyResolver`），其读侧 DSL 已全部下沉 `domain/repository` 的 `default` 方法（ADR-0030）；`archunit_store` 中对应的 `readSideDslOnlyInQueryLayer` 残留基线已随本次改动被 FreezingArchRule 清除，该门禁可解除冻结转真门禁。`RoleService` / `PermissionService` 因全仓零调用方（死代码）已删除。

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
