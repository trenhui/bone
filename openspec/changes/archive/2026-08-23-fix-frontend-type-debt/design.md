# Design: 修复前端存量类型债务

## Context
前端 monorepo 有 15 个 pre-existing 类型错误（`bone-shell` 5 / `bone-system-app` 8 / `bone-extension-app` 2），导致这些 app `tsc --noEmit` 失败。均为存量 lint/类型问题，与业务逻辑无关。

## Decision 1：bone-shell（5 个）
- 移除未使用 import：`Space`/`LayoutOutlined`/`publishThemeChange`/`toggleLayoutMode`。
- 修正 qiankun `patchGlobalVal`（`QiankunProps` 无此属性，移除或改正确属性）。

## Decision 2：bone-system-app（8 个）
- `services/api.ts`：`logApi` 补 `exportLogs`/`analyzeLogs`；`configApi` 补 `getConfigHistory`/`exportConfig`/`importConfig`。
- `LogManagement.tsx`：调用补全的 api。
- `MonitorAlert.tsx` L96：修正 `getLogs` 参数个数（Expected 1 but got 2）。
- `SystemConfig.tsx` L41：setState 从 `PageResult<SystemConfig>` 改为 `SystemConfig[]`（取 `.data.list`）；L80/93/108 用补全 api。
- 移除未使用 `ConfigHistory`。

## Decision 3：bone-extension-app（2 个）
- `extensionApi.ts` 移除重复 `setQiankunToken` 标识符。

## 验收
- 3 个 app `tsc --noEmit` 0 错误；其余 app 不回归。
