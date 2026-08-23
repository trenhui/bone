---
archived-with: 2026-08-23-frontend-shell-dynamic
status: final
---
# Implementation Plan: frontend-shell-dynamic

> Comet Classic `full` workflow 实现计划（桥梁产物）。
> Change: `frontend-shell-dynamic`
> Design Doc: `docs/superpowers/design/frontend-shell-dynamic.md`
> Tasks: `openspec/changes/frontend-shell-dynamic/tasks.md`

## 依赖现状与缓解

- **依赖 `iam-org-menu-baseline` 的 Menu 聚合 API 与改密接口**：尚未实现。
- 缓解策略：在 `@bone/shared-types` 先定义契约（`MenuNode` / `NotificationSummary`），Shell 侧实现「真实调用 + 本地 fallback」双路径；后端就绪后直接切换，无需改前端代码路径。

## 任务分解与执行顺序

### P1 类型与契约（shared-types）
- [x] 新增 `packages/shared-types/src/menu.ts`：`MenuNode`、`MenuList`、`NotificationSummary` 类型。
- [x] `packages/shared-types/src/index.ts` 导出 `./menu`。

### P2 shared-services 补齐
- [x] `authService.ts` 补 `changePassword(payload: ChangePasswordRequest): Promise<void>` → `POST /api/v1/auth/change-password`。
- [x] `shared-types/src/iam.ts` 或本文件定义 `ChangePasswordRequest`（oldPassword / newPassword）。
- [x] 新增 `notificationService.ts`：`getUnreadCount(): Promise<number>`（`GET /api/v1/notifications/unread/count`，fallback 0）。
- [x] `index.ts` 导出 `notificationService`。

### P3 Shell 动态菜单（T1）
- [x] `bone-shell/src/App.tsx`：
  - `AppContent` 组件 boot 时调用 `GET /api/v1/iam/menu/current` 拉取菜单树；
  - 成功则按 `MenuNode` 渲染侧边栏；失败/无数据回退现有 `menuConfig`。
  - 保留现有硬编码 `menuConfig` 作为 fallback 常量。

### P4 路由守卫（T2）
- [x] `App.tsx` 中 6 个微应用路由（`/iam`、`/metadata`、`/integration`、`/masterdata`、`/generator`、`/extension`）统一用 `<Authorized required="...">` 包裹（保留 Dashboard/`/` 与 login/notfound 不包裹）。

### P5 个人中心 / 改密（T3）
- [x] 新增 `bone-shell/src/pages/Profile.tsx`：用户信息卡片 + 改密表单（antd Form），提交调 `authService.changePassword`。
- [x] 顶部 `userMenu` 的「个人中心」改为跳转 `/profile`（新增路由 `/profile`）。
- [x] 通知 Badge：`BellOutlined` 的 `count` 改为 hook `useUnreadCount()`（接 notificationService，fallback 0）。

### P6 测试补齐（T4）
- [x] 新建 vitest 配置（若缺）：`packages/shared-services/vitest.config.ts` + 测试脚本。
- [x] `apiClient` 单测：token 注入、401 事件派发、ApiResponse 解包。
- [x] `authService` 单测：login/token 写入、changePassword 调用。
- [x] Shell 菜单加载单测（mock apiClient，验证 fallback 与远程切换）。

### P7 校验（T5）
- [x] `npm run build` 全绿（含 lint）。
- [x] `vitest run` 通过。

## 关键文件清单
- `bone-frontend/packages/shared-types/src/menu.ts`（新建）
- `bone-frontend/packages/shared-types/src/index.ts`（改）
- `bone-frontend/packages/shared-services/src/authService.ts`（改）
- `bone-frontend/packages/shared-services/src/notificationService.ts`（新建）
- `bone-frontend/packages/shared-services/src/index.ts`（改）
- `bone-frontend/apps/bone-shell/src/App.tsx`（改）
- `bone-frontend/apps/bone-shell/src/pages/Profile.tsx`（新建）
- `bone-frontend/apps/bone-shell/src/pages/Profile.test.tsx`（新建）

## 风险
- 改密接口/菜单接口契约未定 → 类型先行 + 后端就绪切换。
- vitest 环境（jsdom）需配置 → 用 @testing-library/react + jsdom。
