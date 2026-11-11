# 验证报告：frontend-shell-dynamic

> Comet Classic `full` workflow 验证产物。
> Change: `frontend-shell-dynamic`
> Plan: `docs/superpowers/plans/2026-08-23-frontend-shell-dynamic.md`

## 1. 动态菜单（T1）
- Shell `App.tsx` AppContent 内新增 useEffect：登录后调用 `GET /api/v1/iam/menu/current` 拉取菜单树（`createApiClient`），经 `buildMenuFromNodes` 映射为前端菜单；失败时静默保留原有静态 `menuConfig` fallback。
- Menu 契约定义于 `shared-types/src/menu.ts`（`MenuNode`/`MenuList`），与后端 `iam-org-menu-baseline` 的 MenuVO 对齐；后端未就绪时前端可独立运行。
- 验证：新增代码路径编译通过（shell tsc 中仅剩 pre-existing 存量债，与本 change 无关）。

## 2. 路由守卫（T2）
- App.tsx 中 6 个微应用路由（`/iam`、`/metadata`、`/integration`、`/masterdata`、`/generator`、`/extension`）统一用 `<Authorized required={PermissionCodes.SYS_CONSOLE_READ}>` 包裹；`/` Dashboard 与 login 保留原样。
- 保留后端 401 兜底（apiClient 拦截器）不变。

## 3. 个人中心 / 改密 / 通知（T3）
- 新增 `bone-shell/src/pages/Profile.tsx`：用户信息卡片 + 改密表单（antd Form），提交调 `authService.changePassword`（契约 `ChangePasswordRequest{currentPassword,newPassword}`）。
- 顶部 `userMenu`「个人中心」改为 `navigate('/profile')`，并在 Routes 新增 `/profile` 守卫路由。
- 通知 Badge：`BellOutlined` 的 count 改为 `unreadCount`（来自 `notificationService.getUnreadCount()`，失败回退 0）。

## 4. shared-services 补齐与测试（T4）
- `authService.changePassword`：`POST /api/v1/auth/change-password`。
- 新增 `notificationService.getUnreadCount`：`GET /api/v1/notifications/unread/count`，fallback 0。
- Vitest（jsdom）：
  - `apiClient.test.ts`（6 tests）：token 来源链路（qiankun/localStorage/globalContext）、请求拦截器注入 Authorization、响应解包、401 派发 `bone:auth:expired`。
  - `authService.test.ts`（3 tests）：login 写入 token、logout 清理 token+localStorage、changePassword 调用。
- 修复 authService 真实缺陷：logout 未清 localStorage；login/refreshToken 未解包 ApiResponse.data。
- 验证：`npx tsc --noEmit` 通过；`npx vitest run` → 9/9 通过。

## 5. 校验（T5）
- ✅ shared-services：`tsc --noEmit` 通过 + 9 tests 通过。
- ⚠️ shell `npm run build`/`lint` 受 **pre-existing 存量技术债** 阻塞（App.tsx 的 Space/LayoutOutlined/publishThemeChange/patchGlobalVal/toggleLayoutMode 为 git 原始错误，用户确认不修复，已记录）；本 change 新增代码均编译通过。
- ✅ Plan/tasks 全部勾选；build guard 全过。

## 结论
frontend-shell-dynamic 实现与测试全部到位，新增代码编译+测试全绿。存量技术债为 pre-existing，已记录约定后续独立修复。Change 可推进归档。
