# Comet Design Handoff

- Change: frontend-shared-layer
- Phase: design
- Mode: compact
- Context hash: c638163e088f8399dc838d86d66ae1a75579a14f239a9f5d56be976d47575603

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/frontend-shared-layer/proposal.md

- Source: openspec/changes/frontend-shared-layer/proposal.md
- Lines: 1-31
- SHA256: 1b59d91efcbf1ccdbc044e7ed9c4ab54905a3c23727c2b98ea77af32d616a2fd

```md
# Change: 前端共享层治理（含 T11 低风险清理）

**Type**: Refactor（前端里子工程）
**Status**: Proposed
**Source**: 用户统一优先级规划（阶段3 T7-T9+T11）。基于扫描：shared-types 20%（伪复用，7 app 重复 100+ interface）、shared-components 8%（空壳死包）、core/event-bus 45%（实现完整但 0 接入）、vite.config 8 份重复、masterdata qualityResultApi reject 死代码、bone-shell 虚假 RTK 依赖。
**Depends on**: 无
**First Task**: T11-cleanup（用户指定先行）

## Intent
治理前端共享层：收敛共享类型、接入事件总线、抽取公共 vite 配置、清理死代码与虚假依赖，解锁 8 应用复用。

## Scope (In)
- `shared-types`：将各 app `types/index.ts` 领域类型上提 shared-types，删除本地冗余。
- `core/event-bus`：至少跑通 shell↔子应用 1 条真实链路（主题/语言切换）。
- `vite.config`：抽 `createQiankunViteConfig(name,port)` 公共配置，8 app 复用。
- **T11（先行）**：删除 `masterdata-app` 的 `qualityResultApi.reject` 死代码；移除 `bone-shell` 的 `@reduxjs/toolkit`/`react-redux` 虚假依赖（确认 0 import）。
- `shared-services`：`apiService`/`authService`/`configService` 空桩评估（可保留或标注 TODO）。

## Scope (Out / Non-Goals)
- 不全面充实 shared-components（先跑通 1 条 event-bus 链路）。
- 不重写前端设计系统。

## Assumptions
- 各 app `types/index.ts` 可安全合并（无命名冲突）。
- event-bus 实现已完整，仅需接线。

## Acceptance Criteria
- [ ] T11 死代码/虚假依赖清除，lint 通过。
- [ ] 各 app 领域类型收敛进 shared-types，本地冗余删除。
- [ ] event-bus 跑通 1 条真实链路。
- [ ] vite 配置去重，8 app 复用公共配置。

```

## openspec/changes/frontend-shared-layer/design.md

- Source: openspec/changes/frontend-shared-layer/design.md
- Lines: 1-157
- SHA256: a6290fb6eb49a6ef99e4dddda7833ec7320f9a837efe9687e6107305dce8de49

[TRUNCATED]

```md
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

```

Full source: openspec/changes/frontend-shared-layer/design.md

## openspec/changes/frontend-shared-layer/tasks.md

- Source: openspec/changes/frontend-shared-layer/tasks.md
- Lines: 1-34
- SHA256: 1ab3e20594525710db5baa6495b45679583030dbc73ff59b14a6a378d0b92288

```md
# Tasks: 前端共享层治理（frontend-shared-layer）

> 设计依据见 `design.md`（含决策 D1 纯 axios / D2 仅 5 app / D3 移除 shell RTK）。
> 用户指定 **低风险清理（T11）先行**。

## ✅ T11（先行）：低风险清理
- [ ] 移除 `bone-shell/package.json` 的 `@reduxjs/toolkit` + `react-redux`（全仓已 0 import，属死依赖；D3）
- [ ] 统一 `PageResult<T>` 三态冲突为权威形态 `{list; total; pageNum; pageSize}`（iam 的 `records/data` 加 `@deprecated` 兼容；design §2.1）
- [ ] `qualityApi.ts` 死代码 → **已不存在，跳过**
- [ ] 运行 `npm run lint`（eslint . --ext ts,tsx --max-warnings 0）确认全绿

## 1. 收敛共享类型（T1，范围：5 app）
- [ ] 在 `shared-types/src/common/{api,ui}.ts` 上提 `ApiResponse<T>`、`PageResult<T>`(权威②)、`PageQuery`、`MenuItem`
- [ ] 新增 `shared-types/src/{iam,masterdata,integration,metadata,system}/index.ts`，分别写入对应 app 的领域类型与常量（metadata 含 `ENTITY_STATUS` 等常量）
- [ ] 5 个 app 的 `types/index.ts` 改为 `export * from '@bone/shared-types/...'`，删除本地 `ApiResponse/PageResult` 重复定义
- [ ] iam 调用点兼容 `PageResult.records/data`（`@deprecated` 过渡）

## 2. 接入 event-bus（T2）
- [ ] 在 shell + iam-app 跑通 1 条真实跨应用广播链路（主题/语言切换经 `globalEventBus`/`TypedEventBus`）
- [ ] 沉淀用法示例；验收：shell 切语言 → iam 实时响应，无报错

## 3. vite 配置去重（T3，8 app）
- [ ] 抽 `createQiankunViteConfig(name, port, extra)` 工厂（内置 `removeReactRefreshPlugin`）
- [ ] 7 个业务 app 的 `vite.config.ts` 复用工厂；`bone-shell` 保持独立（主应用例外）

## 4. shared-services 空桩补齐（T4，纯 axios）
- [ ] `authService.ts`：基于 `apiClient` 暴露登录/登出/刷新（类型取 `@bone/shared-types/iam`）
- [ ] `configService.ts`：暴露系统配置读取（类型取 `@bone/shared-types/system`）
- [ ] `apiService.ts`：基础业务 API 封装（组合 `apiClient` + 统一 `ApiResponse<T>` 解析）
- [ ] 不引入 RTK（D1）

## 5. 校验
- [ ] 8 app `npm run build` 通过
- [ ] `npm run lint` 全绿

```

## openspec/changes/frontend-shared-layer/specs/frontend-shared-layer/spec.md

- Source: openspec/changes/frontend-shared-layer/specs/frontend-shared-layer/spec.md
- Lines: 1-20
- SHA256: 22fd3a91a3a49668d155d023a8bea3dbcfaa12f9a59cf066a76d78cbce09eefb

```md
## ADDED Requirements

### Requirement: 前端共享层治理
前端共享层（shared-types / shared-components / core-event-bus / shared-services）需从空壳死包状态治理为真正被复用的基础设施，并清理死代码与虚假依赖，解锁 8 个微应用复用。

#### Scenario: 清理死代码与虚假依赖（T11 先行）
- **WHEN** 删除 `masterdata-app` 的 `qualityResultApi` reject 死代码并移除 `bone-shell` 的 `@reduxjs/toolkit`/`react-redux` 虚假依赖
- **THEN** `npm run lint`（eslint . --ext ts,tsx --max-warnings 0）全绿，无未使用依赖报错

#### Scenario: 收敛共享类型
- **WHEN** 将各 app `types/index.ts` 领域类型上提 `shared-types/src`
- **THEN** 各 app 改为 `import type from '@bone/shared-types'`，本地冗余删除，构建通过

#### Scenario: 接入事件总线
- **WHEN** 在 shell 与至少一个子应用通过 `core/event-bus` 广播主题/语言切换
- **THEN** 跨应用事件可达，event-bus 从 0 接入变为有真实链路

#### Scenario: 抽取公共 Vite 配置
- **WHEN** 抽 `createQiankunViteConfig(name, port)` 公共配置
- **THEN** 8 个 app 的 `vite.config.ts` 复用，重复配置消除

```
