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
