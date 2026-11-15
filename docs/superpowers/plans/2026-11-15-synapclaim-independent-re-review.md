---
change: synapclaim-independent-re-review
design-doc: openspec/changes/synapclaim-independent-re-review/design.md
base-ref: 08dd98997762db523696bd9dbfba0a456cf641a8
---

<!-- comet-task-authority: openspec/changes/synapclaim-independent-re-review/tasks.md -->

# Build Plan — 批 3 · 独立复审（实施口径唯一真源，2026-11-15）

> **唯一口径声明**：本 plan 是本 change 实现口径的**唯一真源**。`tasks.md` / `design.md` 已在 Design 阶段批准并被 Runtime 以内容哈希锁定为已接受证据，故本轮**不修改**；其中与当前仓库基线不一致的表述（迁移基线止于 `order=123`、`0124` 仅 staged、并行会话「在途」、按「从零新建」描述已实现能力等）**一律不采信**，以本 plan 第 1、4 节为唯一口径。任务完成状态一律以 `tasks.md` 为准，本 plan 不复制 checkbox 账本，仅以 `comet-task-ref` 映射。
> 依据：2026-11-15 四批范围重核（逐批证据与逐条结论已完整并入本 plan 第 1、4、6 节）+ 用户 2026-11-15 三项裁定。本 plan 自洽，不依赖任何项目外临时文件。

## 1. 基线事实（2026-11-15 实证，校正 design 中的过时口径）

- `dev` HEAD = `285827275`「归集并行会话在途资产并同批同步 E2E 投影契约」，主树 `git status --porcelain` = **0 在途**。`ClaimWorkbenchController` / `ClaimSearchApplicationService` / `AiFeedbackRules` 经 `git cat-file -e HEAD:<f>` 确认 IN HEAD。
- `./scripts/check.sh` 实跑 13 分钟：`CHECK_EXIT=0`、`[59/59]`、末行「✅ 本地自检通过！」。日志中的 `[FAIL]` / `❌` 全部落在 `[42/59]` 种子校验**负向探针**内部，非步骤级红点 —— 判据看 exit 0 + 末行。
- `scripts/migration/migration-manifest.json`：`entries=131` / `executableCount=124`，末尾 `order=124`（`0124_synap_claim_ai_feedback_event.sql` 已入库并登记）。⇒ **新迁移从 0125 起按批次顺序分配**；脚本 + 清单 + `bone-init.sql` 同批入库；已发迁移（含注释）不回改（sha256 journal）。
- `ClaimWorkbenchController` 挂 `/api/v1/claims/**`，已有 `GET /search`（keyword + limit 跨键检索，**无 total / 分页**）与 3 个 AI 回流端点，权限 `claim:claims:read` / `claim:review:write`。
- `ClaimErrorCodes.java` / `ClaimErrors.java` 已被并行会话改动（新增 `CLAIM_AI_FEEDBACK_CONFLICT`→409、`CLAIM_SEARCH_KEYWORD_INVALID`→400），台账 `doc/architecture/Bone-错误码登记.md` §6 第 622/623 行已登记。⇒ 新增错误码须**基于 HEAD 追加**，且三处必齐（常量 + `DEFAULT_HTTP_STATUS` + 前端两语言包 + 台账 §6）。
- `t_claim_review_record` 已存在（`0101_synap_claim_phase1_record_tables.sql` 建表，append-only，含 `uk_claim_role_reviewer`），勿重复建同义表。
- `scripts/check-ddl-doc-sync.py` 已存在且已接入 `check.sh:840`（`[55/59]`），源码已用 `re.finditer`。

## 2. 用户裁定（本 plan 据此落地）

1. **「全部照办」以本 plan 承载，不改已批准文档**：`design.md` / `tasks.md` 在 Design 阶段已批准，被 Runtime 以内容哈希锁定为已接受证据。可逆实测证实：给任一文件追加/改写一行即触发 `CLASSIC_ENTRY_CHECK_FAILED`（`Classic Design document changed after accepted evidence` / `Classic Design requirements changed after approval`），Build 入口 `5/6 blocked` 并要求 `revise-design` 退回 Design 重新批准（连带作废批 1 已批准的 Build 配置）。故本 plan 不触碰这两份文件。
2. **批 1 端点**：复用既有 `ClaimWorkbenchController` 扩展，不另起第二个工作台 Controller；假分页 **两页都修**（`WorkbenchPage` + `ReviewWorkbenchPage`）。
3. **批 4 `4.2`**：确认 `check-ddl-doc-sync.py` 即目标门禁 ⇒ 改为「验证 + 补变异验证」，不重复新建。前端 `constants/errorCodes.ts` 缺登记两个新码却门禁全绿的 **i18n 覆盖盲区单列独立核实**，不并入 4 批。
4. **逐批 Build 配置各自单独确认**，不沿用批 1 的 `executing-plans / tdd / thorough / null`；DDL 迁移属 L3，须架构师审批后才落脚本，**不执行生产库迁移**（L4 禁止 AI 执行）。

## 3. 实施纪律（适用于本 change 全部任务）

- 改公共签名 / 新规前先写**会红的用例**（TDD）；「全绿」不等于有判别力，新增判据必须做变异验证（含互补变异）。
- 新增仓储方法必须同批交 `sql/.../<Repo>/<method>.sql`（否则启动期 `TemplateNotFoundException`）；SQL 模板首行必须是语句关键字、模板内不放注释、SELECT 列对齐活库；仓储方法名禁 `insert/update/delete/persist/flush/merge/exists`。
- 迁移末尾 `information_schema` 强断言 + `SIGNAL`，`MESSAGE_TEXT` ≤ 128 字符；收紧非法值须「写入期拒绝 + 读取期兜底保留」，并配正向钉子。
- 实体 `@Column` 与活库 `information_schema` 逐列求差；持久化只用 bone-metadata-sdk；禁 `SELECT ... FOR UPDATE`（用 `@Version`）。
- 门禁结论只认 `exit 0 + 末行「✅ 本地自检通过！」`；日志里大量 ❌ 是负向探针。本批新增判据须**重跑**全量门禁，不得复用本轮基线绿。
- 关键产物完成即 `git add`（并行会话 checkout/clean 会扫掉未暂存文件）；提交用 `git commit -F <msgfile> --only -- <paths>` 圈定自己的路径。
- 若后续确需修改 `design.md` / `tasks.md` 文本，必须先走 `comet state revise-design <change> --expected-revision <n>` 并由用户重新批准，不得用 edit-review 非语义白名单伪造证据。

## 4. 本批范围重核结论

9 条全部仍需新建；状态 43/46、claim:re-review:write、RE_REVIEW、同人不可自审、复审队列在 synap-claim 仍 0 命中；依赖批 1 任务归属，须批 1 先行。

## 5. 执行配置

**待单独确认**（当前缺 build_mode + tdd_mode，reviewMode=off）。按用户裁定不沿用批 1 批准值，开工前须本批单独 propose-build + decide-build。

## 6. 任务映射（逐条口径；「新建」= 仍需编码，「补齐对接」= 复用既有入口扩展，「验收/验收留证」= 已实现，只跑探针与留证）

- 1.1 登记状态 43/46 并与 44 并列（状态字典 + 状态机流转），验证三状态共存用例与「合并写法」变异用例判红 【新建】— 状态 43/46 与 44 并列（状态字典 + 状态机流转）。t_claim_review_record **已存在**（0101），本任务是状态字典与流转，**不新建表**；先确认 0101 现有列可承载 43/46，勿建同义表。 <!-- comet-task-ref:f095d12b-8718-49e2-a82c-c623f01bfc25 -->
- 1.2 新增权限码 `claim:re-review:write` 并登记字典，验证无权限写入被拒、有权限写入成功 【新建】— 新增权限码 claim:re-review:write 并登记字典；无权限写入被拒、有权限写入成功。配置/作业权限码分离。 <!-- comet-task-ref:099a5b48-f355-434e-ac59-88a85beeaa43 -->
- 2.1 新增错误码常量并补 `ClaimErrors.DEFAULT_HTTP_STATUS`，验证应用启动不抛、HTTP 状态可解析 【新建】— 错误码常量 + ClaimErrors.DEFAULT_HTTP_STATUS（漏则类加载即抛），基于 HEAD 追加，不覆盖并行会话已改的两码。 <!-- comet-task-ref:e2ebf0e1-534b-42a4-8d03-a04ab1c35ce8 -->
- 2.2 补前端中英语言包与错误码台账 §6 登记，验证 `_check_error_code_i18n.py` E1–E5 全绿且漏映射会判红 【新建】— 前端中英语言包 + 台账 §6 登记；_check_error_code_i18n.py E1–E5 全绿且漏映射会判红（需先留意该门禁已知覆盖盲区，见批 2 4.2）。 <!-- comet-task-ref:24bd4a72-b48a-404d-9bb9-91ba03c8125a -->
- 3.1 实现复审队列（按持权人与批 1 任务归属过滤 + 租户隔离），验证跨租户不可见且只出现 43 状态案件 【新建】— 复审队列按持权人 + **批 1 任务归属**过滤 + 租户隔离 ⇒ 依赖批 1 先行，顺序不可颠倒；跨租户不可见、只出现 43 状态案件。 <!-- comet-task-ref:0841d244-b80f-4f6a-ba2b-9dfec33e6e5c -->
- 3.2 实现同人不可自审硬约束，验证初审人提交独立复审被拒且状态不变 【新建】— 同人不可自审硬约束；初审人提交独立复审被拒且状态不变。 <!-- comet-task-ref:62791a3b-5e6b-470e-96f5-f6f68b4a0622 -->
- 4.1 新增 `RE_REVIEW` 开关与关闭后零流量行为，验证关闭时无 43/46 流转且 44 流程不受影响 【新建】— RE_REVIEW 开关与关闭后零流量行为；关闭时无 43/46 流转且 44 流程不受影响。 <!-- comet-task-ref:66f96900-17d3-4d82-985c-f7f0f7de259b -->
- 4.2 跑仓库原生全量门禁并留存运行期证据，验证报告写入 `docs/superpowers/reports/` 且红点归零或明确归因为零交集基线项 【验收】— 重跑全量门禁并留运行期证据到 docs/superpowers/reports/，红点归零或明确归因为零交集基线项。 <!-- comet-task-ref:436e80d4-c709-4de1-afbd-0d694ea9d89e -->
- 4.3 变异验证职责分离与状态并列两处判据确实会转红，验证门禁有判别力 【验收】— 变异验证职责分离与状态并列两处判据确实转红，含互补变异。 <!-- comet-task-ref:15f9a000-f186-4dba-95be-9e39ede9beeb -->

## 7. 停止条件

- 本 plan 只登记实施口径；**未调用 `submit-plan`、未写实现代码、未勾选任何 task**。后续 `submit-plan` 与逐批 Build 配置须经用户过目 / 单独确认。
- 若实现中发现与第 1 节基线冲突（例如迁移号被并行会话占用），先停下来重新取证并报用户，不得自行改号或回改已发迁移。
- 任何需要修改已批准 `design.md` / `tasks.md` 的语义变更，须走 `revise-design` 并由用户重新批准；不得绕过证据哈希。
