---
archived-with: 2026-08-23-fix-frontend-type-debt
status: final
---
# Design Doc: 修复前端存量类型债务

## 1. 背景

前端 monorepo 有 15 个 pre-existing 类型错误（`bone-shell` 5 / `bone-system-app` 8 / `bone-extension-app` 2），导致这些 app `tsc --noEmit` 失败。均为存量 lint/类型问题，与业务逻辑无关。修复后前端整体可编译。

## 2. 关键决策

### Decision 1：bone-shell（5 个）
- 移除未使用 import：`Space`、`LayoutOutlined`、`publishThemeChange`、`toggleLayoutMode`。
- qiankun `patchGlobalVal`（App.tsx L344）：`QiankunProps` 无此属性，从 qiankun 配置对象移除。

### Decision 2：bone-system-app（8 个）
- `services/api.ts`：`logApi` 补 `exportLogs`/`analyzeLogs`；`configApi` 补 `getConfigHistory`/`exportConfig`/`importConfig`。
- `LogManagement.tsx`：调用补全 api。
- `MonitorAlert.tsx` L96：修正 `getLogs` 参数个数。
- `SystemConfig.tsx` L41：setState 从 `PageResult<SystemConfig>` 改 `SystemConfig[]`（取 `.data.list`）；L80/93/108 用补全 api。
- 移除未使用 `ConfigHistory`。

### Decision 3：bone-extension-app（2 个）
- `extensionApi.ts` 移除重复 `setQiankunToken` 标识符。

## 3. 修改文件

- `bone-frontend/apps/bone-shell/src/App.tsx`
- `bone-frontend/apps/bone-system-app/src/services/api.ts`、`src/pages/LogManagement.tsx`、`src/pages/MonitorAlert.tsx`、`src/pages/SystemConfig.tsx`
- `bone-frontend/apps/bone-extension-app/src/services/extensionApi.ts`

## 4. 验收

- 3 个 app `tsc --noEmit` 0 错误；其余 app 不回归。
