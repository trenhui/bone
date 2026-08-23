---
archived-with: 2026-08-23-iam-org-menu-baseline
status: final
---
# Implementation Plan: iam-org-menu-baseline

> Comet Classic `full` workflow 实现计划。
> Change: `iam-org-menu-baseline`
> Design: `docs/superpowers/design/iam-org-menu-baseline.md`
> Tasks: `openspec/changes/iam-org-menu-baseline/tasks.md`

## 实现顺序

### P0 共享层小修（契约对齐）
- [x] `shared-services/src/authService.ts`：`AUTH_BASE` 由 `/api/v1/auth` 改为 `/api/v1/iam`；`changePassword` 路径 `/change-password` → `/me/password`（对齐后端 `AuthController`）。

### P1 后端 Dept 聚合
- [x] `domain/dept/Dept.java`（`TenantAggregateRoot<Long>`：name/parentId/orderNo/status，工厂 create + update/moveParent）
- [x] `domain/repository/DeptRepository.java`（空接口 extends Repository<Dept,Long>）
- [x] `application/command/cmd/` Create/Update/Delete DeptCommand
- [x] `application/command/handler/` Create/Update/DeleteDeptCommandHandler（@Transactional）
- [x] `application/query/dto/` DeptDTO / DeptTreeDTO
- [x] `application/query/qry/DeptTreeQuery` + `DeptTreeQueryHandler`（取全部→内存递归建树）
- [x] `adapter/web/dto/req/` CreateDeptReq / UpdateDeptReq；`resp/` DeptTreeResp
- [x] `adapter/web/controller/DeptController.java`（IAM_V1+"/depts"：POST/PUT/DELETE/GET /tree）

### P2 后端 Menu 聚合
- [x] `domain/menu/Menu.java`（`TenantAggregateRoot<Long>`：name/parentId/path/icon/orderNo/permission/type）
- [x] `domain/repository/MenuRepository.java`
- [x] cmd/handler Create/Update/Delete Menu
- [x] query DTO MenuDTO/MenuTreeDTO
- [x] `MenuTreeQuery` + `MenuTreeQueryHandler`
- [x] `MenuController.java`（IAM_V1+"/menus"：POST/PUT/DELETE/GET /tree）
- [x] `MenuController` 新增 `GET /current`：`MenuCurrentQueryHandler`（按当前用户 authorities 过滤 permission，返回 `List<MenuNode>`，供 Shell 动态菜单）

### P3 后端改密端点
- [x] `AuthController` 新增 `POST /me/password`：解析当前登录账号 id，转 `ChangeMyPasswordCommand`，调 `ChangeMyPasswordCommandHandler`（需确认 handler 是否接受 accountId 参数——若不接受则补 overload/字段）

### P4 前端 iam-app
- [x] `shared-types/src/iam.ts` 追加 `Organization`/`MenuTree` 接口
- [x] `iam-app/src/services/api.ts` 追加 `deptApi`/`menuApi`
- [x] 新增 `pages/OrganizationManagement.tsx`（Tree + Drawer 表单）
- [x] 新增 `pages/MenuManagement.tsx`
- [x] `App.tsx` 追加 `/organizations` `/menus` 路由（ProtectedRoute）

### P5 Shell 菜单项
- [x] `bone-shell/src/App.tsx` 在 `iam` 分组 child 追加「组织机构」「菜单管理」（`ApartmentOutlined`/`MenuOutlined`）

### P6 校验
- [x] `shared-services` tsc + vitest（authService changePassword 路径变更）
- [x] `iam-app` build
- [x] `shell` 编译（受存量债阻塞，记录接受）
- [x] 后端 `mvn -pl bone-iam -am spotless:apply` + ArchUnit（运行环境依赖；若不可用则记录阻塞）

## 关键文件清单（后端）
- `bone-platform/bone-iam/src/main/java/com/bone/iam/domain/dept/Dept.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/domain/menu/Menu.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/domain/repository/{DeptRepository,MenuRepository}.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/cmd/{Create,Update,Delete}{Dept,Menu}Command.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/application/command/handler/{Create,Update,Delete}{Dept,Menu}CommandHandler.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/application/query/qry/{Dept,Menu}TreeQuery.java` + Handler
- `bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/{Dept,Menu}Controller.java`
- `bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/AuthController.java`（改密端点）

## 风险
- 后端 mvn 构建环境（依赖下载、spotless、ArchUnit）可能耗时/受限。
- 树递归组装需验证（手工/单测）。
- `/current` 权限过滤依赖 SecurityContext authorities，需确认 IAM 登录时已注入权限码。
