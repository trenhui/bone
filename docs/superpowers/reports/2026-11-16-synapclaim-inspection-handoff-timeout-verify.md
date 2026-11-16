# 批 4 运行期证据报告 — synapclaim-inspection-handoff-timeout

> 变更：`synapclaim-inspection-handoff-timeout`（抽检打回闭环 / 冲正闸门 / 代理区间 / 转交留痕 / 超时回池 / 抽检比例上屏）
> 工作树：`.worktrees/synapclaim-inspection-handoff-timeout`（base `f329228ad`）
> 执行配置：`executing-plans / tdd / thorough / subagent_dispatch=null`（沿用批 1，用户裁定）
> 唯一口径：`docs/superpowers/plans/2026-11-15-synapclaim-inspection-handoff-timeout.md`（plan-revision=4）

## 0. 结论摘要

13 项任务全部落地，判据以实测数值为准（不接受「应该通过」）。变异验证证明新增判据有判别力（正向绿 + 反向红 + 互补变异绿），
非退化实现。全量门禁结果见 §3。

## 1. 逐项证据（对应 tasks.md 13 项 / task-ref）

### 1.1 数据地基迁移（task 8c8b7549）
- `scripts/migration/0126_synap_claim_inspection_handoff.sql`：新建 `t_claim_inspection_action` / `t_claim_user_agent` /
  `t_claim_task_transfer` 三张 append-only 事实表。
- 代理唯一性：`active_key = IF(state='ACTIVE', principal_id, NULL)` 生成列 + `UNIQUE(tenant_id, active_key)`，
  避开 MySQL 生成列禁 `NOW()` 的限制。
- 末尾 `sp_verify_0126` 用 information_schema 强断言：三表关键列（13/12/13 列）、4 个业务索引、唯一键、生成列；
  `MESSAGE_TEXT` 均 < 128 字符。
- **审计列补全（本批实测发现并修复的隐患）**：实体继承 `TenantAbstractEntity`，SDK 默认 INSERT（`BatchInsertBuilder`/
  `UpsertBuilder`）按实体全部列（含 superclass 的 `created_at/created_by/updated_at/updated_by/deleted`）生成 SQL。
  初版三表缺 `updated_at/updated_by/deleted`（transfer 表连 `created_by` 也缺），仓储 insert 会报 `Unknown column`。
  对照组 `settle_funding_event`（同为 append-only + 同基类）有完整 6 审计列，印证该惯例。已补全并与 bone-init.sql 同步。
- `python3 scripts/check-migration-manifest.py`：**OK，133 文件 / 126 可执行脚本 / order 1..126 连续 / 重号组 9 已登记**。

### 1.2 清单登记 + bone-init.sql + 实体列对齐（task f70e5081）
- `migration-manifest.json` order=126 登记；`bone-init.sql` 同批补建表块（三表）。
- `python3 scripts/check-ddl-doc-sync.py`：DDL 真源 177 表 / 规范 §2 清单 177 表，一致。
- `python3 scripts/check-ddl-required-columns.py`：RC=0（缺列项均为已登记 by-design）。
- `mvn -o compile`（跨模块）：RC=0。

### 1.3 三仓储接口 + SQL 模板（task 66abc568）
- 新增 `ClaimInspectionActionRepository` / `ClaimUserAgentRepository` / `ClaimTaskTransferRepository`，
  只声明查询方法（写入复用 SDK `insert`，不声明 update/delete ⇒ append-only 语义）。
- 4 个 SQL 模板：`findByClaimId`(inspection_action)、`findByPrincipal`/`findActiveByPrincipal`(user_agent)、
  `findByTaskId`(task_transfer)；另 `findExpiredAssignedAllTenants`(task, ALL scope)。
  首行 SELECT 关键字、模板内无注释、`/*bone:tenant*/` 锚点 + `deleted=0`（ALL 模板除外）。
- `python3 scripts/check-sql-entity-column-coverage.py`：受检 58 模板，**OK 无缺口**。
- `mvn -o compile`：RC=0（启动期无 TemplateNotFoundException 的静态前提）。

### 2.1 打回处置留痕 + 重办任务（task dd954de3）
- `InspectionDispositionService.dispose()`：BACK + 开关开 → `ClaimTaskApplicationService.openForClaim(INSPECTION)` 生成重办任务，
  `action.linkTask()` 回填 taskId，落 append-only 留痕。
- 测试 `unpaidBackGeneratesReworkTask`：**绿** —— 断言 view.taskId=9001、DB 留痕 taskId=9001、reversalVerified=1。

### 2.2 已支付冲正前置闸门（task fd7b3808）
- 判定只认资金事实（结算单当前阶段 `PAID`），不接受客户端声明；已支付未冲正打回 → 409 `SC_CLAIM_REVERSAL_REQUIRED`，
  写入期硬拒、不落留痕、不生成任务、不改案件状态。
- 测试 `paidWithoutReversalRejected`：**绿** —— 断言抛码正确 + `verify(insert, never)` + `verify(openForClaim, never)`。
- 变异验证见 §4。

### 3.1 代理生效区间与唯一性（task 212ee9cd）
- `UserAgentService`：注册事前查 `overlaps()` 双向区间重叠 + 生成列唯一键并发兜底；`effectiveAgent` 按 validFrom/validTo 双判断；
  `deactivate` 改 state 释放唯一名额（不删行）。
- 测试 3 项**全绿**：`overlappingActiveAgentRejected`（重叠 409 不落库）、`nonOverlappingAllowed`（不同时段放行）、
  `outsideIntervalNotEffective`（区间外 null、区间内 agentId）。

### 3.2 转交留痕 append-only（task 150f9d18）
- `TaskTransferService.transfer()`：`ClaimTask.transferTo()` 改当前归属投影 + `ClaimTaskTransfer.insert` 追加责任事实。
- `ClaimTaskTransferRepository` 只暴露 insert 与按任务回溯；实体无改字段方法、无 `@Version` ⇒ 历史不可更新。

### 3.3 超时回池扫描（幂等 + 监控计数）（task a7db7aea）
- `ClaimTask.requeueIfExpired()`：仅 ASSIGNED + `claimedAt==null` 回池，重复扫描返回 false；`@Version` 乐观锁兜底并发。
- `TaskRequeueService` 捕获 `OptimisticLockingFailureException` → 幂等跳过（不失败）。
- `TaskTimeoutRequeueJob`：复用 `ScheduleLockService` 单写者 + 全租户扫描 + 逐行 `TenantContextRunner.runAs`；
  Micrometer Counter `claim.task.timeout.requeue.total` 按 outcome=hit/requeued/skipped/failed 分计数。
- 测试 3 项**全绿**：`assignedUnclaimedRequeued`、`repeatedScanIsIdempotent`（重复扫描 update 仅 1 次）、
  `processingTaskNotRequeued`（在办任务不回池）。

### 4.1 抽检比例迁入 t_feature_toggle 并上屏（task dac81d36）
- 后端：`InspectionSamplingJob` 从 `@Value` 写死改为按租户读 `FeatureTenantToggleService.inspectionConfig()`（已在前序提交落地）；
  新增 `GET /review/inspection-handoff/sampling-config` 返回当前生效比例 + 未生效项。
- 前端：`InspectionWorkbenchPage.tsx` Alert 文案由硬编码「默认 5%」改为读回值；配置部分未生效时另出 warning Alert。
  `SamplingConfig` 类型无 ID 字段（ID 一律 string 约束不适用），typecheck 通过。
- `npm run typecheck`（bone-claim-app）：RC=0。

### 4.2 DDL 文档一致性门禁验收（task 482b9b7a）
- 用户裁定改验收：`scripts/check-ddl-doc-sync.py` 已存在、已接入 `check.sh`、已用 `re.finditer`。
- 本批正向审计实测：DDL 真源 177 表 / 规范 §2 清单 177 表，一致（含 package-info.java 类假红回归由既有 finditer 判据覆盖）。
- 变异验证：见 §4（门禁判别力）。

### 4.3 INSPECTION_ACTION 只登记模式（task 3a59a161）
- `FeatureTenantToggleService.isInspectionActionEnabled()`（已在前序提交落地）；处置服务在开关关闭时只落留痕
  （featureEnabled=0），不生成任务、不改案件状态、不查资金。
- 测试 `toggleOffRegistersOnly`：**绿** —— 断言 featureEnabled=false、taskId=null、`verify(openForClaim, never)`、
  `verify(settlementRepo, never)`、DB 留痕 feature_enabled=0。

### 5.1 全量门禁运行期证据（task 24b65510）
- 见 §3。

### 5.2 变异验证（task 54ea429e）
- 见 §4。

## 2. 测试汇总

| 范围 | 结果 |
|---|---|
| 新增 3 服务测试（处置/代理/回池） | 10/10 绿 |
| 开关服务测试（含批4 fail-closed 3 项） | 12/12 绿 |
| synap-claim-app 全量单测（-am） | **1490 run, 0 failures, 0 errors, 7 skipped**（含本批新增 13 项） |
| ArchUnit（ArchitectureTest） | 28/28 绿 |
| 前端 bone-claim-app typecheck | RC=0 |

最终重跑日志：`/tmp/b4-final-tests.log`（`MAVEN_EXIT=0`、`BUILD SUCCESS`）。

## 3. 全量门禁

- `./scripts/check.sh`：**CHECK_EXIT=0，末行「✅ 本地自检通过！」，[59/59] 全绿**（日志 `/tmp/b4-final-check2.log`）。
  该次为联调两处缺陷（裸 String 响应 / `INSPECTION_ACTION` fail-open）修复后的最终重跑，未复用此前基线绿。
- 首次跑曾在 `[40/59] 迁移并入区块与活库一致（sync-init-from-live --check）` 判红：
  `bone-init.sql` 托管区块含 0126 三表，但 `scripts/sync-init-from-live.py` 的 `MERGED_SOURCES` 归档清单漏登记这三表
  （与 0089/0101/0122/0124/0125 完全同款的历史坑）。修复：按"归档只增不改"补登记三表 → 官方脚本 `--check` 绿。
- 另：联调库（bone）原本未应用 0126，导致活库无三表；已在联调库落地 0126 三表结构（含完整审计列），
  使活库与 bone-init.sql 一致——这也是批4端点联调写库的必要前提。
- 日志中 `[42/59]` 的「❌ 种子已过期 / 空清单必须判红」等输出是门禁脚本自身单元测试的**负向探针 fixture**
  （紧跟 `OK: 种子齐备`），非步骤级红点；判据以 exit 0 + 末行「✅ 本地自检通过！」为准。
- 本批新增判据均**重跑**全量门禁（未复用基线绿）。

## 4. 变异验证（判据判别力唯一证明）

| 变异 | 操作 | 判据用例 | 结果 |
|---|---|---|---|
| M1 冲正闸门失效 | `hasUnreversedPayment` 恒 false | `paidWithoutReversalRejected`（正向绿用例本应转红） | **转红**：Expecting throwable but was none ✅ |
| M2 幂等前置失效 | `requeueIfExpired` 前置条件恒 false | `repeatedScanIsIdempotent`（正向绿用例本应转红） | **转红**：Expecting false but was true ✅ |
| C1 互补变异（排除退化） | `hasUnreversedPayment` 恒 true | `unpaidBackGeneratesReworkTask`（本应转红） | **转红**：抛了不该抛的 REVERSAL_REQUIRED ✅ |

三个变异均已恢复，恢复后 10/10 绿、无残留 `MUTANT` 标记。M1（恒 false）与 C1（恒 true）方向互补，
证明闸门是双向判别的真实判据，而非「永远返回同一值」的退化实现。

## 5. 同步义务与造数

- `python3 scripts/check-feature-data-impact.py --staged --strict`：RC=0
  （claim 域数据契约变更已同步 `scripts/e2e/synap-claim-inspection-handoff-e2e.py`，C1 coverage 派生段已 `--write-map` 重生成）。
- E2E 脚本覆盖 E1 抽检比例上屏 / E2 代理登记+重叠 / E3 区间外不生效 / E3c 代理失效 / E4 只登记模式 / E5 转交留痕，
  判定均为「接口状态 + MySQL 第二证据源」。
- 提交前 pre-commit 端点影响门禁曾判 6 个端点「零覆盖」：根因是脚本只写 `BASE` 再运行时拼接，扫描器看不到完整路径字面量；
  改为每个端点写全路径并补 `deactivate` 真实用例后，`python3 scripts/e2e/impact.py --staged --strict` RC=0，
  6/6 端点均被该 E2E 覆盖（未使用 `BONE_E2E_IMPACT=off` 绕过）。

## 6. 前后端联调（真实服务，非 mock）

重建含批4端点的 fat-jar（JDK21，`Started ClaimApplication in 3.5s`），经网关 8888 完成联调：

### 6.1 批4 E2E（`scripts/e2e/synap-claim-inspection-handoff-e2e.py`）
**8/8 PASS**（接口状态 + MySQL 第二证据源）：
- E1 抽检比例上屏：`GET sampling-config` 200，`samplingRatePercent=5`（基线），与配置读回一致。
- E2 代理登记+重叠：登记落 ACTIVE 行；重叠区间 → 409 `SC_CLAIM_AGENT_CONFLICT`（错误码从 `data.errorCode` 读）。
- E3 区间外不生效 / 区间内生效：区间外 `agentId=null`，区间内 `agentId=agent-A`。
- E3c 代理失效：POST `agents/{principalId}/deactivate` → DB `state=INACTIVE`（不删行），原区间内再查 `agentId=null`。
- E4 只登记模式：开关关闭下 BACK 处置 → `featureEnabled=false`、`taskId=null`，DB 留痕 `feature_enabled=0`，不发重办任务。
- E5 转交留痕：转交后 `t_claim_task_transfer` 追加一行（事后恢复任务原归属并清理测试留痕）。
- 脚本末尾清理 E2/E3c/E4 产生的 append 事实（联调库非生产），可重复运行。
- 最终重跑日志 `/tmp/b4-e2e-final.log`：`E2E_RC=0`、8/8 PASS（`all=True`）。

### 6.2 联调发现并修复的两个真实缺陷（E2E 的价值实证）
1. **`ApiResponse.success(String)` 重载陷阱**：`GET agents/effective` 原返回裸 String，命中 `success(String message)` 重载——
   值进了 `message`、`data` 为 null，前端取不到代理人。改为包 `AgentEffectiveView(agentId)` DTO 走 `success(T data)`。
2. **`INSPECTION_ACTION` fail-open**：原实现复用 `isFeatureAllowed(tenantId, true, key)`，未配置时继承全局开启 ⇒ 未配置也能发重办任务，
   违反资金/案件状态闸门 fail-closed 原则。改为独立实现：未配置/未 ENABLED/状态非法一律关闭（只登记）；
   补 3 个单测钉住（未配置→false / 显式 ENABLED→true / 全局关→false）。

### 6.3 前端 UI 巡检（批4独立 shell + claim-app dev server）
因共享 shell :3000 / claim-app :3013 指向主树旧构建，在批4工作树起独立 shell(:3001)+claim-app(:3014)
（`VITE_CLAIM_APP_ENTRY=:3014`）后巡检 8 条理赔路由：
- 8/8 渲染正常（`thin=false`、`hasError=false`），截图在 `/tmp/claim-ui-screens-b4/`。
- **`/claim#/inspection` 顶部 Alert 显示「当前 5%」**（非旧硬编码「默认 5%」），证明抽检比例上屏真实生效——
  页面值与后端 `/sampling-config` 读回值一致。
- console 仅 antd `destroyOnClose` 废弃告警（来自 `AiSuggestionCard.tsx`，本批未改的基线告警），无真实 error。

## 7. 错误码四处齐

新增 3 码：`SC_CLAIM_REVERSAL_REQUIRED`(409) / `SC_CLAIM_AGENT_CONFLICT`(409) / `SC_CLAIM_INSPECTION_ACTION_INVALID`(400)，
四处齐：`ClaimErrorCodes` 常量 + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端 zh-CN/en-US 语言包 + `doc/architecture/Bone-错误码登记.md` §6。
- `check-i18n-sync` / `check-error-code-landing` / `check-error-code-duplication`：均 RC=0。

## 8. Comet 流程诚实终态（Verify 收据死锁）

- Build 已由 CLI 正式完成：13/13 `task-complete`、`complete-build` 成功；`comet check run ... build -- ./scripts/check.sh`
  返回 `exitCode=0`，Run 推进到 `revision=42`（phase=verify）。
- Verify 报告已提交，`full.verify.run` 收据 `status=succeeded`；随后 `comet guard ... verify --apply -- bash .../verify.sh`
  真实执行了独立验证脚本，日志末尾为 `ALL CHECKS PASSED — synapclaim-inspection-handoff-timeout verify`。
- 但 Guard 在写收据时被 Runtime 的 safe-delete 批量阈值拦截（`SAFE_DELETE_BULK_CONFIRM_REQUIRED`，RC=70）。
  只读 `comet state check ... verify --recover` 与 `comet runtime dispatch inspect` 均确认：动作 `:18`
  仍 `status=running`、`receipts=[]`、`outcome=null`，磁盘 check record 停在 `status=started`，
  缺 exitCode / 输入快照 / 哈希等完整回执字段。
- 因此 **不标记 unknown、不声明 not-executed、不伪造成功收据、不手改 `comet-state.yaml` / checkpoint**；
  Comet Run 停在 `phase=verify`、`revision=46`、action `:18` 的诚实终态。代码与验证证据已通过 Git 交付，
  但本 change 未进入 Archive；如需程序化归档须由 Original Executor 补回完整回执后按官方恢复流程处理。
