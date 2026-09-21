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

## 应用层协作服务（`application/service/`）

落点定义与判据见 [ADR-0033](../../doc/architecture/adr/0033-application-collaboration-service.md) D4：可依赖 `domain`，**不持有事务、不承载聚合不变量、不得出现读侧 DSL**（E-10.2 / E-4.2）。本模块现行构件：

| 类 | 用途 | 命中判据 | 备注 |
|---|---|---|---|
| `AccountRoleBindingService` | 账号-角色绑定替换与回读（含权限缓存失效） | D4-3① | 被 4 个用例调用；**待收敛**：`Criteria` 出现在 application（E-4.2） |
| `RolePermissionBindingService` | 角色-权限绑定替换（含权限缓存失效） | D4-3③ | 与上者同构的协作步骤；**待收敛**：`Criteria` 出现在 application（E-4.2） |
| `AuthService` | 认证辅助：按用户名查账号、密码比对（经 `PasswordEncoderPort`） | D4-3② | — |
| `AuditService` | 审计日志落库（`AuditLog.create` + 保存） | D4-3② | 由 `common/util/AuditUtils` 调用 |
| `TenantQuotaEnforcer` | 租户配额校验（账号 / 角色上限，跨 3 个仓储） | D4-3② | 被 2 个应用服务调用；**待收敛**：`Criteria` + `countByCriteria` 出现在 application（E-4.2） |
| `PasswordPolicyValidator` | 密码强度策略（长度 + 弱口令拒绝） | 暂留 | 无 IO 的纯规则，形式命中 D4-1；但失败语义是 `IamErrors` 的业务码→HTTP 状态配对（`BizException`），迁 `domain/service` 须先改造为 `DomainException` + 应用层转换（E-5.3.1）。按触达即收敛 |
| `RoleHierarchyResolver` | 角色祖先闭包解析（按层批量查询、最多 5 层、含环检测） | **待收敛** | 在 application 内直接使用 `QueryBuilder` / `FluentQuery`，属 E-4.2 违规（目前仅由 `archunit_store` 冻结基线兜住）；取数应下沉 `domain/repository` 的 `default` 方法或 `infrastructure/query` |
| `RoleService` | 仅 `RoleRepository` 单点透传 | **待删除** | 全仓零调用方（死代码，E-3.11 第 2 条） |
| `PermissionService` | 仅 `PermissionRepository` 单点透传 | **待删除** | 全仓零调用方（死代码，E-3.11 第 2 条） |

新增同类构件前按 D4 判据顺序核对（纯规则无 IO 且异常语义可表达 → `domain/service`；只协调技术端口 → `support/`；单点透传或无调用方 → 不建）。

> **4 个"待收敛"是同一件事**：条件构造与取数应下沉 `domain/repository` 的 `default` 方法（ADR-0030——域仓储是 `QueryBuilder` / `Criteria` 的 SDK 集成点，`updateByCriteria` 由基类自带）或 `infrastructure/query`，application 只留语义化调用（如"解绑账号全部角色"、"统计租户内账号数"）。`archunit_store` 里 `readSideDslOnlyInQueryLayer` 的**全部 14 行残留都在这 4 个类**——未报红只是被冻结基线兜住，不等于合规。

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
