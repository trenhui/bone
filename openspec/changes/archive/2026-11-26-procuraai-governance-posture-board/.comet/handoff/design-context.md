# Comet Design Handoff

- Change: procuraai-governance-posture-board
- Phase: design
- Mode: compact
- Context hash: a5ef5b69552c09e0ff77b097aa38e6ac6862b6567c205a5d9d10fffff4ed1f9c

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-governance-posture-board/proposal.md

- Source: openspec/changes/procuraai-governance-posture-board/proposal.md
- Lines: 1-30
- SHA256: e8dcbde1c4ef7b8922448d442e355f31d14a698e7f14de42c99005e5ad5c2702

```md
# 提案：procuraAI 治理态势聚合看板

## Why

procuraAI 的 AI 治理后端已相当完整：全域 kill switch、能力三态 fail-closed 准入、具名责任人、一键停用/降级、人工终决与 override 回流、就绪度判词、漂移信号三态、监督有效性三态、合同审查 AI 参与披露。但这些信号分散在不同模块与端点，前端治理页 `GovernanceStatus.tsx` 仍只展示就绪度一项，形成「后端有护栏、运维与审查者看不到」的可观测断点。监管《关于银行业保险业人工智能安全开发应用的指导意见》§15 应用清单、§16 高风险审批与 §21 标识告知留存都要求治理状态可被持续查看与复核；NIST AI RMF 的 MEASURE/MANAGE 与 ISO 42001 也要求治理信号可聚合、可追溯。本轮把已就绪的只读信号统一聚合呈现，不新增任何自动决策能力，不改变人工终决红线。

## What Changes

- 新增只读聚合端点 `GET /api/v1/procurement/ai-governance/posture`，在 ai-governance 模块内聚合「能力清单 + 每能力裁决 + 漂移三态 + 监督三态 + 全域 kill switch + 汇总态势判词」；需要 `procurement:ai-governance:read` 权限，治理开关关闭时端点不注册（fail-closed）。
- 新增领域聚合判词 `PostureVerdict`（`UNDETERMINED` / `ALL_CLEAR` / `ATTENTION_REQUIRED`）与聚合视图 `GovernancePostureView`：任一分项不可判定即整体不可判定，任一能力 `REVIEW_REQUIRED` 即整体需关注；阈值仍只由 owner 服务端配置提供，不接受客户端注入。
- 前端 `GovernanceStatus.tsx` 由单卡片升级为治理态势看板：整体判词卡 + 能力明细表（状态/裁决/漂移/监督四列三态 Tag）+ 全域总闸 + 就绪度 + 合同审查披露入口；未知判词显示原始码，取数失败显示「无法确认」，不压成「正常」。
- 新增前端 `governanceApi.ts` 与契约类型，配套单测、后端领域/应用/契约测试与静态门禁复验。
- **边界（诚实呈现）**：合同审查 AI 参与披露是 per-review 端点，聚合看板不虚构全局披露状态；仅提供到已有披露页的入口。本轮不接真实模型、不声明模型效果达标、不改 owner 冻结的阈值与评测集下界、不改任何 CI 三入口判据集合。

## Capabilities

### New Capabilities

- `procurement/ai-governance-posture`: 只读聚合治理态势的领域判词、后端聚合端点契约与前端诚实渲染要求（fail-closed、三态、无客户端阈值注入）。

### Modified Capabilities

- 无。现有 `ai-readiness` / `ai-drift-metrics` / `ai-oversight-effectiveness` / `ai-decision-governance` / `human-review` 等行为不变，本轮只新增聚合视图，不修改其需求。

## Impact

- 后端：`bone-apps/bone-procurement/bone-procurement-ai-governance`（新增领域聚合判词/视图、应用聚合方法、控制器端点、权限与开关复用）+ 对应测试。
- 前端：`bone-frontend/apps/bone-procurement-app`（GovernanceStatus 页改造、governanceApi 与类型、单测）。
- 规格：`openspec/specs/procurement/ai-governance-posture/spec.md`（归档后新增主 spec）。
- 依赖：仅复用已有 `AiGovernanceApplicationService` 的 capability/decide/drift/oversight 能力与 `readinessApi`；不新增跨模块服务依赖、不改 DDL、不改 CI 判据集合。

```

## openspec/changes/procuraai-governance-posture-board/design.md

- Source: openspec/changes/procuraai-governance-posture-board/design.md
- Lines: 1-49
- SHA256: e9d1949dcdc82d41cf5d145f4f21415e0544e91b33c3c50d7cd8f71e59803fbf

```md
---
comet_change: procuraai-governance-posture-board
role: technical-design
canonical_spec: openspec
---

# Design：procuraAI 治理态势聚合看板

## Approaches

- **方案 A（选用）**：后端在 ai-governance 模块内新增单一只读聚合端点 `/posture` 聚合能力分项（裁决/漂移/监督），readiness 与 kill switch 由前端从既有端点补取，汇总判词在领域层确定性推导。取舍：汇总逻辑可在后端确定性单测、模块边界不破坏、复用既有 service；代价是前端消费两个数据源。
- **方案 B（否决）**：纯前端聚合，不新增后端端点。取舍：零后端改动，但判词规则散落前端、无法后端单测、前端直连多模块端点，违背单一真源，否决。
- **方案 C（否决）**：跨模块后端聚合 readiness + governance + disclosure。取舍：一次拿全，但引入 ai-governance→app 反向依赖或新聚合模块、破坏模块边界，且 disclosure 为 per-review 无全局态可聚合，否决。

## Context

procuraAI 治理信号已分散落地：`AiGovernanceApplicationService` 提供 `listCapabilities()` / `decide(code)` / `driftSignal(code, thresholds)` / `oversightSignal(code, thresholds)` 与 `countOverrides(code)`；全域 kill switch 与就绪度事实由 `bone-procurement-app` 的 `AiReadinessController` 提供；合同审查披露为 per-review 端点 `GET /{reviewId}/disclosure`。前端 `GovernanceStatus.tsx` 仅消费 readiness 一个端点。缺口不在能力本身，而在「一处聚合、确定性汇总、诚实渲染」。本轮严格只读，不触碰人工终决、模型接入、owner 阈值与 CI 判据集合。

## Goals / Non-Goals

**Goals:**

- 在 ai-governance 模块内新增单一只读聚合端点，复用既有 service 方法，不新增跨模块服务依赖。
- 领域层新增确定性汇总判词，规则单一真源：`UNDETERMINED` 优先于 `ATTENTION_REQUIRED` 优先于 `ALL_CLEAR`。
- 前端把治理页升级为态势看板，未知码显示原始码、取数失败显示「无法确认」、披露以入口呈现。
- 后端与前端都以失败/未知为安全侧，不因缺省数据渲染成「通过」。

**Non-Goals:**

- 不接真实模型、不声明模型效果达标、不修改评测集下界与 owner 冻结阈值。
- 不修改现有治理 spec（readiness/drift/oversight/decision-governance/human-review）的行为需求。
- 不新增写端点、不暴露 PII 或模型输出原文、不把披露伪造成全局状态。
- 不改 DDL、不改 CI 三入口判据集合、不改权限码。

## Decisions

1. **聚合位置 = ai-governance 应用服务**：新增 `posture()` 方法，遍历 `listCapabilities()`，对每个能力调用 `decide` / `driftSignal` / `oversightSignal`，组装 `GovernancePostureView`。理由：这些能力都在同一模块与同一 service 上，聚合无需跨模块依赖；readiness 与 kill switch 由前端从已有 `readinessApi` 单独取，避免 ai-governance 反向依赖 app 模块。
2. **阈值来源单一**：漂移/监督阈值仍由 `AiGovernanceProperties` 提供，缺省即 `unconfigured()` → 分项 `UNDETERMINED`。端点只接受路径/无参数，不暴露任何阈值查询参数。
3. **汇总规则领域化**：新增 `PostureVerdict` 枚举与 `GovernancePosturePolicy`（纯函数）。规则：任一分项为 UNDETERMINED 或能力缺失 → `UNDETERMINED`；否则任一能力 drift/oversight actionable 或全域总闸停用 → `ATTENTION_REQUIRED`；否则 `ALL_CLEAR`。kill switch 状态由前端传入视图组合，领域策略只消费已结构化的分项判词。
4. **视图不含敏感原文**：`GovernancePostureView` 与能力明细只含 code / displayName / state / verdict 文本与计数，不回传 reason 原文、model output、PII。
5. **前端诚实渲染**：新增 `governanceApi.ts` 与 `governancePostureView.ts`（含 `verdictTextOrRaw` 同型映射）；未知码回退原始串；取数失败统一「无法确认」；披露区域渲染到 ContractReviewDisclosure 页的入口。
6. **测试策略（TDD）**：先写领域策略测试、应用聚合测试、控制器契约测试、前端视图映射与页面渲染测试（RED），再实现（GREEN）。复用现有 `@ConditionalOnAiGovernanceEnabled` 开关测试与权限断言。

## Risks / Trade-offs

- **聚合 N+1 调用**：每个能力三次 service 调用，能力数量有限且治理为低频只读端点，可接受；若未来能力规模上升再考虑批量查询，本轮不做。
- **跨模块就绪度未纳入后端汇总**：readiness/kill switch 由前端组合展示，后端汇总判词不含 readiness 维度；已在 spec 明确汇总只覆盖能力分项，避免语义漂移。
- **前端未知码兼容**：旧后端或新增判词码时以原始码呈现，可能显示英文枚举值，但比压成「正常」更安全。
- **证据漂移**：审查后若改 design.md，须走 `revise-design → propose-design → decide-design → complete-design` 重走设计确认，避免 `CLASSIC_EVIDENCE_DRIFT`。

```

## openspec/changes/procuraai-governance-posture-board/tasks.md

- Source: openspec/changes/procuraai-governance-posture-board/tasks.md
- Lines: 1-31
- SHA256: b9011f7d7001ad441e099f091ad2de58127f32d93d7c60725705f50e97d99892

```md
# Tasks：procuraAI 治理态势聚合看板

## 1. 领域层：汇总判词与聚合策略（TDD）

- [x] 1.1 新增 `PostureVerdict` 枚举（UNDETERMINED / ALL_CLEAR / ATTENTION_REQUIRED，含 isActionable 语义），先写 `PostureVerdictTest` 覆盖三态；验证：`mvn -o -f bone-procurement-ai-governance/pom.xml -Dtest=PostureVerdictTest test` 通过。 <!-- comet-task:06ce3fbc-895b-4db1-a063-b49b2d6508d4 -->
- [x] 1.2 新增 `GovernancePosturePolicy` 纯函数聚合策略（分项不可判定优先、其次 actionable、否则全清；不接受客户端阈值），先写 `GovernancePosturePolicyTest` 覆盖不可判定优先/需关注/全清/空清单边界；验证：策略测试通过且无架构依赖违规。 <!-- comet-task:fb3342c9-874d-4baa-ba3f-3b19da9c4a37 -->

## 2. 应用层：聚合视图与服务方法（TDD）

- [x] 2.1 新增 `GovernancePostureView` 与能力明细记录（code/displayName/state/裁决/漂移/监督判词与计数，不含 reason 原文/PII/model output），先写视图映射测试；验证：视图不暴露敏感字段。 <!-- comet-task:66f34f85-506a-423a-a3b3-be1eeb4d0e06 -->
- [x] 2.2 `AiGovernanceApplicationService.posture()` 复用 listCapabilities/decide/driftSignal/oversightSignal 聚合，阈值只取 `AiGovernanceProperties`；先写 `GovernancePostureApplicationTest`（含缺省阈值 → UNDETERMINED、actionable → ATTENTION_REQUIRED、全清）；验证：模块测试通过。 <!-- comet-task:156f6d62-2442-4cd3-90ee-7ed1f87d85bd -->

## 3. 适配层：只读聚合端点（TDD）

- [x] 3.1 `AiGovernanceController` 新增 `GET /posture`，`procurement:ai-governance:read` 权限，`@ConditionalOnAiGovernanceEnabled` 开关控制，无客户端阈值参数；先写 `AiGovernanceControllerContractTest` 断言权限/开关/不回传敏感字段；验证：契约测试通过。 <!-- comet-task:64284baa-2d48-40ab-a423-defe5fc45db9 -->

## 4. 前端：治理态势看板（TDD）

- [x] 4.1 新增 `governanceApi.ts` 与 `governancePostureView.ts`（未知码显示原始码、取数失败显示「无法确认」），先写视图映射单测；验证：`vitest run governancePostureView` 通过。 <!-- comet-task:0e6bee59-1e3a-4c8c-af9b-82611c2d8a74 -->
- [x] 4.2 `GovernanceStatus.tsx` 改造为聚合看板（汇总判词卡 + 能力明细表 + 全域总闸 + 就绪度 + 披露入口），先写 `GovernanceStatus.test.tsx`（未知码/失败态/披露入口/无写操作）；验证：页面测试通过。 <!-- comet-task:73fe7765-c1c1-458f-a52d-5bd9f692d695 -->
- [x] 4.3 前端全量静态检查：vitest 全绿 + tsc 无错 + eslint `-f json` 的 errorCount=0；验证：三项命令均通过。 <!-- comet-task:ffdc6de7-a145-4bbb-ac72-a29065bdc587 -->

## 5. 集成验证与 POC 证据

- [x] 5.1 ai-governance 模块 `clean test` 全绿（含本轮新增用例与既有回归）；验证：模块测试计数与全绿。 <!-- comet-task:26709502-826c-40dc-8d09-11585f9599bc -->
- [x] 5.2 采购聚合测试（ProcuraCapabilityCoverageTest 等相关模块）与 procuraAI 五套门禁（evalset/alignment/frontmatter/persistence/event-chain）复验通过；验证：门禁命令全 PASS。 <!-- comet-task:bcc741bb-ca63-4f12-a9be-13f25af2cead -->
- [x] 5.3 内部 POC 走查：以真实能力清单数据渲染态势看板，验证「不可判定优先 / 需关注 / 全清 / 取数失败 / 未知码」五个场景均符合 spec；验证：POC 报告记录五场景实际结果。 <!-- comet-task:3e4f6d65-41f0-4e4f-b97c-6d276e63e45c -->

## Workflow follow-up

- 完成 Verify 后归档 change、提交并推送到 origin/dev（按 Comet Classic 归档与交付流程执行，用户已选 push 方式时在归档阶段确认）。

```

## openspec/changes/procuraai-governance-posture-board/.openspec.yaml

- Source: openspec/changes/procuraai-governance-posture-board/.openspec.yaml
- Lines: 1-2
- SHA256: 3c3c72cd1a100807c59ef8347538654b4d2d22269f59561c985b01cb44c39491

```md
schema: spec-driven
created: 2026-11-25

```

## openspec/changes/procuraai-governance-posture-board/specs/procurement/ai-governance-posture/spec.md

- Source: openspec/changes/procuraai-governance-posture-board/specs/procurement/ai-governance-posture/spec.md
- Lines: 1-73
- SHA256: 323d30585797f68dd861190ff0787b9430fb5066606c3bfd8bc486fd97a87f43

```md
# Spec Delta：procuraAI 治理态势聚合

## Purpose

把 procuraAI 已就绪的只读治理信号（能力裁决、漂移三态、监督有效性三态、全域总闸、就绪度）聚合为单一可复核的态势视图，使运维与审查者能在一处看到「哪些 AI 能力可用、哪些需要关注、哪些不可判定」，而不改变人工终决与 fail-closed 红线。

## ADDED Requirements

### Requirement: 治理态势聚合端点必须只读且 fail-closed

系统 MUST 提供一个需要 `procurement:ai-governance:read` 权限的只读聚合端点，返回能力清单及每个能力的裁决、漂移判词、监督判词，以及全域总闸与汇总态势判词。治理能力开关关闭时，该端点 MUST NOT 注册。端点 MUST NOT 接受任何客户端传入的阈值、判词或状态参数；阈值 MUST 只来自服务端 owner 配置。

#### Scenario: 无权限读取被拒绝

- GIVEN 治理能力开关已启用
- WHEN 一个仅具读权限之外权限的主体请求聚合端点
- THEN 请求被拒绝，且不返回任何聚合数据

#### Scenario: 治理开关关闭时端点不存在

- GIVEN 治理能力开关关闭
- WHEN 任何主体请求聚合端点
- THEN 端点未注册（404 或等效），不返回任何聚合数据

#### Scenario: 客户端阈值注入被忽略

- GIVEN 服务端未配置漂移/监督阈值
- WHEN 请求携带试图自设通过线的查询参数
- THEN 相关分项判词仍为 UNDETERMINED，不因客户端参数变为通过

### Requirement: 汇总态势判词必须由分项确定性推导

系统 MUST 由分项判词确定性推导汇总态势判词 `UNDETERMINED` / `ALL_CLEAR` / `ATTENTION_REQUIRED`：任一必填分项不可判定或缺失时 MUST 为 `UNDETERMINED`；否则任一能力的漂移或监督判词 actionable 时 MUST 为 `ATTENTION_REQUIRED`；全部通过时为 `ALL_CLEAR`。汇总不得掩盖不可判定分项，也不得声明模型效果达标。

#### Scenario: 分项不可判定则整体不可判定

- GIVEN 能力清单中至少一个能力的监督判词为 UNDETERMINED
- WHEN 计算汇总态势判词
- THEN 结果为 UNDETERMINED，即便其余能力全部通过

#### Scenario: 存在需关注能力则整体需关注

- GIVEN 所有分项均可判定，且至少一个能力的漂移判词 actionable
- WHEN 计算汇总态势判词
- THEN 结果为 ATTENTION_REQUIRED

#### Scenario: 全部通过则整体清楚

- GIVEN 所有分项均可判定、所有能力判词均不 actionable、全域总闸未停用
- WHEN 计算汇总态势判词
- THEN 结果为 ALL_CLEAR

### Requirement: 前端必须诚实渲染聚合治理态势

前端治理态页面 MUST 展示汇总判词、能力明细（状态/裁决/漂移/监督）与全域总闸。未知判词码 MUST 显示原始码而非压成「正常」或「未参与」；任一分项取数失败 MUST 显示「无法确认」而非空白或默认通过。合同审查 AI 参与披露 MUST 以入口链接呈现，不得虚构全局披露状态。页面 MUST NOT 触发任何治理写操作，MUST NOT 接收或回显 PII 与模型输出原文。

#### Scenario: 未知判词显示原始码

- GIVEN 后端返回前端未识别的判词码 `SOME_NEW_VERDICT`
- WHEN 页面渲染该判词
- THEN 展示原始码文本 `SOME_NEW_VERDICT`，不展示「正常」

#### Scenario: 取数失败显示无法确认

- GIVEN 聚合端点请求失败或返回空
- WHEN 页面渲染
- THEN 显示「无法确认」提示，不显示默认通过态

#### Scenario: 披露以入口呈现不虚构状态

- GIVEN 合同审查披露为 per-review 端点
- WHEN 页面渲染披露区域
- THEN 仅渲染跳转至已有披露页的入口，不生成全局「已披露/未披露」结论

```
