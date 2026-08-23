# Tasks: 修复前端存量类型债务

## 1. bone-shell（5 个）
- [x] 移除未使用 import：`Space`/`LayoutOutlined`/`publishThemeChange`/`toggleLayoutMode`
- [x] 修正 qiankun `patchGlobalVal`/`looseSandbox`（QiankunProps 无此属性，移除）

## 2. bone-system-app（8 个）
- [x] `logApi` 补 `exportLogs`/`analyzeLogs`
- [x] `configApi` 补 `getConfigHistory`/`exportConfig`/`importConfig`
- [x] 修正 `MonitorAlert` `updateAlertRule` 单参数调用
- [x] 修正 `SystemConfig` `setConfigs` 取 `.list`、`getConfigHistory` 返回 `ConfigHistory[]`
- [x] 清理未使用 `ConfigHistory`（改用 shared-types 类型）

## 3. bone-extension-app（2 个）
- [x] 移除 `extensionApi.ts` 重复 `setQiankunToken` re-export

## 4. 校验
- [x] shell/system/extension 三 app `tsc --noEmit` 0 错误
- [x] 全部 6 个前端 app 0 错误，无回归
