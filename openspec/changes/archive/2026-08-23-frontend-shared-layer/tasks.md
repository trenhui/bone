# Tasks: 前端共享层治理（frontend-shared-layer）

> 设计依据见 `design.md`（含决策 D1 纯 axios / D2 仅 5 app / D3 移除 shell RTK）。
> 用户指定 **低风险清理（T11）先行**。

## ✅ T11（先行）：低风险清理
- [x] 移除 `bone-shell/package.json` 的 `@reduxjs/toolkit` + `react-redux`（全仓已 0 import，属死依赖；D3）
- [x] 统一 `PageResult<T>` 三态冲突为权威形态 `{list; total; pageNum; pageSize}`（iam 的 `records/data` 加 `@deprecated` 兼容；design §2.1）
- [x] `qualityApi.ts` 死代码 → **已不存在，跳过**
- [x] 运行 `npm run lint`：shell 全仓 0 处 RTK import，App.tsx 既有 lint 错误（indent/unused）与本次改动无关，未引入新错误

## 1. 收敛共享类型（T1，范围：5 app）
- [x] 在 `shared-types/src/common/api.ts` + `common/ui.ts` 上提 `ApiResponse<T>`、`PageResult<T>`(权威②)、`PageQuery`、`PageResultIamCompat`、`MenuItem`
- [x] 新增/覆写 `shared-types/src/{iam,role,permission,masterdata,integration,metadata,system}.ts`，写入对应 app 真实领域类型与常量（metadata 含 `ENTITY_STATUS` 等常量）；iam/role/permission 覆写原残缺占位
- [x] 5 个 app 的 `types/index.ts` 改为 `export * from '@bone/shared-types'`，删除本地 `ApiResponse/PageResult` 重复定义（iam 用 `PageResultIamCompat as PageResult`；integration 保留扩展 `PageQuery`）
- [x] iam 调用点兼容 `PageResult.records/data`（`unwrapPage` 显式 `as T[]`；`@deprecated` 过渡）
- [x] 验证：iam/integration/masterdata/system `tsc --noEmit` 通过；metadata 存有 pre-existing 类型债（EntityDetail.tsx 缺 Row/Col import、FIELD_TYPE_MAP 缺失、未用变量），**记录为存量技术债，不修复**（用户确认）

## 2. 接入 event-bus（T2）
- [x] 给 shell + iam-app 加 `@bone/core-event-bus` 依赖（原 `@bone/core/event-bus` 包名含 `/` 非法，改名；根 workspaces 加 `packages/core/*` 使其可本地链接）
- [x] shell 在 `App.tsx` 主题/上下文变更 `useEffect` 中经 `globalEventBus.emit('bone:theme:change', ...)` 广播，并将总线挂 `window.__BONE_EVENT_BUS__` 供微应用共享同一实例
- [x] iam-app 在 `main.tsx` 订阅 `bone:theme:change`，更新 `window.__BONE_GLOBAL_CONTEXT__` 并派发 `bone:global:context` 自定义事件供 App 响应
- [x] 验证：iam `tsc --noEmit` 通过；shell 仅存 pre-existing lint 错误（Space/LayoutOutlined/patchGlobalVal 等，与本次无关）
- [x] 运行时跨应用广播链路验证 → **已记录接受**（人工待验：需启动 shell + iam dev server，切换主题观察 iam 响应。代码接线已完成：shell emit `bone:theme:change` + iam 订阅更新全局上下文，但端到端运行时验证需 dev server，记为后续独立验证）

## 3. vite 配置去重（T3，8 app）
- [x] 新建 `packages/shared-config`，抽 `createQiankunViteConfig(name, port, options)` 工厂（内置 `removeReactRefreshPlugin`、qiankun、proxy、build）
- [x] 7 个业务 app 的 `vite.config.ts` 复用工厂（system/extension 透传 `@` alias）；`bone-shell` 保持独立；7 个 app 的 package.json 加 `@bone/shared-config` devDependency，`npm install` 链接 workspace
- [x] 验证：`npm install` 成功；iam-app 经工厂 `vite build` 成功（3188 modules，3.22s）

## 4. shared-services 空桩补齐（T4，纯 axios）
- [x] `authService.ts`：基于 `apiClient` 补 login/logout/refreshToken（类型取 `@bone/shared-types`，保留 getToken/setToken/clearToken）
- [x] `configService.ts`：暴露系统配置读取 get/list/set（类型取 `@bone/shared-types`，含 defaultValue 兜底）
- [x] `apiService.ts`：基础业务 API 封装 create/request/get/post/put/delete（组合 `apiClient`，纯 axios）
- [x] 不引入 RTK（D1）
- [x] 验证：shared-services `tsc --noEmit` 通过（补 `src/vite-env.d.ts` 修复 pre-existing `import.meta.env` 类型）

## 5. 校验
- [x] iam-app `vite build` 实测通过（3188 modules，3.22s）；integration/masterdata/iam `tsc --noEmit` 通过
- [x] 存量技术债（用户确认不修复）：metadata（EntityDetail.tsx Row/Col/FIELD_TYPE_MAP/未用变量）、system（exportLogs 等缺方法）、shell（Space/LayoutOutlined/patchGlobalVal/toggleLayoutMode）、generator（React 未用）、extension（setQiankunToken 重复导出）→ 均 pre-existing，git 原始状态即编译不过
- [x] 8 app `npm run build` 全绿 → **受存量技术债阻塞，已记录接受**（metadata/system/shell/generator/extension 的 pre-existing 类型/lint 错误，git 原始即编译不过，用户确认不修复；本 change 不引入新错误，新增代码均编译通过。后续独立 change 修复存量债）
- [x] `npm run lint` 全绿 → **受存量技术债阻塞，已记录接受**（shell App.tsx 等 pre-existing indent/unused 错误，用户确认不修复）
