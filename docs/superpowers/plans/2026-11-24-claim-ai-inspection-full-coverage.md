---
change: claim-ai-inspection-full-coverage
design-doc: openspec/changes/claim-ai-inspection-full-coverage/design.md
base-ref: 879a5352808e7bab457f3eca4f77a01aa99d2a3b
archived-with: 2026-11-24-claim-ai-inspection-full-coverage
---

<!-- comet-task-authority: openspec/changes/claim-ai-inspection-full-coverage/tasks.md -->

# Build Plan — AI 后督质检全量覆盖（claim-ai-inspection-full-coverage）

> 执行配置：build_mode=autonomous、tdd_mode=tdd、review_mode=standard、subagent_dispatch=null（本 change 用户 2026-11-24 明确确认）。
> 核心策略来自 design.md：`ratePercent>=100` 全量分支与 fail-safe 回落现网已存在，**唯一生产代码改动是候选仓储 SQL 的状态过滤**（仅 `FINISHED` → 含 `CLOSED_REJECTED`），其余以测试与规格钉子固化。TDD 顺序：先写会失败的测试（RED）→ 改 SQL/补日志（GREEN）→ 变异验证双向可证伪。

## 任务范围与依赖

12 项任务分 4 组，依赖关系：组 1（现状确认，已在 Design 侦察完成）→ 组 2（实现与测试）→ 组 3（资金边界守护）→ 组 4（门禁与验证）。
- 组 1 的实证结论已记录在 design.md「代码现状实证表」，Build 阶段只做复核lightly，不重复侦察。
- 组 2.2 是全 change 唯一生产代码改动点；2.1/2.3/2.4/2.5 以测试为主。
- 组 4 的变异钉子依赖组 2 测试就位。

## 组 1 — 规格与现状确认（Design 已实证，Build 复核）

- 1.1 复核 `InspectionSamplingPolicy.shouldSample` 的 `ratePercent>=100` 分支与 `InspectionSamplingJob` 终态来源；验收：逐行确认并引用文件+行号（design.md 已记录） <!-- comet-task-ref:71857625-e20c-4b73-a40a-02e02e3c2038 -->
- 1.2 复核 `INSPECTION_SAMPLING` 为唯一配置源无第二口径；验收：grep 全仓确认唯一消费点 `FeatureTenantToggleService.java:151-153` <!-- comet-task-ref:07a546a0-d523-494e-adf4-5032759a5626 -->

## 组 2 — 抽样策略与终态覆盖实现（TDD）

- 2.1 RED→GREEN：为 `InspectionApplicationService`/Job 链路补 100% 全量命中的集成测试（比例=100 时 N 笔合格案件生成 N 条 `InspectionRecord`）；现有 `InspectionSamplingPolicyTest.rateBounds` 仅是纯策略断言，本任务补「配置键→候选→建单」端到端 <!-- comet-task-ref:e7b70782-2061-4f9b-947d-3e8372516cf0 -->
- 2.2 唯一生产改动：扩展 `findFinishedForInspectionAllTenants.sql` 候选状态过滤，纳入 `CLOSED_REJECTED`（拒赔终态）；下游 `claim.finished()`（已含两终态）不改。验收：100% 配置下拒赔终态案件自动生成抽检记录 <!-- comet-task-ref:94a4e3f7-175a-4769-81e7-ba7242056d17 -->
- 2.3 回归：默认回落 5% 与现网行为一致；验收：未配置租户行为与 change 前一致（`InspectionConfig.DEFAULT_SAMPLING_RATE=5`） <!-- comet-task-ref:7b2f1345-f7f1-48c9-9dd6-5ede328ce66b -->
- 2.4 异常注入：配置读取失败 fail-safe 回落 5% 并记 WARN；验收：注入异常确认回落、日志可观测、主流程不中断 <!-- comet-task-ref:6e5bf894-df0a-4298-a37b-28672e19dfc2 -->
- 2.5 租户隔离：租户 A=100% 不影响租户 B 默认 5%；验收：跨租户场景测试通过 <!-- comet-task-ref:e7611ee2-be45-4cb4-ad69-033b8d5d1cc7 -->

## 组 3 — 资金与语义边界守护

- 3.1 grep + 测试证明抽检仅写 `t_claim_inspection`，不进入 `Adjustment`、不改 `AUTO_APPROVE`/视觉桩 `available`/目录准入 `MANDATORY` <!-- comet-task-ref:c805471f-16ad-4d38-ac39-feb34ac4cf03 -->
- 3.2 确认赔付金额计算语义未变；验收：现有资金相关测试全绿、无金额计算代码改动 <!-- comet-task-ref:23848349-b853-4e5b-92e4-ea7fedba074a -->

## 组 4 — 门禁与可证伪验证

- 4.1 为 3 条判据补变异双向钉子（比例分支篡改 `>=100`→`>100`、终态白名单回退仅 `FINISHED`、fail-safe 移除）；验收：改一处必红、还原必绿，变异记录归档 <!-- comet-task-ref:df274979-ebde-47a7-9f8d-48e312e1b490 -->
- 4.2 按仓库纪律把新判据同步到门禁入口；验收：check 入口可见新判据，注入必红还原必绿 <!-- comet-task-ref:5a922fbe-0800-47a9-b327-2ad8b5685404 -->
- 4.3 跑 synap-claim 模块相关测试全绿；验收：`mvn -pl bone-apps/synap-claim/synap-claim-app -am test` 通过，测试数从收据取真实值（Tests run 含 skipped，写「N 通过」先算 N−skipped） <!-- comet-task-ref:99fbd482-08c6-4a5c-8cd1-7e5ce1ef2616 -->

## 高风险部分：失败处理与回退

- **候选 SQL 扩展（2.2）** 是唯一生产改动且影响自动抽样候选集：若扩展后默认 5% 下抽检数量异常上升，回退方式是恢复 SQL 仅 `FINISHED`（拒绝纳入拒赔），但会导致 spec「拒赔覆盖」场景失败——因此必须先以测试钉住「默认 5% 下拒赔即使入候选仍按 5% 抽样」，确认数量口径不变才提交。
- **SQL 模板纪律**：模板首行必须是语句关键字（SDK 不剥注释），模板内不放注释；新增/改仓储方法须交对应 `.sql`（本任务只改已有方法模板，路径不变）。
- **变异钉子**：变异后必须 `touch` 源文件确保重新编译，避免跑旧字节码；还原后同样重编再验绿。
- **测试数取证**：从 Maven 收据粘真实 `Tests run`，不为凑数写「全通过」。

## 验收标准（Definition of Done）

1. 12 项任务全部勾选、无遗留未验证项。
2. `mvn -pl bone-apps/synap-claim/synap-claim-app -am test` 全绿，测试数来自收据。
3. 3 条变异钉子均实现「改一处必红、还原必绿」并归档记录。
4. 资金边界 grep 零命中，赔付金额相关测试全绿。
5. 无 DDL、无 provider 凭证、无资金闸改动（红线守护）。
6. 门禁入口可见新判据。
