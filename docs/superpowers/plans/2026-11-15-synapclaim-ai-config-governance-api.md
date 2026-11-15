---
change: synapclaim-ai-config-governance-api
design-doc: openspec/changes/synapclaim-ai-config-governance-api/design.md
base-ref: f115be62ac45698cdcd5f0ed0d7f8c630640341f
baseline-head: 285827275
baseline-manifest-order: 124
baseline-gate: 59/59
plan-revision: 2
---

<!-- comet-task-authority: openspec/changes/synapclaim-ai-config-governance-api/tasks.md -->

# Build Plan — 批 2 · AI 配置治理 API（实施口径唯一真源，2026-11-15）

> **唯一口径声明**：本 plan 是本 change 实现口径的**唯一真源**。`tasks.md` / `design.md` 已在 Design 阶段批准并被 Runtime 以内容哈希锁定为已接受证据，故本轮**不修改**；其中与当前仓库基线不一致的表述（迁移基线止于 `order=123`、`0124` 仅 staged、并行会话「在途」、按「从零新建」描述已实现能力等）**一律不采信**，以本 plan 第 1、4 节为唯一口径。任务完成状态一律以 `tasks.md` 为准，本 plan 不复制 checkbox 账本，仅以 `comet-task-ref` 映射。
> 依据：2026-11-15 四批范围重核（逐批证据与逐条结论已完整并入本 plan 第 1、4、6 节）+ 用户 2026-11-15 三项裁定。本 plan 自洽，不依赖任何项目外临时文件。

## 1. 基线事实（2026-11-15 实证，校正 design 中的过时口径）

- `dev` HEAD = `285827275`「归集并行会话在途资产并同批同步 E2E 投影契约」，主树 `git status --porcelain` = **0 在途**。`ClaimWorkbenchController` / `ClaimSearchApplicationService` / `AiFeedbackRules` 经 `git cat-file -e HEAD:<f>` 确认 IN HEAD。
- `./scripts/check.sh` 实跑 13 分钟：`CHECK_EXIT=0`、`[59/59]`、末行「✅ 本地自检通过！」。日志中的 `[FAIL]` / `❌` 全部落在 `[42/59]` 种子校验**负向探针**内部，非步骤级红点 —— 判据看 exit 0 + 末行。
- `scripts/migration/migration-manifest.json`：`entries=131` / `executableCount=124`，末尾 `order=124`（`0124_synap_claim_ai_feedback_event.sql` 已入库并登记）。⇒ **文件前缀 `0125_` 与 `order` 是两回事**（详见第 7 节并行分支迁移合并协议）：`order` 是 `1..N` 连续执行序号，只能取清单 `max(order)+1`，**禁止跳号 / 预留号段 / 四批各写同一个号**；脚本 + 清单 + `bone-init.sql` 同批入库；已发迁移（含注释）不回改（sha256 journal）。
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

10 条 = 4 新建(1.1–2.1) + 3 验收留证(2.2/3.1/3.2) + 3 验收门禁(4.x)；设计 D2/D7 与 tasks 3.2 早已预判与并行会话成果合并，本次只是「在途」兑现为「已入库」。

## 5. 执行配置

**待单独确认**（当前缺 build_mode + tdd_mode，reviewMode=off）。按用户裁定不沿用批 1 批准值，开工前须本批单独 propose-build + decide-build。

## 6. 任务映射（逐条口径；「新建」= 仍需编码，「补齐对接」= 复用既有入口扩展，「验收/验收留证」= 已实现，只跑探针与留证）

- 1.1 新增 L1/L2/L3 配置受控写端点（`@PreAuthorize` + 租户隔离 + 审计留痕），验证无权限与跨租户请求均被拒 【新建】— L1/L2/L3 配置受控写端点（@PreAuthorize + 租户隔离 + 审计留痕）。ratchet / 棘轮 / 仅收紧 / t_feature_toggle 写端点在 controller+application 仍 **0 命中**；只读底座 FeatureToggle 实体与 Repository 可复用。 <!-- comet-task-ref:5e145902-7ff6-4ea3-9de7-8c410c0c10f4 -->
- 1.2 实现只收紧棘轮比较（逐字段、拒绝放宽、允许相等与更严格），验证放宽用例返回错误码且库中值不变、收紧用例读回新值 【新建】— 只收紧棘轮逐字段比较：拒绝放宽、允许相等与更严格；放宽用例返回错误码且库中值不变。 <!-- comet-task-ref:55bc64de-7930-4876-b4ca-54331cae7971 -->
- 1.3 新增写 API 开关与关闭回落，验证开关关闭时写请求被拒且治理页回到只读 【新建】— 写 API 开关与关闭回落；关闭时写请求被拒且治理页回只读。开关须有消费方，避免「配了不生效」。 <!-- comet-task-ref:0448d17d-12ab-4232-98f2-c6fe495a720e -->
- 2.1 治理页改为可写并显式标注「仅收紧」，验证 `tsc` / `eslint` 通过且 ID 字段为 `string` 【新建】— 治理页改可写并显式标注「仅收紧」；tsc / eslint 通过、ID 字段为 string。 <!-- comet-task-ref:bae83e17-83a8-4bfc-90c1-1b330f1fcab3 -->
- 2.2 初审页上报 AI 建议曝光与裁决（带 `traceId`、AI 参与四态、`ReasonType`），验证曝光计入分母、`blind_label` 与非 `AI_ASSISTED` 裁决被拒 【验收留证】— **已前后端闭环，勿重写**：前端 workbench/telemetry.ts（注释「理赔域后端契约已补齐并真机联调通过」）+ persist.ts 的 aiFeedbackApi.record + AiSuggestionCard；后端 /{claimId}/ai/feedback + AiFeedbackRules（blind_label 禁区、非 AI_ASSISTED 拒裁决、ReasonType 四值、AI 参与四态）。改为跑曝光分母 / 盲标 / 非 AI_ASSISTED 探针并留证。 <!-- comet-task-ref:6e5c9061-373e-4f57-aea6-efdced5d0b8d -->
- 3.1 以租户内 `event_id` 实现幂等（同键同载荷返回首次结果、同键异载荷 409），验证重放探针与并发探针 【验收留证】— **已实现**：ClaimFeedbackApplicationService:47 findByEventId + CLAIM_AI_FEEDBACK_CONFLICT(409) + 0124 的 uk_tenant_event + 终态断言。改为跑重放 / 并发探针并留证，不再建表建码。 <!-- comet-task-ref:2e0d7615-962a-460e-8cd4-14ddc438f226 -->
- 3.2 与并行会话在途的 `0124` / `AiFeedback*` 成果合并且不重复建表，验证清单 M1/M2/M3 全绿、启动期无 `TemplateNotFoundException` 【验收留证】— **已入库**：0124 与 AiFeedback* 已在 HEAD（design 中「并行会话在途」措辞过时，以本 plan 为准）。改为验证清单 M1/M2/M3 全绿、启动期无 TemplateNotFoundException，确认不重复建表。 <!-- comet-task-ref:ed1fb46c-d157-4a41-b8cb-d85c76f6eb29 -->
- 4.1 跑放开阈值、关闭写 API、盲标拒绝三条负向判据并留存运行期证据，验证报告写入 `docs/superpowers/reports/` 【验收】— 跑放开阈值、关闭写 API、盲标拒绝三条负向判据并留证到 docs/superpowers/reports/。 <!-- comet-task-ref:3d63c6d3-99f2-46b8-8a66-3faecbfb0b68 -->
- 4.2 跑仓库原生全量门禁（含错误码双语、造数资产同步、E2E 影响分析），验证红点归零或明确归因为零交集基线项 【验收】— 重跑全量门禁（含错误码双语、造数资产同步、E2E 影响分析）。注意：并行会话新增两码在前端 constants/errorCodes.ts 0 命中却门禁全绿 ⇒ **i18n 覆盖盲区单列独立核实，不并入本批**。 <!-- comet-task-ref:aacede13-7249-49a9-b491-77ec95147e9c -->
- 4.3 变异验证「放宽阈值被拒」判据确实会转红，验证门禁有判别力 【验收】— 变异验证「放宽阈值被拒」判据确实转红，并排除互补变异退化。 <!-- comet-task-ref:6264245f-7583-4dcb-8a9a-805078db200a -->

## 7. 并行分支迁移合并协议（依据已接受 ADR + 清单判据，纠正上一版口径）

> 上一版写「新迁移从 `0125` 起按批次顺序分配」并给批 1 写死 `order=125`。经 `scripts/check-migration-manifest.py` 判据与 ADR-0001 /《ADR-生产数据库增量迁移策略》复核，该表述会让 M3 / M4 判红。**本节为唯一口径**。

| 概念 | 定义 | 约束来源 |
|---|---|---|
| 文件名 4 位前缀（如 `0125_`） | 脚本命名，按字典序参与顺序冻结 | M4：按 `order` 排开必须等于文件名词典序；不一致须在 `outOfLexOrder` 逐项声明理由 |
| `order` | **连续执行序号** `1..N`（N = 可执行 SQL 数） | M3：`1..N` 连续无重复 ⇒ **禁止跳号、禁止预留号段、禁止四批各写同一个号** |
| `ordinalConflicts` | 4 位前缀重号组（≥2 文件）登记 | M6：实际重号组与登记必须双向一致，且每条带 `verdict`（谁先谁后、为何可共存） |

协议（实现落脚本时执行，设计期不占号）：

1. **不预占**：四批均不固定 `0125_...` 或 `order=125`。落脚本前先 `git fetch` + 读主树 `scripts/migration/migration-manifest.json` 的 `max(order)`，取 `max+1`（当前 124 ⇒ 125；若并行会话已占用则继续后移）。
2. **同批三件**：脚本 + 清单末尾条目 + `bone-init.sql` 建表块同批入库；M1/M2/M3/M4/M11 全绿。依据 ADR-0001（DDL 单轨真源）与《生产数据库增量迁移策略》（「迁移脚本必须与 bone-init.sql 最终状态一致」、expand/contract 新增列 nullable + DEFAULT）。
3. **合并时重算**：后合并的分支必须重算 `order` 并同步改文件名前缀以保持 M4 词典序；调整对象是**尚未应用的新脚本**。已发脚本（含注释）不可回改——`migrate.py` 按 sha256 journal 校验。
4. **避免重号前缀**：每脚本使用唯一 4 位前缀，不制造 M6 重号组；万不得已共用前缀须登记 `ordinalConflicts` + `verdict`。
5. **大表**：按 ADR 走 gh-ost / pt-osc，不做原地锁表变更。

## 8. 执行顺序与共享文件串行化

依赖顺序：**批 1 → 批 3 → 批 4**；批 2 可与批 1 并行。本批可与批 1 **并行**（配置受控写 / 棘轮与任务表无耦合）；2.2、3.1、3.2 三条已实现能力只验收留证，不重写。

共享文件（四批都会碰）必须**串行合并、基于 HEAD 追加、不得整文件覆盖**：`ClaimErrorCodes.java`、`ClaimErrors.java`、`migration-manifest.json`、`bone-init.sql`、`ClaimWorkbenchController.java`、前端两语言包、`doc/architecture/Bone-错误码登记.md` §6。新增错误码三处必齐（常量 + `DEFAULT_HTTP_STATUS` + 前端两语言包 + 台账 §6）；每次合并后重跑对应判据，避免「静默丢一条改动」。

## 9. Definition of Done / 验收证据（可机器判读）

| 证据 | 产出 | 判据 |
|---|---|---|
| 运行期证据报告 | `docs/superpowers/reports/<change>-*.md` | 含本批每条判据的**实测数值**，不接受「应该通过」 |
| 全量门禁 | `/tmp/<change>-check.log` | `./scripts/check.sh` `exit 0` **且**末行「✅ 本地自检通过！」；日志内 ❌ 须逐条归因为负向探针 |
| 迁移清单判据 | `python3 scripts/check-migration-manifest.py` | M1/M2/M3/M4/M6/M11 全绿，`order` 连续且等于词典序 |
| 变异验证 | 门禁日志 + 报告 | 每个**新增**判据至少 1 个正向用例 + 1 个互补变异能判红；排除模运算式 / 阈值式退化 |
| 编译与启动 | `mvn -o compile` / 启动日志 | 无 `TemplateNotFoundException`、无缺列告警、类加载不抛 |
| 任务勾选 | `comet state task-complete` | 由 CLI 驱动，**不手改 `tasks.md`**（会被证据哈希判为 drift） |

## 10. 风险登记

| 风险 | 触发信号 | 影响 | 缓解 |
|---|---|---|---|
| 并行会话占用 `0125` / 推进 `order` | 主树清单 `max(order) > 124` | 合并撞号，M3 / M4 判红 | 落脚本前重算 `max+1`（第 7 节）；提交门禁第 5 道 fail-fast |
| 已实现能力被重写 | `telemetry.ts` / `AiFeedbackRules` / 0124 被改动 | 重复造轮子、与并行会话冲突 | 批 2 的 2.2/3.1/3.2 只验收留证 |
| 基线声明漂移 | 主树 HEAD / 清单 order / 门禁项数变化 | 照抄旧基线静默降级 | frontmatter 指纹 + 提交门禁同源校验；开工前 pre-flight（第 12 节） |
| 错误码 i18n 覆盖盲区 | 新码在 `constants/errorCodes.ts` 0 命中却门禁全绿 | 前端无法展示错误语义 | **单列独立核实**，不混入四批 |
| 共享文件并发覆盖 | 错误码 / 清单 / Controller 同时被多会话改 | 静默丢改动 | 串行合并 + 基于 HEAD 追加（第 8 节） |

## 11. 决策记录（ADR-lite）

- **上下文**：`design.md` / `tasks.md` 在 Design 阶段已批准，被 Runtime 以 sha256 锁定为已接受证据；范围重核又发现并行会话已入库部分目标能力，四份 design 的迁移基线全部过时。
- **决策**：重标与基线校正结论写进本 plan，并声明本 plan 为实现口径唯一真源；**不改已批准文档**。
- **备选与代价**：① `revise-design` 重走 Design —— 4 批 `phase` 退回 design、批 1 已批准的 Build 配置作废、四分支产物重写重推，代价最大；② 直接改文档 —— 可逆实测证实立即 `5/6 blocked`（`CLASSIC_EVIDENCE_DRIFT`），不可行；③ 只放外部清单 —— Comet 流程内看不到口径来源，且 `/tmp` 会丢。
- **后果**：plan 成为唯一口径，但 `design.md` / `tasks.md` 文本仍含过时表述，靠本 plan 的「不采信」声明消解；后续若需**语义**变更，仍须走 `revise-design` 并由用户重新批准，不得用 edit-review 非语义白名单伪造证据。

## 12. 开工前 pre-flight（三条，必须在各自 worktree 内执行）

1. `comet state check <change> build --json` —— 必须 `5/5 blocked=False`；在主树跑会报 `4/5` 路径归属假红。
2. `python3 scripts/check-migration-manifest.py` —— 确认清单判据全绿，并记下当前 `max(order)`。
3. `git -C "$(git worktree list --porcelain | head -1 | sed 's/^worktree //')" show HEAD:scripts/migration/migration-manifest.json` —— 用主树真源复核基线；与 frontmatter 的 `baseline-manifest-order` 不一致即停手更新 plan，不得照抄旧值。

> ⚠️ **环境变量陷阱（2026-11-15 实测）**：在 pre-commit 钩子或任何注入了 `GIT_DIR` 的上下文里，`git -C <主树>` 会被 `GIT_DIR` 覆盖，**静默读到当前分支 HEAD** 的旧清单。四条分支自身 HEAD 的清单 `max(order)=123`（分支点 `6f0ea427f` 早于 `285827275` 引入 0124），`dev` 主树才是 124。故执行上述命令前须 `unset GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE GIT_COMMON_DIR`；提交门禁 `verify-baseline.py` 已按此修复，并以「声明 123 判红 / 声明 124 判绿」双向变异验证，未通过改指纹凑绿。

## 13. 停止条件

- 本 plan 只登记实施口径；**未调用 `submit-plan`、未写实现代码、未勾选任何 task**。后续 `submit-plan` 与逐批 Build 配置须经用户过目 / 单独确认。
- 若实现中发现与第 1 节基线冲突（例如迁移号被并行会话占用），先停下来重新取证并报用户，不得自行改号或回改已发迁移。
- 任何需要修改已批准 `design.md` / `tasks.md` 的语义变更，须走 `revise-design` 并由用户重新批准；不得绕过证据哈希。
