# 审核疲劳防控 Verify 证据（synapclaim-review-fatigue-controls）

- 变更：`openspec/changes/synapclaim-review-fatigue-controls`
- 日期：2026-11-17
- 基线：`7dcae54d0`（check.sh 59/59）
- 范围：单班超量候选不可用 + soft prompt；零 DDL、不碰金额/拒赔/支付

## 1. 真实性边界（先写清楚没做什么）

- **班末硬排除未实现**：服务端没有可信班次结束事实源。评估端点的 `minutesToShiftEnd`
  由调用方传入，只影响 `sensitiveEligible` 提示，**不阻断派单、不产生转派动作**；验收登记 gap，
  不宣称 §11.10 全项完成。
- **连续时长是本机会话近似值**：前端用 `sessionStorage` 记录工作台会话起点并计算分钟数，
  只是 soft prompt 的输入；服务端不伪造班次、不把客户端声明当作工作量事实。
- **工作量事实只取库内**：`t_claim_task.finished_at + status=DONE + UTC 当日`；
  不新增表、字段、迁移或 SQL 模板。
- 金额阈值只用于提示，不进入理算、拒赔、结算、支付。

## 2. 实现与配置消费证据

| 判据 | 落地位置 | 证据 |
|---|---|---|
| 配置唯一解析真源 | `domain/model/config/ReviewFatigueConfig.java` | 四项基线 120/120/30/10000；缺键/非整数/越界/未知键逐项回落并在 `rejected` 登记 |
| fail-closed 消费 | `FeatureTenantToggleService.reviewFatigueConfig(tenantId)` | 无记录 / 未 ENABLED / 空 JSON / 坏 JSON 一律基线；坏 JSON 记 `$json` |
| 真实工作量取数 | `ClaimTaskRepository.countFinishedTodayByAssignee` | 显式 tenant + assignee + `DONE` + `finishedAt >= UTC dayStart`；无 SQL 模板 |
| 超量候选不可用 | `FatigueAwareEligibilityPort` → `TaskAssigner` | `finishedToday >= maxPerShift` 即 false → 既有 `ALL_INELIGIBLE` 回落入池 |
| 异常 fail-closed | 同一包装器 + 应用层取数 lambda | 异常/return null/负计数一律不可用，不外溢到收单链 |
| 只读评估 | `GET /api/v1/review/fatigue-guard/evaluation` | 只返回 DTO；E2E 直查任务行数前后不变 |
| 前端 soft prompt | `ReviewWorkbenchPage.tsx` + `services/reviewFatigue.ts` | 读取评估端点显示超量/休息提示与配置拒绝项；不清空队列、不禁用提交 |

## 3. 测试与门禁（真实执行结果）

- 定向单测：`ReviewFatigueConfigTest` 8 + `FatigueAwareEligibilityPortTest` 6 +
  `ReviewFatigueGuardServiceTest` 7 + `ReviewFatigueGuardControllerTest` 2 +
  `FeatureTenantToggleServiceTest` 16 + `ClaimTaskApplicationServiceTest` 11
  = **50/50 PASS，0 failures，0 errors**。
- 全模块：`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test`
  = **1520 run, 0 failures, 0 errors, 7 skipped**（基线 1490 → 新增 30）。
- 完整门禁：`./scripts/check.sh` 在 `.worktrees/_dev-merge-tmp` 执行，
  **RC=0、[59/59]、末行「✅ 本地自检通过！」**；未关闭任何门禁、未用绕过开关。
- 前端：`npm run typecheck -w bone-claim-app`（tsc --noEmit）**RC=0**。
- E2E 影响：`python3 scripts/e2e/impact.py --staged --strict` **RC=0**，
  新端点 `GET /api/v1/review/fatigue-guard/evaluation` 覆盖 1/1。
- 造数同步：`python3 scripts/check-feature-data-impact.py --staged --strict` **RC=0**；
  新 E2E 已登记进 `scripts/registry/data-assets.json` 的 claim 域用例。

## 4. 真实前后端联调（网关 8888 + 主服务 8087 + MySQL）

重建 fat-jar 覆盖 `.run-jars/claim.jar`（备份 `claim.jar.bak-20261117-before-fatigue`）并重启 8087；
执行 `python3 scripts/e2e/synap-claim-review-fatigue-e2e.py`，**E2E_RC=0，5/5 PASS**：

- E1 评估端点 200，返回四项配置 + `rejected`，不是裸 String（避开 `ApiResponse.success(String)` 陷阱）；
- E2 `continuousMinutes=120` ⇒ `restPrompt=true`（等于阈值即提示）；
- E3 `claimAmount=10000 & minutesToShiftEnd=0` ⇒ `sensitiveEligible=false`（仅提示）；
- E4 只读端点前后 `t_claim_task` 行数 **177 → 177**，无写副作用；
- E5 响应 `maxPerShift=120` 与 `t_feature_toggle` 读回（`REVIEW_FATIGUE_GUARD` 无配置）一致。

## 5. 变异验证（判据有判别力）

| 变异 | 期望 | 实测 |
|---|---|---|
| `FatigueAwareEligibilityPort`: `done < max` → `done <= max` | 超量边界用例变红 | RC=1，红在 `atLimitIsIneligible` / 分配入池用例 |
| `ReviewFatigueGuardService`: `continuousMinutes >= rest` → `>` | 阈值边界用例变红 | RC=1，红在 `restPromptBoundary` |
| 恢复源码后重跑 | 全绿 | RC=0；两文件与索引逐字一致 |

## 6. Comet 收据

- 12/12 任务由 CLI `task-complete` 记录；Build 与 Verify 的阶段动作只走 CLI/Guard。
- Build check 权威回执：`comet check run synapclaim-review-fatigue-controls build --cwd .worktrees/_dev-merge-tmp -- ./scripts/check.sh` **RC=0**，`scope=build / tier=full / exitCode=0`，内容哈希 `0c99aad88a917daf712cf624e8bc36e733c3899cb87f7d55fb756cca4518d6b1`，输入前后快照一致。首次尝试因工作树里的 node_modules 符号链接被 Comet 在派发前拒绝，recover 证实 command=null / record=missing / receipts=[]，按官方 mark-unknown + retry(not-executed) 后重跑成功；未伪造收据、未手改受管状态。
- Verify Guard `full.verify.check :18` **按上述诚实终态处理**：Runtime 日志 `aa2d4d0a-94d6-4785-8348-b746aba57b96.log` 完整记录 `[1/6]` 至 `[6/6]` 与 `ALL CHECKS PASSED`，但 action record 停在 `status=started`、`result=null`、`outcome=null`（recover 显示 `recordOutcome=null`）。因此**不声明 not-executed、不 cancel、不手改受管 state、不伪造回执**；本批保持 verify/rev38/:18 的诚实终态，未程序化 Archive。

## 7. 结论

本批交付了**可证伪的单班超量准入闸门与只读 soft prompt**，配置缺失/非法一律 fail-closed 且可观测；
班末硬排除因缺服务端班次事实源仍为 gap。P1「AI 不碰钱」、三档分诊与 G0–G4 门禁未放宽，
不得据此宣称 L3。
