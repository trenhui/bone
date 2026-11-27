# Comet Design Handoff

- Change: claim-ai-inspection-full-coverage
- Phase: design
- Mode: compact
- Context hash: 9d78ffdca512fcc1ab2753c12a5f61481eb32b6a567190d1c068710495167d5f

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/claim-ai-inspection-full-coverage/proposal.md

- Source: openspec/changes/claim-ai-inspection-full-coverage/proposal.md
- Lines: 1-27
- SHA256: 2a7fa0f335ebefab147e7f956322e497877fbb143a5f055c3a40f42dbf14c4f2

```md
## Why

PRD §14（V1.0）要求「100% AI 后督质检（自动通过/拒赔结论全量覆盖）」，但当前实现只支持租户默认 5% 抽样，且自动抽样仅覆盖 `FINISHED`（已支付）终态，拒赔结论走人工发起（MANUAL）——不满足 V1.0 全量覆盖口径。本 change 在**不碰资金闸、不接真实 provider、不改赔付语义**的前提下，把全量质检做成可配置、可证伪的内部闭环能力，为后续监管口径启用提供工程前置。

## What Changes

- **抽样策略升级**：`InspectionSamplingPolicy.shouldSample` 支持 `ratePercent>=100` 全量命中（代码已具备此分支，本 change 补齐规格钉子与配置口径）。
- **抽样范围扩展**：自动抽样来源从仅 `FINISHED` 扩展到含拒赔终态，使「自动通过 + 拒赔」两类结论均可被后督覆盖；默认配置维持现网行为不变。
- **租户级开关口径**：`t_feature_toggle.INSPECTION_SAMPLING` 按租户读取，100% 只能由租户显式配置启用，无配置回落 5%。
- **规格与门禁**：新增 capability spec 定义抽样比例、终态覆盖、默认回落的 REQUIREMENT 与验收场景；补变异测试（比例篡改、终态范围回退双向必红）并按仓库纪律接入门禁。

**非目标（明确不做）**：不改 `AUTO_APPROVE`、视觉桩 `available`、目录准入 `MANDATORY` 等资金闸；不接真实商业 provider；不改赔付金额计算语义；不新增 DDL；不宣称模型准确率达标；不改 Go-Live 13 册 draft 状态。

## Capabilities

### New Capabilities
- `claim/ai-inspection-full-coverage`: 100% AI 后督质检的可配置契约——抽样比例（含 100% 全量）、终态覆盖范围（含拒赔）、租户级开关、默认回落与 fail-closed 边界，以及每项的变异可证伪性。

### Modified Capabilities
- 无。不修改既有 `claim/inspection-handoff-timeout` 等 spec 的需求；本 change 为新增能力，不改赔付主链与决策结果语义。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app`（domain/model/inspection、adapter/schedule、application）；均为抽样策略与范围，不改 `Adjustment` 资金路径。
- **配置**：`t_feature_toggle.INSPECTION_SAMPLING` 租户开关口径（沿用现有键，无新增 DDL）。
- **测试**：`InspectionSamplingPolicy` 正向/反向钉子；终态覆盖场景测试；门禁同步。
- **不受影响**：资金闸、provider 凭证、DDL、Go-Live 合规分册、赔付金额语义。

```

## openspec/changes/claim-ai-inspection-full-coverage/design.md

- Source: openspec/changes/claim-ai-inspection-full-coverage/design.md
- Lines: 1-102
- SHA256: 31e23cfce04e16ed61819543e9138e0d4a0839e3a9ac827baf6f5e83e4ea9a9b

[TRUNCATED]

```md
---
comet_change: claim-ai-inspection-full-coverage
role: technical-design
canonical_spec: openspec
---

## Context

现有后督抽检由 `InspectionSamplingJob` 从 `t_feature_toggle.INSPECTION_SAMPLING` 按租户读取比例（无配置回落 5%），`InspectionSamplingPolicy.shouldSample` 已具备 `ratePercent>=100` 全量分支，但自动抽样候选来源仅 `FINISHED` 终态，拒赔走人工发起（MANUAL）。代码能力与 PRD §14 V1.0「100% 覆盖」口径之间缺的是规格钉子、终态范围与可证伪门禁，而非全新机制。详见 proposal.md 的 Why。

### 代码现状实证（Design 侦察，文件:行）

| 断言 | 判定 | 证据 |
|---|---|---|
| `ratePercent>=100` 全量分支已存在 | 属实 | `domain/model/inspection/InspectionSamplingPolicy.java:29-38`：`if (ratePercent >= 100) return true;` |
| 比例来自入参、策略类无 IO | 属实 | `InspectionSamplingPolicy.java:29`（纯函数）；配置由 `adapter/schedule/InspectionSamplingJob.java:78-82` 读取后透传 |
| 自动候选来源仅 `FINISHED` | 属实 | `resources/sql/.../ClaimCaseRepository/findFinishedForInspectionAllTenants.sql:7`：`WHERE c.status = 'FINISHED'` |
| 第二道门槛 `claim.finished()` 已含两终态 | 属实（关键） | `ClaimCase.java:783-785` → `valueobject/ClaimStatus.java:76-79`：`terminal() = FINISHED \|\| CLOSED_REJECTED` |
| 拒赔终态枚举名 | `CLOSED_REJECTED`（code "45"） | `ClaimStatus.java:52-53` |
| `INSPECTION_SAMPLING` 唯一配置源 | 属实 | 常量 `domain/model/config/FeatureToggle.java:57`；唯一消费 `application/FeatureTenantToggleService.java:151-153` |
| 无配置回落 5% | 属实 | `domain/model/inspection/InspectionConfig.java:14,16-47`；服务层 fail-safe `FeatureTenantToggleService.java:147-175` |
| 抽检只写 `t_claim_inspection`、不碰资金 | 属实 | `application/InspectionApplicationService.java:106-108`；`InspectionRecord.java:35`（`@Table`）；inspection 包内无 `Adjustment`/`AUTO_APPROVE`/`available`/`MANDATORY` |
| 100% 纯策略单测已有；终态范围/端到端测试缺失 | 属实 | `InspectionSamplingPolicyTest.java:30-38`；`InspectionSamplingJobTest.java:102-104`（候选状态写死 `FINISHED`） |

**关键推论**：`sampleIfEligible` 的终态校验 `claim.finished()`（`InspectionApplicationService.java:93`）已经接受 `FINISHED` 与 `CLOSED_REJECTED`。因此「拒赔纳入自动抽检」的**唯一实现改动点是仓储候选查询 SQL 的状态过滤**（`findFinishedForInspectionAllTenants.sql:7`），下游建单链路无需改动。这是现成但未启用的扩展点，也是本设计选择「复用而非重写」的根本依据。

## Goals / Non-Goals

**Goals:**
- 把「100% 全量 + 拒赔纳入自动抽检」固化为可机检的规格与测试
- 保持默认配置（5%）与现网行为完全一致，100% 仅显式开启
- 每项行为配变异双向钉子（改比例/改终态必红，还原必绿）

**Non-Goals:**
- 不新增 DDL、不改资金路径、不接 provider、不声称模型准确率
- 不调整具体抽检限值以外的理赔业务规则

## Decisions

**决策 1：复用现有抽样策略，不重写机制。**
`InspectionSamplingPolicy.shouldSample` 的全量分支已存在（`ratePercent>=100` → true），本 change 只补测试与规格，不引入新抽样算法。备选是重写为独立采样器——否决，因增加资金主链外的不确定性且无功能收益（YAGNI）。

**决策 2：终态范围扩展只改候选 SQL 的白名单，且默认不生效。**
将候选查询从 `WHERE c.status = 'FINISHED'` 扩展为包含 `CLOSED_REJECTED`（终态白名单）；不改 `sampleIfEligible`（其 `claim.finished()` 已兼容）。白名单默认仍只含 `FINISHED` 行为不变的思路通过「100% 配置显式启用」实现——即拒赔终态在进入候选后仍需比例判定，默认 5% 下拒赔即使入候选也按 5% 抽样，不产生全量副作用。备选一「默认全终态」——否决，改变现网行为且纳入非终态；备选二「在 sampleIfEligible 另写状态判断」——否决，重复 `ClaimStatus.terminal()` 语义制造第二口径。

**决策 3：fail-safe 回落，不 fail-fast。**
配置读取失败时回落 5% 并记 WARN 诊断日志，不中断主理赔流程。备选是 fail-fast 拒绝——否决，因抽检为质检旁路，不应成为理赔主链的可用性风险。

> **Verify 修订（2026-11-24，独立审查 IMPORTANT-1）**：原实现只 catch 了 JSON 解析，`featureToggleRepository.findByFeatureKey` / `isOn` 的仓储读取在 try 块外，DB 故障时会冒泡到 Job 计入 failed 并跳过该笔，与 spec Requirement 3「配置读取失败回落 5%」不符。已把仓储读取一并纳入 try/catch，任一异常均回落 5% 并登记 `$json` 诊断；补 `samplingRepositoryFailureFallsBack` 测试（注入仓储异常）。这是本 change 的生产代码第 2 处改动。

**决策 4：红线隔离。**
抽检结果只写 `t_claim_inspection`（`InspectionRecord.java:35`），不进入 `Adjustment` 资金路径；不触碰 `AUTO_APPROVE`、视觉桩 `available`、目录准入 `MANDATORY`。这是本 change 与 V1.0 中被排除的「反欺诈视觉/资金闸」能力的硬边界。

## Test Strategy（可证伪）

| 判据 | 测试类型 | 验收 | 变异钉子 |
|---|---|---|---|
| 100% 全量命中 | 策略单测 + 应用服务层（配置键→建单） | 比例=100 时案件建单（`sampleIfEligibleOutcomes`）；策略 `rateBounds` 钉 100 全量 | 全量分支 `return true`→`return false` → 必红（`>=100`→`>100` 数学等价，不用作变异点） |
| 拒赔终态纳入 | 仓储候选 SQL 契约测试（`InspectionSamplingTerminalScopeTest`） | SQL 同时含 `FINISHED`/`CLOSED_REJECTED` 且保留 `NOT EXISTS` 去重 | 终态白名单回退为仅 `FINISHED` → 必红 |
| 默认 5% 回落 | 值对象单测（`InspectionConfigTest`）+ 服务层（`FeatureTenantToggleServiceTest`） | 无记录/缺键/非整数/越界一律回落 5% 且登记原因 | `DEFAULT_SAMPLING_RATE` 5→0（clean 重编避免常量内联）→ 必红 |
| 租户隔离 | 跨租户场景测试（`InspectionSamplingJobTest.usesPerTenantConfiguredRate`） | 租户 A=100% 不影响租户 B 默认 5% | — |
| fail-safe | 异常注入测试（`samplingBrokenJsonFallsBack`） | 配置读取异常→回落 5% + 登记 `$json`、主流程不中断 | 同默认回落变异 |
| 资金边界 | grep + 测试 | 抽检仅写 `t_claim_inspection`，无资金闸字段写入 | — |

### 变异双向实证（2026-11-24，clean 重编后跑）

| 变异 | 判据测试 | 变异后 | 还原后 |
|---|---|---|---|
| 全量分支 `return true`→`false` | `InspectionSamplingPolicyTest,InspectionApplicationServiceTest` | RC=1 红 | RC=0 绿 |
| 终态白名单回退仅 `FINISHED` | `InspectionSamplingTerminalScopeTest` | RC=1 红 | RC=0 绿 |
| `DEFAULT_SAMPLING_RATE` 5→0 | `InspectionConfigTest,FeatureTenantToggleServiceTest` | RC=1 红 | RC=0 绿 |
| fail-safe catch 回落 5→100（Verify 修复新增） | `FeatureTenantToggleServiceTest.samplingRepositoryFailureFallsBack` | RC=1 红 | RC=0 绿 |

> 注：常量内联坑——`static final int` 会在编译期内联到引用方，仅 touch 源文件不足以让测试用上新值，须 `mvn clean` 才能可靠验证该类变异。

### 独立最终集成审查（review_mode=standard）

由独立 reviewer（Explore 子代理，无实现上下文）对最终 diff 审查，结论：**1 项 IMPORTANT（已修复）+ 4 项 SUGGESTION**。

| 项 | 结论 | 处置 |

```

Full source: openspec/changes/claim-ai-inspection-full-coverage/design.md

## openspec/changes/claim-ai-inspection-full-coverage/tasks.md

- Source: openspec/changes/claim-ai-inspection-full-coverage/tasks.md
- Lines: 1-23
- SHA256: c75fcb20da7109442fc3e93a7cf64bd3d14a9788878076fde8fdc91823dca867

```md
## 1. 规格与现状确认

- [x] 1.1 核对 `InspectionSamplingPolicy.shouldSample` 现有 `ratePercent>=100` 分支与 `InspectionSamplingJob` 终态来源，记录到 change 说明；验收：逐行确认两处代码路径并在任务备注引用文件+行号 <!-- comet-task:71857625-e20c-4b73-a40a-02e02e3c2038 -->
- [x] 1.2 确认 `t_feature_toggle.INSPECTION_SAMPLING` 为唯一配置源且无第二处口径；验收：grep 全仓确认只有该键被抽检逻辑消费 <!-- comet-task:07a546a0-d523-494e-adf4-5032759a5626 -->

## 2. 抽样策略与终态覆盖实现

- [x] 2.1 为 `InspectionSamplingPolicy` 补 100% 全量命中的正向单测；验收：比例=100 时 N 笔案件生成 N 条抽检记录，测试通过 <!-- comet-task:e7b70782-2061-4f9b-947d-3e8372516cf0 -->
- [x] 2.2 扩展自动抽样终态白名单，纳入拒赔终态（默认仍未配置时不生效）；验收：100% 配置下拒赔终态案件自动生成抽检记录，测试通过 <!-- comet-task:94a4e3f7-175a-4769-81e7-ba7242056d17 -->
- [x] 2.3 保持默认回落 5% 与现网行为一致；验收：未配置租户的抽检行为与 change 前完全一致，回归测试通过 <!-- comet-task:7b2f1345-f7f1-48c9-9dd6-5ede328ce66b -->
- [x] 2.4 配置读取失败 fail-safe 回落 5% 并记诊断日志；验收：注入配置读取异常，确认回落且日志可观测、主理赔流程不中断，测试通过 <!-- comet-task:6e5bf894-df0a-4298-a37b-28672e19dfc2 -->
- [x] 2.5 租户隔离：租户 A=100% 不影响租户 B 默认 5%；验收：跨租户场景测试通过 <!-- comet-task:e7611ee2-be45-4cb4-ad69-033b8d5d1cc7 -->

## 3. 资金与语义边界守护

- [x] 3.1 确认抽检结果不进入 `Adjustment` 资金路径、不改 `AUTO_APPROVE`/视觉桩 `available`/目录准入 `MANDATORY`；验收：grep + 测试证明抽检仅写质检/复核记录，无资金闸字段写入 <!-- comet-task:c805471f-16ad-4d38-ac39-feb34ac4cf03 -->
- [x] 3.2 确认赔付金额计算语义未变；验收：现有资金相关测试全绿，无金额计算代码改动 <!-- comet-task:23848349-b853-4e5b-92e4-ea7fedba074a -->

## 4. 门禁与可证伪验证

- [x] 4.1 为每条新判据补变异双向钉子（比例篡改、终态范围回退、fail-safe 移除）；验收：改一处必红、还原必绿，变异记录归档 <!-- comet-task:df274979-ebde-47a7-9f8d-48e312e1b490 -->
- [x] 4.2 按仓库纪律把新判据同步到相关门禁入口；验收：check 入口可见新判据，注入必红还原必绿 <!-- comet-task:5a922fbe-0800-47a9-b327-2ad8b5685404 -->
- [x] 4.3 跑 synap-claim 模块相关测试全绿；验收：`mvn -pl <synap-claim模块> -am test` 通过，测试数从收据取真实值 <!-- comet-task:99fbd482-08c6-4a5c-8cd1-7e5ce1ef2616 -->

```

## openspec/changes/claim-ai-inspection-full-coverage/.openspec.yaml

- Source: openspec/changes/claim-ai-inspection-full-coverage/.openspec.yaml
- Lines: 1-2
- SHA256: 01c3365f21ad2129d7e1db5577bc59c382883cdcbfece60900a8ac86293e3398

```md
schema: spec-driven
created: 2026-11-23

```

## openspec/changes/claim-ai-inspection-full-coverage/specs/claim/ai-inspection-full-coverage/spec.md

- Source: openspec/changes/claim-ai-inspection-full-coverage/specs/claim/ai-inspection-full-coverage/spec.md
- Lines: 1-45
- SHA256: 0778e38719a0df4a6c7bf51b2357fe4eed8c53707ae4adba217c0fd23c4727f1

```md
## Purpose

定义 100% AI 后督质检的可配置契约：抽样比例（含全量）、终态覆盖范围（含拒赔）、租户级开关与默认回落，使理赔结论的后督抽检在内部环境可判定、可证伪，且不触碰资金闸或赔付语义。

## ADDED Requirements

### Requirement: 全量抽样比例
后督抽检比例 MUST 支持 0–100 的租户级配置；当比例配置为 100 时，系统 MUST 对该租户在抽检范围内的每一笔合格案件生成抽检记录，不得抽样遗漏。

#### Scenario: 配置 100% 时全量命中
- **WHEN** 租户 `INSPECTION_SAMPLING` 配置为 100，且存在 N 笔合格案件
- **THEN** 系统对该 N 笔案件全部生成抽检记录，抽检数等于 N

#### Scenario: 未配置时回落默认比例
- **WHEN** 租户未配置 `INSPECTION_SAMPLING`
- **THEN** 系统使用现网默认比例 5%，行为与本 change 之前完全一致

### Requirement: 终态覆盖范围
自动抽检的终态范围 MUST 覆盖自动通过结论与拒赔结论两类；当配置为 100% 时，两类终态的案件均 MUST 进入抽检，不得因终态类型被静默排除。

#### Scenario: 拒赔终态纳入自动抽检
- **WHEN** 一笔案件以拒赔终态完结，且抽检比例为 100
- **THEN** 系统自动为该拒赔案件生成抽检记录，无需人工发起

#### Scenario: 自动通过终态纳入自动抽检
- **WHEN** 一笔案件以自动通过终态完结，且抽检比例为 100
- **THEN** 系统自动为该案件生成抽检记录

### Requirement: 租户隔离与配置口径
抽检比例 MUST 按租户维度读取；一个租户的配置 MUST NOT 影响其他租户的抽检行为；配置读取失败时 MUST 回落默认比例并记录原因，不得中断主理赔流程。

#### Scenario: 租户配置互不干扰
- **WHEN** 租户 A 配置 100%、租户 B 未配置
- **THEN** 租户 A 全量抽检，租户 B 使用默认 5%

#### Scenario: 配置读取失败 fail-safe
- **WHEN** 抽检配置读取失败
- **THEN** 系统回落默认 5% 并记录诊断信息，主理赔流程不受影响

### Requirement: 资金与语义边界
本能力 MUST NOT 改变赔付金额计算、自动放款开关、视觉反诈桩可用状态或目录准入策略；抽检结果仅用于质检与人工复核，不得作为自动放款的触发条件。

#### Scenario: 抽检不改变放款姿态
- **WHEN** 抽检比例为 100 且某案件被抽检
- **THEN** 该案件的放款状态与金额计算同抽检前一致，AUTO_APPROVE 与各 fail-closed 桩状态不变

```
