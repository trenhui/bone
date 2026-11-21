# 四阶段落地现状与解锁规划 · 任务清单

## 1. 落地现状登记（四态 + 证据锚点）

- [x] 1.1 产出四阶段逐节点落地现状矩阵：对每个节点标 `IMPLEMENTED`/`PARTIAL_DEFAULT_OFF`/`STUB_SAFETY_LOCK`/`DESIGN_ONLY`，附代码证据锚点（类名/行号或配置文件键值）；以「与评估报告 §三/§六逐项对账一致、且 15 节点全覆盖」为验收。 <!-- comet-task:7e12e145-14b9-4cac-a10b-bf05d29cb61d -->
- [x] 1.2 单列两处与路线图的实质偏离（真实保单表 `t_claim_policy` 替换模拟关联、本地确定性图筛查 `E2_GRAPH_LOCAL` 替代平台图库），以「两项均在矩阵中标注且不掩盖」为验收。 <!-- comet-task:618de2a7-8d32-4603-a3b4-a35f49071191 -->

## 2. 代码可落地差距的收口规划（不改资金语义）

- [x] 2.1 规划生产金额引擎从 `@Deprecated AdjustmentEngine` 回退切到新管线的步骤，含 fail-closed 回退与切换前后金额对照验证；以「规划含回退路径与对照验证、明确不改金额语义」为验收。 <!-- comet-task:f3d919f2-6238-406f-8bba-e315f2bb39e1 -->
- [x] 2.2 规划免赔剩余四维（区域/医保/药品/诊疗）的「显式拒绝 + 稳定原因码」收口，保留医院/病种白名单；以「规划含白名单不变与变异验证要求、不新增 DDL」为验收。 <!-- comet-task:7bab9306-5716-4961-8ee5-6dfc3eadf670 -->

## 3. 四类外部前置解锁路径（责任方 + 门禁 + AI 边界）

- [x] 3.1 盲标数据集解锁：登记 ≥300 条真实脱敏人工盲标的前置、`BASELINE_WIRED`/`FROZEN_EVALSET_ACTIVE` 门禁、算法/评测团队责任方、AI 只评测不造数冒充；以「四要素齐全且明确合成数据标 SIMULATION」为验收。 <!-- comet-task:4e926e15-e90d-40ad-8bc4-2c7acfebc4e2 -->
- [x] 3.2 目录准入解锁：登记授权目录数据 + `CATALOG_POLICY` NONE→MANDATORY 的前置、`CATALOG_ADMISSION_ACTIVE` 门禁、产品+架构责任方；以「四要素齐全且注明改错会全量停摆」为验收。 <!-- comet-task:95b3d24e-0f75-48d4-b420-88557e27efd2 -->
- [x] 3.3 Go-Live 合规解锁：登记 13 册补正文并标 `current` 的前置、`GOLIVE_NOT_DRAFT` 门禁、合规/法务（L4）责任方、AI 禁代签；以「四要素齐全且明确 L4 边界」为验收。 <!-- comet-task:704a5335-8ce2-4f11-a3a0-1fa3b8f47933 -->
- [x] 3.4 资金闸双签解锁：登记视觉桩 `available=false`→`true` 的 L2 变更前置、指定 Review 人+产品签核人责任方、AI 不单方变更；以「四要素齐全且标注此变更会打开自动放款」为验收。 <!-- comet-task:2a1bccd7-4fd3-4c09-9ac3-9a82a4135a28 -->

## 4. 边界与防假绿固化

- [x] 4.1 钉死三条红线（资金闸 L2 双签、readiness 12 项判据单一真源且证据视图不翻绿、合成/桩不得冒充真人盲标或真实 provider）；以「三条在规格中有对应 Scenario、且可见于设计文档」为验收。 <!-- comet-task:29a8596c-2ce0-4ab9-8b83-70829e293960 -->
- [x] 4.2 确认本 change 零生产代码改动、零 DDL、零门禁放宽、零活库数据变更；以 `git diff --name-only` 仅含本 change 文档产物且全量 `check.sh` exit 0 为验收。 <!-- comet-task:57950727-df97-44c4-9624-e21bc62b5020 -->

## 5. 落地计划与交付

- [x] 5.1 在 `docs/superpowers/plans/` 产出可执行落地计划，按依赖顺序排列 1–4 组任务，标注每项前置、门禁判据与责任方；以「计划文件路径在 artifactRefs 可解析、每项含验收方式」为验收。 <!-- comet-task:debf39dd-0a2b-4b22-a350-6aec7e6c0174 -->
- [x] 5.2 产出面向评审的四阶段对齐交付说明（含可视化对齐结论：绿=完整实现/琥珀=部分默认关/红=桩或未实现）；以「说明与 spec 四态登记一致、无夸大口径」为验收。 <!-- comet-task:1020725f-97e9-4212-98fc-a45ddb0b8ca1 -->
