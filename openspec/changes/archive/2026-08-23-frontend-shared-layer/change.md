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
