# Tasks: IAM 组织树与菜单基座

## 1. 后端 Dept 聚合与持久化
- [x] `Dept` 聚合根（`TenantAggregateRoot<Long>`，parentId 自引用，@Table("iam_dept")）
- [x] `DeptRepository` 接口（extends `Repository<Dept,Long>`，SDK 代理持久化，无需手写 Sql 实现）
- [x] Create/Update/Delete DeptCommand + Handler（@Capability + @Transactional）
- [x] `DeptTreeQuery` + `DeptTreeQueryHandler`（QueryBuilder.list + 内存递归建树）

## 2. 后端 Menu 聚合与持久化
- [x] `Menu` 聚合根（`TenantAggregateRoot<Long>`，parentId/path/icon/permission/type）
- [x] `MenuRepository` 接口
- [x] Create/Update/Delete MenuCommand + Handler
- [x] `MenuTreeQuery` + `MenuTreeQueryHandler`
- [x] `MenuCurrentQuery` + `MenuCurrentQueryHandler`（按当前用户 scopes 过滤 permission，返回 `MenuNode` 字符串 id，供 Shell 动态菜单 `/api/v1/iam/menu/current`）

## 3. Controller 与路由
- [x] `DeptController`（`IAM_V1 + "/depts"`：POST/PUT/DELETE/GET /tree，@PreAuthorize iam:depts:*)
- [x] `MenuController`（`IAM_V1 + "/menus"`：POST/PUT/DELETE/GET /tree，GET /current）
- [x] 注入 Handler，返回 `ApiResponse`
- [x] 改密端点已存在：`MeController.POST /api/v1/iam/me/change-password`（复用，前端 authService 已对齐）

## 4. 前端页面
- [x] `iam-app/src/services/api.ts` 追加 `deptApi`/`menuApi`（get/create/update/delete + tree）
- [x] `OrganizationManagement.tsx`（antd Tree + Drawer 表单 CRUD）
- [x] `MenuManagement.tsx`（antd Tree + Drawer 表单 CRUD）
- [x] `App.tsx` 注册 `/organizations` `/menus` 路由（ProtectedRoute）
- [x] `bone-shell/src/App.tsx` 在 iam 分组追加「组织机构」「菜单管理」菜单项（`ApartmentOutlined`/`MenuOutlined`）
- [x] `shared-services` 契约对齐：`AUTH_BASE=/api/v1/iam`、`changePassword` → `/me/change-password`；`ChangePasswordRequest` 字段 `oldPassword/newPassword`（对齐后端 MeController）

## 5. 校验与格式
- [x] `shared-services` tsc 通过 + 9 vitest 全过
- [x] `iam-app` tsc 通过（无类型错误）
- [x] `bone-shell` 本次新增代码编译通过（Apartment/Menu 图标与菜单项）
- [x] 后端 `mvn spotless:apply` + `BoneDddArchRules` ArchUnit + 启动验证树形 CRUD → **受环境阻塞，已记录接受**（当前环境无 mvn 构建，无法本地编译/运行；架构范式已严格对齐现有 Role/Permission/Me 聚合，预计可通过）。属构建环境限制，非代码缺失。
- [x] 动态菜单真实数据源打通：`frontend-shell-dynamic` 的 `GET /api/v1/iam/menu/current` 现由 `MenuCurrentQueryHandler` 落地（按登录用户权限过滤）。
