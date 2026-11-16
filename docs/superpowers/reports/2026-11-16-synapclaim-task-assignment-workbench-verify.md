# 运行期证据报告：synapclaim-task-assignment-workbench（批 1）

- 日期：2026-11-16
- 阶段：Build 收尾（任务 5.1 / 5.2 / 5.3）
- 绑定分支：`comet/synapclaim-task-assignment-workbench`｜工作流：classic｜产物语言：zh-CN
- 构建配置：`build_mode: executing-plans`、`tdd_mode: tdd`、`review_mode: thorough`
- 环境：本机 MySQL 8（`bone` 库）、JDK 21、Maven 3.8.6、Node 前端 worktree 内通过符号链接复用主树 `node_modules`

## 0. 判据来源与一处诚实说明

`tasks.md` 5.1 要求「逐条跑提案 §13.5 批 1 的**五条**可执行判据」。实际核查发现：

- 提案 §13.5 的上游原文（v2.3 深度设计）**不在本仓库当前可见范围**（`openspec/changes/archive/2026-11-13-synapclaim-workbench-assignment-review-quality/` 的 `design.md` 是薄壳，仅含四批路线；`doc/` 下没有 §13.5 原文）。
- 因此本报告**不据其虚构条目**，五条判据取自本 change 已批准的 `design.md` 中 `Test Strategy` 与 `Risks / Trade-offs` 的「验证判据」列，逐条可回指。

| # | 判据（口径来源） | 结论 |
| --- | --- | --- |
| C1 | 迁移终态：`information_schema` 强断言 + 清单 M1–M11 + 全量基线覆盖（design.md Migration Plan / Risks 行 1–2） | **PASS** |
| C2 | 分页同源：`total` = 逐页行数之和 = ID 去重数（design.md Test Strategy「137 条翻页」） | **PASS** |
| C3 | 索引命中：三条索引 `EXPLAIN` 均命中；1 万在办 100 次采样 P95 < 300ms（Risks 行「索引未命中导致 P95 不达标」） | **PASS** |
| C4 | 并发双领取恰 1 成功（Test Strategy「并发双领取恰 1 成功」） | **PASS** |
| C5 | 分配可观测：命中率 ≥0.90 且回落计数按小时可分桶（Migration Plan 第 2 步 / 任务 4.2） | **PASS** |

---

## 1. C1 迁移终态与清单

### 1.1 库内结构（`information_schema` 实测）

| 表 | 列数 | 索引 | 生成列 |
| --- | --- | --- | --- |
| `t_claim_task` | 26 | `PRIMARY(id)`、`uk_tenant_task_no(tenant_id,task_no)`、`uk_tenant_active(tenant_id,active_key)`、`idx_tenant_assignee_status(tenant_id,assignee_id,status,priority,created_at)`、`idx_tenant_dept_status(tenant_id,assign_dept_id,status,created_at)`、`idx_tenant_claim_type(tenant_id,claim_id,task_type)` | `active_key` `VIRTUAL GENERATED`（表达式 161 字符） |
| `t_claim_assign_rule` | 20 | 主键 + `uk_tenant_rule_name` 等 | 无 |

生成列表达式（活库原文，托管区块同步后与 `bone-init.sql` 逐字一致）：

```sql
active_key varchar(64) GENERATED ALWAYS AS (
  if((status in ('PENDING_CLAIM','ASSIGNED','PROCESSING') and (deleted = 0)),
     concat(claim_id, ':', task_type), NULL)
) VIRTUAL
```

### 1.2 迁移账本与清单

| 判据 | 实测 |
| --- | --- |
| `schema_migration_journal` 0125 行 | `order_no=125`、`success=1`、`duration_ms=96`、`source=apply` |
| journal sha256 vs 磁盘 | `ae136932b2ee…8794e` **完全一致**（⇒ 0125 此后不可再改，含注释） |
| `check-migration-manifest.py` | `132 个文件 / 125 个可执行脚本 / order 1..125 连续 / 重号组 9 个已登记 / 已下线 0` |
| `check-migration-init-coverage.py` | `init 174 张 / 终态存活 88 / 已覆盖 88 / 待并入 0 / blocked 5`；列级 `291 列新增 / 缺口 0` |
| `sync-init-from-live.py --check` | `OK: 迁移并入区块与活库一致（36 张表）` |
| `check-ddl-doc-sync.py` | `DDL 真源 174 / 规范 §2 清单 174` → 一致 |

> 本轮实测发现并修复的一处真实缺口：两张新表已并入 `bone-init.sql` 的**托管区块**，却未登记进 `sync-init-from-live.py` 的 `MERGED_SOURCES`（与文件中 0089 / 0101 / 0122 / 0124 的历史先例同款）。未登记时 sync 重建区块会**把它们抹掉**，`--check` 判「多表（init 有、活库无）」。

---

## 2. C2 分页同源（真实数据，非构造）

同源机制：`ClaimTaskRepository` 内私有 `myTaskCriteria(...)` 是唯一口径，`findMyTasks`（多分页参数）与 `countMyTasks`（只有过滤参数）都复用它；应用层只负责组装 `PageResult`。两道判据同时盯这件事——仓储单测钉「同一组过滤实参」、架构门禁钉「count 实参 = find 去掉分页参数后完全一致」。

取活库最大租户（`tenant_id=0`，129 条在办），按 `priority, created_at, id` 全序分页：

| 指标 | 实测 |
| --- | --- |
| `total` | 129 |
| 每页行数（`size=20`） | `[20,20,20,20,20,20,9]`，共 7 页 |
| 逐页行数之和 | 129 |
| 跨页 ID 去重数 | 129 |
| 判据 | `sum==total` ✅ / `distinct==total` ✅ / 每页 ≤20 ✅ |

### 2.1 本批修掉的一个真实缺陷（对照实验）

首轮探针（137 条在办）出现 `sum=137` 但 `distinct=135` —— **并列排序导致跨页重复 2 行、漏 2 行**。根因：`ORDER BY priority, created_at` 会并列，`LIMIT/OFFSET` 在并列区间内的取行不确定。修复：`myTasks` 与 `findPoolTasks` 均补 `orderByAsc(id)` 作最终 tiebreaker。修复后同一探针 `total=137 / sum=137 / distinct=137`。

这条是「假分页」缺陷的第二形态：不仅总数可能不准，**翻页本身也会错**，且用户侧只表现为「某条待办翻来翻去找不到」。

### 2.2 跨租户隔离（任务 3.1 的探针，用数据反证）

同一张 `t_claim_task` 里有 2 个租户、共 147 行（`tenant=0` 129 行、`tenant=1001` 18 行）。按 `tenant=0` 分页实测 `total=129`、逐页取回 129 条**互不重复**——若租户条件没生效，会取到 147 行。⇒ 隔离成立（`Criteria` 恒带 `tenant_id`，SQL 模板含 `/*bone:tenant*/` 锚点由 SDK 二次注入）。

结果字段最小化：`MyTaskRow` 只暴露 12 个业务字段（taskId / taskNo / claimId / taskType / status / priority / claimType / sourceChannel / claimAmount / assignedAt / claimedAt / createdAt），不含姓名、证件号等 PII；雪花 ID 一律 `String` 传输。

---

## 3. C3 索引命中与 P95

数据规模：`tenant_id=999002`，10,000 条在办（20 个归属人 × 每人 500 条），`ANALYZE TABLE` 后测。

### 3.1 EXPLAIN 四条查询形态

| 场景 | type | key | rows | Extra |
| --- | --- | --- | --- | --- |
| 我的待办（`tenant+assignee+status`，`ORDER BY priority,created_at,id`，`LIMIT/OFFSET`） | `range` | `idx_tenant_assignee_status` | 502 | `Using index condition; Using where; Using filesort` |
| 部门池（`tenant+assign_dept+status`） | `ref` | `idx_tenant_dept_status` | 1 | `Using where` |
| 生效唯一性查找（`tenant+claim+task_type+status IN`） | `ref` | `idx_tenant_claim_type` | 1 | `Using where` |
| 案件下任务（`tenant+claim`，`ORDER BY created_at,id`） | `ref` | `idx_tenant_claim_type` | 1 | `Using where; Using filesort` |

> `Using filesort` 记录在此不掩饰：`status IN (...)` 使索引无法直接提供 `(priority,created_at)` 的全局有序，需对 502 行做内存排序。判据因此不靠「有无 filesort」，而靠 P95 实测（下节）。

### 3.2 100 次采样（随机归属人 × 随机偏移，预热 10 次后计时）

| P50 | P95 | max | min | 判据 |
| --- | --- | --- | --- | --- |
| 1.41 ms | **1.85 ms** | 2.09 ms | 1.10 ms | P95 < 300 ms → **PASS（余量 162×）** |

---

## 4. C4 并发互斥（真实双线程，各 50 轮）

| 探针 | 做法 | 实测 |
| --- | --- | --- |
| 并发双建同案同类型生效任务 | 两线程同时 `INSERT` 同一 `(tenant, claim_id, task_type)`，`Barrier` 对齐起点 | 50/50 轮均 `ok=1` + 另一条 `1062 唯一键冲突`；落库恰 50 行（应 50） |
| 并发双领取同一行 | 两线程同时 `UPDATE ... WHERE id=? AND version=?` | 50/50 轮受影响行数之和恒 `1` |
| 终态释放唯一性 | 将一行置 `DONE` 后查 `active_key`，再同案同类型重开 | `active_key=NULL`，重开成功 |

结论：行级互斥由**数据库**保证（生成列唯一键 + `@Version`），不依赖应用层先查后写；这也正是方案 C（分布式锁）被否的理由被实测坐实。

---

## 5. C5 灰度可观测

脚本：`scripts/ops/claim-assign-gray-stats.sql`（按小时分桶）+ `scripts/ops/claim_assign_gray_report.py`（判定与退出码）。

构造 32 行验证数据（独立租户，三个小时桶）后逐路径实测：

| 路径 | 期望 | 实测 |
| --- | --- | --- |
| 全窗口 `--since 08:00` | 32 行 / 25 命中 / 0.7812 / exit 1（低于 0.90） | 分桶 `08:00 10行/5命中/0.5`、`09:00 10/8/0.8`、`10:00 12/12/1.0`；合计 0.7812；**exit 1** |
| 仅好窗口 `--since 10:00` | 12/12=1.0 / exit 0 | 1.0000 → **exit 0**；输出「✅ 命中率 1.0000 达标」 |
| 只报数 `--min-hit-rate 0` | exit 0 且**不得**说「达标」 | 输出「未启用阈值判定」→ exit 0 |
| 注入串 `--since "…' OR '1'='1"` | exit 2（格式校验挡住） | **exit 2**，未触库 |
| 未来窗口 | exit 0（干净环境不判红） | 「无分配记录」→ exit 0 |

补充两条反向判据：

- **`BACKFILL` 必须排除**：若未排除，窗口内行数应为 179（32+147 行回填），实测 32 ⇒ 排除生效。回填是照抄既有归属人，算进命中率等于用回填数据冒充规则效果。
- **`--tenant` 必须真下推**：`--tenant 999001` 得 32 行、`--tenant 1` 得「无记录」⇒ 过滤下推到 SQL，而非事后筛选。首版该参数只打印提示不真过滤，已修。

### 5.1 在途回填（任务 4.1）

| 步骤 | 实测 |
| --- | --- |
| dry-run 探测 | 147 条在途赔案：107 条有既有 `first_reviewer_id`、40 条入池 |
| `--apply` | 写入 147 行，终态断言通过（生效任务数=1、状态一致、归属一致） |
| 幂等复跑 | `OK: 无需回填`（确定性主键 `sha1(tenant:claim:type)[:15]` + `INSERT IGNORE`） |
| 库内核验 | 「入池却带归属人」=0、「同案同类型多生效」=0 |

---

## 6. 5.3 变异验证（证明判据有判别力）

本批开始时**两处判据都不存在**：`check-slice-truncation.py` 的 G33 只扫 `bone-metadata-app`，全仓无任何门禁检查「工作台把分页 size 写死」或「`total` 由整表 count 得出」。

对既有 `check-paging-total-normalize.py` 先做变异摸底（4 种形态，均**未转红**）：

| 注入到 `ReviewWorkbenchPage.tsx` 的变异 | 门禁结果 |
| --- | --- |
| `setTotal(resp?.data?.data?.length ?? 0)` | 绿（`?? 0` 被当合法归一） |
| `setTotal(resp?.data?.data?.length)` | 绿（正则只匹配 `.total`，`.length` 不在射程） |
| `setTotal(Number(resp?.data?.data?.length))` | 绿（同上） |
| `const t = resp?.data?.total; setTotal(t)` | 绿（docstring 声称能抓，实测抓不到） |

⇒ 该门禁管的是 `.total` 的**类型归一**，不判「这个 total 是不是真总数」。故把两条判据补进已接线的 `check-synapclaim-architecture.py`（判据三 / 判据四），并同批补负向探针与正向互证。

### 6.1 对真实代码的变异（两处均转红）

| 变异 | 注入点 | 门禁输出 |
| --- | --- | --- |
| A 前端固定切片 | `size: pageSize` → `size: 50` | `RED`：`ReviewWorkbenchPage.tsx:210: 分页 size 写死为 50` |
| B1 整表判断 | `countPolicyPage(tenantId, keyword, configStatus)` → `countPolicyPage(tenantId)` | `RED`：`count 少了过滤实参 ['keyword','configStatus']` |
| B2 细粒度（互补） | → `countPolicyPage(tenantId, keyword)` | `RED`：`缺 configStatus` |

B2 是关键：只漏一个过滤条件也必须判红，否则「阈值式/模运算式退化」无从发现。

### 6.2 判据自身的判别力（`--self-test`）

```
PASS 正向基线：子域归属 / HC-003 / 分页同源 / 无固定切片 均合规
PASS 负向探针 quota 错挂 Config
PASS 负向探针 子域重复归属
PASS 负向探针 映射与磁盘不一致
PASS 负向探针 响应信封直出领域实体
PASS 负向探针 total 整表判断（count 少过滤条件）
PASS 负向探针 total 来源不明（无配对 find*Page）
PASS 负向探针 分页 size 写死成 50
PASS 正向互证 受控 size 变量与单条探测 size: 1 / 表格 pageSize: 20（不误报）
self-test: 7/7 负向探针判红，1 个正向互证不误报，基线全绿
```

对真实仓库四个判据全 `PASS`（零违规）——过程中修掉三处自身误报，均属「判据过宽」而非「代码有问题」：

1. 注释里的示例 `size: 50` 被判红 ⇒ 加块注释剥离（保留行号）；
2. `count` 省略 `sortBy`（排序不影响总量）被判「不同源」⇒ 允许剥离分页/排序实参后再比对；
3. 配置页 `{ page: 1, size: 200 }` 拉下拉选项被判「固定切片」⇒ 射程收到 `*WorkbenchPage.tsx`（作业台列表页）。

---

## 7. 5.2 全量门禁：红点归因与修复

首轮 `scripts/check.sh`：`CHECK_EXIT=1`、触发熔断（连续 3 次失败）。剥 ANSI 后按步骤归因，**9 个步骤有红点**。逐条处置：

### 7.1 由本批引入（已修）

| 步骤 | 红点 | 处置 |
| --- | --- | --- |
| [40] 迁移并入区块 ↔ 活库 | `多表 t_claim_task / t_claim_assign_rule` | 两张表登记进 `MERGED_SOURCES` + 跑 `sync-init-from-live.py` 重生成区块 |
| [41] 造数资产同步义务 | `C3 域 claim 的数据契约变更未同步造数/用例` | 新增 E2E 造数场景 `claim-task-assignment`（15 条不变量 + 期望值 oracle），见 §7.3 |
| [48] synapClaim 架构逻辑边界 | `子域清单与磁盘不一致`（磁盘多出 `task`） | `task` 子域登记进 `synapclaim-domain-context-map.json`（归 `Claim` 上下文，与 review/inspection 同属作业执行面） |
| [42] 门禁脚本自身单测 | `test_scripts_catalog` 判红：2 个新脚本未编目 | `scripts/README.md` §4/§8 补登记；`test_bone_data` / `test_e2e_datagen` 补新场景断言 |
| [55] DDL 真源 ↔ 规范 §2 | `文档漏表 t_claim_task / t_claim_assign_rule` | 规范 §2.13 补登两表，计数 44 → 46，并记修订记录 |
| [1] Spotless / [3] 就近 ArchUnit | ① 3 个新增测试类格式违规（长行未断行、javadoc 折行）；② **架构违规**：`ArchitectureTest.read_side_dsl_only_in_query_layer` 判红——应用层 `myTasks` 直接构造 `Criteria` 并调用 `orderByAsc/page`，违反 DDD E-4.2「读侧 DSL 必须留在仓储/查询层后面」 | ① 模块 `spotless:apply`；② 把 Criteria 构造下沉到 `ClaimTaskRepository` 的 `default` 方法（私有 `myTaskCriteria` 共用），新增 `findMyTasks`/`countMyTasks`，应用层只组装 `PageResult`。复跑：三测试类 `Tests run: 30, Failures: 0`、`ArchitectureTest Tests run: 28, Failures: 0`、`BUILD SUCCESS` |

### 7.2 系统性缺陷：7 个门禁在 worktree 内恒空扫描（已修，影响全部四批）

`check-sql-template.py` 报「扫描 0 个」时查出根因：这些门禁用**绝对路径**的 `parts` 判断 `.worktrees` 并 `continue`，而 comet 每个批次都在 `.worktrees/<change>/` 内执行 ⇒ 绝对路径**每一条**都含 `.worktrees`，整个仓库（含本 worktree 自己）被跳过。

| 脚本 | 修复前症状 |
| --- | --- |
| `check-sql-template.py` | 扫描 0 个 → 判红（看似严格，实际零覆盖） |
| `check-doc-code-symbols.py` | 「仓库 Java 类 0 个」→ 377 处文档误报漂移 |
| `check-sdk-persistence.py` | 豁免清单 22 条「已无对应代码」 |
| `check-i18n-sync.py` | 「代码常量 0」→ 白名单 111 条全部显得「不需要豁免」 |
| `check-archunit-freeze-baseline.py` | 0 个模块被发现 |
| `check-error-code-duplication.py` | 零扫描 |
| `check-event-envelope-version.py` | 零扫描（两处） |
| `check-security-fail-open-default.py` | 零扫描 |

统一改为「按相对仓库根的路径判断」。修复后实测覆盖：SQL 模板 **67 个**、Java 类 **3794 个**、yml 6 + java 2461 个、ArchUnit 模块 **33 个**、i18n 代码常量 **393 个**。

> 这是本批最有价值的发现：门禁在这些 worktree 里**要么假红、要么假绿**，而「红」会被作者当成噪声无视、「绿」则完全没有保护。判据的存活状态不能只看「有没有被 check.sh 调用」。

### 7.3 与本批零交集（归因，不改动）

| 红点 | 归因证据 |
| --- | --- |
| `test_ai_native_readiness_evidence_gates`（6≠7 BLOCKED）、`test_ai_native_readiness_flywheel`（self_test 失败） | `RUNTIME_MODEL_COVERAGE` 依赖 `outputs/claims-e2e-result.json` 的 runtime 段，而 `outputs/` 被 `.gitignore:295` 忽略 ⇒ **新建 worktree 天然没有该产物**，判据落到 `RUNTIME_ONLY`。主树 dev 同一测试**通过**（该文件存在于主树工作区）。把同一份产物（15,547 B，2026-11-07 生成，内容未改）复制进 worktree 以对齐基线后，两个测试**通过**；该产物只会让判据更保守地报 `BLOCKED`，不可能造成假绿 |
| `[42] ❌ R6: check-orphan.py …` | 日志中的 ❌ 是门禁单测**内部负向探针**打印的，`python3 -m unittest discover -s scripts/tests` 实测 `UNIT_RC=0` |

### 7.4 最终结果

全量门禁共 **59 步**，末步为 `[59/59] DDD doc↔code 同步`。日志尾部字面输出：

```
[58/59] 对外 SDK 契约面（禁止空壳却宣称是 SDK，有契约面必带测试）...
  OK: 对外发布模块均有真实契约面且带测试
[59/59] DDD doc↔code 同步（编号残留 / 符号真实性 / 链接与锚点）...
DDD doc↔code sync lint: OK
✅ 本地自检通过！
CHECK_EXIT=0
```

与"空壳恒过"的判据对照（本轮实测值，非引用旧日志）：

| 步骤 | 字面结论 |
| --- | --- |
| `[38/59]` 迁移清单 | `OK: 迁移清单校验通过（132 个文件 / 125 个可执行脚本 / order 1..125 连续 / 重号组 9 个已登记）` |
| `[39/59]` init ⊇ 迁移建表终态 | `OK: 全量基线覆盖迁移建表终态（init 174 张 / 终态存活 88 张 / 已覆盖 88 / 待并入 0 / 活库探测 bone 233 张表）` |
| `[40/59]` 并入区块 ↔ 活库 | `OK: 迁移并入区块与活库一致（36 张表）`；调用行为 `sync-init-from-live.py --check`（带判定开关，非 report-only） |
| `[41/59]` 种子菜单 permission | `[G40] ✅ 菜单 permission 全部有对应权限点（222 个带权限菜单 / 103 个权限点 / 0 孤儿）` |
| `[42/59]` 门禁脚本单测 | `Ran 677 tests in 79.798s / OK`；`UNIT_RC=0` |
| 门禁存活（R1–R6） | `✅ 后端 blocking 门禁 15 条（基线 15）；空壳步骤 0 条；门禁脚本 1/1 已接线（其中 1 个刻意留 CI-only，0 个孤儿豁免，均已在 allowlist 登记）` |

**关于日志里 7 处 `❌` 的诚实说明**：它们全部落在步骤 `[42/59]` 的**区间内**（第 1159–1235 行），是该步门禁单测的**负向探针**打印（`幽灵菜单 claim:phantom` 应判红、空清单应判红、`R6` 孤儿门禁应判红等），不是真实红点。判据以 `CHECK_EXIT=0` + 末行「✅ 本地自检通过！」为准，逐步骤的 `❌` 才是真红点——本轮无。

---

## 8. 汇总

| 项 | 结果 |
| --- | --- |
| C1 迁移终态 | PASS（26/20 列、6 索引、1 生成列、journal sha256 一致、清单 M1–M11 全绿、init 174/待并入 0） |
| C2 分页同源 | PASS（129 = 129 = 129；并修复并列排序真实缺陷 135→137） |
| C3 索引 + P95 | PASS（4 形态均命中；P95 1.85 ms vs 阈值 300 ms） |
| C4 并发互斥 | PASS（50/50 轮恰 1 成功；乐观锁受影响行数恒 1；终态可重开） |
| C5 灰度可观测 | PASS（5 路径行为正确；BACKFILL 排除；`--tenant` 真下推） |
| 5.3 变异验证 | PASS（固定切片 + 整表 total 两处均转红，含只漏一个过滤条件的细粒度变异） |
| 5.2 全量门禁 | PASS（59 步；首轮 9 个红点步骤 → 0；`CHECK_EXIT=0` + 末行「✅ 本地自检通过！」；7 个门禁的 worktree 空扫描缺陷已修） |
| 单元测试 | 新增 30 个（`TaskAssignerTest` 12 / `ClaimTaskTest` 10 / `ClaimTaskApplicationServiceTest` 8）全绿；E2E 造数自测 17/17 绿；门禁单测全集 `UNIT_RC=0` |
| 联调 E2E | `scripts/e2e/synap-claim-task-workbench-e2e.py` 17/17 项 PASS（真实服务 + MySQL 双证据源），见 §9 |
| 前端 | `tsc` RC=0、`eslint`（改动页）RC=0、`vite build` 3368 模块 transformed / BUILD_RC=0 |

### 8.1 遗留与后续（非本批阻断项）

1. **`--tenant` 之外的多租户口径**：灰度 SQL 现在支持单租户下推，但「跨租户对比」仍需人工拼多次执行；批 4 若做统一报表再抽象。
2. **`check-ddl-doc-sync.py` 的抽取口径偏宽**：§2 内任何反引号 token 都被当表名，作者写字段级说明就会假红（本次因此改了措辞）。批 4 已有「设计文档引用的列必须存在于 DDL」的同类计划，建议同批收紧为「只认表格/清单位置的 token」。
3. **`check-paging-total-normalize.py` 名称与射程不符**：它只判 `.total` 的类型归一，不判语义同源（§6 已实测）。语义部分现由 `check-synapclaim-architecture.py` 判据三承担，但仅覆盖理赔模块；跨模块推广需独立窗口。
4. **`MappingPage.tsx` 的 `size: 200`**：拉标准码下拉选项，不是分页，故新判据不拦；但它是隐性截断上限（第 201 个标准码选项会静默缺失），建议后续改为搜索式加载。

---

## 9. 联调：新增端点的 E2E 与「门禁真的拦住了我」

### 9.1 为什么补这一节（门禁拦截记录）

首次提交时 pre-commit 的 `impact.py --staged --strict` **直接判红**：

```
本次改动触及 3 个端点，已覆盖 1 个：
    ✓ POST   /api/v1/claims/tasks/{taskId}/claim
  ❌ 零覆盖 2 个端点（本次变更引入/涉及，必须补用例）：
    ✗ GET    /api/v1/claims/tasks/my
    ✗ GET    /api/v1/claims/{claimId}/tasks
阻断：暂存的 Controller 变更存在『零覆盖端点』。
```

这是本批**唯一一次**自己被门禁挡住，恰好证明它是有生命力的（而不是"接上线但恒 exit 0"）。
处理方式是补真实用例，**没有**用 `BONE_E2E_IMPACT=off` 绕过（那属于纯重构通道）。

### 9.2 用例设计（`scripts/e2e/synap-claim-task-workbench-e2e.py`）

复用同目录既有 `e2e_synap_claim.py` 的登录 / 调用 / 直连 MySQL 助手（不复制第三份），
每条断言都是「接口 2xx + MySQL 第二证据源」，共 17 项：

| 编号 | 断言 | 实测结果 |
| --- | --- | --- |
| T1a/T11 | 我的待办可读；`taskId` 为字符串（JS 53 位精度） | `http=200 total=114 size=20`，首行 `742005683569421475` |
| T1b | `total` 与库内同源 | `api=114 db=114` |
| T1c/T1d | 逐页累加 == `total` == 去重数；跨页无重叠（`size=7` 质数分页，必留不满尾页） | `页数=17 行数=114 total=114 去重=114` |
| T2/T2b | 排序全序 `(priority, createdAt)`；重复请求逐位一致（并列不漂移） | 17 页全序；20 行逐位相同 |
| T3 | 归属是写入期事实：`PENDING_CLAIM` 行不得有归属人 | 核对 20 行，0 异常 |
| T4 | 某案任务列表与库内 `deleted=0` 集合完全相等 | `api=1 db=1` |
| T5 | 同案同类型至多一条生效任务（全库扫描） | 无重复生效行 |
| T6 | 终态行 `active_key` 恒为 NULL（不占唯一性） | 违反行数 = 0 |
| T7 | 自领成功：`status→PROCESSING` + `claimed_at` 落库 | `http=200 assignee=1 claimed_at=2026-11-16 12:50:03.557` |
| T8 | 重复领取 409 `SC_CLAIM_TASK_CONFLICT` 且 `claimed_at` 不被二次改写 | `http=409 errorCode=SC_CLAIM_TASK_CONFLICT 未变=True` |
| T9 | 并发双线程同一任务：期望值由起始状态决定（入池⇒恰 1 成功；已领⇒恰 0 成功） | 起始 `PENDING_CLAIM`，`200=1 409=1` |
| T10a | 伪造 `X-Tenant-Id: 1001` 无效（租户取 token claim，§10.3） | 返回 20 行，越租户 0 行 |
| T10b | 另一租户身份要么被授权层拒绝，要么只见自家数据 | `http=403`（拒绝，未泄露任何行） |

> T10 为什么不用「同租户第二身份」：本 dev 库里 `mdapprover2/mdapprover3` 并不存在（旧用例假设有），
> 而同租户唯一可用账号就是 `admin`。为了**不改别人密码**来凑用例，改用另一租户的既有账号
> `tenant_admin`（tenant 1001）——租户头与 token claim 必须一致，探针强度反而更高。

### 9.3 运行前置（这条用例需要真实服务）

```bash
# 用 worktree 的 fat-jar 覆盖并重启 claim（旧 jar 无新端点，直接跑会 404）
mvn -o -pl bone-apps/synap-claim/synap-claim-app -am -DskipTests clean package
cp .run-jars/claim.jar .run-jars/claim.jar.bak-$(date +%Y%m%d-%H%M)
cp <worktree>/target/synap-claim-1.0.0.jar .run-jars/claim.jar
bash .run-jars/start-claim.sh          # 杀 8087 → 起新 jar → 等 /actuator/health
python3 scripts/e2e/synap-claim-task-workbench-e2e.py
```

实测：`[12:46:55] claim listening on :8087 (http 200, 5s)`，随后 E2E `通过 17/17 项`、`E2E_RC=0`。
重启前先确认 `unzip -l claim.jar | grep ClaimTaskApplicationService` 有命中——只看「服务起来了」会
把旧 jar 当成新 jar（本次就是这么先发现 404 的）。
