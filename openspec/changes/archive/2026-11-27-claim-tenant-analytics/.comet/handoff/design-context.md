# Comet Design Handoff

- Change: claim-tenant-analytics
- Phase: design
- Mode: compact
- Context hash: d22a61d430bc9013fb343bbfa888f1de452e9e190a5a0dd313554e033d159e68

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/claim-tenant-analytics/proposal.md

- Source: openspec/changes/claim-tenant-analytics/proposal.md
- Lines: 1-27
- SHA256: 601fb27c2e1ae4dc06e2abeff4aa215525ff10ffa1b69074f07f237e26be6fd1

```md
## Why

运营分析看板已有只读端点 `GET /api/v1/claims/analytics/summary` 与前端 `/analytics` 菜单，聚合终态分布、FALLBACK 降级、影子自动化率、P50–P90 时长，但全部是跨租户全量口径。PRD §14 V2.0 要求「运营报表」支持按保司（租户）维度对比。本 change 在**不改指标口径、不把影子值当出金率、不跨租户泄漏**的前提下，补按 tenant 分组的只读报表能力。

## What Changes

- **租户分组聚合**：`ClaimAnalyticsApplicationService` 增加按 `tenantId` 分组的终态分布、影子自动化率、时长分位聚合。
- **只读端点扩展**：`ClaimAnalyticsController` 增加按租户维度的查询参数与响应分组；沿用现有 `scopeNote` 防「影子值当出金率」造假。
- **前端分组视图**：`/analytics` 增加租户维度切换与对比；无租户维度时退化为现有全量口径。
- **规格与门禁**：新增 capability spec 定义分组口径、租户隔离、降级语义；补变异测试。

**非目标（明确不做）**：不改任何指标定义与计算口径；不把 Shadow 影子自动化率表述为生产出金率；不跨租户返回数据；不新增 DDL；不改资金闸或赔付语义。

## Capabilities

### New Capabilities
- `claim/tenant-analytics`: 按保司（租户）维度的运营报表契约——分组口径、租户隔离、scopeNote 降级语义、无维度时的全量回退，以及变异可证伪性。

### Modified Capabilities
- 无。不修改既有分析能力的需求口径；本 change 增加分组维度，不改指标定义。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app`（application、adapter/web）；前端 `bone-claim-app` 的 `/analytics` 页面。
- **数据**：仅读取现有案件的 `tenantId` 字段，无 schema 变更。
- **测试**：租户隔离、分组正确性、scopeNote 保留、无维度回退场景测试。
- **不受影响**：指标口径、资金路径、provider、DDL、合规分册。

```

## openspec/changes/claim-tenant-analytics/design.md

- Source: openspec/changes/claim-tenant-analytics/design.md
- Lines: 1-44
- SHA256: 2ef5bb1b022a9fbcf241d54f318fbaacbd3d6cf77f1dbce159b6006d6c48b38d

```md
---
comet_change: claim-tenant-analytics
role: technical-design
canonical_spec: openspec
---

## Context

现有 `ClaimAnalyticsApplicationService` 聚合终态分布/FALLBACK/影子自动化率/P50–P90 时长，自带 scopeNote 防造假；`ClaimAnalyticsController` 提供只读端点，前端 `/analytics` 已是正式菜单。缺的只是按 `tenantId` 分组的维度。详见 proposal.md 的 Why。

## Goals / Non-Goals

**Goals:**
- 在现有指标聚合上叠加租户分组维度，复用同一指标定义
- 保证跨租户隔离，默认无维度回退全量
- 保留 scopeNote 降级语义

**Non-Goals:**
- 不改指标定义、不新增 DDL、不改资金路径

## Decisions

**决策 1：复用现有聚合逻辑，外层按 tenantId 分组。**
不改 `ClaimAnalyticsApplicationService` 的指标计算内核，只在结果层按 tenantId 分组。备选是为每个租户单独跑聚合——否决，因重复计算且易产生口径漂移。

**决策 2：租户隔离在查询层强制。**
分组查询 MUST 按请求方权限过滤 tenantId，不依赖前端传参。备选是前端控制——否决，因存在越权取数风险。

**决策 3：无维度回退全量。**
未指定租户维度时返回与现有一致的全量结果，保证向后兼容。

## Risks / Trade-offs

- [分组查询数据量上升] → 复用现有聚合的时间窗口与只读端点；规模化性能留待容量阶段评估
- [影子指标被误读为出金率] → scopeNote 强制随分组结果返回，测试钉死其存在
- [跨租户泄漏] → 查询层强制过滤 + 隔离测试

## Migration Plan

无数据迁移。部署后默认（无租户维度）行为与现网一致；前端新增租户切换为增量 UI，不影响旧入口。

## Open Questions

无。

```

## openspec/changes/claim-tenant-analytics/tasks.md

- Source: openspec/changes/claim-tenant-analytics/tasks.md
- Lines: 1-21
- SHA256: 8dcb2e36e9b42bb698a0f68ec954c6c87c8eba27bf9ee2e728b4e95a33a5b400

```md
## 1. 现状与口径确认

- [ ] 1.1 核对 `ClaimAnalyticsApplicationService` 现有指标（终态分布/FALLBACK/影子自动化率/P50-P90）与 scopeNote 生成逻辑；验收：逐项确认并在任务备注引用文件+行号 <!-- comet-task:01f54f2e-8331-4130-8028-9f6cb9274e88 -->
- [ ] 1.2 确认案件实体含 `tenantId` 且现有查询未分组；验收：代码确认+grep <!-- comet-task:dbe4f879-eb20-4102-be73-489ade5c38bb -->

## 2. 租户分组实现

- [ ] 2.1 在聚合结果层按 tenantId 分组；验收：分组场景测试通过，各组指标与全量口径一致 <!-- comet-task:97ba6261-bccc-40d1-97dc-5f950b80d274 -->
- [ ] 2.2 查询层强制按请求方权限过滤 tenantId；验收：租户 A 请求不返回 B 数据，越权测试通过 <!-- comet-task:bec6fa02-e0a6-453f-a94a-79062433df89 -->
- [ ] 2.3 无租户维度时回退全量口径；验收：与现有 `/analytics/summary` 结果一致，回归测试通过 <!-- comet-task:ac9876b9-e9e4-41df-9993-3f9a7fbef891 -->
- [ ] 2.4 影子自动化率随分组结果携带 scopeNote；验收：响应必含 scopeNote 且表述为影子口径，测试通过 <!-- comet-task:cc58e374-3c69-44bb-9b87-bfefe3cf518d -->

## 3. 前端分组视图

- [ ] 3.1 `/analytics` 增加租户维度切换与对比；验收：前端页面可切换租户查看分组报表，手动验证通过 <!-- comet-task:cc2d3ff8-0ca4-4473-9d60-86f5bae876dc -->

## 4. 边界与门禁

- [ ] 4.1 确认报表只读、不改指标口径/资金/配置；验收：grep + 测试证明无写操作 <!-- comet-task:67df3ee1-9e58-48fb-8828-c6c97b2380c1 -->
- [ ] 4.2 补变异双向钉子（分组口径、隔离过滤、scopeNote）；验收：改一处必红还原必绿 <!-- comet-task:6242c046-44f0-4b79-b806-e22f66dc11b3 -->
- [ ] 4.3 跑 synap-claim 相关测试全绿；验收：mvn 测试通过，测试数从收据取真实值 <!-- comet-task:42f28dc9-91fb-4c59-bfb7-8095ced2af89 -->

```

## openspec/changes/claim-tenant-analytics/.openspec.yaml

- Source: openspec/changes/claim-tenant-analytics/.openspec.yaml
- Lines: 1-2
- SHA256: 01c3365f21ad2129d7e1db5577bc59c382883cdcbfece60900a8ac86293e3398

```md
schema: spec-driven
created: 2026-11-23

```

## openspec/changes/claim-tenant-analytics/specs/claim/tenant-analytics/spec.md

- Source: openspec/changes/claim-tenant-analytics/specs/claim/tenant-analytics/spec.md
- Lines: 1-37
- SHA256: 3f6d97ada8705de6f19f7003633a81ac83f675ac1b7a48819d04affaf0875c39

```md
## Purpose

定义按保司（租户）维度的运营报表契约：租户分组、跨租户隔离、影子自动化率的 scopeNote 降级语义与无维度时的全量回退，使运营分析既能按租户对比，又不改变既有指标口径或产生跨租户数据泄漏。

## ADDED Requirements

### Requirement: 租户分组聚合
运营报表 MUST 支持按 `tenantId` 分组返回终态分布、影子自动化率与时长分位；分组结果与全量口径 MUST 使用同一套指标定义，不得为分组另立计算口径。

#### Scenario: 按租户分组返回
- **WHEN** 请求按租户维度的运营报表
- **THEN** 响应按 tenantId 分组，每组指标与全量口径定义一致

#### Scenario: 无租户维度时回退全量
- **WHEN** 请求未指定租户维度
- **THEN** 返回与现有 `/analytics/summary` 一致的全量口径结果

### Requirement: 跨租户隔离
报表 MUST NOT 在任一租户的分组结果中返回其他租户的数据；请求方无权访问的租户数据 MUST 被排除。

#### Scenario: 租户 A 看不到租户 B 数据
- **WHEN** 租户 A 请求分组报表，数据集中同时含 A、B 两租户案件
- **THEN** 响应仅含租户 A 的分组数据，不含租户 B

### Requirement: 影子指标降级语义
分组报表中的影子自动化率 MUST 携带 scopeNote，明确其为 Shadow 评测口径而非生产出金率；该指标 MUST NOT 被表述或命名为生产自动化率。

#### Scenario: 影子指标带降级说明
- **WHEN** 分组结果包含影子自动化率
- **THEN** 响应附 scopeNote 说明其为影子评测口径，不得作为出金率证据

### Requirement: 指标口径与资金边界
本能力 MUST NOT 改变任何既有指标的计算方式，MUST NOT 改变赔付金额、放款开关或 fail-closed 姿态；报表为只读观测，不产生任何业务副作用。

#### Scenario: 报表只读无副作用
- **WHEN** 生成分组报表
- **THEN** 不产生任何案件状态变更、资金写入或配置改动

```
