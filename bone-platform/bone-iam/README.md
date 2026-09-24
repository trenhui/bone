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

## domain 分组形态（E-10 登记）

**目标形态**（[ADR-0036](../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`——聚合根 / 聚合内实体 / 值对象在聚合包内**直接平铺**，`event/` `projection/` 为聚合内子包；`repository` / `gateway` 端口留在 `domain/` 根。

**本模块现状**：**已迁移至目标形态**（2026-09-22 完成，ADR-0036 顺序中的最后一棒）。聚合构件全部位于 `domain/model/{account,app,audit,dept,menu,permission,role,session,tenant}/`；`domain/{client,gateway,repository}` 留在 `domain/` 根。

| 迁移前 | 迁移后 |
|---|---|
| `domain/{account,app,audit,dept,menu,permission,role,session,tenant}` | `domain/model/{聚合}/` |
| `{聚合}/event`（`account` / `audit` / `dept` / `menu` / `permission` / `role`） | `domain/model/{聚合}/event` |
| `{聚合}/vo`（`account` / `app` / `audit` / `permission` / `role`） | `domain/model/{聚合}/valueobject`（按 ADR-0036 D2 已统一命名） |
| `domain/repository`、`domain/gateway`、`domain/client` | **不变**（端口不进 `model/`）；`client`（`SsoClient` / `StorageClient`）是**端口接口**而非聚合模型，故留根 |

## 应用层结构（无 service 子包）

应用层入口构件只有语义化 `*ApplicationService`（平铺 `application/` 根目录，见 ADR-0028）。**不另设 `application/service/`「服务层」**——它曾制造与 `*ApplicationService` 同层竞争的「第二编排层」（[ADR-0033](../../doc/architecture/adr/0033-application-collaboration-service.md) 已撤销，由 [ADR-0035](../../doc/architecture/adr/0035-application-layer-keeps-only-application-service.md) 取代）。

跨切面复用逻辑**不再另设服务层**，按 ADR-0035 D3 的四个落点归位。本模块现行三个角色包/助手是**存量**（登记在 `doc/architecture/application-constructs-baseline.json`，只可收缩）：

| 现存构件 | 落点（ADR-0035 D3） | 说明 |
|---|---|---|
| `AccountRoleBindingService`（`application/binding/`） | 绑定写入 → 聚合不变量；权限缓存失效 → `application/port/out` + `infrastructure` | AS 只保留编排 |
| `TenantQuotaEnforcer`（`application/policy/`） | 配额规则 → `domain/service`（接收计数值、不做 IO） | 计数已下沉 `AccountRepository#countByTenant` / `RoleRepository#countByTenant`（E-4.2 已收敛） |
| `PasswordPolicyValidator`（`application/policy/`） | 无 IO 纯规则 → `domain/service` 或值对象 | 迁移时须把失败语义改为 `DomainException` + 应用层翻译（E-5.3.1） |
| `RoleHierarchyResolver`（`application/` 根） | 纯图算法 → 只服务登录用例则内联 `AuthApplicationService`，否则做 `domain/service` 普通类 | 取数已下沉 `RoleRepository#findByIds` |

> **`@Service` 不构成"留在应用层"的理由**：`domainCoreShouldOnlyDependOnAllowedPackages` 禁止 domain 依赖 Spring stereotype，正解是让领域类**不带 stereotype**（由应用层构造，或在 `config` 类里用 `@Bean` 装配），而不是把它挪回 application 并新开一个角色包。

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
| `iam_session` | 会话（网关/Redis 持久化，非关系表，经 `RefreshTokenSessionGatewayAdapter`） | Session |
| `bone_application` | 应用 | Application |
| `bone_module` | 模块 | Module |
| `bone_app_permission` | 应用-权限授权 | Application/Permission |
