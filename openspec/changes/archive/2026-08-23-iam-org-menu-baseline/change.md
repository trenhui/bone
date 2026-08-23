# Change: IAM 组织树与菜单基座

**Type**: Feature（共享基座）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段1 T2）。基于前后端深度扫描：IAM 后端 90%、前端 95%，但缺组织树/菜单，导致主数据部门授权、系统权限树、Shell 动态菜单无共享前置。
**Depends on**: 无

## Intent
为 Bone 平台补齐 IAM 的组织机构（Dept）树与菜单（Menu）聚合及 CRUD 能力，使其成为所有业务模块共享的权限/归属基座。

## Scope (In)
- `bone-iam` 后端：新增 `Dept` 聚合（树形、多租户）+ `Menu` 聚合 + Command/Query Handler + controller（`@EnableSqlRepositories` 持久化）。
- `bone-iam-app` 前端：组织机构管理页（树形）、菜单管理页（树形/CRUD），接后端 `/api/v1/iam` 现有约定。
- 复用现有 CQRS 分层与 `ApiResponse`/`PageResult` 统一响应。

## Scope (Out / Non-Goals)
- 不重写权限引擎、不改变现有 RBAC 模型（账号经角色）。
- 不做多租户组织隔离改造（保持现有 TenantContext 机制）。

## Assumptions
- `bone-iam` 已有 `TenantAbstractEntity` 基类与 SqlRepository 基础设施，可直接复用。
- 前端 iam-app 路由已就位，仅需新增两个页面与 menu 项。

## Acceptance Criteria
- [ ] Dept 树形 CRUD 可用（增删改查、父子层级）。
- [ ] Menu 树形 CRUD 可用。
- [ ] 前端组织/菜单管理页接后端真实 API（非 mock）。
- [ ] `BoneDddArchRules` ArchUnit 校验通过（分层未破坏）。
- [ ] 代码经 `mvn spotless:apply` 格式一致。
