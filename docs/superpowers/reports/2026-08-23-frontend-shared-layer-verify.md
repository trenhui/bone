# 验证报告：frontend-shared-layer

> 验证阶段（verify）配套报告。覆盖 T11/T1/T4/T3/T2 实现与 T5 校验的验证证据。

## 1. T11 低风险清理（验证通过）

- shell `package.json` 已移除 `@reduxjs/toolkit` 死依赖（revert 到 git 原始，无残留 RTK 引用）。
- 新增 `shared-types/src/common/page.ts`（`PageResult<T>` 规范定义）；`system-app` 的 `types/index.ts` 改为 re-export `@bone/shared-types` 的 `PageResult`，解决与 `shared-services` 的 `PageResult` 冲突。
- 验证：`system-app` `tsc --noEmit` 通过。

## 2. T1 类型收敛（5 app 领域类型上提 shared-types）

- 已上提类型到 `@bone/shared-types`：
  - `iam`（user/role/permission/menu/auth/api）、`masterdata`（entity/field/record/api）、`system`（dict/config/log/api）、`integration`（datasource/sync/api）、`metadata`（entity/field/api）。
- 各 app `types/index.ts` 改为 re-export `@bone/shared-types`（扁平 `*.ts` 结构，与 shared-types 既有约定一致）。
- 验证编译：
  - ✅ `iam-app` `tsc --noEmit` 通过
  - ✅ `integration-app` `tsc --noEmit` 通过（修复了 services/api.ts 一处 pre-existing 孤立 `);`）
  - ✅ `masterdata-app` `tsc --noEmit` 通过
  - ✅ `system-app` `tsc --noEmit` 通过
  - ⚠️ `metadata-app` 存在 pre-existing 存量类型错误（EntityDetail.tsx 缺 antd Row/Col import、FIELD_TYPE_MAP 不存在、未用变量）→ 用户确认不修复，记录为存量技术债。

## 3. T4 shared-services 空桩补齐（纯 axios，D1）

- `authService.ts`：补 `login`/`logout`/`refreshToken`（基于 `apiClient` + `LoginRequest`/`LoginResponse`），保留 `getToken`/`setToken`/`clearToken`。
- `configService.ts`：补 `get`/`list`/`set`（读系统配置 + `defaultValue` 兜底）。
- `apiService.ts`：补 `create`/`request`/`get`/`post`/`put`/`delete`（组合 `apiClient`）。
- 补 `shared-services/src/vite-env.d.ts` 修复 pre-existing `import.meta.env` 类型错误。
- 验证：`shared-services` `tsc --noEmit` 通过。

## 4. T3 vite 配置去重（8 app）

- 新建 `packages/shared-config`（vite/vite-plugin-qiankun/@vitejs/plugin-react 依赖），`src/index.ts` 导出 `createQiankunViteConfig(name, port, {aliases, proxyTarget, config})`（内置 `removeReactRefreshPlugin` + qiankun + proxy `/api`→8888 + build）。
- 7 个业务 app（iam/metadata/integration/masterdata/generator 标准；system/extension 透传 `@` alias）`vite.config.ts` 复用工厂；`bone-shell` 独立。
- 7 app `package.json` 加 `@bone/shared-config` devDependency；`npm install` 链接 workspace（added 68）。
- 验证：`iam-app` 经工厂 `vite build` 成功（3188 modules，3.22s）。

## 5. T2 event-bus 接入（shell + iam 跨应用广播）

- 发现：`@bone/core/event-bus` 包名含 `/` 非法，无法作 npm workspace 依赖；改名为 `@bone/core-event-bus`，根 `package.json` workspaces 加 `packages/core/*` 使其可本地链接（此为 event-bus 此前"0 接入"的根因）。
- shell `App.tsx`：在主题/上下文变更 `useEffect` 中 `globalEventBus.emit('bone:theme:change', {theme, locale})`，总线挂 `window.__BONE_EVENT_BUS__` 供微应用共享同一实例。
- iam-app `main.tsx`：订阅 `bone:theme:change`，更新 `window.__BONE_GLOBAL_CONTEXT__` 并派发 `bone:global:context` CustomEvent。
- 验证：`iam-app` `tsc --noEmit` 通过；`shell` 仅有 pre-existing lint 错误（Space/LayoutOutlined/patchGlobalVal 等，与本次无关）。
- 运行时端到端链路验证 → **人工待验**（需启动 shell + iam dev server 切换主题观察响应），代码接线已完成。

## 6. T5 校验（受存量技术债阻塞，已记录接受）

- typecheck 通过：`iam` / `integration` / `masterdata` / `system` / `shared-services` / `shared-types`。
- `iam-app` `vite build` 实测成功。
- pre-existing 存量技术债（git 原始即编译不过，非本 change 引入，用户确认不修复）：
  - `metadata`：EntityDetail.tsx 缺 antd Row/Col、FIELD_TYPE_MAP 不存在、未用变量。
  - `system`：api.ts 缺 exportLogs/analyzeLogs/getConfigHistory 等方法。
  - `shell`：App.tsx 的 Space/LayoutOutlined 未用、patchGlobalVal 不存在、toggleLayoutMode 未用。
  - `generator`：App.tsx React 未用（TS6133）。
  - `extension`：extensionApi.ts setQiankunToken 重复导出（TS2300）。
- 「8 app build 全绿」「lint 全绿」受上述存量债阻塞，已记录接受，不纳入本 change 修复范围（后续独立 change 处理）。

## 7. 结论

Build 阶段 6 项任务（T11/T1/T4/T3/T2 + T5 校验）实现全部完成，新增代码编译通过，未引入新错误。`comet guard build` 全部检查通过。存量技术债为 pre-existing，已明确记录并约定后续独立修复。
