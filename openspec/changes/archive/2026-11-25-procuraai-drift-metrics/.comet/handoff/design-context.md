# Comet Design Handoff

- Change: procuraai-drift-metrics
- Phase: design
- Mode: compact
- Context hash: 35c97bd4781e8118fff093a242de8a7f4905dfc7d076f20ccc5d1683e8796daf

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-drift-metrics/proposal.md

- Source: openspec/changes/procuraai-drift-metrics/proposal.md
- Lines: 1-29
- SHA256: 443efb7756c45f10740b7f7b9f5c6283e34ab24c993c090a2d756abd8d67a78a

```md
# Proposal

## Why

procuraAI 的 AI 决策治理骨架已落地（能力登记、HITL 人工终决、override 回流、kill switch、披露双标识），但金监总局《关于银行业保险业人工智能安全开发应用的指导意见》§6/§9 要求的「漂移监测闭环」与业界 STP/override 率指标仍只有计数输入（`countOverrides`），没有受控判词与只读观测面。没有判词就无法在样本不足或阈值未配置时 fail-closed，容易被误读成"模型健康"。

## What Changes

- 新增**漂移信号**领域值对象与纯函数裁决：三态判词（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED），基于人工终决台账与追溯链的真实计数。
- 阈值未配置、样本不足、计数非法一律判 UNDETERMINED；触阈仅表示"需 owner 复核/评测扩样"，**任何判词都不声明模型效果达标**。
- 新增只读治理指标投影与 HTTP 端点（read 权限），来自现有台账/追溯的真实计数，不引入新 DDL、不落库、不改能力状态。
- 新增领域单测、应用服务负向测试、HTTP 契约测试与内部模拟 POC 验证报告。

## Capabilities

### New Capabilities

- `procurement/ai-drift-metrics`: AI 决策漂移信号与治理指标的只读观测能力——定义三态判词、fail-closed 规则与只读指标的对外行为。

### Modified Capabilities

（无）

## Impact

- 代码：`bone-procurement-ai-governance` 模块新增领域值对象/领域服务、应用只读方法、Controller 只读端点及测试。
- 数据：仅读取现有 `t_proc_human_decision` / `t_proc_governance_trace`（经端口），不新增表、不改 `bone-init.sql`。
- 门禁：不改 A10.17 三入口判据集合、不改 evalset 状态、不改 CI 配置。CI 绿只证明结构与 fail-closed 不变量成立，不代表模型效果达标。
- 权限：新端点使用现有 `procurement:ai-governance:read` 权限码。

```

## openspec/changes/procuraai-drift-metrics/design.md

- Source: openspec/changes/procuraai-drift-metrics/design.md
- Lines: 1-59
- SHA256: bedf12520ba3d21a53cbb809c008f35d4572a259c380e827f04de01ed0a4030d

```md
# Design

## Context

procuraAI 治理模块（`bone-procurement-ai-governance`）已有人工终决台账 `HumanDecisionLedger.countOverrides` 与追溯链 `GovernanceTracePort.listByCapability`，可给出真实推翻计数；但缺漂移判词与只读指标面。治理 bean 由 `bone.procurement.ai-governance.enabled` 条件装配（默认关闭），端点强制 `@PreAuthorize`。DDL 已有 `t_proc_human_decision` / `t_proc_governance_trace`（`bone-init.sql`），无需新增表。

## Goals / Non-Goals

**Goals:**
- 用真实计数给出 fail-closed 漂移三态判词，判词可被单测与 POC 验证。
- 提供只读治理指标端点，不产生副作用。
- 明确"判词 ≠ 模型效果结论"的合规边界。

**Non-Goals:**
- 不接真实模型、不训练、不声明模型效果达标。
- 不改 evalset 状态（保持 `seededDraft`）、不改 owner/法务结论、不冻结契约/DDL。
- 不改 A10.17 三入口判据集合、不改 CI 配置、不新增门禁 key。
- 不自动改变能力状态、不触发真实重评/再训练（属 owner/L3）。

## Decisions

### 决策 1：漂移判词为纯领域值对象 + 纯函数裁决

在 `domain/model/capability/` 新增 `DriftVerdict`（三态枚举）、`DriftThresholds`（阈值配置 record）、`DriftSignal`（判词结果 record），由 `domain/service/DriftSignalPolicy` 纯函数计算。判定顺序：阈值合法性 → 样本充足性 → 计数一致性 → 触阈判定。

- **理由**：领域零框架依赖，可被 ArchUnit `domain_independent` 守护；纯函数无需端口/mock，负向探针确定性强；符合现有 `CapabilityGovernancePolicy` / `FairnessRule` 的领域服务惯例。
- **替代方案**：放在应用服务内判定——被否，因无法被领域单测独立覆盖，且违反分层职责。

### 决策 2：计数取自现有端口，不新增持久化

应用服务 `driftSignal(code)` 用 `tracePort.listByCapability(code)` 统计 `HUMAN_FINAL_DECISION` / `HUMAN_OVERRIDE` 事件数作为样本数，用 `ledger.countOverrides(code)` 取推翻数。不新增 port、不改 DDL、不写 trace。

- **理由**：`recordHumanDecision` 每次终决都会 trace append（现有测试 `治理裁决与终决全程留痕` 已证明），计数与台账自洽；只读端点不得有副作用。
- **替代方案**：新增 `countAll` 端口方法——被否，因需要改所有实现（SDK 适配 + 内存 + 测试 stub）并触碰仓储方法白名单门禁，超出最小变更。

### 决策 3：阈值由调用方配置传入，缺省不可判定

`DriftThresholds` 缺省为未配置（null 阈值），此时判 UNDETERMINED。应用服务方法签名接收显式阈值参数；HTTP 端点使用配置属性（默认未配置）。

- **理由**：阈值属 owner 通过线（D-10 未闭合），本 change 不得替 owner 定阈值；缺省 UNDETERMINED 是唯一诚实的 fail-closed 行为。
- **替代方案**：硬编码默认阈值（如 0.3）——被否，等于伪造 owner 通过线。

### 决策 4：只读投影 + 现有权限码

新增应用 DTO `DriftMetricsView`（record）与 Controller `GET /capabilities/{code}/drift`，权限 `procurement:ai-governance:read`。不新增权限码（避免改安全配置与前端授权面）。

## Risks / Trade-offs

- **样本数来自追溯事件计数，非独立终决计数表**：若历史终决未全部留痕会低估样本数 → 后果偏保守（倾向 UNDETERMINED），不会误报触阈；现有留痕测试保证每次终决都有事件。
- **阈值缺省不可判定**：开箱即用体验为 UNDETERMINED，需 owner 配置阈值才有信号 → 这是刻意诚实，非缺陷；在 POC 报告与端点原因码中说明。
- **hook 拦截写操作**：开发期需保持 selection 指向本 change（已 `state select procuraai-drift-metrics`）；如被其他会话切走需重新 select。

## Migration Plan

无数据迁移。新端点向后兼容（新增路径，不改既有契约）。回滚：移除新增类与端点即可，无库表变更。

## Open Questions

无影响构建的问题。真实阈值由 owner 在 D-10 闭合时给定，不影响本 change 的实现与验收（缺省 UNDETERMINED）。

```

## openspec/changes/procuraai-drift-metrics/tasks.md

- Source: openspec/changes/procuraai-drift-metrics/tasks.md
- Lines: 1-34
- SHA256: 992f81ea0e8071858ba8171f7b5c149c36399cf32c734fab9b8cf5c4a659c9e6

```md
# Tasks

## 1. 领域层：漂移信号值对象与裁决策略

- [ ] 1.1 新增 `DriftVerdict` 三态枚举（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED），含 `isActionable()` 与"不声明模型健康"语义 <!-- comet-task:26780ad8-5398-4bac-a1a9-bc0bc42601fa -->
- [ ] 1.2 新增 `DriftThresholds` 配置 record（缺省未配置、阈值 (0,1]、最小样本量正数校验） <!-- comet-task:a12efd58-ea0c-403e-948a-56da00dd6b6d -->
- [ ] 1.3 新增 `DriftSignal` 结果 record（能力码/判词/样本数/推翻数/override 率/阈值/最小样本/原因码/计算时刻，`claimsModelHealthy()` 恒 false） <!-- comet-task:48f4a725-0dd6-44a9-af8a-ff4f4948c66c -->
- [ ] 1.4 新增 `DriftSignalPolicy` 纯函数裁决：阈值合法性→样本充足→计数一致→触阈判定，原因码常量齐全 <!-- comet-task:054d7690-6697-40d3-963b-a3ad01d1723e -->
- [ ] 1.5 编写 `DriftSignalTest` 领域单测（阈值未配置/样本不足/触阈/未触阈/非法输入共 5 组，RED→GREEN） <!-- comet-task:8b0caf41-82bb-4c88-83ac-e91742786dbb -->

## 2. 应用层：只读方法与投影

- [ ] 2.1 新增 `DriftMetricsView` 只读投影 record <!-- comet-task:3a1c754e-077c-4ca2-955f-aee2e7bb2561 -->
- [ ] 2.2 `AiGovernanceApplicationService` 新增 `driftSignal(code, thresholds)`：从 tracePort 计样本数、ledger 取推翻数，未登记能力 fail-closed <!-- comet-task:5435d83d-d1d8-45e1-9913-ae4f2ae9fcaa -->
- [ ] 2.3 应用服务测试：真实计数一致性、阈值缺省/样本不足 fail-closed、触阈需复核无副作用、未登记能力拒绝 <!-- comet-task:14508752-ccb6-47bb-b93f-cbff4bc3d70f -->

## 3. 适配层：只读 HTTP 端点与契约

- [ ] 3.1 `AiGovernanceController` 新增 `GET /capabilities/{code}/drift`，权限 `procurement:ai-governance:read`，返回 `ApiResponse<DriftMetricsView>` <!-- comet-task:27527739-2e55-421a-8df3-efd1c9a64a65 -->
- [ ] 3.2 端点使用配置属性驱动阈值（默认未配置 → UNDETERMINED），不新增权限码 <!-- comet-task:bec22584-c189-4d4c-a492-1d52e7b8accd -->
- [ ] 3.3 更新 `AiGovernanceControllerContractTest`：路径/方法/权限/ApiResponse 包装锁定 <!-- comet-task:f33bebc2-824d-4ee9-bc74-f49348517648 -->

## 4. 验证与 POC 报告

- [ ] 4.1 `mvn spotless:apply` 后跑 ai-governance 模块测试全绿（含 ArchitectureTest） <!-- comet-task:79153d37-3707-451e-bf24-c4ec3f834ab2 -->
- [ ] 4.2 跑契约模块与采购聚合测试，确认无回归 <!-- comet-task:f4360b4d-20b4-4097-8624-afe72878651e -->
- [ ] 4.3 跑 procuraAI 文档门禁（alignment / evalset / frontmatter / persistence / event-chain），确认不退化 <!-- comet-task:7d47e61b-58c5-4f68-9e3c-b8d257b9aaca -->
- [ ] 4.4 内部模拟 POC：构造真实终决序列验证三态判词与 fail-closed 负向探针，产物落 POC 验证报告 <!-- comet-task:1b38d9b5-01e2-4df6-aed8-ada82be5a81e -->
- [ ] 4.5 报告诚实标注边界：CI 绿不等于模型效果达标、阈值待 owner、未改 DDL/CI 判据集合 <!-- comet-task:f17d0cb0-0317-4cf9-9b8a-941ab26e794f -->

## Workflow follow-up

- 归档到 `openspec/changes/archive/`，同步主 spec `procurement/ai-drift-metrics`
- 提交并推送 `origin/dev`（沿用用户既有授权）

```

## openspec/changes/procuraai-drift-metrics/specs/procurement/ai-drift-metrics/spec.md

- Source: openspec/changes/procuraai-drift-metrics/specs/procurement/ai-drift-metrics/spec.md
- Lines: 1-59
- SHA256: c063aed8de59be22d5931c0112ed14a724a7424a955ac32421654175952a7ed8

```md
# Spec Delta

## Purpose

为 AI 决策治理提供漂移信号与治理指标的只读观测能力：基于人工终决与追溯的真实计数，以 fail-closed 三态判词呈现漂移风险，帮助 owner 判断是否需要复核与评测扩样，同时明确不声明模型效果达标。

## ADDED Requirements

### Requirement: 漂移信号三态判词

系统 SHALL 对指定能力码计算漂移信号，判词只能是 UNDETERMINED、NO_ACTIONABLE_SIGNAL、REVIEW_REQUIRED 三态之一；任何判词都不得对外声明"模型达标/模型健康"。

#### Scenario: 阈值未配置时不可判定
- **WHEN** 能力已登记且存在人工终决记录，但漂移阈值未配置
- **THEN** 判词为 UNDETERMINED，原因为阈值未配置，override 率为不可判定（不返回数值）

#### Scenario: 样本不足时不可判定
- **WHEN** 已配置阈值，但观测窗口内人工终决样本数低于最小样本量
- **THEN** 判词为 UNDETERMINED，原因为样本不足，override 率为不可判定

#### Scenario: override 率触阈需复核
- **WHEN** 已配置阈值且样本充足，人工终决推翻率大于或等于阈值
- **THEN** 判词为 REVIEW_REQUIRED，返回真实 override 率，提示需 owner 复核与评测扩样，且不自动改变任何能力状态

#### Scenario: 未触阈仅为无信号不构成背书
- **WHEN** 已配置阈值且样本充足，人工终决推翻率低于阈值
- **THEN** 判词为 NO_ACTIONABLE_SIGNAL，返回真实 override 率，且明确该判词不构成模型效果背书

### Requirement: 漂移信号 fail-closed 输入约束

系统 SHALL 对非法输入 fail-closed：阈值超出 (0,1]、最小样本量非正数、推翻数大于样本数、样本数或推翻数为负，均不得产生有效判词。

#### Scenario: 非法阈值被拒绝
- **WHEN** 配置的 override 率阈值小于等于 0 或大于 1
- **THEN** 该配置被拒绝，不产生漂移信号

#### Scenario: 计数矛盾被拒绝
- **WHEN** 推翻数大于样本总数，或任一计数为负
- **THEN** 该次评估被拒绝，不产生漂移信号

#### Scenario: 未登记能力不可评估
- **WHEN** 对未登记的能力码请求漂移信号
- **THEN** 请求被拒绝，不返回任何判词或指标

### Requirement: 治理指标只读观测端点

系统 SHALL 提供需要 `procurement:ai-governance:read` 权限的只读 HTTP 端点，返回能力码、三态判词、样本数、推翻数、override 率、阈值、最小样本量、原因码；读操作不得写入追溯链、不得改变能力状态、不得落库。

#### Scenario: 只读端点返回真实计数
- **WHEN** 具 read 权限的调用方查询已登记能力的治理指标
- **THEN** 端点返回 ApiResponse 包装的指标投影，样本数与推翻数取自人工终决台账与追溯链的真实计数

#### Scenario: 缺少权限被拒绝
- **WHEN** 无 `procurement:ai-governance:read` 权限的调用方查询指标
- **THEN** 请求被拒绝，不返回指标

#### Scenario: 读操作无副作用
- **WHEN** 多次查询同一能力的治理指标
- **THEN** 追溯链条目数不变、能力状态不变、无新增持久化记录

```
