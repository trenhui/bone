# synapClaim 四阶段落地现状与解锁规划 · 实施计划

> **For agentic workers:** 本计划按 tasks.md 逐项落实。每项引用稳定的 comet-task ID；不另建 checkbox。
> 产物语言：zh-CN。唯一事实源：`doc/ai-product/synapClaim/09-records/synapClaim-实现度与联调评估报告.md`（HEAD `5b6b81ae3`）。

**Goal:** 把四阶段路线图逐节点落地现状固化为可机检的四态契约，规划代码可落地差距收口与四类外部前置解锁，且不改资金行为、不伪造外部前置。

**Architecture:** 单一 OpenSpec change，产出治理规格 + 技术设计 + 落地计划 + 评审交付说明；证据视图只读、readiness 12 项判据单一真源；所有解锁项绑定责任方与门禁判据，AI 只准备与验证、不代签。

**Tech Stack:** OpenSpec spec-driven（Markdown），Comet Classic SDK Run，现有 `check.sh` / `_ai_native_readiness.py` 门禁。

<!-- comet-task-authority: openspec/changes/synapclaim-four-phase-landing-plan/tasks.md -->

## Global Constraints

- 不改生产代码、不加 DDL、不放宽门禁、不动活库数据。
- 视觉反诈 `available=false` 资金闸属 L2，变更须双签；AI 不单方变更。
- 合成/桩数据必须显式标注 SIMULATION，不得冒充真人盲标或真实 provider 参与。
- 证据视图冲突时取保守值（未就绪/BLOCKED），不得翻绿。
- 四态：`IMPLEMENTED` / `PARTIAL_DEFAULT_OFF` / `STUB_SAFETY_LOCK` / `DESIGN_ONLY`。

---

## 任务组 1：落地现状登记

### Task 1.1 · 四阶段逐节点四态矩阵
<!-- comet-task-ref:7e12e145-14b9-4cac-a10b-bf05d29cb61d -->
- 依据评估报告 §三/§六与四阶段对照表，逐节点登记四态 + 代码证据锚点。
- 覆盖 15 个节点：一阶段（报案导入/保单关联/人机审核/单证发票校验/基础理算/结案回传）、二阶段（额度锁冻结扣减释放/可读推导式/复杂免赔/共享保额）、三阶段（质检规则引擎/OCR/目录对码）、四阶段（多租户隔离权限/RocketMQ/保单支付中台）。
- 验收：与报告 §三/§六逐项对账一致；每态有类名/行号或配置键值锚点；15/15 覆盖。
- 产物：`doc/ai-product/synapClaim/09-records/` 下的四阶段对齐交付说明（Task 5.2 合并产出）。

### Task 1.2 · 两处实质偏离登记
<!-- comet-task-ref:618de2a7-8d32-4603-a3b4-a35f49071191 -->
- 单列：真实保单表 `t_claim_policy`（超前替换「模拟保单关联」）、本地确定性图筛查 `E2_GRAPH_LOCAL`（用户已授权，替代平台图库；发票跨案/同证件 90 天高频，命中只转人工不碰钱）。
- 验收：两项均在矩阵中标注且不掩盖；与 design 决策 5 一致。

## 任务组 2：代码可落地差距收口规划

### Task 2.1 · 生产引擎切换规划
<!-- comet-task-ref:f3d919f2-6238-406f-8bba-e315f2bb39e1 -->
- 规划从 `@Deprecated AdjustmentEngine` 回退切到新管线的顺序：先补新管线金额对照测试 → 灰度 Shadow 观察 → 保留 `@Deprecated` 回退与 fail-closed。
- 验收：规划含回退路径与切换前后金额对照验证；明确不改金额语义、不打开自动放款。

### Task 2.2 · 免赔剩余四维显式拒绝规划
<!-- comet-task-ref:7bab9306-5716-4961-8ee5-6dfc3eadf670 -->
- 规划区域/医保/药品/诊疗「显式拒绝 + 稳定原因码（`LIMITED_CONDITION_DIMENSION_UNSUPPORTED`）」收口；医院/病种白名单保持不变。
- 验收：规划含白名单不变与变异验证要求；不新增 DDL、不臆造规则。

## 任务组 3：四类外部前置解锁路径

### Task 3.1 · 盲标数据集解锁
<!-- comet-task-ref:4e926e15-e90d-40ad-8bc4-2c7acfebc4e2 -->
- 登记：前置 ≥300 条真实脱敏人工盲标；门禁 `BASELINE_WIRED`/`FROZEN_EVALSET_ACTIVE`；责任方算法/评测团队；AI 只跑评测、不造数冒充；合成数据标 SIMULATION。
- 验收：四要素齐全，SIMULATION 标注明确。

### Task 3.2 · 目录准入解锁
<!-- comet-task-ref:95b3d24e-0f75-48d4-b420-88557e27efd2 -->
- 登记：前置授权目录数据 + `CATALOG_POLICY` NONE→MANDATORY；门禁 `CATALOG_ADMISSION_ACTIVE`；责任方产品+架构；注明改错会全量停摆。
- 验收：四要素齐全，风险提示明确。

### Task 3.3 · Go-Live 合规解锁
<!-- comet-task-ref:704a5335-8ce2-4f11-a3a0-1fa3b8f47933 -->
- 登记：前置 13 册补正文并标 `current`；门禁 `GOLIVE_NOT_DRAFT`；责任方合规/法务（L4）；AI 禁代签。
- 验收：四要素齐全，L4 边界明确。

### Task 3.4 · 资金闸双签解锁
<!-- comet-task-ref:2a1bccd7-4fd3-4c09-9ac3-9a82a4135a28 -->
- 登记：前置视觉桩 `available=false`→`true` 的 L2 变更；责任方指定 Review 人 + 产品签核人；AI 不单方变更；标注此变更会打开自动放款。
- 验收：四要素齐全，资金后果与双签要求明确。

## 任务组 4：边界与防假绿固化

### Task 4.1 · 三条红线固化
<!-- comet-task-ref:29a8596c-2ce0-4ab9-8b83-70829e293960 -->
- 资金闸 L2 双签、readiness 12 项单一真源且证据视图不翻绿、合成/桩不得冒充真人盲标或真实 provider。
- 验收：三条在 spec 中有对应 Scenario（`four-phase-landing-governance/spec.md`），且可见于 design.md 决策 3/4。

### Task 4.2 · 零改动边界确认
<!-- comet-task-ref:57950727-df97-44c4-9624-e21bc62b5020 -->
- `git diff --name-only`（相对 baseRef `a0f6db537`）仅含本 change 文档产物；全量 `check.sh` exit 0。
- 验收：无生产代码/DDL/门禁/活库改动；check.sh 末行 `✅ 本地自检通过！`。

## 任务组 5：落地计划与交付

### Task 5.1 · 可执行落地计划
<!-- comet-task-ref:debf39dd-0a2b-4b22-a350-6aec7e6c0174 -->
- 本文件即落地计划：按依赖顺序排列 1–4 组任务，每项含前置、门禁判据与责任方。
- 验收：计划路径在 artifactRefs 可解析；每项含验收方式。

### Task 5.2 · 四阶段对齐交付说明
<!-- comet-task-ref:1020725f-97e9-4212-98fc-a45ddb0b8ca1 -->
- 产出面向评审的对齐说明，含可视化结论：绿=完整实现 / 琥珀=部分默认关 / 红=桩或未实现。
- 验收：说明与 spec 四态登记一致，无夸大口径，四类外部前置如实标注。
