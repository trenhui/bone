# Proposal: 修复前端存量类型债务

## 目标（Why）

前端 monorepo 存在 15 个 pre-existing 类型错误（git 原始即存在），导致 3 个 app（bone-shell / bone-system-app / bone-extension-app）`tsc` 无法通过，是已归档 change 前端 build 无法全绿的根因。修复后前端整体回归「可编译、可发布」。

## 范围（What）

1. **bone-shell（5 个）**：移除未使用 import（`Space`/`LayoutOutlined`/`publishThemeChange`/`toggleLayoutMode`）；修正 qiankun `patchGlobalVal` 错误属性。
2. **bone-system-app（8 个）**：`logApi` 补 `exportLogs`/`analyzeLogs`；`configApi` 补 `getConfigHistory`/`exportConfig`/`importConfig`；修正 `MonitorAlert` 参数个数、`SystemConfig` setState 类型；清理未使用 `ConfigHistory`。
3. **bone-extension-app（2 个）**：移除 `extensionApi.ts` 重复的 `setQiankunToken` 标识符。

## 非目标（Non-Goals）

- 不改动业务逻辑（仅类型/接口对齐与未使用清理）。
- 不引入新功能。

## 验收标准（Acceptance Criteria）

- [ ] `bone-shell` tsc 0 错误。
- [ ] `bone-system-app` tsc 0 错误。
- [ ] `bone-extension-app` tsc 0 错误。
- [ ] 其余前端 app（masterdata/iam/integration）不回归。
