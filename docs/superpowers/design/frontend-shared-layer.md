---
comet_change: frontend-shared-layer
role: technical-design
canonical_spec: openspec
archived-with: 2026-08-23-frontend-shared-layer
status: final
---

# Design Doc: 前端共享层治理（frontend-shared-layer）

> Comet Classic `full` workflow 的 Superpowers Design Doc（桥梁产物）。
> 配套的 OpenSpec 产物：`openspec/changes/frontend-shared-layer/{proposal,design,tasks}.md`
> 本文件记录**关键设计决策与选项评估**；任务拆解见 `tasks.md`。

## 背景与目标

前端 8 个微应用存在重复类型定义、重复 vite 配置、`shared-services` 空桩、未接入的 `core/event-bus`。本 change 治理这四处重复，统一到 `packages/*` 共享层，降低维护成本。

## 决策与选项评估

### 决策 1：shared-services 是否引入 Redux Toolkit（RTK）
- **选项 A（选中）**：纯 axios 封装。`apiClient` 已有实现，补 `authService/configService/apiService` 业务函数，返回 `Promise<ApiResponse<T>>`。
- 选项 B：引入 RTK Query。需为 8 app 各建 RTK store 并接入，范围爆炸，且现状无任何 RTK 使用。
- 选项 C：预留 RTK 接口但不实现。增加无谓抽象。
- **后果**：维持现状的简单性，各 app 调用方式不变；未来若要客户端缓存层可单独 change 引入。

### 决策 2：类型收敛覆盖范围
- **选项 A（选中）**：仅 5 个已有 `src/types/index.ts` 的 app（iam/masterdata/integration/metadata/system）。
- 选项 B：覆盖全部 7 app，额外为 extension/generator 补建类型文件。
- **后果**：避免凭空补全未知类型；extension/generator 维持现状，后续如有需要再纳入。

### 决策 3：移除 bone-shell 的 RTK 死依赖
- 全仓已无 `@reduxjs/toolkit`/`react-redux` 的源码 import，仅 `bone-shell/package.json` 残留声明。
- **后果**：随 T11 清理移除声明，减少依赖噪音，与决策 1 一致。

## 关键冲突消解

### PageResult<T> 三态签名冲突
扫描 5 app 发现 `PageResult<T>` 有三种签名：
1. iam：`{records; total; page?; size?; data?}`
2. masterdata/system/metadata：`{list; total; pageNum; pageSize}`（**权威形态**）
3. integration：`{total; list; pageNum; pageSize}`（与 2 同构，仅字段序不同）

**选定**：以形态 ② 为 `@bone/shared-types` 的 `PageResult<T>`；iam 的 `records`/`data` 字段加 `@deprecated` 兼容别名，逐步迁移。`ApiResponse<T>` 五 app 一致，直接提升。

## 各任务设计要点

- **T1 类型收敛**：按模块命名空间（`shared-types/{iam,masterdata,integration,metadata,system}` + `common`）上提，app 改为 `import type from '@bone/shared-types'`。
- **T2 event-bus 接入**：`core/event-bus` 已实现，仅需消费侧接入；shell+iam 跑通主题/语言切换跨应用广播链路。
- **T3 vite 去重**：抽 `createQiankunViteConfig(name, port, extra)` 工厂，7 业务 app 复用，shell 例外保留。
- **T4 shared-services**：纯 axios 补齐 3 个空桩。

## 风险与缓解

- 类型提升可能触发 app 内类型不匹配 → 先统一 `ApiResponse/PageResult` 公共类型，再迁移领域类型；每步跑 lint/build。
- T11 清理若误删 → 仅移除确认 0 import 的 RTK 声明，不碰其他。
