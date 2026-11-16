# 运行期证据报告：synapclaim-independent-re-review（批 3 · 独立复审）

- 日期：2026-11-16
- 阶段：Build 收尾（任务 1.1–4.3）
- 绑定分支：`comet/synapclaim-independent-re-review`｜工作流：classic｜产物语言：zh-CN
- 构建配置：`build_mode: executing-plans`、`tdd_mode: tdd`、`review_mode: thorough`（由 Comet build.confirm 批准，plan §5「待单独确认」为 design 阶段遗留文字，实际以 Comet configuration 为准）
- 环境：本机 MySQL 8（`bone` 库）、JDK 21、Maven 3.8.6、Node 前端 worktree 内通过符号链接复用批 1 worktree 的 `node_modules`

## 0. 数据与依赖基线（实测）

- `dev` HEAD `4d9579f2e`（已合并批 1 任务分配工作台，四方 SHA 一致）；本 worktree HEAD 基于其 merge（`2f262daa3`）+ Build 配置提交（`8e05e0d1d`）。
- plan frontmatter 机检三字段：`baseline-head=4d9579f2e`、`baseline-manifest-order=125`、`plan-revision=4` —— 与真源 MATCH（提交门禁输出确认）。`baseline-gate=59/59` 为 advisory 人工复核快照，不机检。
- 迁移清单：`python3 scripts/check-migration-manifest.py` → `132 个文件 / 125 个可执行脚本 / order 1..125 连续 / 重号组 9 个已登记 / 已下线 0 / kind ddl=47 dml=15 doc=5 mixed=62 tool=2 verify=1`，RC=0。
- 本批不新建迁移（状态字典复用既有 `t_claim_review_record`，权限码用 bootstrap seed 大 id 段 `9153006` 注册，未新建 DDL）。

## 1. 任务逐条判据（9/9）

| 任务 | 判据 | 实测 | 结论 |
|---|---|---|---|
| 1.1 状态 43/46 与 44 并列 | 三状态共存 + 「合并写法」变异判红 | `ClaimCaseReReviewTest.threeStatesCoexist` PASS；变异 M1（46→44）→ 11 测试 1 error 判红 | PASS |
| 1.2 权限码 `claim:re-review:write` | 字典登记 + 无权限被拒/有权限成功 | `ClaimDictLabels.PERMISSION` + 前端 permissions.ts + dicts snapshot/generated 四处齐；三个端点均 `@PreAuthorize("hasAuthority('claim:re-review:write')")` | PASS |
| 2.1 错误码常量 + HTTP 状态 | 启动不抛、HTTP 可解析 | `SC_CLAIM_REREVIEW_NOT_FOUND→404`、`SC_CLAIM_REREVIEWER_CONFLICT→409` 入 `ClaimErrors.DEFAULT_HTTP_STATUS`；全量门禁类加载通过 | PASS |
| 2.2 双语包 + 台账 §6 | `_check_error_code_i18n.py` E1–E5 全绿 | `后端常量 59 / HTTP 59 / zh-CN 284 键 / en-US 284 键`，`常量↔HTTP 双向齐备 + 理赔码双语 100% + 中英零差集`，RC=0；两码各 5 处命中（常量/HTTP/两语言包/台账 §6） | PASS |
| 3.1 复审队列（持权人 + 租户隔离 + 只出 43） | 跨租户不可见、只出 43 | `IndependentReReviewApplicationServiceTest.queueFiltersByTenantStatusAndAssignee` PASS（钉 tenantId 传参 + status=43 + assignee 过滤）；变异 M4（持权人过滤反转）判红 | PASS |
| 3.2 同人不可自审 | 初审人提交被拒、状态不变、不落库 | 领域 `selfReReviewRejected`/`selfReReviewGuardStrict` + 应用 `selfReReviewRejectedWithDistinctCode` PASS；映射可辨认码 `SC_CLAIM_REREVIEWER_CONFLICT`；变异 M2（删守卫）→ 3 failures 判红 | PASS |
| 4.1 RE_REVIEW 开关 fail-closed | 关闭无 43/46 流转、44 不受影响 | `featureOffBlocksSubmit` PASS（switch off → BizException + 状态不变 + `never().saveClaim`）；`requireFeatureEnabled()` 在 submit/complete 双入口前置；变异 M3（恒放行）判红 | PASS |
| 4.2 原生全量门禁 + 运行期证据 | `check.sh` exit 0 + 末行绿 + 报告落盘 | `CHECK_RC=0`、末行 `✅ 本地自检通过！`、步骤级真实 ❌ 为零；本报告即运行期证据 | PASS |
| 4.3 变异验证判别力 | 每新增判据 1 正向 + 互补变异 | 4 变异判红（M1–M4）+ 1 互补判绿（C1）；见 §3 | PASS |

## 2. 全量门禁（判据真源）

- 命令：`./scripts/check.sh`（`/tmp/batch3-check-final.log`）
- 结果：**`CHECK_RC=0`，末行 `✅ 本地自检通过！`**
- 59/59 步骤全过；日志内 `❌ 种子已过期/校验失败/seed-manifest 空清单` 均为 `check-seed-consistency` 的**负向自测探针输出**（紧邻 `self-test 通过`，每条后跟 `OK: 通过`），非步骤级真实红点。
- 修复记录（本轮实测发现的真实红点）：首轮（`/tmp/batch3-check-clean.log`）`CHECK_RC=1`，根因是 `[41–42/59]` 的 C3「功能变更→造数资产同步义务」判红——本批改了 3 个 domain/model 数据契约文件（ClaimCase/ClaimStatus/FeatureToggle），但新增的 E2E 用例 `synap-claim-independent-re-review-e2e.py` 未登记进 `scripts/registry/data-assets.json` 的 claim 域 `cases`，门禁不认作同批同步。按 §3.4 用标准登记方式加入 cases 并 `--write-map` 更新 coverage 派生段后，C3 转绿（`本次已同步: synap-claim-independent-re-review-e2e.py`），重跑全量门禁 exit 0。未使用 acks 豁免、未绕过门禁。

## 3. 变异验证（判别力实证）

证据：`/tmp/b3-mutation-evidence.log`；命令基于聚合器 `mvn -o -f bone-apps/synap-claim/pom.xml -pl synap-claim-app -am test`，每个变异前 `touch` 全部 java 强制重编译（排除增量编译陈旧字节码假红），每个变异后立即从原始副本还原，结束 `git diff` 零残留。

| 编号 | 变异 | 期望 | 实测 |
|---|---|---|---|
| M1 | 状态 46 code 改 44（合并写法） | 红 | `threeStatesCoexist` error，11 测试 1 error，mvn rc=1 ✅ |
| M2 | 删同人自审守卫 | 红 | 3 failures（`selfReReviewRejected`/`selfReReviewGuardStrict` + 应用码测试），rc=1 ✅ |
| M3 | RE_REVIEW 开关守卫改恒放行 | 红 | `featureOffBlocksSubmit` failure，rc=1 ✅ |
| M4 | 队列持权人过滤传固定错值 | 红 | `queueFiltersByTenantStatusAndAssignee` failure，rc=1 ✅ |
| C1 | 异常文案加一字（语义无关） | 绿 | 无 FAILURE/ERROR，rc=0 ✅（证明测试非恒红） |

## 4. 编译 / 启动 / 前端 / E2E 覆盖

- 后端：聚合器 `mvn -o ... -am test` 全绿（领域 7 + 应用 4 = 11 用例）；ArchUnit 门禁随全量门禁 PASS（Controller 走 `TenantPort`、save 配对无违规）。
- 前端（本批改 5 个常量文件：dicts snapshot/generated、permissions、两语言包）：`npm run typecheck` RC=0、`npm run lint` RC=0；`npx vitest run apps/bone-claim-app` → **8 files / 131 tests 全过**。
  - 全仓库 `npm run test` 有 3 例失败，集中在 `bone-extension-app` 的 `deletePlugin`（异常类型断言），与本批零交集——本批未触碰 extension-app，失败文件最近由并行会话提交 `285827275` 改动，归因为零交集基线项。
- E2E 覆盖映射：`scripts/e2e/impact-map.json` 中三个新端点 `GET /claims/re-review/queue`、`POST /claims/{claimId}/re-review/submit`、`PUT /claims/{claimId}/re-review/complete` 的 `cases` 均含 `synap-claim-independent-re-review-e2e.py`；`impact.py --strict` 9/9 通过。

## 5. 错误码四处齐（本次新增两码）

| 码 | 常量 | DEFAULT_HTTP_STATUS | zh-CN | en-US | 台账 §6 |
|---|---|---|---|---|---|
| `SC_CLAIM_REREVIEW_NOT_FOUND` | ✅ 404 | ✅ | ✅ | ✅ | ✅ |
| `SC_CLAIM_REREVIEWER_CONFLICT` | ✅ 409 | ✅ | ✅ | ✅ | ✅ |

i18n 门禁 `_check_error_code_i18n.py`：59 常量 / 59 HTTP / 284+284 键，零差集，RC=0。

## 6. 审阅发现（不在本批范围，提请裁定）

- **复审队列持权人参数**：`reReviewQueue` 端点把 `reviewerId` 作为可选 `@RequestParam`，持权人取 `reviewerId == null ? CurrentOperator.requireId() : reviewerId`。即持有 `claim:re-review:write` 权限者可传任意 `reviewerId` 查询他人待复审队列。设计 §5 方案 C 明确拒绝「队列按人过滤可被直连绕过」，本实现虽在后端按参数过滤且有租户隔离、`complete` 端点另有 `reviewerId == secondReviewerId` 硬校验（防冒充提交结论），但持权人可由客户端指定这一点仍偏离「持权人视角」语义。是否收紧为「一律取 CurrentOperator」，需用户裁定后走独立变更，本次收口不擅改（设计已批准、E2E/单测已覆盖该参数行为）。
- `SC_CLAIM_REREVIEW_NOT_FOUND`（404）已四处齐登记，但当前应用服务查无案件走的是既有 `CLAIM_NOT_FOUND`；该码为预留，未产生行为差异。

## 7. 结论

批 3（独立复审）9/9 任务判据全绿：全量门禁 exit 0 + 末行绿、11 后端用例 + 131 前端用例全过、i18n/迁移清单/E2E 覆盖/变异判别力全部实证。两项审阅发现按红线不在收口阶段擅改，提请用户裁定。
