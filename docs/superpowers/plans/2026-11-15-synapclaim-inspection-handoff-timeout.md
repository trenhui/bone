---
change: synapclaim-inspection-handoff-timeout
design-doc: openspec/changes/synapclaim-inspection-handoff-timeout/design.md
base-ref: ceded7b69333cb6c0b1dd5ca2cea2d0c9b16bc1f
---

<!-- comet-task-authority: openspec/changes/synapclaim-inspection-handoff-timeout/tasks.md -->

# Build Plan — 批 4 · 抽检与交接超时（实施口径唯一真源，2026-11-15）

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

13 条 = 12 新建 + 1 验收(4.2)；三张表与 INSPECTION_ACTION 开关在迁移/代码中仍 0 命中。

## 5. 执行配置

**待单独确认**（当前缺 build_mode + tdd_mode，reviewMode=off）。按用户裁定不沿用批 1 批准值，开工前须本批单独 propose-build + decide-build。

## 6. 任务映射（逐条口径；「新建」= 仍需编码，「补齐对接」= 复用既有入口扩展，「验收/验收留证」= 已实现，只跑探针与留证）

- 1.1 追加迁移建 `t_claim_inspection_action` / `t_claim_user_agent` / `t_claim_task_transfer`（代理用生成列唯一键），末尾 `information_schema` 强断言 + `SIGNAL`，验证 `check-migration-manifest.py` 通过 【新建】— 三张表 t_claim_inspection_action / t_claim_user_agent / t_claim_task_transfer 在迁移中 **0 命中**；代理用生成列唯一键（MySQL 无 partial unique index）。新迁移号从 0125 起按批次顺序分配；末尾 information_schema 强断言 + SIGNAL。L3 须架构师审批。 <!-- comet-task-ref:8c8b7549-e722-4dd7-ba68-ff6c0e2829c0 -->
- 1.2 清单末尾空位登记 + `bone-init.sql` 同批补建表块 + 实体 `@Column` 与活库求差，验证 M1–M4/M11 全绿且 `mvn -o compile` 通过 【新建】— 清单末尾空位登记 + bone-init.sql 同批补建表块 + 实体 @Column 与活库求差；M1–M4/M11 全绿、mvn -o compile 通过。 <!-- comet-task-ref:f70e5081-1085-4fc7-ac95-fc3347f21f93 -->
- 1.3 三个新仓储接口与对应 SQL 模板同批交付，验证启动期无 `TemplateNotFoundException`、SELECT 列可读回 【新建】— 三个新仓储接口与 SQL 模板同批交付；首行语句关键字、模板内不放注释、SELECT 列可读回；启动期无 TemplateNotFoundException。 <!-- comet-task-ref:66abc568-c909-44be-8f8b-b824582e1d05 -->
- 2.1 实现打回处置 append-only 留痕与重办任务生成，验证处置记录关联任务且不可改写 【新建】— 打回处置 append-only 留痕与重办任务生成；处置记录关联任务且不可改写。 <!-- comet-task-ref:dd954de3-7f11-42a6-9df1-d65364424da3 -->
- 2.2 实现已支付冲正前置闸门，验证未冲正打回被拒、冲正后打回成功且资金流水含冲正事实 【新建】— 已支付冲正前置闸门；未冲正打回被拒、冲正后打回成功且资金流水含冲正事实（账户是投影、流水是事实）。 <!-- comet-task-ref:fd7b3808-04bd-45aa-bd76-407d02386baf -->
- 3.1 实现代理生效区间与唯一性，验证区间外不生效、重叠代理被拒 【新建】— 代理生效区间与唯一性；区间外不生效、重叠代理被拒。 <!-- comet-task-ref:212ee9cd-98be-4ac1-8d77-73786260285f -->
- 3.2 实现转交留痕（append-only），验证历史转交不可更新 【新建】— 转交留痕 append-only；历史转交不可更新。 <!-- comet-task-ref:150f9d18-dd2e-4c93-a9aa-d67fa85191f5 -->
- 3.3 实现超时回池扫描（幂等 + 监控计数），验证重复扫描不重复回池 【新建】— 超时回池扫描幂等 + 监控计数；重复扫描不重复回池。 <!-- comet-task-ref:a7db7aea-4c61-4f09-8da6-f45a1de463b5 -->
- 4.1 抽检比例迁入 `t_feature_toggle` 并上屏，验证页面值与配置读回值相等、ID 字段为 `string` 【新建】— 抽检比例迁入 t_feature_toggle 并上屏；页面值与配置读回值相等、ID 字段 string。开关须有消费方。 <!-- comet-task-ref:dac81d36-89a9-4f8c-8dd6-e6d51f90d2db -->
- 4.2 新增 DDL 文档一致性门禁并全仓跑正向审计（避免 package-info.java 那类假红），验证 `search()` + `finditer` 计数对账且变异能判红 【验收】— **用户裁定改验收**：scripts/check-ddl-doc-sync.py 已存在、已接入 check.sh:840 [55/59]、已用 re.finditer ⇒ 不重复新建门禁。改为全仓跑正向审计（含 package-info.java 假红回归用例）+ finditer 计数对账 + 补变异验证（能判红且有互补变异）。 <!-- comet-task-ref:482b9b7a-566d-4f9a-96d7-d043585c3624 -->
- 4.3 新增 `INSPECTION_ACTION` 开关与只登记模式，验证关闭时不生成重办任务、不改案件状态 【新建】— INSPECTION_ACTION 开关与只登记模式；关闭时不生成重办任务、不改案件状态。 <!-- comet-task-ref:3a59a161-7359-4563-803d-ffa12d536aaa -->
- 5.1 跑仓库原生全量门禁并留存运行期证据，验证报告写入 `docs/superpowers/reports/` 且红点归零或明确归因为零交集基线项 【验收】— 重跑全量门禁并留运行期证据到 docs/superpowers/reports/；红点归零或明确归因为零交集基线项。 <!-- comet-task-ref:24b65510-7335-4469-808d-84d2b619384d -->
- 5.2 变异验证「已支付未冲正被拒」「重复扫描幂等」两处判据确实会转红，验证门禁有判别力 【验收】— 变异验证「已支付未冲正被拒」「重复扫描幂等」两处判据确实转红，含互补变异。 <!-- comet-task-ref:54ea429e-5a68-4387-8317-df86b2d3c07b -->

## 7. 停止条件

- 本 plan 只登记实施口径；**未调用 `submit-plan`、未写实现代码、未勾选任何 task**。后续 `submit-plan` 与逐批 Build 配置须经用户过目 / 单独确认。
- 若实现中发现与第 1 节基线冲突（例如迁移号被并行会话占用），先停下来重新取证并报用户，不得自行改号或回改已发迁移。
- 任何需要修改已批准 `design.md` / `tasks.md` 的语义变更，须走 `revise-design` 并由用户重新批准；不得绕过证据哈希。
