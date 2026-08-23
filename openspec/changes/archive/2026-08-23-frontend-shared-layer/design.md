# Design: 前端共享层治理（frontend-shared-layer）

> 本文件为 Comet Classic **design 阶段**产物。基于 `/comet-design` 进入，change 已通过 open 守卫，phase=design。
> 产品动机见 `proposal.md`（= `change.md` 副本）。本文件聚焦**技术设计并落地到可执行的任务**。

语言：中文（zh-CN）

---

## 0. 决策记录（设计依据）

在进入设计前，已对仓库真实代码做核实，并就两个重大架构选择征求用户确认：

| # | 决策点 | 结论 | 理由 |
|---|--------|------|------|
| D1 | `shared-services` 客户端状态层是否引入 RTK | **不引入**，仅做 axios 封装 | 现状各 app 均直接 `axios` 调接口，无 RTK store；引入 RTK Query 需改造所有 app 的 store 层，属范围爆炸。纯 axios 封装与现状一致、范围可控。 |
| D2 | 类型收敛（T1）覆盖范围 | **仅现有 5 个 app**（iam / metadata / integration / masterdata / system） | extension-app、generator-app 无 `src/types/index.ts`，不凭空补全其类型，维持原状。 |
| D3 | `bone-shell/package.json` 的 `@reduxjs/toolkit` + `react-redux` | **移除**（确认 0 处 import） | 全仓已无任何 `useSelector/useDispatch/configureStore` 使用，属死依赖。与 D1 一致。 |

---

## 1. 现状核实（偏离原假设的修正）

设计前扫描真实代码，发现 `proposal/design` 初稿中若干假设已过时，以本核实为准：

1. **`qualityApi.ts` 死代码已不存在**：`masterdata-app/src/api/qualityApi.ts` 在仓库中已找不到，`Promise.reject('not implemented')` 全仓 0 匹配。原 T11 子项作废。
2. **RTK 仅剩声明、无使用**：`@reduxjs/toolkit`/`react-redux` 仅在 `bone-shell/package.json` 出现，无任何源码 import。T11 清理项简化为"移除 package.json 声明"。
3. **`shared-services` 并非全空桩**：`apiClient.ts`(96 行)、`queryClient.ts`(51 行) 已具实质实现，真正空桩仅 `apiService.ts`(6)、`authService.ts`(7)、`configService.ts`(3)。T4 只需补齐这 3 个。
4. **`event-bus` 实现完整**：`packages/core/event-bus/src` 已导出 `TypedEventBus`、`MicroAppMessenger`、`globalEventBus`、类型 `GlobalUser/GlobalPermissions/GlobalContext/GlobalContextChangeEvent` 等。T2 是**接入验证**而非从零实现。
5. **`vite.config` 跨 8 个 app 重复模板**：除 shell 为特例外，其余 7 个 app（含 extension、generator）均使用相同结构（`react({fastRefresh:false})` + `removeReactRefreshPlugin` + `qiankun(name)` + port/proxy）。T3 复用面为 8 app。
6. **`PageResult<T>` 三态签名冲突**（关键）：`ApiResponse<T>` 5 app 一致；但 `PageResult<T>` 存在 3 种签名，是 T1 的主要冲突点（详见 §2.1）。

---

## 2. T1 — 收敛共享类型（范围：5 个 app）

### 2.1 类型冲突消解规则（强制）

扫描 5 个 app 的 `src/types/index.ts`，公共类型冲突如下：

| 类型 | 签名分歧 | 消解方案 |
|------|----------|----------|
| `ApiResponse<T>` | 5 app **完全一致** `{code; message; data}` | 直接提升到 `@bone/shared-types` 的 `common` 命名空间，原 app 改为 `import type { ApiResponse } from '@bone/shared-types'` |
| `PageResult<T>` | **三态冲突**：<br>① iam：`{records; total; page?; size?; data?}`<br>② masterdata/system/metadata：`{list; total; pageNum; pageSize}`<br>③ integration：`{total; list; pageNum; pageSize}` | 以 **②为权威形态**（覆盖 3/5 app，且字段语义清晰）。在 `@bone/shared-types` 提供 `PageResult<T>` 统一类型；iam 的 `records/data` 字段标记为 `@deprecated` 兼容别名，逐步迁移。integration 与 ② 同构仅字段序不同，直接复用 ②。 |
| `MenuItem` | 仅 masterdata 定义 `{key; label; icon: React.ReactNode; path}` | 提升为 `shared-types` 的 `common`（`icon` 类型改为 `ReactNode` 或 `ReactNode \| string` 以兼容 shell），其余 app 未来复用。 |
| 领域类型（Account/Role/Permission/Tenant/Connector/FlowNode/MetaEntity…） | 各 app 独占，无跨 app重名冲突 | 按模块命名空间收敛：`shared-types/iam`、`shared-types/masterdata`、`shared-types/integration`、`shared-types/metadata`、`shared-types/system`（见 §2.2）。不合并跨模块。 |

> 注：extension / generator 无 `types/index.ts`，**不在本 change 纳入**。

### 2.2 收敛目录结构（落地形态）

`@bone/shared-types` 当前 `main: ./src/index.ts`，按命名空间扩展：

```
packages/shared-types/src/
├── index.ts                 # 统一出口，re-export 下列
├── common/
│   ├── api.ts               # ApiResponse<T>, PageResult<T>(权威②), PageQuery
│   └── ui.ts                # MenuItem 等 UI 通用类型
├── iam/
│   └── index.ts             # Account, Role, Permission, Tenant, Login* ... (来自 iam-app)
├── masterdata/
│   └── index.ts             # MasterDataEntity, DataQuality*, MasterDataRecord ...
├── integration/
│   └── index.ts             # Connector, IntegrationFlow, FlowNode, FlowConnection ...
├── metadata/
│   └── index.ts             # MetaEntity, MetaField, MetaRelation, 常量(ENTITY_STATUS...)
└── system/
    └── index.ts             # SystemConfig, AlertRule, SystemLog, Metrics ...
```

迁移步骤（每 app）：
1. 在 `shared-types/src/<module>/index.ts` 写入该 app 的类型（保持原字段签名）。
2. app 内 `types/index.ts` 改为仅 `export * from '@bone/shared-types/...'`（保留文件以兼容相对 import，或直接在引用处改 `@bone/shared-types`）。
3. 删除 app 内与 `shared-types` 重复的 `ApiResponse/PageResult` 定义，改用统一类型。
4. iam 的 `PageResult` 调用点增加兼容：保留 `records`/`data` 读取做类型断言过渡，后续 change 清理。

---

## 3. T2 — 接入 event-bus（真实链路验证）

`core/event-bus` 已实现，本任务目标是**在 shell + 至少 1 个子应用跑通 1 条真实跨应用广播链路**，验证可用性并沉淀用法。

- 链路候选：**主题/语言切换** 通过 `globalEventBus` / `TypedEventBus` 跨应用广播。
- 接入点：
  - shell 在全局设置区派发 `GlobalContextChangeEvent`（用户/语言/主题变更）。
  - iam-app 订阅该事件并本地响应（如刷新用户信息、切换 locale）。
- 验收：在 shell 切换语言 → iam-app 实时收到事件并变更 UI；控制台无报错；事件类型为 `TypedEventBus` 的强类型事件。
- 不新增 event-bus 能力，仅做消费侧接入与示例。

---

## 4. T3 — vite 配置去重（8 app）

抽取 `packages/shared-config`（或置于 `shared-utils`）中的 `createQiankunViteConfig(name, port, options)`：

```ts
// 形如
export function createQiankunViteConfig(name: string, port: number, extra?: ViteConfigLike) {
  return defineConfig({
    plugins: [react({ fastRefresh: false }), removeReactRefreshPlugin(), qiankun(name, { useDevMode: true })],
    resolve: { alias: { 'vite-plugin-qiankun/helper': ... } },
    server: { port, host: '0.0.0.0', cors: true, origin: `http://localhost:${port}`, hmr: { overlay: false }, proxy: { '/api': { target: 'http://localhost:8888', changeOrigin: true } } },
    build: { outDir: 'dist', sourcemap: true },
    ...extra,
  })
}
```

- 7 个业务 app 的 `vite.config.ts` 简化为 `export default createQiankunViteConfig('bone-xxx-app', 30xx)`。
- `bone-shell` 因非 qiankun 子应用（主应用），**保持独立配置**，不强制复用（仅作例外说明）。
- `removeReactRefreshPlugin` 一并内联进工厂函数。

---

## 5. T4 — shared-services 空桩补齐（纯 axios）

基于 D1（纯 axios，不引入 RTK）：

- `apiClient.ts` / `queryClient.ts` 已有实现，保留。
- 补齐 3 个空桩：
  - `authService.ts`：基于 `apiClient` 暴露登录/登出/刷新 token 函数（类型取自 `@bone/shared-types/iam`）。
  - `configService.ts`：暴露系统配置读取函数（类型取自 `@bone/shared-types/system`）。
  - `apiService.ts`：作为基础业务 API 调用封装（组合 `apiClient` + 统一响应解析 `ApiResponse<T>`）。
- 不引入 RTK Query / async thunk；返回 `Promise<ApiResponse<T>>` 供 app 直接 `await` 使用。

---

## 6. T10 / T11 — 低风险清理（先于主任务）

> 用户指定低风险清理**先行**。基于 §1 现状核实修订：

- [x] ~~`qualityApi.ts` 死代码清理~~ → **已不存在，跳过**（核实 §1.1）。
- [ ] 移除 `bone-shell/package.json` 的 `@reduxjs/toolkit` + `react-redux`（确认全仓 0 import，D3）。
- [ ] `PageResult<T>` 三态冲突统一为权威形态 ②（§2.1），iam 的 `records/data` 加 `@deprecated` 兼容。
- [ ] 运行 `npm run lint`（eslint . --ext ts,tsx --max-warnings 0）确认全绿。

---

## 7. 执行顺序（tasks 分组）

1. **T11 清理先行**：移除 shell RTK 声明 → 统一 `PageResult` 冲突 → lint 验证。
2. **T1 类型收敛**：按 §2.2 目录结构上提 5 app 类型，app 改 import。
3. **T4 shared-services 补齐**：纯 axios 封装 3 个空桩。
4. **T3 vite 去重**：抽工厂函数，7 app 复用。
5. **T2 event-bus 接入**：shell+iam 真实链路验证。
6. **校验**：8 app `npm run build` + `npm run lint` 全绿。

---

## 8. RT 红旗自检（设计阶段）

- ❌ 未引入 RTK（D1 明确拒绝）→ 规避范围爆炸。
- ❌ 未凭空补全 extension/generator 类型（D2）→ 规避臆测。
- ❌ 未新增后端依赖 / ORM（本 change 纯前端）→ 遵守持久化唯一性约束（不涉及）。
- ✅ 所有类型坚守 `export type` / `interface`，不放运行时值到 `shared-types`（除 metadata 的常量 `ENTITY_STATUS` 等已被 app 使用，需连同常量一并上提并在 `shared-types/metadata` 导出）。
- ✅ 冲突消解有明确权威形态，不做"猜测性合并"。
