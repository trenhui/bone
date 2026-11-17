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
