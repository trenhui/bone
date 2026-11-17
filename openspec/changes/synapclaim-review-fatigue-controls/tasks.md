# Tasks: synapclaim-review-fatigue-controls

## 1. 配置真源

- [x] 1.1 新增 `ReviewFatigueConfig` 纯值对象：解析 `maxPerShift` / `restPromptMinutes` / `shiftEndExclusionMinutes` / `sensitiveAmountYuan`，基线分别为 120 / 120 / 30 / 10000；非法类型、越界、未知键一律回落或登记 rejected，不静默生效。验证：配置单测覆盖正向、缺键、越界、非法 JSON 映射后的 map。 <!-- comet-task:85687aa9-e25b-418e-b92a-6e8bf7178549 -->

## 2. 配置消费

- [x] 2.1 `FeatureToggle` 新增 `FEATURE_REVIEW_FATIGUE_GUARD` 常量；`FeatureTenantToggleService.reviewFatigueConfig(tenantId)` 按 fail-closed 读取，无记录/未开启/解析失败均回落基线并 WARN。验证：服务单测覆盖无记录、关闭、合法配置、非法 JSON。 <!-- comet-task:0c5f5567-60b8-4536-8cb4-bd431ac66da1 -->

## 3. 工作量取数

- [x] 3.1 `ClaimTaskRepository` 新增 default `countFinishedTodayByAssignee(Long tenantId, String assigneeId, Instant dayStart)`，显式带租户、复核人、`status=DONE`、`finishedAt >= dayStart`；不新增 SQL 模板与迁移。验证：仓储/应用测试钉住口径；SQL 模板门禁仍绿。 <!-- comet-task:d22421a0-87f9-4b56-bf4a-d1d18fa92335 -->

## 4. 分配闸门

- [x] 4.1 `ClaimTaskApplicationService.decide` 在调用 `TaskAssigner.decide` 前包裹 `AssigneeEligibilityPort`：仅对 `AssigneeType.USER` 读当日完成量，达到 `maxPerShift` 即判不可用；其它类型保持原判定。验证：超量复核人不被选中，规则回落入池；未超量行为不变。 <!-- comet-task:a4f53325-3c37-4e1c-a9d2-fb20eda52ba3 -->
- [x] 4.2 计数异常/仓储不可用按“候选不可用”fail-closed，不抛出把收单链拖挂，也不把状态未知的人当成可用。验证：异常注入用例断言回落入池且无异常外溢。 <!-- comet-task:86309f14-10b5-4948-a4fc-80c5061e14ce -->

## 5. 软提示评估

- [x] 5.1 新增 `ReviewFatigueDecision` 纯值对象与 `ReviewFatigueGuardService.evaluate(...)`：输出 `overloaded` / `restPrompt` / `sensitiveEligible` 及生效配置；连续时长与班末分钟由调用方传入，服务端不伪造班次事实。验证：阈值边界（等于阈值即提示/超量）、空值与敏感金额边界。 <!-- comet-task:f7c2ad14-1eed-4185-ac33-6dd483552579 -->
- [x] 5.2 新增只读端点 `GET /review/fatigue-guard/evaluation`，入参 `continuousMinutes` / `minutesToShiftEnd` / `claimAmount`，返回评估 DTO；不写库、不改案件状态、不产生转派动作。验证：Controller 单测与端点路径影响登记。 <!-- comet-task:8f4353c8-5bde-492f-b184-f2ee564b58ee -->

## 6. 前端提示

- [x] 6.1 在复核工作台读取评估端点并显示超量/休息 soft prompt；不清空队列、不阻断提交、不替代人工终审。验证：前端 typecheck；若现有数据链路无法在同一变更内可靠接线，则本项转为验收 gap 并在报告说明，不得以静态文案冒充已生效。 <!-- comet-task:1938b56b-cdae-405b-adc0-f303889d0bbb -->

## 7. 测试与门禁

- [x] 7.1 新增/扩展单测覆盖配置解析、软提示阈值、超量回落入池、异常 fail-closed、端点契约；`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` 全绿。 <!-- comet-task:a73275f5-d6d7-4936-a123-16ed21741438 -->
- [x] 7.2 `./scripts/check.sh` [59/59] 全绿，末行「✅ 本地自检通过！」；不关闭任何既有门禁、不使用绕过开关。 <!-- comet-task:eba2c739-cd15-4ee6-bf26-d00e23790cdb -->

## 8. 文档与诚实边界

- [x] 8.1 `AI技术方案-版本历史.md` 新增 v2.11：登记单班超量与软提示已落地、参数口径、零 DDL；明确班末硬排除因缺服务端班次事实源仍未完成，不勾选 §11.10 全项完成。 <!-- comet-task:8a1e91b2-115d-4be9-8372-4170ddccaba0 -->
- [x] 8.2 独立 Verify 报告记录真实测试/门禁结果、端点覆盖、配置消费证据与未落地 gap；Comet Guard 若出现收据死锁，保持诚实终态，不伪造回执。 <!-- comet-task:a6d975eb-76c0-46a5-997f-3ffd6fa4eb22 -->
