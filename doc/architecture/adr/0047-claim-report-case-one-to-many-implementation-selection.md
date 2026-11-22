# ADR-0047：智能理赔「报案 1:0..N 赔案」拆分 — 实现选型、接口面与迁移处置

| 项 | 内容 |
|----|------|
| **状态** | **已接受**（2026-11-22；依《报案 1:0..N 双分支实施方案评审材料》裁定，按推荐方案执行） |
| **日期** | 2026-11-22 |
| **决策者** | synap-claim 架构（change `claim-report-case-one-to-many`） |
| **关联** | 评审材料 `docs/superpowers/reports/2026-11-22-claim-report-case-branch-review.md`；验证报告 `docs/superpowers/reports/2026-11-22-claim-report-case-one-to-many-verify.md`；契约 `doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md` §9.3.1 / §9.5 |
| **下游同步** | 契约 §9.3.1（已同步，含「刻意不做」清单）；`bone-init.sql` + 模块 `V25__synap_claim_report.sql`（三处 DDL 同批）；网关**无需改动**（复用既有前缀）；`Bone-DDD-最终实践方案.md` 无规则改动（本决策只应用既有规则 E-1.1 / G-1.6，未修改其语义） |

---

## 背景

### 业务口径

报案（FNOL，事故告知事件）与赔案（赔付处理单元）是两个聚合：一次告知可对应 0..N 笔赔付（分案、补赔、二次手术，或一次事故多张发票分期提交）。按 JR/T 0212-2020《财产保险业务要素数据规范》，事故与赔付在数据模型中本就分层。旧模型把两者压成一张表，物理层就排除了「同一起事故追加第二笔赔案」，而设计文档早已埋下 `batch_no` / `is_copy` 伏笔。

### 工程约束

同一功能在两条分支上各有一版实现，**迁移同号（`0139`）不同名**：

| | `comet/claim-report-case-one-to-many`（本 ADR 采纳） | `comet/claim-id-linkage` |
|---|---|---|
| 相对 `dev` | +20 提交 | +2 提交 |
| 聚合边界收口 | `ClaimIntakePort`（同事务建报案 + 首案） | `ClaimSubmissionOrchestrator`，**注释明写禁止 `@Transactional`** |
| `report_id` | 回填后 `NOT NULL` | 允许 `NULL` |
| 报案号来源 | 单一真源（读侧 `LEFT JOIN t_claim_report` 取 `r.report_no`） | 追加冗余列 `t_claim_case.report_no`，读侧直读 `c.report_no` |
| 接口面 | `/api/v1/claims/reports/{reportNo}[/cases]` | `/api/v1/claim-reports/**` |
| 存量回填 | 纯 SQL 回填（前缀 `CL→RP` 改写，校验位不含前缀故仍合法） | 不回填 |
| 网关 | 零改动 | 新增 `Path=/api/v1/claim-reports/**` 路由（提交 `bd3aaa0b2`） |

`0138_synap_claim_claim_no_global_unique.sql` 在两分支是**同一 blob**，冲突面只收敛到 `0139` 文件名本身。

## 决策

1. **域模型与事务边界**：保留本分支实现。报案登记与首案创建经 `ClaimIntakePort` 在**同一事务、同一应用服务**内完成；`t_claim_case.report_id` 回填后收紧为 `NOT NULL`。
2. **不设冗余列**：报案号唯一真源为 `t_claim_report.report_no`，`t_claim_case` 不加 `report_no` 列。
3. **接口面统一**：`GET /api/v1/claims/reports/{reportNo}`、`POST /api/v1/claims/reports/{reportNo}/cases`。
4. **弃用对方实现的报案拆分部分**，不为「抢救 id-linkage」做额外剥离（理由见下）。
5. **活库残留列由新迁移清理**：`0140_synap_claim_drop_case_report_no_redundant.sql`，不回改已执行的 `0139`。

## 理由

### 事务：R9 约束的是「聚合根类型数」，不是「不许有事务」

对方为通过 R9（`BoneDddArchRules.oneAggregatePerTransaction()`）而放弃 `@Transactional`，代价是可观测的业务后果：**建案失败后库里留下一条 0 赔案的报案**，而幂等键挂在赔案上、报案层无幂等键 ⇒ 客户端重试会**再造一条报案**。

真实判据不是「能不能加注解」，而是「一个事务里出现了几个聚合根类型」。用端口把「建报案 + 建首案」收口到同一个应用服务，事务里仍然只有一个新增聚合根类型，门禁自然通过 —— 实测 `ArchitectureTest` 28/28 全绿，`oneAggregatePerTransaction` 未被绕过也未被放宽。

### 冗余列：活库已经在替我们证明它会烂

实测（2026-11-22 晚，共享库，数字会漂）：`t_claim_case.report_no` 列存在、可空、**0 行有值**，而对方读侧 SQL 直读 `c.report_no` ⇒ 在同时装过两套 `0139` 的混合库上，那条读路径**读不出报案号**且不报错。这正是「同一语义两处配置源 ⇒ 配了不生效」的存储版：写入方写 A，读取方读 B，谁都不报错。

本分支的读侧 `findClaimWithItems.sql` 是 `LEFT JOIN t_claim_report r` 取 `r.report_no`，单一真源。

### 接口面：少一个需登记的前缀 = 少一处静默 404 面

网关按**前缀白名单**转发（`bone-platform/bone-gateway/src/main/resources/application.yml:151` 已有 `Path=/api/v1/claims/**`）。因此：

- 本方案的 `/{reportNo}`、`/{reportNo}/cases` 落在既有前缀下，**零网关改动**；
- 对方的 `/api/v1/claim-reports/**` 必须新增路由，而对方自己的提交注释就写明了后果：

  > 缺此路由时四个端点整体 404 且不产生后端日志（请求没进上游 DispatcherServlet），排查时极易误判为「服务没实现」。

  该分支为此外加了一个补登记提交 `bd3aaa0b2` —— 一次可避免的运维事故面。

### 弃用对方分支不损失功能

`ClaimIdLink` / `PolicyNoLink` / `policyDeepLinks` / `sclaimRoutes` 四组文件位于**共同祖先** `baf3f70a7`，两分支 blob 逐字节相同，已随本分支路径带入。故「保留本分支」不丢任何功能，无需按功能拆分剥离。（评审材料初稿曾误判为「对方独占」，该结论已作废并保留纠错记录。）

### 合并面已验证干净

`git merge-tree --write-tree dev HEAD` → RC=0、无冲突输出；分叉点 `b8c4b71eb` 即 `dev` 当前提交（`dev` 相对本分支**独占 0 提交**）⇒ **fast-forward 可合**。

## 后果

### 正面

- 库层保证「赔案必有报案」（`report_id NOT NULL` + §10.1/§10.2 终态断言），从根上消灭无主赔案。
- 报案号单一真源，不存在「冗余列与真源分叉」这类静默数据错误。
- 零网关改动，接口面落在既有白名单内。
- 合并零冲突、可 fast-forward；两分支 `0138` 同 blob，冲突面仅 `0139` 文件名。

### 负面 / 风险

- **账本历史残留**：本机活库 `schema_migration_journal` 在 `order 145` 记的是**对方文件名** `0139_synap_claim_report_one_to_many.sql`。本 ADR 采纳的实现文件名不同（`0139_synap_claim_report_case_split.sql`）⇒ 部署时 `migrate.py status` 会把对方那条报为 `orphan`（账本有、清单无），并把我的 `0139` 列为 `pending` 重放。重放本身**幂等无害**（§4/§5/§6/§8 全部条件化），记账后会出现两行 `order_no=145` —— 允许，账本主键是 `file`，`order_no` 按 `migrate.py` 注释只是「最近一次记账时在清单里的位置」。处置见下。
- **本机活库仍留着 `t_claim_case.report_no`**（0 行有值）。0140 已就绪但未执行。
- **接口面既定即锁定前端与 E2E**：对方若继续合并，其前端调用与 `scripts/e2e/synap-claim-report-one-to-many-e2e.py` 需改到 `/api/v1/claims/reports/…`，否则被 E2E 影响面门禁判零覆盖。
- **`0139` 内 8 处 `PREPARE` + `SIGNAL` 是坏写法**（MySQL 1295「not supported in the prepared statement protocol」）：中止行为正确，但错误信息与意图无关，只在异常路径暴露。这是**全仓存量问题**（同类写法散布多个迁移），不在本 change 修 —— 在本已通过验证的迁移上做 8 处结构性重写会让验证证据失效，收益与风险不成比例。另开 change 做全仓清扫（`0140` 已是正确写法，可作模板）。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 保留对方（Orchestrator + 无事务 + `report_no` 冗余列 + `claim-reports` 前缀） | 建案失败留 0 赔案报案且报案层无幂等 ⇒ 重试重复建报案；冗余列已在混合库上证明会让读路径静默读空；需额外登记网关前缀 |
| B. 两套并存，按前缀分面 | 同一聚合两套写路径 ⇒ 状态机、幂等归属、读侧投影三处口径分叉，无收敛日 |
| C. 保留本分支但沿用 `claim-reports` 前缀 | 白送一处「必须多登记一个前缀」的静默 404 面，且与 `/api/v1/claims/**` 语义重叠 |
| D. 回退为 1:1 | 与 JR/T 0212-2020 的事故/赔付两聚合口径冲突；分案需求已在设计文档埋线，迟早要重建表 |
| E. 保留两套迁移 `0139`，运行时按环境择一 | 迁移不可「按环境择一」，清单/账本/`bone-init.sql` 三处真源会永久分叉 |

## 合规与迁移

### 合并

```bash
git checkout dev
git merge --ff-only comet/claim-report-case-one-to-many   # 已验证零冲突
```

若 `dev` 已前进（他人先行合并），先 `rebase` 再复跑 `git merge-tree --write-tree dev HEAD` 复核冲突面 —— 唯一预期冲突点是 `0139` 文件名与 `migration-manifest.json` 的 `order`。

### 部署迁移

**前置条件**：`scripts/migrate.py` 硬依赖 `mysql` 客户端二进制（`MYSQL_BIN` 或 `PATH` 中的 `mysql`，见 `migrate.py:144`），缺失时直接 `sys.exit("❌ 找不到 mysql 客户端")`。**本机（macOS 开发机）无该客户端，故以下命令属部署/CI 步骤，不要在工作站照抄执行** —— 若在无客户端环境硬跑，失败信息是「找不到 mysql 客户端」，容易被误读为「迁移有问题」。

```bash
python3 scripts/migrate.py status      # 预期：我方 0139 为 pending，对方 0139 为 orphan
python3 scripts/migrate.py apply       # 0139 幂等可重放，末尾 §10 六条终态断言 fail-closed
```

### 清理冗余列（0140）

执行前必须两项核对，缺一不可：

```sql
-- ① 列内无数据（有数据则 0140 打印清单并 SIGNAL 中止，不会自动删）
SELECT COUNT(*) FROM t_claim_case WHERE report_no IS NOT NULL AND report_no <> '';
-- ② 没有其它服务持有连接池（共享库：删列会让仍在跑旧分支代码的实例读路径立刻 500）
SELECT id, user, host, db, command, time FROM information_schema.processlist WHERE db LIKE 'bone%';
```

确认后 `python3 scripts/migrate.py apply`。**不在共享活库手工 DDL** —— 手工执行会留下「库已改、账本无记录」的状态，下次 `migrate.py` 重放时行为不可预测。

### 账本 orphan 处置（环境清理，不入仓库脚本）

确认对方分支的报案拆分不再合并后：

```sql
DELETE FROM schema_migration_journal WHERE file = '0139_synap_claim_report_one_to_many.sql';
```

### 关联资产

| 类别 | 文件 |
|------|------|
| 迁移 | `scripts/migration/0139_synap_claim_report_case_split.sql`、`0140_synap_claim_drop_case_report_no_redundant.sql` |
| 全量基线 | `bone-init.sql`（同批，`createCheckTableCoverage` 200=200 已校验） |
| 模块参考态 | `bone-apps/synap-claim/synap-claim-app/src/main/resources/db/migration/V25__synap_claim_report.sql` |
| 聚合与端口 | `ClaimReport.java`、`ClaimCase.java`、`ClaimIntakePort.java`、`ClaimReportApplicationService.java` |
| 接口 | `ClaimReportController.java`（`/api/v1/claims/reports`） |
| 前端 | `bone-frontend/apps/bone-claim-app/src/services/claim.ts`（`/reports/{reportNo}[/cases]`，base `/api/v1/claims`） |
| E2E | `ClaimLifecycleE2ETest#reportDetailAndAppendCaseEndToEnd`、`scripts/e2e/synap-claim-report-case-e2e.py` |

## 遗留（不属本 ADR 决策范围）

Comet Run `claim-report-case-one-to-many` 停在 `verify`、`verifyResult=pending`、`archived=false`。Action `:20` 悬在 `running`，五条恢复通道在 `@rpamis/comet` 0.5.2 源码层面全部封闭：`record-outcome` 仅在 `record.status==='completed'` 时生成模板；`retry` 的 `resolution` 只接受 `'not-executed'`；`mark-unknown` 语义为「未执行」且 `terminal()` 不含 `unknown`；`acknowledge-stopped` 要求 `terminal(action)`；`cancel` 是 Run 级。根因是 Classic 域从未调用 `acknowledgeStopped`（仅 Native 域有 `reconcileInterrupted` 回路）。检查本身已跑成 —— 收据日志尾行为 `Tests run: 1662, Failures: 0, Errors: 0, Skipped: 8` + `BUILD SUCCESS`。按既定原则处理：不 cancel、不 mark-unknown、不伪造回执，以已提交的验证报告与 Git 提交为最终事实。
