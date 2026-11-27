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
