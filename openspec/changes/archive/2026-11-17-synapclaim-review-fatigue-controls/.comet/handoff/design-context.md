# Comet Design Handoff

- Change: synapclaim-review-fatigue-controls
- Phase: design
- Mode: compact
- Context hash: cbf8a76de2110d4133da063c95a63471914ad6ecb35d8dec5a2c1398c815babc

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/synapclaim-review-fatigue-controls/proposal.md

- Source: openspec/changes/synapclaim-review-fatigue-controls/proposal.md
- Lines: 1-35
- SHA256: 7707228e7c2ed26eab2d89b8f16594a6087a9dd31ea82c34c0bc34fa7afd574b

```md
# Proposal: synapclaim-review-fatigue-controls

## Why

工程详细设计 §11.10 与易用性提案 §4.5 均要求把「审核疲劳防控」从文档变成工作台规则：单班审核量上限、连续审核休息提醒、主管抽检生效、大额/高风险不派给超量或临近班末的复核人，参数走 `feature_toggle`。现状是四项均只在文档与测试方案中登记（`§11.10` 待办仍未勾选，前端提案 D5 实测「未上屏」），属于典型的“写了规范但没有消费方”，无法成为人工终审质量下限。

本批只做**不引入新 DDL、不碰金额/拒赔终审、可单测证伪**的最小闭环：用现有 `t_claim_task` 的 `finished_at` 事实统计当日完成量，超过配置上限时该复核人不再作为可分配候选（由既有 `TaskAssigner` 的三级回落回池，形成自动转派入口）；同时提供工作台可调用的软提示评估端点。外部工时系统不可得时，班末/高风险排除如实登记 gap，不伪造。

## What Changes

- **配置值对象**：新增 `ReviewFatigueConfig`（纯 POJO），从 `REVIEW_FATIGUE_GUARD` 的 `config_json` 解析 `maxPerShift`（默认 120）、`restPromptMinutes`（默认 120）、`shiftEndExclusionMinutes`（默认 30）、`sensitiveAmountYuan`（默认 10000）；非法值回落基线并在 `rejected` 登记原因，未知键不静默生效。
- **配置消费**：`FeatureToggle.FEATURE_REVIEW_FATIGUE_GUARD` 新增常量；`FeatureTenantToggleService.reviewFatigueConfig(tenantId)` 按既有 fail-closed 口径读取（无记录/未开启/非法 JSON → 基线 + 拒因），消除“配了不生效”。
- **真实工作量取数**：`ClaimTaskRepository` 新增 default 方法 `countFinishedTodayByAssignee`，按租户 + 复核人 + `status=DONE` + `finished_at >= 当日零点` 计数；不新增 SQL 模板、不新增迁移（复用现有 `findByCriteria/countByCriteria`）。
- **分配闸门**：`ClaimTaskApplicationService.decide` 在 `TaskAssigner.decide` 前用疲劳配置包裹 `AssigneeEligibilityPort`：`USER` 候选当日完成量达到上限即判不可用，走既有 `ALL_INELIGIBLE → 入池` 回落，由任务中心再派给未超量复核人。软提示不阻断主链，计数的异常按“不可用”fail-closed，不把案件派给状态未知的人。
- **软提示评估端点**：新增只读 `GET /review/fatigue-guard/evaluation`，入参 `continuousMinutes`、`minutesToShiftEnd`、`claimAmount`（均可空），返回生效配置、是否超量、是否应提示休息、是否适合敏感案件；只提示不流转，不改变案件状态。
- **测试**：配置解析正向/越界/非法 JSON；仓储计数口径；分配超量转池；端点正向与 fail-closed；`mvn -pl bone-apps/synap-claim/synap-claim-app -am test` 与 `./scripts/check.sh` 全绿。
- **文档回写**：`AI技术方案-版本历史.md` 新增 v2.11，登记已落地的两项与仍未落地的班末/高风险硬约束边界，不勾选 §11.10 全项完成。

## Capabilities

### New Capabilities

- `claim/review-fatigue`：软提示评估端点与配置真源；只提供可观测判据，不替代人工终审。

### Modified Capabilities

- `claim/task-assignment`：候选复核人准入增加“单班超量不可用”判据；仍保持无规则/全不可用时入池，不把分配失败升级成收单失败。

## Impact

- **代码**：`ReviewFatigueConfig`、`FeatureToggle`、`FeatureTenantToggleService`、`ClaimTaskRepository`、`ClaimTaskApplicationService`、新 Controller 与对应单测；无新表、无新迁移、无新错误码。
- **零 DDL**：`finished_at` 已存在于 `t_claim_task`，Criteria default 方法即可取数。
- **无金额决策**：`sensitiveAmountYuan` 仅用于工作台提示与候选资格，不进入理算、拒赔或支付路径；AI/规则仍不碰钱。
- **诚实边界**：本批没有可信“当日班次结束时刻/连续时长”服务端事实源，因此 `minutesToShiftEnd` / `continuousMinutes` 由调用方传入，只做 soft prompt；不宣称已实现“班末硬排除”。主管抽检生效已由前批 4 的抽检处置链落地，本批不重复实现。
- **前端**：同批在复核工作台展示超量/休息提示（若工作台现有数据链路不足以读取评估端点，则在验收中登记为 gap，不以静态文案冒充上屏）。

```

## openspec/changes/synapclaim-review-fatigue-controls/design.md

- Source: openspec/changes/synapclaim-review-fatigue-controls/design.md
- Lines: 1-48
- SHA256: b346689f1a3c5e79bb5fdec85da02555b76323907165900d19fb7539d5890528

```md
---
comet_change: synapclaim-review-fatigue-controls
role: technical-design
canonical_spec: openspec
---

# Design: synapclaim-review-fatigue-controls

## 架构决策

沿用既有「domain 纯判据 + application 读配置/取数 + adapter 只做端口实现」分层，不新增架构概念、不引入新基础设施。

### 配置口径

`ReviewFatigueConfig` 是唯一解析真源，输入来自 `t_feature_toggle` 的 `REVIEW_FATIGUE_GUARD.config_json`。无记录/未开启/非法 JSON/越界字段一律回落代码基线，并在 `rejected` 中登记原因；应用层只把解析后的值对象交给领域判据，domain 不依赖 JSON/Spring。未知键必须出现在 `rejected`，避免“配了不生效”。

### 数据流

```
FeatureToggleRepository → FeatureTenantToggleService.reviewFatigueConfig(tenantId)
                                      ↓ ReviewFatigueConfig
ClaimTaskRepository.countFinishedTodayByAssignee(tenantId, assigneeId, UTC dayStart)
                                      ↓ long finishedToday
ClaimTaskApplicationService.decide()
   └─ FatigueAwareEligibilityPort(delegate + config + finishedToday)
        └─ TaskAssigner.decide(ctx, rules, guardedEligibility)
             ├─ 未超量 → 既有命中逻辑
             └─ 超量/计数异常 → ALL_INELIGIBLE → 入池 → 任务中心再派
```

`GET /review/fatigue-guard/evaluation` 只读取同一配置与调用方传入的连续时长/班末分钟/金额，返回评估 DTO；不写库、不改状态、不自动改派。

### 关键判定

1. **真实事实只取库内**：`finished_at` 已存在于 `t_claim_task`，按租户 + 复核人 + `DONE` + UTC 当日零点过滤；不用客户端声明的完成数。
2. **超量即候选不可用**：达到上限（`finishedToday >= maxPerShift`）即判不可用，交给 `TaskAssigner` 既有回落入池，保持“分配失败不等于收单失败”的不变量。
3. **异常 fail-closed**：仓储异常/返回不可信时按不可用处理，绝不因“查不到”把人当可用；主链不抛异常。
4. **软提示不阻断**：连续时长达到阈值只返回 `restPrompt=true`，不清空队列、不禁用提交、不改案件状态。
5. **敏感案件边界如实**：`sensitiveEligible=false` 目前只用于提示；没有可信服务端班次结束事实前，不宣称“班末硬排除”已完成。
6. **不碰钱**：金额阈值只用于提示/候选资格，不进入理算、拒赔、支付；AI/规则仍不得触碰资金结论。

### 不做的事（防范围蔓延 / 防造假绿）

- 不新增表、字段、迁移或 SQL 模板；`finished_at` 已存在。
- 不接 IAM 实时在职状态（缺降级契约，仍沿用 `DefaultAssigneeEligibilityAdapter` 的形态判定）。
- 不实现服务端班末硬排除：调用方传入的分钟数只做提示，验收登记 gap。
- 不重复实现主管抽检：`t_claim_inspection` 与抽检处置链已在前序批次落地。
- 不改拒赔/理算/支付路径，不引入自治 Agent。

```

## openspec/changes/synapclaim-review-fatigue-controls/tasks.md

- Source: openspec/changes/synapclaim-review-fatigue-controls/tasks.md
- Lines: 1-37
- SHA256: d0f5959f349828df85474a3c060ca1789261dbc3f0f10b1fa1931f46ecf2271b

```md
# Tasks: synapclaim-review-fatigue-controls

## 1. 配置真源

- [x] 1.1 新增 `ReviewFatigueConfig` 纯值对象：解析 `maxPerShift` / `restPromptMinutes` / `shiftEndExclusionMinutes` / `sensitiveAmountYuan`，基线分别为 120 / 120 / 30 / 10000；非法类型、越界、未知键一律回落或登记 rejected，不静默生效。验证：配置单测覆盖正向、缺键、越界、非法 JSON 映射后的 map。 <!-- comet-task:85687aa9-e25b-418e-b92a-6e8bf7178549 -->

## 2. 配置消费

- [x] 2.1 `FeatureToggle` 新增 `FEATURE_REVIEW_FATIGUE_GUARD` 常量；`FeatureTenantToggleService.reviewFatigueConfig(tenantId)` 按 fail-closed 读取，无记录/未开启/解析失败均回落基线并 WARN。验证：服务单测覆盖无记录、关闭、合法配置、非法 JSON。 <!-- comet-task:0c5f5567-60b8-4536-8cb4-bd431ac66da1 -->

## 3. 工作量取数

- [x] 3.1 `ClaimTaskRepository` 新增 default `countFinishedTodayByAssignee(Long tenantId, String assigneeId, Instant dayStart)`，显式带租户、复核人、`status=DONE`、`finishedAt >= dayStart`；不新增 SQL 模板与迁移。验证：仓储/应用测试钉住口径；SQL 模板门禁仍绿。 <!-- comet-task:d22421a0-87f9-4b56-bf4a-d1d18fa92335 -->

## 4. 分配闸门

- [x] 4.1 `ClaimTaskApplicationService.decide` 在调用 `TaskAssigner.decide` 前包裹 `AssigneeEligibilityPort`：仅对 `AssigneeType.USER` 读当日完成量，达到 `maxPerShift` 即判不可用；其它类型保持原判定。验证：超量复核人不被选中，规则回落入池；未超量行为不变。 <!-- comet-task:a4f53325-3c37-4e1c-a9d2-fb20eda52ba3 -->
- [x] 4.2 计数异常/仓储不可用按“候选不可用”fail-closed，不抛出把收单链拖挂，也不把状态未知的人当成可用。验证：异常注入用例断言回落入池且无异常外溢。 <!-- comet-task:86309f14-10b5-4948-a4fc-80c5061e14ce -->

## 5. 软提示评估

- [x] 5.1 新增 `ReviewFatigueDecision` 纯值对象与 `ReviewFatigueGuardService.evaluate(...)`：输出 `overloaded` / `restPrompt` / `sensitiveEligible` 及生效配置；连续时长与班末分钟由调用方传入，服务端不伪造班次事实。验证：阈值边界（等于阈值即提示/超量）、空值与敏感金额边界。 <!-- comet-task:f7c2ad14-1eed-4185-ac33-6dd483552579 -->
- [x] 5.2 新增只读端点 `GET /review/fatigue-guard/evaluation`，入参 `continuousMinutes` / `minutesToShiftEnd` / `claimAmount`，返回评估 DTO；不写库、不改案件状态、不产生转派动作。验证：Controller 单测与端点路径影响登记。 <!-- comet-task:8f4353c8-5bde-492f-b184-f2ee564b58ee -->

## 6. 前端提示

- [x] 6.1 在复核工作台读取评估端点并显示超量/休息 soft prompt；不清空队列、不阻断提交、不替代人工终审。验证：前端 typecheck；若现有数据链路无法在同一变更内可靠接线，则本项转为验收 gap 并在报告说明，不得以静态文案冒充已生效。 <!-- comet-task:1938b56b-cdae-405b-adc0-f303889d0bbb -->

## 7. 测试与门禁

- [x] 7.1 新增/扩展单测覆盖配置解析、软提示阈值、超量回落入池、异常 fail-closed、端点契约；`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` 全绿。 <!-- comet-task:a73275f5-d6d7-4936-a123-16ed21741438 -->
- [x] 7.2 `./scripts/check.sh` [59/59] 全绿，末行「✅ 本地自检通过！」；不关闭任何既有门禁、不使用绕过开关。 <!-- comet-task:eba2c739-cd15-4ee6-bf26-d00e23790cdb -->

## 8. 文档与诚实边界

- [x] 8.1 `AI技术方案-版本历史.md` 新增 v2.11：登记单班超量与软提示已落地、参数口径、零 DDL；明确班末硬排除因缺服务端班次事实源仍未完成，不勾选 §11.10 全项完成。 <!-- comet-task:8a1e91b2-115d-4be9-8372-4170ddccaba0 -->
- [x] 8.2 独立 Verify 报告记录真实测试/门禁结果、端点覆盖、配置消费证据与未落地 gap；Comet Guard 若出现收据死锁，保持诚实终态，不伪造回执。 <!-- comet-task:a6d975eb-76c0-46a5-997f-3ffd6fa4eb22 -->

```

## openspec/changes/synapclaim-review-fatigue-controls/.openspec.yaml

- Source: openspec/changes/synapclaim-review-fatigue-controls/.openspec.yaml
- Lines: 1-2
- SHA256: b75d296d3b54880b00a14fd17257d690f593cd5d3db17201d597c19528a9f905

```md
schema: spec-driven
created: 2026-11-17

```

## openspec/changes/synapclaim-review-fatigue-controls/specs/claim/review-fatigue/spec.md

- Source: openspec/changes/synapclaim-review-fatigue-controls/specs/claim/review-fatigue/spec.md
- Lines: 1-45
- SHA256: 6abcf0b6566d49c753dd37fb0b7addbf721801501b391f78e69655e0c0a6aede

```md
## Purpose

把设计文档中的审核疲劳控制变成可观测、可证伪的工作台判据：以现有任务事实统计单班完成量，
超过上限的复核人不再进入候选；连续审核提示只做 soft prompt，不阻断人工终审，也不改变资金路径。

## ADDED Requirements

### Requirement: 单班超量候选不可用
系统 SHALL 在任务归属求解前，按租户与复核人统计 UTC 当日 `status=DONE` 的任务数；
当该数达到配置上限（基线 120）时，该 `USER` 候选 MUST 判为不可用，由既有 `TaskAssigner`
的“有匹配但候选不可用 → 入池”回落处理。计数异常 MUST 按不可用处理，不得把状态未知的人派入任务。

#### Scenario: 达到单班上限
- **WHEN** 复核人当日完成数等于 `maxPerShift`，且其为唯一匹配规则目标
- **THEN** 该候选不可用，任务入池并可被再派给未超量复核人

#### Scenario: 计数异常
- **WHEN** 工作量仓储查询抛异常
- **THEN** 候选按不可用处理，任务入池；异常不外溢到收单链

### Requirement: 配置 fail-closed 且可观测
系统 SHALL 从 `REVIEW_FATIGUE_GUARD` 的 `config_json` 读取参数；无记录、未开启、非法 JSON、
字段越界或未知键 MUST 回落基线或登记拒绝原因，不得静默生效，也不得把拒绝值写回任务事实。

#### Scenario: 非法配置
- **WHEN** `maxPerShift` 配置为 0 或非整数
- **THEN** 使用基线 120，并在评估结果的 `rejected` 中给出原因

### Requirement: 连续审核 soft prompt
系统 SHALL 提供只读评估端点，返回是否超量、是否应提示休息、敏感案件是否适合派给该复核人；
提示 MUST 不阻断提交、不自动改派、不产生新的案件状态。

#### Scenario: 连续时长达到阈值
- **WHEN** 调用方传入 `continuousMinutes` 等于配置阈值
- **THEN** `restPrompt=true`，但端点不执行任何写操作

#### Scenario: 敏感案件班末提示
- **WHEN** 调用方传入金额达到敏感阈值且 `minutesToShiftEnd` 小于班末排除阈值
- **THEN** `sensitiveEligible=false`，仅作为工作台提示返回；本 change 不宣称已实现服务端班末硬排除

## MODIFIED Requirements

### `claim/task-assignment`
候选可用性判定在既有类型/ID 检查之外，增加“单班完成量未达上限”判据；无规则与全不可用仍统一入池，
不得把分配失败升级为收单失败。

```
