## Why

`synapClaim` 四阶段实施路线图（MVP 闭环 / 理算额度 / 智能化 / 生产级中台）与代码事实之间存在系统性认知差：主干已落地，但后期阶段大量节点处于「默认关 / 桩 / 仅设计」，`synapClaim-实现度与联调评估报告`（HEAD `5b6b81ae3`）已逐项核实。若不把「哪些已落地、哪些是刻意的安全姿态、哪些卡在外部前置」固化为可机检的契约，后续极易发生两类错误：把 fail-closed 默认关误当缺陷去「修好」（打开资金闸 = L2 资金变更），或把桩/设计误当已达标对外宣称（把 L0 伪装成 L3 的假绿）。

现在需要一个统一的设计与落地规划，把评估结论转成**可追踪、可分步解锁、边界清晰**的治理契约，供后续每阶段开工与解锁时对照。

## What Changes

- 新增一份「四阶段落地现状 + 解锁规划」治理契约，逐节点标注：`IMPLEMENTED` / `PARTIAL_DEFAULT_OFF` / `STUB_SAFETY_LOCK` / `DESIGN_ONLY`，并给出对应的代码证据锚点。
- 明确**代码可落地差距**的收口顺序（生产理算引擎从 `@Deprecated` 回退切到新管线、免赔剩余维度的「显式拒绝而非静默忽略」收口），但不改任何资金行为与金额语义。
- 明确**四类外部前置解锁路径**（盲标数据集 / 目录准入 / Go-Live 13 册合规 / L2 资金闸双签）：每类给出前置条件、门禁判据、责任方、AI 边界（AI 只准备与验证，不代签、不伪造证据）。
- 钉死三条不可越界红线：视觉反诈 `available=false` 资金闸属 L2 双签方可变更；`ai-native-readiness` 12 项判据是唯一真源，本规划产出的证据视图不得翻绿门禁；合成/桩数据不得冒充真人盲标或真实 provider 参与。
- **不改变任何生产行为**：本 change 是设计 + 规划产物（规格、设计文档、落地计划），不含运行时代码改动、不含 DDL、不含门禁放宽。

## Capabilities

### New Capabilities

- `claim/four-phase-landing-governance`: 四阶段落地现状的如实登记、代码可落地差距的收口顺序、外部前置解锁的门禁与责任方、以及 AI 不代签/不伪造/不翻绿门禁的边界契约。

### Modified Capabilities

（无：本 change 只新增治理契约，不改变现有 capability 的需求语义。）

## Impact

- 产物：`openspec/changes/synapclaim-four-phase-landing-plan/` 下的 proposal / specs / design / tasks，以及 `docs/superpowers/plans/` 下的落地计划。
- 不触碰：`bone-apps/synap-claim` 生产代码、`scripts/check.sh` 门禁清单、`ai-native-readiness` 12 项判据、迁移清单、活库数据。
- 依赖事实来源：`doc/ai-product/synapClaim/09-records/synapClaim-实现度与联调评估报告.md`（四阶段逐节点证据）、`doc/ai-product/synapClaim/05-plan/MVP可落地执行方案.md`（分期口径）。
- 责任方边界：盲标数据→算法/评测团队；目录准入→产品+架构；Go-Live 13 册→合规/法务（L4，AI 禁止代签）；资金闸双签→指定 Review 人+产品签核人。
