---
change: synapclaim-engineering-evidence-rerun
design-doc: openspec/changes/synapclaim-engineering-evidence-rerun/design.md
base-ref: 3520939c319258562d013c7127654834adf7e817
---

<!-- comet-task-authority: openspec/changes/synapclaim-engineering-evidence-rerun/tasks.md -->

# Build Plan — 工程证据底座（实施口径唯一真源，2026-11-20）

> 本计划引用已批准的 `design.md` 与 `tasks.md`（Open/Design 已锁定为已接受证据，本轮不修改）。任务完成状态一律以 tasks.md 为准，本计划仅以 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 1. 执行配置

`build_mode=direct` / `tdd_mode=tdd` / `review_mode=off` / `subagent_dispatch=null` / `direct_override=true`（用户 2026-11-20 明确选择，`propose-build --confirmed` 已批准）。即使直接实施，仍保留真实 RED/GREEN 与变异验证。

## 2. 基线事实（2026-11-20 实证）

- 分支 `master`，HEAD `3520939c3`（与 origin/dev 同 SHA）；工作树干净。
- 代码：`bone-apps/synap-claim`（app + adjustment-engine + sdk）；三模块测试 219 个，主源码约 460 类。
- 门禁真源：`doc/ai-product/synapClaim/tools/_ai_native_readiness.py`，当场重跑 7 SAT / 5 BLOCK，L0，`l3Claimable=false`。
- 现有 fail-closed 判据散落：`DecisionGraph`/节点 handler、`LiabilityRule.LimitedConditions`、`AiCapabilityReadinessService`/`AiRuntimeHealthLocator`、`AiNativeReadinessService`。
- 错误码集中在 `ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS`，前端两语言包，台账 `doc/architecture/Bone-错误码登记.md` §6。

## 3. 实施纪律

- TDD：先写会红的用例，再实现转绿；每项新增判据配变异验证（移除接线翻转、只改字面串不翻绿、互补变异）。
- 分层：adapter → application → domain ← infrastructure；Controller 返回 `ApiResponse<T>`，不裸返领域对象；只读入口不新增写接口。
- 错误码：优先复用既有 fail-closed 原因码；确需新增则四处齐备（常量 + DEFAULT_HTTP_STATUS + 前端两语言包 + 台账 §6）。
- 不碰资金闸默认值、不改金额语义、不新增 DDL、不接真实 provider、不改 Go-Live 文档。
- 门禁结论只认 `exit 0` + 末行「✅ 本地自检通过！」；日志内 ❌ 多为负向探针。
- 改 Java 后 `mvn spotless:apply`；关键产物完成即 `git add`（共享树并行会话保护）。

## 4. 任务映射

### 组 1 · 证据视图与决策轨迹

- 1.1 新增 `EngineeringEvidenceService`（只读）：汇总 fail-closed 落点、不可达维度、provider 口径、能力声明一致性，输出稳定判据 ID / 状态 / 原因码；先写"结构化可机检且无状态写入"的失败用例再实现。 <!-- comet-task-ref:bb09dcd6-717a-4545-a84b-2fa50a00f083 -->
- 1.2 决策轨迹 `MANUAL_REQUIRED` 补可查询 fail-closed 原因码（视觉桩/规则桩/provider 未配/knowledge 不可用）；验证节点拓扑、决策结果、跳过集合不变。 <!-- comet-task-ref:53a88480-38d6-4ef0-a469-9c6217ba12d7 -->
- 1.3 暴露只读查询入口（Controller + `ApiResponse`）；Web 层测试验证只读语义与原因码字段，不新增写接口。 <!-- comet-task-ref:081b118d-4551-4592-a831-8828c7ec0731 -->

### 组 2 · 不可达维度显式拒绝

- 2.1 `LimitedConditions` 判定入口检测区域/医保/药品/诊疗四类无配置列维度，返回稳定原因码显式拒绝；优先复用既有码，否则四处齐备；验证拒绝与"不改变金额语义"。 <!-- comet-task-ref:7f273004-4118-4b61-be1e-d6fd3f18dfa5 -->
- 2.2 变异测试：医院/病种两已支持维度不受影响；去掉拒绝判据即翻红。 <!-- comet-task-ref:844afc07-ceb3-45b5-a4e7-92ff8267e8b3 -->

### 组 3 · provider fail-fast

- 3.1 自动放行前置校验读 AI 网关 base-url，缺失即可诊断 fail-fast、决策保持 `MANUAL_REQUIRED`，复用 `AiRuntimeHealthLocator` 口径、桩不冒充模型。 <!-- comet-task-ref:c3519c2f-e44c-4993-984c-a5cbd4a2b511 -->
- 3.2 E2E 负向钉子：无 provider 时主链仍走 MANUAL_REQUIRED 非中断；运行态产物如实标注桩口径与真实参与率。 <!-- comet-task-ref:790db1cd-a4df-44a0-a495-acb434b4a210 -->

### 组 4 · 门禁与验收

- 4.1 `scripts/check.sh` 接线新增判据（带 `--check`）+ `.comet/check-policy.json` v2 登记；12 项判据不放宽、冲突取保守，自检负向探针验证。 <!-- comet-task-ref:d4ec56fd-ed9f-46f6-a838-ae7e246a3844 -->
- 4.2 全部新增判据通过变异验证：移除接线翻转、只改字面串不翻绿。 <!-- comet-task-ref:aedcd5e4-59ea-440e-8759-4e1db1bef6aa -->
- 4.3 `mvn spotless:apply`；跑 `./scripts/check.sh` + synapClaim 专项 E2E，确认 exit 0 + 末行通过，无资金行为变更、无假绿。 <!-- comet-task-ref:eed3af34-4eb1-443f-99fb-3634f707d185 -->

## 5. 回滚

无 DDL、无数据迁移；回滚即移除新增服务/判据/门禁接线与测试，既有 fail-closed 行为不变。
