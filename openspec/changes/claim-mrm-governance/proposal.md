## Why

MRM 模型风险治理的离线内核已落码（`AiModelCard` 模型卡+三道防线+PSI 漂移四态、`ChampionChallengerComparator` 逐案离线对比、`AiCalibrationReportController` 只读报告端点），但不落库、未运行期接线、无偏差监测维度，且真实盲标样本活库 0 条（`evaluate` 空集返回 `NO_EVIDENCE`）。PRD §14 V1.0 要求「MRM 模型风险治理闭环（模型卡片/漂移监控/champion-challenger/偏差监测）」。本 change 在**不落库新增 DDL、不伪造金标样本、不碰监管签署与资金闸**的前提下，把离线内核接线为运行期可查能力并补偏差监测只读视图。

## What Changes

- **运行期可查接线**：把模型卡、PSI 漂移四态、champion-challenger 对比结果接为运行期只读查询能力（沿用离线内核，不新增 DDL）。
- **偏差监测视图**：新增按机构等级等维度的偏差监测只读视图；样本不足时明确返回 `INSUFFICIENT_SAMPLES` 且不给数值。
- **诚实边界**：无金标样本时保持 `NO_EVIDENCE`/`INSUFFICIENT_SAMPLES`，不得臆造偏差结论；不把模型卡标为「已验证」。
- **规格与门禁**：新增 capability spec 定义可查口径、偏差维度、样本不足语义；补变异测试。

**非目标（明确不做）**：不落库新增 DDL（沿用离线内核）；不伪造金标样本/blind label；不碰监管签署（金发〔2026〕8 号等）、资金闸、赔付语义；不把 Go-Live 合规分册升 current；不宣称模型无偏差。

## Capabilities

### New Capabilities
- `claim/mrm-governance`: MRM 模型风险治理的运行期可查契约——模型卡/漂移四态/champion-challenger 结果的可查口径、偏差监测维度、样本不足 fail-closed 语义，以及变异可证伪性。仅覆盖运行期查询与偏差视图，不重复现有 `claim/ai-calibration-flywheel` 的校准指标/离线评测需求。

### Modified Capabilities
- 无。不修改 `claim/ai-calibration-flywheel` 的需求；本 change 的偏差监测是新增行为，不改动校准内核。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app`（domain/model/evaluation、application、adapter/web controller）；沿用离线内核，无 schema 变更。
- **数据**：只读现有评测/案件数据；无金标样本时如实返回不足，不写造数据。
- **测试**：可查口径、偏差维度、样本不足 fail-closed、变异测试。
- **不受影响**：资金路径、provider 凭证、DDL、Go-Live 合规分册、赔付语义。
