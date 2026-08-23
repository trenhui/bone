---
change: frontend-shared-layer
design-doc: docs/superpowers/design/frontend-shared-layer.md
base-ref: 82c405d3c1bc851db624e9fb25f4aa8cbb17dbe9
archived-with: 2026-08-23-frontend-shared-layer
---

# 实施计划：前端共享层治理（frontend-shared-layer）

> 本计划基于 Design Doc（`docs/superpowers/design/frontend-shared-layer.md`）与 `openspec/changes/frontend-shared-layer/tasks.md` 拆解。
> 语言：中文（zh-CN）。执行顺序遵循用户指定「低风险清理先行」。

## 关键设计决策（必读）

- **D1 纯 axios，不引入 RTK**：`shared-services` 仅做 axios 封装，返回 `Promise<ApiResponse<T>>`；不为 8 app 各建 RTK store。
- **D2 仅 5 个 app 类型收敛**：仅覆盖已有 `src/types/index.ts` 的 iam / metadata / integration / masterdata / system；extension / generator 维持现状。
- **D3 移除 shell RTK 死依赖**：全仓已 0 处 `@reduxjs/toolkit` / `react-redux` 源码 import，仅 `bone-shell/package.json` 残留声明。

## PageResult<T> 三态冲突消解（全局统一规则）

| 形态 | 来源 | 字段 |
|------|------|------|
| ① | iam | `{records; total; page?; size?; data?}` |
| ② | masterdata/system/metadata（**权威**） | `{list; total; pageNum; pageSize}` |
| ③ | integration（与②同构，仅字段序） | `{total; list; pageNum; pageSize}` |

**选定**：`@bone/shared-types` 的 `PageResult<T>` 采用权威形态②；iam 的 `records`/`data` 作为 `@deprecated` 兼容别名保留，逐步迁移。
`ApiResponse<T>` 五 app 一致（`{code; message; data}`），直接提升。

---

## 阶段 0：T11 低风险清理先行

### T11-1 移除 shell RTK 死依赖（D3）
- **步骤**
  1. 编辑 `apps/bone-shell/package.json`，删除 `dependencies` 中的 `"@reduxjs/toolkit"` 与 `"react-redux"`（确认全仓无源码 import）。
- **验收**：`grep -rn "@reduxjs/toolkit\|react-redux" bone-frontend` 仅命中空壳或 0 命中；`npm run lint` 在 shell 无未解析模块报错。

### T11-2 统一 PageResult<T> 冲突（§2.1）
- **步骤**
  1. 在 `packages/shared-types/src/common/api.ts` 定义权威 `PageResult<T> = {list; total; pageNum; pageSize}` 与 `@deprecated` 的 `PageResultIamCompat`（含 `records?; data?`）。
  2. 各 app `types/index.ts` 的本地 `PageResult` 改为 `export type { PageResult } from '@bone/shared-types'`，iam 额外兼容 `records`/`data` 读取点用类型断言过渡。
- **验收**：5 app 内 `PageResult` 引用统一来自 `@bone/shared-types`；iam 的 `unwrapPage` 等读取点保留 `records ?? data` 兼容逻辑且类型编译通过。

### T11-3 Lint 验证
- **步骤**：在 `bone-frontend` 根运行 `npm run lint`（eslint . --ext ts,tsx --max-warnings 0）。
- **验收**：全绿，无新增 warning/error。

---

## 阶段 1：T1 类型收敛（仅 5 app）

### T1-1 公共类型上提
- **步骤**
  1. `packages/shared-types/src/common/api.ts`：`ApiResponse<T>`、`PageResult<T>`（权威②）、`PageQuery`。
  2. `packages/shared-types/src/common/ui.ts`：`MenuItem`（icon 类型放宽为 `ReactNode | string` 以兼容 shell）。
- **验收**：`shared-types` 编译通过；导出可见。

### T1-2 领域类型按模块命名空间上提
- **步骤**：新增以下文件并写入对应 app 的领域类型（保持原字段签名；metadata 含 `ENTITY_STATUS` 等常量）：
  - `packages/shared-types/src/iam/index.ts`
  - `packages/shared-types/src/masterdata/index.ts`
  - `packages/shared-types/src/integration/index.ts`
  - `packages/shared-types/src/metadata/index.ts`
  - `packages/shared-types/src/system/index.ts`
- **验收**：每个文件 `export type/interface` 完整，无运行时值混入（除已使用的常量）。

### T1-3 更新 `shared-types/src/index.ts` 出口
- **步骤**：re-export `common/*` 与 5 个模块命名空间。
- **验收**：`import type { ApiResponse } from '@bone/shared-types'` 与 `import type { Account } from '@bone/shared-types/iam'` 均可用。

### T1-4 app 内切换 import
- **步骤**：5 app 的 `src/types/index.ts` 改为 `export * from '@bone/shared-types/...'`；删除本地与 `shared-types` 重复的 `ApiResponse`/`PageResult` 定义；调用点改用统一类型。
- **验收**：5 app `npm run build` 通过；无重复定义报错。

---

## 阶段 2：T4 shared-services 空桩补齐（纯 axios，D1）

### T4-1 authService.ts
- **步骤**：基于 `packages/shared-services/src/apiClient.ts` 暴露 `login/logout/refreshToken` 函数，类型取自 `@bone/shared-types/iam`，返回 `Promise<ApiResponse<...>>`。
- **验收**：函数可被 iam-app 调用；类型编译通过。

### T4-2 configService.ts
- **步骤**：暴露系统配置读取函数，类型取自 `@bone/shared-types/system`。
- **验收**：编译通过，可被 system-app 引用。

### T4-3 apiService.ts
- **步骤**：作为基础业务 API 封装（组合 `apiClient` + 统一 `ApiResponse<T>` 解析）。
- **验收**：编译通过；不为其引入 RTK。

---

## 阶段 3：T3 vite 配置去重（8 app）

### T3-1 抽工厂函数
- **步骤**：在 `packages/shared-config`（或 `shared-utils`）新增 `createQiankunViteConfig(name, port, extra)`，内置 `react({fastRefresh:false})` + `removeReactRefreshPlugin()` + `qiankun(name,{useDevMode:true})` + 通用 `server`/`build`。
- **验收**：工厂函数导出可见，含可覆盖 `extra.resolve.alias`（system/extension 的 `'@'` 别名）。

### T3-2 复用工厂
- **步骤**：7 个业务 app 的 `vite.config.ts` 简化为 `export default createQiankunViteConfig('bone-xxx-app', 30xx, extra?)`；`bone-shell` 保持独立配置（主应用例外）。
- **验收**：8 app `npm run build` 通过；shell 配置不变。

---

## 阶段 4：T2 event-bus 接入（真实链路）

### T2-1 主题/语言切换跨应用广播
- **步骤**：在 shell 的全局设置区派发 `GlobalContextChangeEvent`（语言/主题变更）经 `globalEventBus`/`TypedEventBus`；在 iam-app 订阅该事件并本地响应（刷新 locale/UI）。
- **验收**：shell 切语言 → iam-app 实时收到事件并变更 UI；控制台无报错；事件类型为强类型。

---

## 阶段 5：校验

### T5-1 全量构建
- **步骤**：8 app 各自 `npm run build`；`shared-types`/`shared-services`/`shared-config` 构建通过。
- **验收**：全部成功。

### T5-2 Lint 全绿
- **步骤**：`npm run lint`（eslint . --ext ts,tsx --max-warnings 0）。
- **验收**：全绿。

---

## 提交策略
- 按阶段分组提交：T11 → T1 → T4 → T3 → T2 → 校验。每阶段完成并本地构建/lint 通过后提交，commit message 注明对应任务与范围扩展（若有）。
- delta spec 的增量更新遵循 build 阶段 Step 4 分级：小改动直接改 `openspec/changes/frontend-shared-layer/specs/*`，中/大改动暂停等用户确认。
