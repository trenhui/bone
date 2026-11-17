---
change: synapclaim-review-fatigue-controls
design-doc: openspec/changes/synapclaim-review-fatigue-controls/design.md
base-ref: 7dcae54d0ca37bb1191707c543d122467e229d26
baseline-head: 7dcae54d0
baseline-gate: 59/59
plan-revision: 1
---

<!-- comet-task-authority: openspec/changes/synapclaim-review-fatigue-controls/tasks.md -->

# Build Plan: synapclaim-review-fatigue-controls

- Design: `openspec/changes/synapclaim-review-fatigue-controls/design.md`
- Tasks: `openspec/changes/synapclaim-review-fatigue-controls/tasks.md`
- Baseline: `7dcae54d0`
- Config: `executing-plans / tdd / thorough / subagent_dispatch=null`
- Scope: 零 DDL、不碰金额/拒赔/支付；单班超量真实取数并回池，soft prompt 只提示；班末硬排除登记 gap。

## 实施顺序

1. 先写会红的测试与值对象：`ReviewFatigueConfig` 解析与 rejected 口径。
   <!-- comet-task-ref:85687aa9-e25b-418e-b92a-6e8bf7178549 -->
2. 接入 `FEATURE_REVIEW_FATIGUE_GUARD` 与 `FeatureTenantToggleService.reviewFatigueConfig`，无配置/非法配置必须回落基线。
   <!-- comet-task-ref:0c5f5567-60b8-4536-8cb4-bd431ac66da1 -->
3. 增加 `countFinishedTodayByAssignee`，只按库内 `finished_at` 事实统计。
   <!-- comet-task-ref:d22421a0-87f9-4b56-bf4a-d1d18fa92335 -->
4. 在 `ClaimTaskApplicationService.decide` 包裹准入端口：超量 `USER` 候选不可用，异常同样不可用，统一回池。
   <!-- comet-task-ref:a4f53325-3c37-4e1c-a9d2-fb20eda52ba3 -->
   <!-- comet-task-ref:86309f14-10b5-4948-a4fc-80c5061e14ce -->
5. 实现 `ReviewFatigueDecision` 与纯评估服务；连续时长/班末分钟由调用方给，服务端不伪造班次事实。
   <!-- comet-task-ref:f7c2ad14-1eed-4185-ac33-6dd483552579 -->
6. 增加只读评估端点；只有返回，不写库、不改状态、不转派。
   <!-- comet-task-ref:8f4353c8-5bde-492f-b184-f2ee564b58ee -->
7. 工作台读取评估并显示 soft prompt；若现有数据链路不足则转验收 gap，不用静态文案冒充生效。
   <!-- comet-task-ref:1938b56b-cdae-405b-adc0-f303889d0bbb -->
8. 跑定向单测、`synap-claim-app -am` 全量与 `check.sh`，判据以 exit code 和末行总结果为准。
   <!-- comet-task-ref:a73275f5-d6d7-4936-a123-16ed21741438 -->
   <!-- comet-task-ref:eba2c739-cd15-4ee6-bf26-d00e23790cdb -->
9. 回写 v2.11 与独立 Verify 报告；班末硬排除未落地部分保持 gap，composer 收据死锁时保持诚实终态。
   <!-- comet-task-ref:8a1e91b2-115d-4be9-8372-4170ddccaba0 -->
   <!-- comet-task-ref:a6d975eb-76c0-46a5-997f-3ffd6fa4eb22 -->

## 完成判据

- 12/12 任务由 CLI `task-complete` 勾选。
- 定向测试与全模块 `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` 全绿。
- `./scripts/check.sh` [59/59]，末行 `✅ 本地自检通过！`。
- 不新增迁移/SQL 模板；新增端点有路径影响登记与 Controller 测试。
- Verify 报告如实列出 soft prompt 与班末 gap，不宣称 §11.10 全项完成。
