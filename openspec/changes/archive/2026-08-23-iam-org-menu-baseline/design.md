# Design: IAM 组织树与菜单基座

## Context
IAM 后端已 90% 完成（账号/角色/权限/租户/审计/认证），但缺组织机构树与菜单管理。这是主数据部门授权、系统权限树、Shell 动态菜单的共享前置。当前各业务模块无法按部门归属数据、Shell 菜单硬编码写死。

## Decision 1：聚合建模
- 新增 `Dept`（组织机构）聚合：继承 `TenantAbstractEntity`，含 `parentId`（树形自引用）、`name`、`code`、`orderNo`、`leader`、`phone`。树形查询用递归 CTE 或内存组装（数据量小，内存组装即可）。
- 新增 `Menu`（菜单）聚合：含 `parentId`、`name`、`path`、`component`、`icon`、`sort`、`permission`、`type`（目录/菜单/按钮）。

## Decision 2：持久化与分层
- 复用 `bone-iam` 现有 `@EnableSqlRepositories` + `BaseRepository`；PO 命名 `IamDeptDO`/`IamMenuDO`。
- CQRS：`CreateDeptCommand`/`UpdateDeptCommand`/`DeleteDeptCommand`/`DeptTreeQuery`；`CreateMenuCommand`/.../`MenuTreeQuery`。Handler 在 `application/command/handler` 与 `application/query/handler`。
- controller 直接注入 Handler（遵循 DDD §14.3.2）。

## Decision 3：前端
- iam-app 新增 `OrganizationManagement.tsx`（ProTree 树形 + 抽屉表单）、`MenuManagement.tsx`（同上）。
- 复用 `@bone/shared-services` 的 apiClient；新增 `iam/orgApi`、`iam/menuApi`。

## Risks
- 树形删除需校验是否有子节点（软删 + 子节点级联标记）。
- Menu 与现有前端路由约定需对齐（path 字段格式）。
