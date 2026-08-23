---
archived-with: 2026-08-23-fix-frontend-type-debt
status: final
---
# Plan: 修复前端存量类型债务

> 对应 `openspec/changes/fix-frontend-type-debt/tasks.md`。
> 设计：`docs/superpowers/design/fix-frontend-type-debt.md`

## 1. bone-shell（5 个）
- [x] 移除未使用 import：Space/LayoutOutlined/publishThemeChange/toggleLayoutMode
- [x] 修正 qiankun patchGlobalVal/looseSandbox（QiankunProps 无此属性，移除）

## 2. bone-system-app（8 个）
- [x] logApi 补 exportLogs/analyzeLogs
- [x] configApi 补 getConfigHistory/exportConfig/importConfig
- [x] MonitorAlert updateAlertRule 单参数修正
- [x] SystemConfig setConfigs 取 .list；getConfigHistory 返回 ConfigHistory[]
- [x] 移除未使用 ConfigHistory import（改用类型）

## 3. bone-extension-app（2 个）
- [x] 移除 extensionApi 重复 setQiankunToken

## 4. 校验
- [x] shell/system/extension tsc 0 错误；全部 6 个前端 app 0 错误，无回归
