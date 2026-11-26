---
change: procuraai-oversight-effectiveness
design-doc: openspec/changes/procuraai-oversight-effectiveness/design.md
base-ref: 3a28d2a422e650a30610ddcff6600f89b0b7eb69
archived-with: 2026-11-25-procuraai-oversight-effectiveness
---

# Plan — procuraai-oversight-effectiveness

<!-- comet-task-authority: openspec/changes/procuraai-oversight-effectiveness/tasks.md -->

本计划按 design.md 的 5 个决策实施人类监督有效性护栏。执行方式 autonomous + tdd + standard review。任务权威为 `<classic-task-authority-ref>`，每项任务以 comet-task-ref 映射 UUID；验收以 `mvn -o -f bone-apps/bone-procurement/pom.xml test`（模块链）与门禁脚本为证据。

## 1. 领域层：监督有效性三态判词（TDD RED→GREEN）

<!-- comet-task-ref:2e4c60fb-5a3c-4cf4-8b43-6f030fc1dc4e -->
- 1.1 新增 `OversightVerdict` 三态枚举（`UNDETERMINED` / `NO_ACTIONABLE_SIGNAL` / `REVIEW_REQUIRED`）与 `isActionable()`；先写枚举单元测试（RED），实现后转 GREEN。

<!-- comet-task-ref:96d90b1d-7b2c-4bf0-b20d-e2aaf0f7d798 -->
- 1.2 新增 `OversightThresholds` record（`reviewDurationFloorMs` / `minSampleSize` / `fastReviewRatioThreshold`），`unconfigured()` 缺省未配置、构造器 fail-closed 校验（负阈值、非正样本量）；单元测试验证拒绝与 `configured()`。

<!-- comet-task-ref:2f2166da-abfe-4da1-a673-4e68567faf91 -->
- 1.3 新增 `OversightSignal` 结果 record（能力码/判词/样本数/三类风险计数/原因码/`computedAt`），原因码常量，`claimsModelHealthy()` 恒 false；单元测试验证护栏与只读字段。

<!-- comet-task-ref:89adf35e-c053-4dde-9e00-80ac81c64e02 -->
- 1.4 新增 `OversightSignalPolicy` 纯函数裁决：非法输入拒绝 → 阈值未配置/样本不足 `UNDETERMINED` → 无理由推翻/未回流评测/耗时过短触阈 `REVIEW_REQUIRED` → 否则 `NO_ACTIONABLE_SIGNAL`；TDD 覆盖六类场景与边界（等于阈值、100%、零样本、非法计数），先 RED 后 GREEN。

依赖：1.1–1.3 为 1.4 提供值对象；验收：领域测试全绿，负向探针（非法输入/非法阈值）必红。

## 2. 应用层：真实计数与只读服务

<!-- comet-task-ref:97c9c4aa-5f9c-4864-aa52-8b2836310c32 -->
- 2.1 扩展 `HumanDecisionLedger` 接口新增 `findByCapability(CapabilityCode)` 只读查询；`HumanDecisionLedgerAdapter` 复用私有 `query("capabilityCode", ...)`，退役 `InMemoryHumanDecisionLedger` 同步签名；编译通过验证接口一致。

<!-- comet-task-ref:2a3136dc-0ef3-4a5d-8f4a-11b5a98198cc -->
- 2.2 在 `AiGovernanceApplicationService.oversightSignal(code, thresholds)` 校验能力已登记，按能力码取终决记录统计样本数与三类风险计数，委托 `OversightSignalPolicy`；不写 trace、不改状态、不落库。应用测试验证真实计数一致、未登记能力拒绝、多次读无副作用。

<!-- comet-task-ref:5ce4acba-2028-4964-bf70-1800142be5dd -->
- 2.3 新增 `OversightMetricsView` 只读投影（`modelHealthClaim=NONE`、`requiresOwnerReview`、原因码与计数），`from(signal)` 不回传 `reason`/`aiConclusion` 原文（PII 禁入）；单元测试验证映射与护栏。

## 3. 适配层：配置与只读端点

<!-- comet-task-ref:415e4ad1-d12f-4f63-8084-6f672971bf9f -->
- 3.1 `AiGovernanceProperties` 新增 oversight 阈值字段（`reviewDurationFloorMs` 默认 null、`minSampleSize` 默认 20、`fastReviewRatio` 默认 null）及 getter/setter；配置测试验证缺省 fail-closed。

<!-- comet-task-ref:7164c6a2-5293-4d5e-b9a4-0fb3584984a2 -->
- 3.2 `AiGovernanceController` 注入 properties 构造服务端阈值，新增 `GET /capabilities/{code}/oversight`，方法只接受路径参数 `code`（无 `@RequestParam` 阈值），复用 `procurement:ai-governance:read`；契约测试锁定端点数量（十端点）、权限码与方法签名。

<!-- comet-task-ref:e106a57b-17e7-454a-bdc5-e879ecb1db65 -->
- 3.3 更新 `AiGovernanceToggleTest`：验证 oversight 阈值缺省未配置、判词缺省 `UNDETERMINED`；测试通过验证。

## 4. 验证与内部模拟 POC

<!-- comet-task-ref:e56a7657-9653-4187-ba65-2ba0f37e4b10 -->
- 4.1 运行 ai-governance 模块全部单测（含新增领域/应用/投影/契约/配置测试），零失败；以 maven surefire 报告为证据。

<!-- comet-task-ref:ef93af99-b05f-4b56-bee9-29e197446955 -->
- 4.2 运行采购聚合模块构建与测试（后台执行避免前台超时），确认无跨模块回归；以退出码 0 为证据。

<!-- comet-task-ref:8b8e19e4-dc78-4ef3-8160-c4d59e279ebc -->
- 4.3 内部模拟 POC：构造真实终决记录（有效复核/橡皮图章/无理由推翻/未回流评测/样本不足）驱动策略与投影，验证三态判词与 fail-closed 与规格一致；POC 报告逐条记录输入输出与断言。

<!-- comet-task-ref:317bbefc-480b-4463-8b93-adf98f42ba11 -->
- 4.4 独立审查（standard）：由独立 reviewer 审查最终 diff 的 DDD 分层、fail-closed、PII 禁入、无副作用、阈值不可注入；CRITICAL/IMPORTANT 必须修复后复验。

<!-- comet-task-ref:84554a99-3c74-465b-924f-c8390d88fb7b -->
- 4.5 生成 Verify 报告（`docs/superpowers/reports/`），汇总交付物 file:line、POC 场景、负向探针、门禁结果、独立审查与诚实边界；以报告结论 PASS 为验收。

## 执行约束与失败处理

- 不改 DDL/CI 三入口判据集合/evalset 状态/ProcuraAI 文档族真源；不接真实模型；不声明模型效果达标。
- RED 阶段以"缺少符号/断言失败"为成功信号；不得回退代码伪造 GREEN。
- 若 Mockito 嵌套桩失败，先建局部变量再打桩；ArchUnit E-5.4 只约束 application 层写操作，本 change 保持只读。
- 采购聚合测试用后台任务执行并读退出码，避免前台 SIGTERM 假失败。
