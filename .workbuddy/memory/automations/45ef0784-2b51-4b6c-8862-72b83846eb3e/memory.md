# 夜间设计复核自动化 · 执行记录

> 提示词：`scripts/prompts/02-nightly-design-review.md`（每晚 23:01）。此处只存高层摘要，详细结论见各晚报告。

## 2026-09-26（首跑，23:56 触发）
- 前提全部满足（工作树 0 脏 / 锚定新鲜 23:40 / 基线可读）；当前分支为 `release/mvp-v1.0` → 按 §0 前提 2 切出 `codex/nightly-bone-iam-20260926`。
- 选中 **bone-iam**（轮换 #1，`pending`）。合批门槛未过（设计稿 1876 行 > 600；与 bone-metadata-server 有逻辑外键耦合）→ **降级为 1 个模块**；周六 → 只做 **A 阶段**。
- 产物：报告 `doc/design/modules/review-report-bone-iam.md`；v2 稿追加至 doc6 末尾；`_review-status.yaml` → bone-iam `design_ready`、next_run=`bone-metadata-server`；新建 `_nightly-log.md` 首行。
- 结果：阻断级 **10** 条，L3/L4 待审批 **4** 项，AI 自审 **PASS**，未写代码、**未提交**（阶段 B 才允许）。
- 下一步：等人工卡点 `doc/design/approvals/bone-iam.yaml`（或 `doc/design/modules/bone-iam/_review-approved.yaml`）；需先确认 ① 导出契约基准 ② 并发方案（FOR UPDATE vs version DDL）③ 前端缺口范围。

## 2026-09-27 口径纠偏（用户裁定）
- 「周六按 §5 只跑 A 阶段、不写代码不提交」**是错的，已废止**。正确口径：一路走完 **A 设计 → A' 方案自审 → B 实现 → B' 代码复核 → C 联调 → D 验收（造测试数据 + 模拟人工操作）→ 提交**，与星期几无关。
- 已改：`scripts/prompts/02-nightly-design-review.md` → v3 六段闭环（人工卡点改默认放行 + 可显式叫停；报告扩为 9 章）；bone-iam 报告补 六/七/八 章；`_review-status.yaml` bone-iam `next_stage: "B"`。
- 已沉淀 skill：`~/.workbuddy/skills/bone-nightly-design-loop`（触发词：夜间复核 / 模块闭环 / 02-nightly-design-review）。

### 复用经验（供后续夜晚）
1. 先跑 `git status --porcelain` + `git branch --show-current`，脏树或在 release/* 上直接按规定切分支/退出。
2. 取证用两个并行 Explore 子代理（后端 B1-B8 / 前端 F1-F5）能显著省 token；但**子代理结论必须自己复核**——本次修正了 2 处误判（HC-006 关于 AuditLogCleanupJob 的误报、国际化方案 §6.4 对 bone-iam 已过时）。
3. 锚定文件的 `design_vs_code_gaps` / `hc_evidence` 是线索不是结论，一律重跑命令。
4. zsh 下 `grep --include=*.ts` 会被通配符吃掉 → 用 Grep 工具的 `glob` 参数。

## 2026-09-27 07:05 · bone-iam · B→B'→C→D（六段走完，提交 a922f8c63）
- 用新 skill `bone-nightly-design-loop` 执行；`design_ready` → `implemented`。
- 并发会话占用半份后端清单（AuditController/MfaController/RefreshTokenIssuer/prod yml/AccountApplicationService）；本轮只做未认领项 + 全部前端。
- 落地：ID 改 string（shared-types iam/role/permission + api.ts + 4 个页面）、导出改 blob + 参数名对齐、新增 `isoLocal()` 修时间格式 400、配额单 JVM 分段锁（FOR UPDATE 属 L3）、HealthIndicator 真检（STUB→db/redis UP）、SessionResp、Profile 页、lifecycle/qiankun-entry 单一真源。
- 门禁：bone-iam 159 tests 绿、spotless:check 绿、commit 触发 check.sh 全绿。
- 新发现：B-11 时间格式（已修）、B-12 MFA 业务码丢失（并发会话占用，未抢改）、B-13 apiClient 不透传响应头（建议）。
- D 段：Playwright 8 场景全过，console/pageerror/5xx=0，测试数据 NIGHTLY-158934 已删并 API 自证。
- skill 已回写三条硬经验：监管实例跑 fat jar 不加载新代码 / 并发会话处置 / 契约要查「参数名 + 格式」两层。

## 2026-09-27 08:21 · 架构规约新增「禁止业务层 SELECT ... FOR UPDATE」
- 用户裁定：`SELECT ... FOR UPDATE`（悲观锁）易死锁、且行锁仅单 MySQL 实例有效 → 多副本部署完全失效，必须禁止；并发默认走乐观锁，参考业界最佳实践给替代方案，并写进架构规约让 agent 强制遵守。
- 已落地的既有基础（避免重复造轮子）：SDK 原生 `@Version` 乐观锁已由 **ADR-0031（D1/D2 已实现）** 落地（`BaseRepository#update`→`DynamicUpdateBuilder` 自动 `SET version=version+1` + `WHERE version=:old`，0 行抛 `OptimisticLockingFailureException`）；`数据库开发规范 §1` 已强制聚合根表含 `version INT NOT NULL DEFAULT 0`。E-5.3 原表仍把「悲观锁」列为允许项（短事务高冲突），本次纠正。
- 改动 4 个文件（已提交 `589047f3d`，路径显式 add 排除并发会话 WIP）：
  1. `AGENTS.md` §一.7 新增硬规则（禁止 FOR UPDATE，默认 @Version，SDK `ColumnAllocator` 的 `FOR UPDATE SKIP LOCKED` 豁免）。
  2. `doc/agents/05-数据库与安全.md` 新增 §8.4 并发控制与锁策略（死锁根因 5 条 / 乐观锁用法 / 替代矩阵 / iam_tenant 配额原子条件更新样例 / 例外 / 自查）。
  3. `doc/architecture/数据库开发规范.md` §1 追加并发控制硬规则。
  4. `doc/architecture/Bone-DDD-最终实践方案.md` E-5.3 「悲观锁」行改为「业务层禁止」+ 硬规则说明。
  5. `bone-nightly-design-loop` skill（user 级 `~/.workbuddy/skills/...`）：FOR UPDATE 纳入 §五 禁碰清单 + §七 取证铁律第 10 条 B 段必查（SDK ColumnAllocator 除外）。
- 实测结论：全仓 grep `FOR UPDATE` 仅命中 SDK 内部 `ColumnAllocator`/`ColumnAllocationRepository`/`dialect`/`SqlUtil`/`TenantSqlRewriter`（均豁免），**业务/应用/adapter 层无任何 `SELECT ... FOR UPDATE` SQL** → 规则零存量违规，无需 remediation。
- 待办（未动，留给架构师）：`doc/design/modules/6. IAM账号权限管理模块详细设计方案.md:1294` 仍在推荐配额用 `SELECT ... FOR UPDATE`（走已登记 JDBC 通道）；该设计稿与新区规冲突，需架构师择时改为「原子条件更新 / @Version」口径（属设计稿治理，不当作代码违规静默改）。
- 未 push（push 是上一轮被 429 阻断的独立诉求；本规则改动已本地提交，待用户显式 go-ahead 再 push 双远程）。
