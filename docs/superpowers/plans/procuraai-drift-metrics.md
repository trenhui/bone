---
change: procuraai-drift-metrics
design-doc: openspec/changes/procuraai-drift-metrics/design.md
base-ref: e6e84a86b8691e6d2ddd93180c59d317decc1275
archived-with: 2026-11-25-procuraai-drift-metrics
---

<!-- comet-task-authority: openspec/changes/procuraai-drift-metrics/tasks.md -->

# Plan — procuraAI 漂移信号与治理指标只读观测

## 范围与约束

- 目标：为 AI 决策治理补上漂移信号三态判词 + 治理指标只读观测（金监 §6/§9、业界 override 率）。
- 设计真源：`openspec/changes/procuraai-drift-metrics/design.md`（D1–D4）。
- 行为真源：`specs/procurement/ai-drift-metrics/spec.md`（3 Requirement / 10 Scenario）；tasks.md 15 个 tracked task。
- 红线：不改 DDL / `bone-init.sql`、不改 evalset 状态、不改 A10.17 三入口判据集合、不改 CI 配置、不新增权限码、不接真实模型、不声明模型效果达标、不自动改能力状态。
- 工作区：当前 `dev`，isolation=current。
- 执行配置：`build_mode=autonomous`、`tdd_mode=tdd`、`review_mode=standard`。

## 依赖顺序

```
1 领域值对象+纯函数裁决(TDD) ─┐
                              ├→ 3 适配层只读端点+契约 → 4 门禁验证+POC 报告
2 应用只读方法+投影+测试 ─────┘
```

## 实施步骤

### 1. 领域层（RED→GREEN）

<!-- comet-task-ref:26780ad8-5398-4bac-a1a9-bc0bc42601fa -->
- **1.1** 新增 `DriftVerdict` 三态枚举，`isActionable()` 仅 REVIEW_REQUIRED 为真。
<!-- comet-task-ref:a12efd58-ea0c-403e-948a-56da00dd6b6d -->
- **1.2** 新增 `DriftThresholds` record：缺省未配置、阈值 (0,1]、最小样本量正数校验。
<!-- comet-task-ref:48f4a725-0dd6-44a9-af8a-ff4f4948c66c -->
- **1.3** 新增 `DriftSignal` record：能力码/判词/样本数/推翻数/override 率/阈值/最小样本/原因码/计算时刻；`claimsModelHealthy()` 恒 false。
<!-- comet-task-ref:054d7690-6697-40d3-963b-a3ad01d1723e -->
- **1.4** 新增 `DriftSignalPolicy` 纯函数：阈值合法性 → 样本充足 → 计数一致 → 触阈判定；原因码常量齐全。
<!-- comet-task-ref:8b0caf41-82bb-4c88-83ac-e91742786dbb -->
- **1.5** 先写 `DriftSignalTest`（阈值未配置/样本不足/触阈/未触阈/非法输入 5 组）确认 RED，再实现转 GREEN。

### 2. 应用层

<!-- comet-task-ref:3a1c754e-077c-4ca2-955f-aee2e7bb2561 -->
- **2.1** 新增 `DriftMetricsView` 只读投影 record。
<!-- comet-task-ref:5435d83d-d1d8-45e1-9913-ae4f2ae9fcaa -->
- **2.2** `AiGovernanceApplicationService.driftSignal(code, thresholds)`：tracePort 计 `HUMAN_FINAL_DECISION`/`HUMAN_OVERRIDE` 事件样本数、ledger 取推翻数，未登记能力 fail-closed；只读无副作用。
<!-- comet-task-ref:14508752-ccb6-47bb-b93f-cbff4bc3d70f -->
- **2.3** 应用服务测试：真实计数一致性、阈值缺省/样本不足 fail-closed、触阈需复核、只读无副作用、未登记能力拒绝。

### 3. 适配层

<!-- comet-task-ref:27527739-2e55-421a-8df3-efd1c9a64a65 -->
- **3.1** Controller 新增 `GET /capabilities/{code}/drift`，`procurement:ai-governance:read`，返回 `ApiResponse<DriftMetricsView>`。
<!-- comet-task-ref:bec22584-c189-4d4c-a492-1d52e7b8accd -->
- **3.2** 阈值由调用方 query 配置（阈值/最小样本），缺省未配置 → UNDETERMINED；不新增权限码。
<!-- comet-task-ref:f33bebc2-824d-4ee9-bc74-f49348517648 -->
- **3.3** 更新 `AiGovernanceControllerContractTest` 锁定路径/方法/权限/ApiResponse 包装。

### 4. 验证与报告

<!-- comet-task-ref:79153d37-3707-451e-bf24-c4ec3f834ab2 -->
- **4.1** `mvn spotless:apply` + ai-governance 模块测试全绿（含 ArchitectureTest）+ 架构门禁。
<!-- comet-task-ref:f4360b4d-20b4-4097-8624-afe72878651e -->
- **4.2** 采购聚合模块测试无回归。
<!-- comet-task-ref:7d47e61b-58c5-4f68-9e3c-b8d257b9aaca -->
- **4.3** procuraAI 文档门禁（alignment / evalset / frontmatter / persistence / event-chain）不退化。
<!-- comet-task-ref:1b38d9b5-01e2-4df6-aed8-ada82be5a81e -->
- **4.4** 内部模拟 POC：真实终决序列验证三态判词与 fail-closed 负向探针，产物落 POC 验证报告。
<!-- comet-task-ref:f17d0cb0-0317-4cf9-9b8a-941ab26e794f -->
- **4.5** 报告诚实标注：CI 绿 ≠ 模型效果达标、阈值待 owner、未改 DDL/CI 判据集合。

## 验收方式

- 领域与应用单测 + HTTP 契约测试全绿；架构门禁通过。
- 采购模块与 procuraAI 文档门禁无新增红灯（对照 HEAD 基线）。
- POC 报告含三态判词真实计数证据与负向探针，且明确不声明模型效果达标。
- 工作区改动仅限 `bone-procurement-ai-governance` 模块 + change 文档 + POC 报告；无 DDL/CI 变更。

## 风险与回退

- 样本数来自追溯事件，历史留痕不完整会低估样本 → 偏保守（倾向 UNDETERMINED），不会误报触阈；现有留痕测试保证每次终决有事件。
- 阈值缺省 UNDETERMINED 是刻意诚实，非缺陷。
- 回退：移除新增类/端点/测试与 change 文档即可，无库表变更。
