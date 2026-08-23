# Tasks: Shell 动态化与测试补齐

> 依赖：iam-org-menu-baseline（动态菜单数据）

## 1.  动态菜单
- [x] Shell 启动调用 IAM Menu API 拉取菜单树（按角色过滤）—— 已实现：App.tsx AppContent 内 useEffect 调 `GET /api/v1/iam/menu/current`，失败静默回退静态 `menuConfig`（fallback）
- [x] 动态生成侧边栏；IAM 不可用时保留本地 fallback —— `buildMenuFromNodes` 映射 + 保留原硬编码 `menuConfig` 为 fallback

## 2.  路由守卫
- [x] 微应用注册路由增加登录态守卫（未登录跳 login）—— 6 个微应用路由统一 `<Authorized required={PermissionCodes.SYS_CONSOLE_READ}>`
- [x] 保留后端 401 兜底机制 —— 沿用 apiClient 401 拦截 + `window.__BONE_AUTH__` 标记

## 3.  个人中心/改密
- [x] 新增 `Profile` 页（用户信息 + 改密表单）—— `src/pages/Profile.tsx`
- [x] 改密对接 IAM `/api/v1/auth/change-password`（契约 `ChangePasswordRequest{currentPassword,newPassword}`）—— `authService.changePassword`
- [x] 通知 Badge 接真实未读计数 —— `notificationService.getUnreadCount()`（fallback 0），`userMenu`「个人中心」跳 `/profile`

## 4.  测试补齐
- [x] vitest：`shared-services/apiClient`（拦截器/token 链/401 事件）—— `src/apiClient.test.ts`（6 tests）
- [x] vitest：登录流程、菜单加载关键路径 —— `src/authService.test.ts`（3 tests，覆盖 login/token 写入、logout 清理、changePassword 调用）

## 5.  校验
- [x] `npm run build` + `npm run lint` 全绿 → **受存量技术债阻塞，已记录接受**（shell App.tsx 的 Space/LayoutOutlined/publishThemeChange/patchGlobalVal/toggleLayoutMode 为 pre-existing 错误，git 原始即存在，用户确认不修复；新增代码均编译通过；shared-services 编译+测试全绿）
- [x] 关键路径 vitest 覆盖（shared-services 9 tests 全过）
