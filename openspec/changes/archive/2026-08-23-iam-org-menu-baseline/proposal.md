# Proposal: IAM 组织树与菜单基座

## Why

前后端深度扫描显示：IAM 后端聚合度 90%、前端 95%，平台已具备账号/角色/权限能力，但**缺组织机构（Dept）树与菜单（Menu）聚合**。这导致：

- 主数据（masterdata）部门授权无共享前置；
- 系统（system）权限树无归属基座；
- 刚完成的 `frontend-shell-dynamic` 动态菜单（`GET /api/v1/iam/menu/current`）仍依赖本 change 提供真实数据，目前只能 fallback 静态配置。

补齐 Dept/Menu 聚合后，所有业务模块可共享统一的组织归属与菜单基座。

## What Changes

- **后端 `bone-iam`**：新增 `Dept` 聚合（树形、多租户，parentId 自引用）+ `Menu` 聚合（树形、多租户）。
  - 每个聚合含 DO（`@Table` + `TenantAggregateRoot<Long>`）、Repository 接口 + Sql 实现、Create/Update/Delete/Tree Command + Handler、TreeQuery + Handler。
  - 新增 `DeptController`（`/api/v1/iam/depts`）、`MenuController`（`/api/v1/iam/menus`），返回 `ApiResponse`/`PageResult`。
  - 新增 `GET /api/v1/iam/menu/current`（当前用户可见菜单树，供 Shell 动态菜单消费）。
- **前端 `bone-iam-app`**：新增「组织机构管理」页（antd Tree + 抽屉表单）、「菜单管理」页，接上述真实 API；在 Shell 静态菜单 `iam` 分组追加对应菜单项。
- 复用现有 CQRS 分层、`BoneDddArchRules` 架构约束与 `ApiResponse`/`PageResult` 统一响应。

## Impact

- 新后端聚合与接口（不破坏现有 RBAC 模型，不改权限引擎）。
- 前端新增 2 页 + Shell 菜单项 2 条。
- 打通 `frontend-shell-dynamic` 的动态菜单真实数据源与改密接口契约。
- 依赖运行环境：`mvn`（后端 spotless + ArchUnit）、`npm`（前端构建）。
