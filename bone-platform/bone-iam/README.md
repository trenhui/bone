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

## 应用层协作服务

落点定义与判据见 [ADR-0033](../../doc/architecture/adr/0033-application-collaboration-service.md) D4：可依赖 `domain`，**不持有事务、不承载聚合不变量、不得出现读侧 DSL**（E-10.2 / E-4.2）。按窄职责拆为三个子包：

- `application/service/`：`AuthService`（认证辅助）、`AuditService`（审计落库）、`RoleHierarchyResolver`（角色祖先闭包解析）。
- `application/binding/`：账号-角色、角色-权限绑定的替换与回读（含权限缓存失效）。
- `application/policy/`：密码强度策略、租户配额校验。

本模块现行构件：

| 类 | 包 | 用途 | 命中判据 | 备注 |
|---|---|---|---|---|
| `AccountRoleBindingService` | `binding` | 账号-角色绑定替换与回读（含权限缓存失效） | D4-3① | 条件构造下沉 `AccountRoleRepository#replaceBindingsForAccount`（E-4.2 已收敛，ADR-0030） |
| `RolePermissionBindingService` | `binding` | 角色-权限绑定替换（含权限缓存失效） | D4-3③ | 条件构造下沉 `RolePermissionRepository#replaceBindingsForRole`（E-4.2 已收敛） |
| `AuthService` | `service` | 认证辅助：按用户名查账号、密码比对（经 `PasswordEncoderPort`） | D4-3② | 全租户登录定位入口，`allTenantEntryPointsOnlyCalledBy` 白名单登记点（FQN `com.bone.iam.application.service.AuthService`） |
| `AuditService` | `service` | 审计日志落库（`AuditLog.create` + 保存） | D4-3② | 由 `common/util/AuditUtils` 调用 |
| `TenantQuotaEnforcer` | `policy` | 租户配额校验（账号 / 角色上限，跨 2 个仓储） | D4-3② | 计数下沉 `AccountRepository#countByTenant` / `RoleRepository#countByTenant`（E-4.2 已收敛） |
| `PasswordPolicyValidator` | `policy` | 密码强度策略（长度 + 弱口令拒绝） | 暂留 | 纯规则，失败语义为 `IamErrors` 业务码→HTTP 状态配对（`BizException`）；迁 `domain/service` 须先改 `DomainException` + 应用层转换（E-5.3.1），按触达即收敛 |
| `RoleHierarchyResolver` | `service` | 角色祖先闭包解析（按层批量查询、最多 5 层、含环检测） | — | 取数已下沉 `RoleRepository#findByIds`（E-4.2 已收敛），仅保留纯图算法（BFS + 环检测 + 深度截断） |

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
