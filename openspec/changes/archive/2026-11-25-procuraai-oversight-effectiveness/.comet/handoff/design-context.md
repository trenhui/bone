# Comet Design Handoff

- Change: procuraai-oversight-effectiveness
- Phase: design
- Mode: compact
- Context hash: 2b2426d43f4e520118ca0649a3736e1b688405148ec50235a6bc2fe0a096d3ea

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-oversight-effectiveness/proposal.md

- Source: openspec/changes/procuraai-oversight-effectiveness/proposal.md
- Lines: 1-31
- SHA256: 8ac1e048c93face4032f02be06697dfa9570233b2860243b27f9c49536987e9a

```md
# Proposal

## Why

采购 AI 治理已落地人工终决台账（`HumanFinalDecision` 记录复核人、耗时、AI 结论与人工结论、`overrideAccepted`、`evaluationFeedbackRaised`），但**这些字段没有任何护栏或指标消费**：一次 50ms 的"橡皮图章"复核、一次无理由的推翻、一次未回流评测的 override，与一次真正有效的人类监督在系统中完全等价。Gartner 2026-05 对四级自治明确警告 *approval fatigue 会让审批退化成"虚假安全"*——有 HITL 按钮 ≠ 有真实控制。上一轮 `ai-drift-metrics` 只观测了 override **比率**，无法识别上述三类监督质量失真。本 change 用领域纯函数三态判词 + 只读治理端点补齐这一缺口，坚持 AI 仅辅助、人工终决、fail-closed、不声明模型效果达标。

## What Changes

- 新增领域值对象与策略：监督信号三态判词（`UNDETERMINED` / `NO_ACTIONABLE_SIGNAL` / `REVIEW_REQUIRED`），识别三类风险——复核耗时低于 owner 配置下限（橡皮图章）、override 未填写理由、override 未回流评测扩样。
- 新增监督阈值配置（`reviewDurationFloorMs`、`minSampleSize`），服务端配置、不接受客户端注入；缺省未配置即 `UNDETERMINED`。
- 复用 `HumanDecisionLedger`：新增按能力码返回终决记录列表的只读查询，领域层据此计算真实计数，不新增端口、不改 DDL、不落新表。
- 新增只读 HTTP 端点 `GET /api/v1/governance/capabilities/{code}/oversight`，复用 `procurement:ai-governance:read` 权限码，返回三态判词、原因码、真实计数与"不构成模型背书"标记；读操作无副作用。
- 新增领域/应用/契约测试与内部模拟 POC 验证报告，覆盖 fail-closed、权限、无副作用与三类风险触阈。

## Capabilities

### New Capabilities

- `procurement/ai-oversight-effectiveness`: 采购 AI 人类监督有效性护栏——基于人工终决台账真实数据，以 fail-closed 三态判词观测复核是否流于形式、推翻是否有理由并回流评测，供 owner 判断是否需要强化监督，明确不构成模型效果背书。

### Modified Capabilities

- 无。不改动任何既有 capability 的需求行为。

## Impact

- 代码：`bone-apps/bone-procurement/bone-procurement-ai-governance`（domain / application / adapter / infrastructure 的 HumanDecisionLedger 只读扩展）。
- API：新增一个只读治理端点，纳入 `AiGovernanceControllerContractTest` 的端点与权限锁定。
- 配置：`AiGovernanceProperties` 新增监督阈值字段，默认未配置。
- 数据：不新增 DDL/表/列，只读消费既有 `HumanDecisionEntity` 字段。
- 门禁：不改 CI 三入口判据集合、不改 evalset 状态、不改 ProcuraAI 文档族真源。

```

## openspec/changes/procuraai-oversight-effectiveness/design.md

- Source: openspec/changes/procuraai-oversight-effectiveness/design.md
- Lines: 1-66
- SHA256: ffebdcf00b8aab61d6af0e323fabbe736e9ddc0e326fecae4f92e337e6c2c543

```md
---
comet_change: procuraai-oversight-effectiveness
role: technical-design
canonical_spec: openspec
---

# Design

## Context

`bone-procurement-ai-governance` 已有完整的人工终决台账：`HumanFinalDecision` 记录 `reviewDurationMs`、`reason`（推翻理由）、`aiConclusion`/`finalConclusion`（判定是否 override）、`evaluationFeedbackRaised`，并通过 `HumanDecisionLedgerAdapter` 以 SDK 仓储持久化（`HumanDecisionEntity` 各列齐备）。上一轮 `ai-drift-metrics` 已建立"领域纯函数三态判词 + 服务端配置阈值 + 只读投影 + fail-closed"的成熟范式。本设计复用该范式，把监督质量的三类失真（耗时过短/无理由推翻/未回流评测）纳入同一套只读观测，不引入模型、不改 DDL、不改 CI 门禁集合。

## Goals / Non-Goals

**Goals:**
- 用领域纯函数基于人工终决真实记录计算监督有效性三态判词，识别三类监督质量失真。
- 提供只读、带权限、无副作用的治理端点。
- 阈值服务端配置、缺省 fail-closed、客户端不可注入。
- 完整测试与内部模拟 POC 证据，做到机器可验证。

**Non-Goals:**
- 不接真实模型、不调用 LLM、不做评分/推理。
- 不改 DDL/表/列、不新增端口实现（仅扩展既有 `HumanDecisionLedger` 只读查询）、不改 evalset、不改 CI 三入口判据集合、不改 ProcuraAI 文档族真源。
- 不自动改变能力状态、不放开任何自治档、不声明模型效果或监督充分性达标、不替代人工终决。

## Decisions

### D1 领域纯函数裁决，复用 DriftSignal 范式
新增 `OversightVerdict`（三态枚举 + `isActionable()`）、`OversightThresholds`（record，`reviewDurationFloorMs`/`minSampleSize`/`fastReviewRatioThreshold`，缺省 `null` 未配置 + fail-closed 校验）、`OversightSignal`（结果 record，含三类真实计数、原因码、`claimsModelHealthy()=false`）、`OversightSignalPolicy`（纯函数裁决）。
**理由**：与 `DriftSignalPolicy` 同构，便于测试、便于 owner 复用判读习惯；纯函数不依赖 Spring/IO，可被 TDD 完整覆盖。
**备选**：在应用服务内内联判断——被否，因为无法独立做负向探针与变异测试。

### D2 数据来自既有人工终决台账，只读扩展不新增端口
`HumanDecisionLedger` 新增 `List<HumanFinalDecision> findByCapability(CapabilityCode code)`；`HumanDecisionLedgerAdapter` 复用其私有 `query("capabilityCode", code.value())` 实现；`InMemoryHumanDecisionLedger`（已退役骨架）同步补齐方法签名以保持接口一致。应用服务据此用 stream 统计样本数与三类风险计数。
**理由**：数据已在库、字段齐备，只读扩展风险最低；不新增 DDL/表/仓储，符合范围边界。
**备选**：新增独立 repository/端口——被否，超出范围且引入双写风险。

### D3 阈值仅由服务端配置提供，缺省未配置
`AiGovernanceProperties` 新增 `oversightReviewDurationFloorMs`（`Long`，默认 `null`）、`oversightMinSampleSize`（int，默认 20）、`oversightFastReviewRatio`（`Double`，默认 `null`）。Controller 只读服务端属性构造 `OversightThresholds`，端点方法只接受路径参数 `code`，不接收任何阈值请求参数。
**理由**：owner 通过线不可由客户端注入（上一轮独立审查 I1 的教训）；阈值缺省未配置即 `UNDETERMINED`，fail-closed。

### D4 只读端点复用现有权限码与投影范式
`GET /api/v1/governance/capabilities/{code}/oversight` + `@PreAuthorize("hasAuthority('procurement:ai-governance:read')")`，返回 `ApiResponse<OversightMetricsView>`（含 `modelHealthClaim=NONE`、`requiresOwnerReview`）。契约测试锁定端点数量、权限码与方法签名（只 `String.class`）。
**理由**：与 `drift` 端点一致，最小权限面、无新权限码、无 DB 写。

### D5 三类风险优先于比率，未触阈不构成背书
裁决顺序：非法输入拒绝 → 阈值未配置 `UNDETERMINED` → 样本不足 `UNDETERMINED` → 无理由推翻/未回流评测任一存在即 `REVIEW_REQUIRED` → 耗时过短占比触阈 `REVIEW_REQUIRED` → 否则 `NO_ACTIONABLE_SIGNAL`。任何判词 `claimsModelHealthy()` 恒为 false。
**理由**：推翻必须留理由并回流是硬治理要求（不是统计概率），出现即需 owner 复核；比率阈值用于"橡皮图章"这种规模化失真。
**边界**：`evaluationFeedbackRaised` 由既有人工终决写入路径在 override 时自动置真（上一轮已交付的契约），本 change 是只读护栏、不改该语义；因此"未回流评测"仅覆盖台账中标记确为假的历史遗留/外部写入记录。让新数据由真实回流流程显式赋值属人工终决契约变更，留 owner 独立立项，不在本 change 范围。

## Risks / Trade-offs

- [风险] 推翻理由可能含 PII → 缓解：只统计"是否为空"，不回传 `reason`/`output` 原文；投影只含计数与原因码。
- [风险] 阈值未配置时若把全部样本算作耗时过短，`UNDETERMINED` 判词下仍会外泄"100% 橡皮图章"的误导计数 → 缓解：`reviewDurationFloorMs` 未配置时 `fastReviewCount` 直接置 0，并以负向测试锁定。
- [诚实边界] "未回流评测"对新 API 数据不可达（既有写入路径自动置真）→ 缓解：spec/design 明示仅覆盖历史遗留/外部写入记录，应用测试显式断言，Verify 报告列为留给 owner 的独立立项项，不声称护栏已验证新数据回流。
- [风险] `findByCapability` 全量 load 记录在大租户下可能偏重 → 缓解：本期为只读治理端点、低频调用；不在主业务链路；后续如需要再由 owner 立项分页聚合。
- [风险] 已退役 `InMemoryHumanDecisionLedger` 与 SDK adapter 接口不同步导致编译失败 → 缓解：同批改方法签名，编译期即可发现。
- [风险] 比率阈值边界（等于阈值）口径分歧 → 缓解：明确 `>=` 触阈，与 drift 一致，并加边界测试。

## Migration Plan

无数据迁移。新增字段默认值使配置缺省为未配置，行为 fail-closed；部署后由 owner 显式配置阈值才会产生 `REVIEW_REQUIRED`/`NO_ACTIONABLE_SIGNAL`。回滚只需移除端点与领域类，无需数据清理。

## Open Questions

无。所有会影响规格与任务拆分的问题均已在探索阶段由源码实证解决。

```

## openspec/changes/procuraai-oversight-effectiveness/tasks.md

- Source: openspec/changes/procuraai-oversight-effectiveness/tasks.md
- Lines: 1-28
- SHA256: da0a94ea77bb94188688c91fe6814ebdcce516d9716a201f6a917846fe6764c7

```md
# Tasks

## 1. 领域层：监督有效性三态判词

- [x] 1.1 新增 `OversightVerdict` 三态枚举（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED）与 `isActionable()`；用枚举单元测试验证三态与可操作性判定 <!-- comet-task:2e4c60fb-5a3c-4cf4-8b43-6f030fc1dc4e -->
- [x] 1.2 新增 `OversightThresholds` record（reviewDurationFloorMs/minSampleSize/fastReviewRatioThreshold），缺省未配置、非法阈值 fail-closed 校验；用单元测试验证负数/非正数被拒与 configured() 判定 <!-- comet-task:96d90b1d-7b2c-4bf0-b20d-e2aaf0f7d798 -->
- [x] 1.3 新增 `OversightSignal` 结果 record（能力码/判词/样本数/三类风险计数/原因码/computedAt），`claimsModelHealthy()` 恒 false；用单元测试验证护栏 <!-- comet-task:2f2166da-abfe-4da1-a673-4e68567faf91 -->
- [x] 1.4 新增 `OversightSignalPolicy` 纯函数裁决：阈值未配置/样本不足→UNDETERMINED，无理由推翻/未回流评测/耗时过短触阈→REVIEW_REQUIRED，否则 NO_ACTIONABLE_SIGNAL；用 TDD 覆盖全部六类场景与边界（等于阈值、100%、零样本、非法计数），RED→GREEN <!-- comet-task:89adf35e-c053-4dde-9e00-80ac81c64e02 -->

## 2. 应用层：真实计数与只读服务

- [x] 2.1 扩展 `HumanDecisionLedger` 接口新增 `findByCapability(CapabilityCode)` 只读查询，SDK adapter 复用 query，退役内存实现同步签名；用编译通过验证接口一致 <!-- comet-task:97c9c4aa-5f9c-4864-aa52-8b2836310c32 -->
- [x] 2.2 在 `AiGovernanceApplicationService` 新增 `oversightSignal(code, thresholds)`：校验能力已登记，按能力码取终决记录统计样本数与三类风险计数，委托纯函数裁决；用应用测试验证真实计数一致、未登记能力拒绝、无副作用 <!-- comet-task:2a3136dc-0ef3-4a5d-8f4a-11b5a98198cc -->
- [x] 2.3 新增 `OversightMetricsView` 只读投影（含 modelHealthClaim=NONE、requiresOwnerReview、原因码与计数）；用单元测试验证 from(signal) 映射不回传 PII 与原文 <!-- comet-task:5ce4acba-2028-4964-bf70-1800142be5dd -->

## 3. 适配层：配置与只读端点

- [x] 3.1 在 `AiGovernanceProperties` 新增 oversight 阈值字段（reviewDurationFloorMs 默认 null、minSampleSize 默认 20、fastReviewRatio 默认 null）及 getter/setter；用配置测试验证缺省 fail-closed <!-- comet-task:415e4ad1-d12f-4f63-8084-6f672971bf9f -->
- [x] 3.2 在 `AiGovernanceController` 新增 `GET /capabilities/{code}/oversight`，注入 properties 构造服务端阈值，方法只接受路径参数 code，复用 read 权限码；用契约测试锁定端点数量（十端点）、权限码与方法签名不含请求参数 <!-- comet-task:7164c6a2-5293-4d5e-b9a4-0fb3584984a2 -->
- [x] 3.3 更新 `AiGovernanceToggleTest`：验证 oversight 阈值缺省未配置；用测试通过验证 <!-- comet-task:e106a57b-17e7-454a-bdc5-e879ecb1db65 -->

## 4. 验证与内部模拟 POC

- [x] 4.1 运行 ai-governance 模块全部单测（含新增领域/应用/投影/契约/配置测试），零失败；用 maven surefire 报告验证 <!-- comet-task:e56a7657-9653-4187-ba65-2ba0f37e4b10 -->
- [x] 4.2 运行采购聚合模块构建与测试，确认无跨模块回归；用 maven 退出码 0 验证 <!-- comet-task:ef93af99-b05f-4b56-bee9-29e197446955 -->
- [x] 4.3 执行内部模拟 POC：以人工构造的真实终决记录（有效复核/橡皮图章/无理由推翻/未回流评测/样本不足）驱动端点投影，验证三态判词与 fail-closed 与规格一致；用 POC 报告记录每步输入输出与断言 <!-- comet-task:8b8e19e4-dc78-4ef3-8160-c4d59e279ebc -->
- [x] 4.4 执行独立审查（standard）：检查 DDD 分层、fail-closed、PII 禁入、无副作用、阈值不可注入；CRITICAL/IMPORTANT 必须修复后复验，报告审查结论 <!-- comet-task:317bbefc-480b-4463-8b93-adf98f42ba11 -->
- [x] 4.5 生成 Verify 报告（docs/superpowers/reports/），汇总交付物 file:line、POC 场景、负向探针、门禁结果、独立审查与诚实边界；用报告文件存在且结论 PASS 验证 <!-- comet-task:84554a99-3c74-465b-924f-c8390d88fb7b -->

```

## openspec/changes/procuraai-oversight-effectiveness/.openspec.yaml

- Source: openspec/changes/procuraai-oversight-effectiveness/.openspec.yaml
- Lines: 1-2
- SHA256: 3c3c72cd1a100807c59ef8347538654b4d2d22269f59561c985b01cb44c39491

```md
schema: spec-driven
created: 2026-11-25

```

## openspec/changes/procuraai-oversight-effectiveness/specs/procurement/ai-oversight-effectiveness/spec.md

- Source: openspec/changes/procuraai-oversight-effectiveness/specs/procurement/ai-oversight-effectiveness/spec.md
- Lines: 1-77
- SHA256: 173f930d71e32daff3300a5240ad69c12a81fa45d0bf2ff9cbfe47d3eac5d6cd

```md
# procurement/ai-oversight-effectiveness Specification

## Purpose
为采购 AI 治理提供人类监督有效性的只读观测能力：基于人工终决台账的真实记录，以 fail-closed 三态判词识别复核流于形式（耗时过短）、推翻无理由、推翻未回流评测三类监督质量失真，帮助 owner 判断是否需要强化人工监督，同时明确不声明模型效果达标、不替代人工终决。

## ADDED Requirements

### Requirement: 监督有效性三态判词

系统 SHALL 对指定能力码计算监督有效性信号，判词只能是 UNDETERMINED、NO_ACTIONABLE_SIGNAL、REVIEW_REQUIRED 三态之一；任何判词都不得对外声明"模型达标/模型健康/监督充分"，也不得自动改变任何能力状态或绕过人工终决。

系统 SHALL 仅依据人工终决台账的真实记录计算，识别以下三类可复核风险：
- 复核耗时低于 owner 配置的时长下限（`reviewDurationFloorMs`）视为"橡皮图章复核"；
- AI 结论与人工结论不一致（override）但理由为空视为"无理由推翻"；
- override 但 `evaluationFeedbackRaised` 为假视为"未回流评测"。

> 边界说明（诚实口径）：本 capability 是**只读审计护栏**，不改变人工终决既有契约。当前人工终决写入路径在 override 时自动把 `evaluationFeedbackRaised` 置为真（"推翻即触发评测增补"的既有承诺），因此通过本系统 API 新建的 override 不会命中"未回流评测"；该检查覆盖的是台账中标记确为假的记录（历史遗留、数据迁移或外部写入）。调用方不得据此声称"新数据已被检测为回流正常"。让新数据的回流标记由真实评测回流流程显式赋值属于人工终决契约变更，需独立立项，不在本 capability 范围内。

#### Scenario: 阈值未配置时不可判定
- **WHEN** 能力已登记且存在人工终决记录，但监督阈值未配置
- **THEN** 判词为 UNDETERMINED，原因为阈值未配置，不返回监督质量结论

#### Scenario: 样本不足时不可判定
- **WHEN** 已配置阈值，但人工终决记录数低于最小样本量
- **THEN** 判词为 UNDETERMINED，原因为样本不足，不返回监督质量结论

#### Scenario: 橡皮图章复核触阈需复核
- **WHEN** 已配置阈值且样本充足，复核耗时低于下限的记录达到或超过 owner 配置的比例阈值
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注复核耗时过短，返回真实计数，提示需 owner 复核监督流程，且不自动改变能力状态

#### Scenario: 无理由推翻需复核
- **WHEN** 已配置阈值且样本充足，存在 override 但理由为空的记录
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注无理由推翻，返回真实计数，且不自动改变能力状态

#### Scenario: 未回流评测需复核
- **WHEN** 已配置阈值且样本充足，台账中存在 override 但 `evaluationFeedbackRaised` 为假的记录（历史遗留或外部写入）
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注未回流评测，返回真实计数，且不自动改变能力状态

#### Scenario: API 新建 override 不命中未回流评测
- **WHEN** override 由本系统人工终决 API 新建，其 `evaluationFeedbackRaised` 被既有写入路径置为真
- **THEN** 该记录不计入未回流评测数；系统不得因此对外声称"回流已被本护栏验证正常"

#### Scenario: 无风险信号不构成背书
- **WHEN** 已配置阈值且样本充足，三类风险均未触阈
- **THEN** 判词为 NO_ACTIONABLE_SIGNAL，且明确该判词不构成模型效果或监督充分性背书

### Requirement: 监督信号 fail-closed 输入约束

系统 SHALL 对非法输入 fail-closed：阈值为负、最小样本量非正、时长下限为负、记录计数为负，均不得产生有效判词。阈值只能由服务端配置提供，不得由 HTTP 请求参数注入。

#### Scenario: 非法阈值被拒绝
- **WHEN** 配置的时长下限或最小样本量或风险比例为负数
- **THEN** 该配置被拒绝，不产生监督信号

#### Scenario: 未登记能力不可评估
- **WHEN** 对未登记的能力码请求监督有效性信号
- **THEN** 请求被拒绝，不返回任何判词或指标

#### Scenario: 客户端不得注入阈值
- **WHEN** 调用方尝试通过请求参数覆盖服务端阈值
- **THEN** 请求参数被忽略或拒绝，判词只依据服务端配置计算

### Requirement: 监督有效性只读观测端点

系统 SHALL 提供需要 `procurement:ai-governance:read` 权限的只读 HTTP 端点，返回能力码、三态判词、原因码、真实计数（样本数/耗时过短数/无理由推翻数/未回流评测数）与"不构成模型背书"标记；读操作不得写入追溯链、不得改变能力状态、不得落库。

#### Scenario: 只读端点返回真实计数
- **WHEN** 具 read 权限的调用方查询已登记能力的监督有效性
- **THEN** 端点返回 ApiResponse 包装的信号投影，各计数取自人工终决台账的真实记录

#### Scenario: 缺少权限被拒绝
- **WHEN** 无 `procurement:ai-governance:read` 权限的调用方查询监督有效性
- **THEN** 请求被拒绝，不返回信号

#### Scenario: 读操作无副作用
- **WHEN** 多次查询同一能力的监督有效性
- **THEN** 追溯链条目数不变、能力状态不变、人工终决台账记录数不变、无新增持久化记录

```
