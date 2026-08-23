---
archived-with: 2026-08-23-iam-org-menu-baseline
status: final
---
# Design: IAM 组织树与菜单基座

> Comet Classic `full` workflow — design 阶段产物。
> Change: `iam-org-menu-baseline`
> 依赖：无。被 `frontend-shell-dynamic` 消费（动态菜单 `GET /api/v1/iam/menu/current`、改密 `POST /api/v1/iam/me/password`）。

## 现有架构范式（已探查确认）

- **聚合根基类**：`com.bone.core.domain.TenantAggregateRoot<Long>`（`change.md` 中假设的 `TenantAbstractEntity` 不存在，已纠正）。`@Table` + `@Id` + `@GeneratedValue(strategy = DISTRIBUTED_ID)`。工厂方法 `static create(...)` 内 `addDomainEvent(...)`。
- **Repository**：空接口继承 `com.bone.metadata.sdk.Repository<Agg, Long>`，持久化由 SDK `@EnableSqlRepositories` 代理，提供 `save/findById/update` 及 Criteria/QueryBuilder 查询能力（**无需手写 Sql 实现**）。
- **CommandHandler**：`application/command/handler/` 下 `@Capability` + `@Component` + `@RequiredArgsConstructor` + `@Transactional`，注入 Repository，返回 id 或 void。tenantId 优先命令值 → `TenantContext.getTenantIdAsLong()` → 默认 0L。
- **Controller**：`adapter/web/controller/` 下 `@RestController @RequestMapping(PlatformApiPaths.IAM_V1 + "/xxx")`，注入 Handler，`@PreAuthorize("hasAuthority('iam:xxx:read|write')")`，返回 `ApiResponse.success(...)`。
- **改密已有**：`ChangeMyPasswordCommandHandler` 存在，但 `AuthController` 仅暴露 login/logout/refresh，**缺 change-password HTTP 端点**。

## 后端设计

### Dept 聚合（组织树，多租户）
- `domain/dept/Dept.java`：`TenantAggregateRoot<Long>`，字段 `name, parentId(Long), orderNo, status`，工厂 `create(...)`、方法 `update(...)`/`moveParent(...)`。
- `domain/repository/DeptRepository.java`：空接口 `extends Repository<Dept, Long>`。
- `application/command/cmd/`：`CreateDeptCommand`/`UpdateDeptCommand`/`DeleteDeptCommand`。
- `application/command/handler/`：`CreateDeptCommandHandler`/`UpdateDeptCommandHandler`/`DeleteDeptCommandHandler`（`@Transactional`）。
- `application/query/dto/DeptDTO.java`、`DeptTreeDTO.java`。
- `application/query/qry/DeptTreeQuery.java` + `DeptTreeQueryHandler`：从 Repository 取全部（按 tenantId），内存递归组装树 `List<DeptTreeDTO>`（`children` 嵌套）。
- `adapter/web/controller/DeptController.java`：`IAM_V1 + "/depts"`
  - `POST /` create（iam:depts:write）
  - `PUT /{id}` update（iam:depts:write）
  - `DELETE /{id}` delete（iam:depts:write）
  - `GET /tree` 树（iam:depts:read）—— 供菜单/授权前端拉组织树
- DTO/Req/Resp：`adapter/web/dto/req/CreateDeptReq`/`UpdateDeptReq`、`resp/DeptTreeResp`。

### Menu 聚合（菜单树，多租户）
- `domain/menu/Menu.java`：`TenantAggregateRoot<Long>`，字段 `name, parentId(Long), path, icon, orderNo, permission, type`，工厂 `create(...)`。
- `domain/repository/MenuRepository.java`：空接口。
- `application/command/cmd/` + `handler/`：Create/Update/Delete（同 Dept）。
- `application/query/dto/MenuDTO.java`、`MenuTreeDTO.java`。
- `application/query/qry/MenuTreeQuery.java` + `MenuTreeQueryHandler`：组装菜单树。
- `adapter/web/controller/MenuController.java`：`IAM_V1 + "/menus"`
  - `POST /` `PUT /{id}` `DELETE /{id}`（iam:menus:write）
  - `GET /tree`（iam:menus:read）

### 当前用户菜单（Shell 动态菜单数据源）★ 关键
- `MenuController` 新增 `GET /current`（`iam:menus:read`）：返回当前用户可见菜单树。
  - 实现：`MenuCurrentQueryHandler` 取当前租户所有菜单 → 按当前用户权限码（`SecurityContext` 中 authorities）过滤 `permission` 非空节点 → 组装 `List<MenuNode>`（复用 `shared-types` 的 `MenuNode` 契约：`id,parentId,name,path,icon,order,permission,children`）。
  - 返回 `ApiResponse<List<MenuNode>>`，前端 `frontend-shell-dynamic` 的 `buildMenuFromNodes` 直接消费。

### 改密端点（对齐 frontend-shell-dynamic 契约）
- `AuthController` 新增 `POST /me/password`（`@PreAuthorize` 登录即可，需账号归属校验）：接收 `{currentPassword, newPassword}`，转 `ChangeMyPasswordCommand`（需解析当前登录账号 id），调 `ChangeMyPasswordCommandHandler`。
- 注：前端 `Profile.tsx` 调 `authService.changePassword` → `POST /api/v1/auth/change-password`。**契约分歧**：前端用 `/api/v1/auth/`，后端在 `/api/v1/iam`。两个选择：
  - (A) 后端在 `IAM_V1`（`/api/v1/iam`）新增 `/me/password`，前端改 base 为 `/api/v1/iam`（需改 `authService` 的 AUTH_BASE）—— 更合规。
  - (B) 后端同时暴露 `/api/v1/auth/change-password` 作为别名。
  - **采用 (A)**：一致性优先；同步把 `shared-services` 的 `AUTH_BASE` 由 `/api/v1/auth` 改为 `/api/v1/iam`，并补 `changePassword` 路径为 `/me/password`。前端 `frontend-shell-dynamic` 已归档，需小修 `authService.ts`（AUTH_BASE + changePassword 路径）。此修正在本 change 一并进行并验证。

## 前端设计

- `packages/shared-types/src/iam.ts`：追加 `Organization`/`Menu`/`MenuTree` 接口（对齐后端 DTO）。
- `bone-iam-app/src/services/api.ts`：追加 `deptApi`/`menuApi` 对象（`tree`/`create`/`update`/`delete`）。
- 新增 `pages/OrganizationManagement.tsx`：antd `Tree`（左侧组织树）+ `Table`/`Drawer` 表单增删改（仿 `AccountManagement.tsx`）。
- 新增 `pages/MenuManagement.tsx`：antd `Tree` + 抽屉表单。
- `App.tsx`：新增两条 `<Route>`（`/organizations`、`/menus`，`ProtectedRoute` 包裹）。
- `bone-shell/src/App.tsx`：在 `iam` 分组 `children` 追加「组织机构」「菜单管理」两项（`ApartmentOutlined`/`MenuOutlined`）。
- `shared-services/src/authService.ts`：AUTH_BASE 改 `/api/v1/iam`，`changePassword` 路径 `/me/password`。

## 校验
- 后端：`mvn -pl bone-iam -am spotless:apply` + `BoneDddArchRules` ArchUnit（分层约束：`adapter` 依赖 `application`/`domain`，不反向）。
- 前端：`iam-app` `npm run build`；`shared-services` `tsc` + `vitest`；`shell` 编译（受存量债阻塞，记录接受）。
- 改密链路端到端：前端 Profile 提交 → `/api/v1/iam/me/password` → `ChangeMyPasswordCommandHandler`（人工 dev 验证）。

## 风险
- 树组装逻辑（递归 parentId）需单测或手工验证。
- `@PreAuthorize` 权限码需与前端菜单 `permission` 字段一致；`/current` 过滤依赖 `SecurityContext` authorities，需确认 IAM 已注入。
- mvn 构建环境依赖完整（首次可能下载依赖耗时）。
