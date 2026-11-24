## Context

MRM 离线内核已落码：`AiModelCard`（模型卡+三道防线+PSI 漂移四态，明确不落库）、`ChampionChallengerComparator`（逐案离线对比，缺金标产 `NO_GOLD`）、`AiCalibrationReportController`（`GET /api/v1/claim-ops/ai-calibration/report`，样本不足返回 `INSUFFICIENT_SAMPLES`）。真实盲标样本活库 0 条。详见 proposal.md。

## Goals / Non-Goals

**Goals:**
- 把离线内核接为运行期可查只读能力
- 补按机构等级等维度的偏差监测视图，样本不足 fail-closed
- 保持无金标时的诚实不可用

**Non-Goals:**
- 不落库 DDL、不伪造样本、不碰监管签署与资金闸、不宣称模型无偏差

## Decisions

**决策 1：沿用离线内核，运行期只读查询，不落库。**
查询直接读内核当前状态，不引入模型卡表/偏差表。备选是持久化治理数据——否决，本轮范围规避 DDL（L3），且离线内核已足够支撑只读查询。

**决策 2：偏差维度只读观测 + 样本量可见。**
按机构等级等维度分组，展示样本量；低于阈值返回 `INSUFFICIENT_SAMPLES`。备选是无论样本量都给数值——否决，会伪造结论，违反诚实原则。

**决策 3：无金标 fail-closed。**
`NO_EVIDENCE`/`INSUFFICIENT_SAMPLES` 如实返回，不臆造优劣；与现有 calibration flywheel 的「不回流在线决策」边界一致。

## Risks / Trade-offs

- [无金标导致能力看似空] → 如实返回不可用成因，作为后续真实评测数据的前置；不以桩数据刷绿
- [偏差维度涉及敏感属性] → 仅按机构等级等业务维度，样本可见，符合治理口径；不引入未授权的个人敏感维度
- [治理查询被误当合规通过] → 规格明确不触碰监管签署与合规分册 currency

## Migration Plan

无数据迁移、无 schema 变更。部署后治理查询为增量只读端点；无样本时返回不可用，待真实盲标样本入池后自然可用。

## Open Questions

无。
