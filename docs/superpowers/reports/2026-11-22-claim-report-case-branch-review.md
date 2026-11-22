# 报案 1:0..N 双分支实施方案评审材料

> 产出日期：2026-11-22
> 用途：同一需求在两处分支各有一套实现，合并前必须二选一。本文只做取证与对比，**不含已生效的决定**。
> 取证方式：`git diff dev...<branch>` / `git show <branch>:<file>` / 活库 `information_schema` 查询，全部命令可复现。

---

## 0. 一页结论

**两个分支分叉于 `2a2e40134`，各自独占的内容只有报案拆分那一套；对方没有任何本分支缺失的功能。**

| | `comet/claim-report-case-one-to-many`（本分支） | `comet/claim-id-linkage`（对方） |
|---|---|---|
| 分叉点 | `2a2e40134`（报案号 Feistel 内核） | 同一分叉点 |
| 独占提交 | 6 个（1 功能 + 4 文档 + 1 chore） | 2 个（1 功能 + 1 网关路由 fix） |
| 相对 dev | +16 提交 / 90 文件 / +5575 −164 | +14 提交 / 68 文件 / +3543 −87 |
| 报案 1:N 拆分 | 有 | 有 |
| 赔案号↔保单号互链前端 | **有**（共同祖先 `baf3f70a7`） | **有**（同一提交，blob 相同） |
| 编号内核（Feistel/Base32/Luhn） | 有（`2a2e40134`，0138 即出自此） | 引用同一份 0138 |

⇒ **可以整条取舍，不存在功能损失。** 这一点在初稿里判错并已更正，过程见 §4。

**需要裁决的三件事**（详见 §5）：

1. 报案拆分的域模型与迁移保留哪一套 —— 建议保留本分支。
2. HTTP 接口面统一到哪一套路径 —— 两套现已分叉，前端只能跟一套。
3. 活库残留列 `t_claim_case.report_no` 的清理（脚本 0140 已交付，执行时机见 §6）。

---

## 1. 事实基线

### 1.1 迁移号冲突

| 项 | 本分支 | 对方分支 |
|---|---|---|
| `0138_synap_claim_claim_no_global_unique.sql` | 有，blob `531d4e9e…` | 有，blob `531d4e9e…`（**字节相同**） |
| `0139_*.sql` | `0139_synap_claim_report_case_split.sql` | `0139_synap_claim_report_one_to_many.sql` |

- `dev` 上**两者都没有**，两条分支均未落地。
- 0138 相同 ⇒ 无冲突。冲突面收敛到 0139 本身：**同号不同名**，会同时触发迁移清单门禁的 M4（序列须等于文件名词典序）与 M6（重号登记）。
- 结论：0139 只能留一个文件。另一套若要保留，必须改号，而改号并不能消除语义重复——两套都在 `CREATE TABLE t_claim_report`。

### 1.2 活库现状（已被两套混合应用）

`bone` 库当前同时存在（2026-11-22 18:58 实测 `information_schema`）：

- `t_claim_report`（双方都建，DDL 形状一致），646 行
- `t_claim_case.report_id`，**`is_nullable=NO`**，0 行 NULL（本分支回填后 `MODIFY` 收紧生效）
- `t_claim_case.report_no`，`is_nullable=YES`、注释「归属报案号(RP前缀,冗余展示)」、**0 行有值**（对方脚本添加；本分支的 `bone-init.sql`、实体与全部代码均无此列）

即活库结构是**两套的交集 + 一处孤儿列**，任何一套单独部署都不会产生这个形状。这就是必须出 0140 清理迁移的原因。

### 1.3 测试规模（各自分支，非合并态）

| | 本分支 | 对方分支 |
|---|---|---|
| `synap-claim-app` 全量 | 1662（Skipped 8，实际执行 1654） | 未在本机复跑 |
| 报案相关新增用例 | `ClaimReportTest` 14 / `ClaimReportApplicationServiceTest` 7 / `ClaimCaseAppendCaseTest` 6 / `ClaimReportControllerTest` 11 / `AppendCaseReqTest` 5 / `ClaimIntakePortAdapterTest` 3 | `ClaimReportApplicationServiceTest` 6 / `ClaimSubmissionOrchestratorTest` 4 / `ClaimReportAndSplitTest` 5 / `ReportNoGeneratorTest` 7 |
| 前端 | 23 文件 / 214 用例 + tsc 零错 | 见 §4（含 id-linkage 用例，未拆分统计） |

> 对方的用例数只统计了报案相关四个文件，不代表其全量。**本表不能读成「我的测试更多 = 我的更好」**，两边覆盖面不同：对方有 id-linkage 的组件测试，本分支完全没有那部分。

---

## 2. 逐项对比

| # | 维度 | 本分支 | 对方分支 | 判别要点 |
|---|---|---|---|---|
| 1 | 建案事务边界 | `submit()` 单事务内建报案+首案，经 `ClaimIntakePort` 收口 | `ClaimSubmissionOrchestrator` **显式禁止 `@Transactional`**，两个 ApplicationService 各自开事务 | 见 §3.1 |
| 2 | `t_claim_case.report_id` | 先加可空列、回填、再 `MODIFY … NOT NULL` | `BIGINT DEFAULT NULL`，**保持可空** | 见 §3.2 |
| 3 | `t_claim_case.report_no` | **无此列**（单一真源，读侧 JOIN） | 有，冗余展示列；`findClaimWithItems.sql` 直读 `c.report_no` | 见 §3.3 |
| 4 | 存量回填 | §5 回填 `id = claim.id`、`report_no = CONCAT('RP', SUBSTRING(claim_no,3))`，纯 SQL | **无任何 DML 回填**（注释：系统未上线、无存量数据） | 见 §3.4 |
| 5 | 迁移幂等与终态断言 | 幂等 + 5 条终态断言 + 同名异形表负向验证 | information_schema 判定幂等 + 3 条 SIGNAL 断言 | 相当 |
| 6 | 建案接口 | 沿用 `POST /api/v1/claims`（建案即建报案+首案） | 新增 `POST /api/v1/claim-reports`（独立登记报案） | 见 §3.5 |
| 7 | 追加赔案接口 | `POST /api/v1/claims/reports/{reportNo}/cases` | `POST /api/v1/claim-reports/{reportNo}/cases` | 路径前缀分叉 |
| 8 | 查询接口 | `GET /api/v1/claims/reports/{reportNo}`（详情，含 cases） | `GET /api/v1/claim-reports/{reportNo}/cases`（仅列表） | 返回形状不同 |
| 9 | 关闭报案 | 无 API（`ReportStatus.CLOSED` 只能由后续用例/运维置位，已登记「刻意不做」） | `POST /api/v1/claim-reports/{reportNo}/close` | 范围差异 |
| 10 | 报案状态机 | `REPORTED` / `CLOSED` 两值，只表达「能否追加」 | 同两值 | 一致 |
| 11 | 幂等 | 键保留在赔案侧；`ClaimCase#getReportId()` 是新增的唯一归属判据；跨报案复用同键 → 400 并点名 | 键保留在赔案侧（`splitCase(command, key)`） | 一致 |
| 12 | 追加赔案继承 | 保单/计划/被保人/出险时间取自报案；请求体出现保单维度即 400 并点名 | 同类继承（`SplitCase` 命令） | 一致 |

---

## 3. 关键分歧深挖

### 3.1 事务边界：两种过 R9 的解法，代价不同

**R9 门禁**（`BoneDddArchRules.oneAggregatePerTransaction()`，E-5.1）按**聚合根类型数**计数，一个事务里出现两个聚合根即红。

- 本分支：`ClaimIntakePort` 把「建报案 + 建首案」收进一个端口，**保持单事务**。`ArchitectureTest` 28/28 绿。
- 对方：编排类注释明写「本类所有方法禁止加 `@Transactional`」，理由是加了外层事务后两个 `@Transactional` 默认 `REQUIRED` 会并入同一物理事务，等于绕过门禁的方法级盲区。

**分歧的实质不是风格，是原子性。**

对方方案下，「报案已建、首案写入失败」会留下一条 0 赔案的报案。在 1:0..N 模型里这不是非法数据（0 是合法基数），所以对方的选择**在语义上站得住**；但代价是：建案请求返回错误时，调用方重试会再建一条报案，而幂等键挂在赔案上——**报案层没有幂等键**，重复报案无人拦。

本分支的选择把两个写入放进同一事务，冲突整体回滚，重试是干净的。代价是必须引入端口层，且 R9 需要靠「按聚合类型计数 + 端口收口」通过，属于对门禁的正面处理而非绕过。

> 评审问一句就够：**建案失败时，你希望库里有没有那条报案？** 两种答案都能自圆，但必须明确写进契约。

### 3.2 `report_id` 可空性

- 对方：`DEFAULT NULL`。好处是有回填窗口、历史/旁路数据不炸；坏处是「无主赔案」这个状态在库里长期合法，任何漏写归属的 bug 都不会报错。
- 本分支：回填后 `MODIFY COLUMN report_id BIGINT NOT NULL`。**孤儿赔案在库层不可能存在**，漏写归属在写入时就炸。

本分支的活库对账结果（2026-11-22 18:58 查询）：`t_claim_report` 646 行、`t_claim_case` 648 行、**无主赔案 0 行**，说明回填覆盖完整，`NOT NULL` 可以安全加上。

> 该库是共享活库，行数会随其他会话与本机 E2E 执行漂移（半小时前同一查询是 640/640）。**判据看「无主赔案 = 0」与 `report_id` 的 `is_nullable=NO`，不要记行数。**

### 3.3 冗余列 `t_claim_case.report_no`

对方的 `findClaimWithItems.sql` 直接 `SELECT c.report_no, c.report_id`——**读侧依赖这个冗余列**。这意味着每次赔案写入都必须同步维护 `report_no`，否则详情页展示的报案号与 `t_claim_report` 的真值分叉，且分叉是静默的（没有约束能发现）。

本分支不引入该列，读侧 `LEFT JOIN t_claim_report` 取真值。

`LEFT JOIN` 而非 `INNER JOIN` 是刻意的：回填窗口内 `report_id` 尚未落地的历史行不能整行消失。

**活库现状已经证明冗余列会烂**：该列存在、可空、0 行有值。也就是说对方那条读路径在混合库上是**读不出报案号**的——这正好是「两套并行应用」最典型的事故形态。

### 3.4 存量回填

对方的前提是「系统未上线、无存量数据、零兼容负担」，因此迁移里没有任何 DML。

本分支的前提是有存量（活库 640 条报案即为回填产物），因此迁移含：

- §5 回填：`id = claim.id`，`report_no = CONCAT('RP', SUBSTRING(claim_no, 3))`
- 回填合法性依据：`BizNoCodec` 的**校验位只覆盖日期+载荷、不含前缀**，所以 `CL → RP` 前缀改写后校验位仍然成立，回填是纯 SQL，不需要在迁移里实现 Feistel。
- 迁移末尾 5 条终态断言，并对「同名但形状不符的存量表」做负向验证（`CREATE TABLE IF NOT EXISTS` 对这种表是静默 no-op）。

**这一项取决于一个业务事实：这套系统到底有没有存量。** 若确定无存量，本分支的回填段是无害冗余；若不确定，对方的空回填就是漏项。

### 3.5 接口面已分叉，前端只能跟一套

| 能力 | 本分支 | 对方 |
|---|---|---|
| 建案（含首案） | `POST /api/v1/claims` | `POST /api/v1/claims`（另有 `POST /api/v1/claim-reports` 单独登记） |
| 报案详情 | `GET /api/v1/claims/reports/{reportNo}` | — |
| 同案清单 | 含在详情里（`ReportDetail.report + cases`） | `GET /api/v1/claim-reports/{reportNo}/cases` |
| 追加赔案 | `POST /api/v1/claims/reports/{reportNo}/cases` | `POST /api/v1/claim-reports/{reportNo}/cases` |
| 关闭报案 | 无 | `POST /api/v1/claim-reports/{reportNo}/close` |

两个前缀 `/api/v1/claims/reports/…` 与 `/api/v1/claim-reports/…` **不能共存**于同一前端，否则同一资源有两个入口、网关白名单与 E2E 影响面映射各记一半。

> 注：新增端点必须同批补 `scripts/e2e/` 用例，否则 `impact.py --staged --strict` 会以「零覆盖」阻断提交。这意味着**换接口面 = 重写 E2E 用例**，成本要计入。

---

## 4. 一处误判的更正：id-linkage 不是对方独占

**初稿的错误结论**：曾判定 `ClaimIdLink` / `PolicyNoLink` / `policyDeepLinks` / `sclaimRoutes` 四组文件为对方分支独占，进而推出「不存在整条弃用对方这个选项」。

**错在哪**：取证只做了单边 —— 查了 `git ls-tree dev` 零命中，就推断「对方独占」，**没有查本分支自己有没有**。实际 `git ls-tree -r --name-only HEAD` 四组文件全在，blob 与对方逐字节相同：

```
bone-frontend/apps/bone-claim-app/src/components/ClaimIdLink.tsx    mine=d60bb1fa… theirs=d60bb1fa…
bone-frontend/apps/bone-claim-app/src/workbench/sclaimRoutes.ts     mine=8d9a4d21… theirs=8d9a4d21…
```

`git branch --contains baf3f70a7` 显示该提交在**两条分支上都存在**，且 `git merge-base --is-ancestor baf3f70a7 dev` 为假 —— 即它落在共同祖先与 dev 之间的历史里，两条分支都继承到了。

**正确结论**：id-linkage 是**共同祖先**，不是独占。两条分支的真实分叉点是 `2a2e40134`，各自独占提交如下：

```
本分支: dd0b42e5d e90aebb75 609bf7352 440e260f5 438d42e49 69e66f88b   (6)
对方  : bd3aaa0b2 c23d2b2c6                                            (2)
```

⇒ 取舍可以是整条分支级的，不存在功能损失；D2（id-linkage 剥离）因此**取消**。

**教训（保留在本文而非悄悄改掉）**：「对方有没有」和「我有没有」是两个独立问题。只查一侧就下「独占」结论，得到的是**误报**，而误报比漏报更贵 —— 它会让决策者基于一个不存在约束去做本不必做的额外工作（本文初稿的 D2 就是这样的额外工作）。

---

## 5. 需要裁决的三件事

> **裁决已定稿（2026-11-22）**：三件事按下述推荐方案采纳，决策已归档为
> [ADR-0047](../../../doc/architecture/adr/0047-claim-report-case-one-to-many-implementation-selection.md)
> （状态「已接受」）。本节保留当时的利弊对照，供追溯「为什么这么定」，不再作为待办事项。
> 唯一仍待执行的是 D4 的**执行时机**（需协调共享活库使用者），以及 D3 引申出的对方分支改动。

### D1 报案拆分的域模型与迁移保留哪一套

| | 保留本分支 | 保留对方分支 |
|---|---|---|
| 建案原子性 | 单事务，失败整体回滚 | 两个事务，失败留 0 赔案报案 |
| 孤儿赔案 | 库层不可能（NOT NULL） | 库层合法（可空） |
| 报案号真源 | 单一（JOIN） | 双（列 + 表，需手工同步） |
| 存量回填 | 有 | 无 |
| R9 | 端口收口 + 单事务 | 禁外层事务 |
| 落地成本 | 已有 4 个提交 + 1662 测试基线 + E2E | 需补齐回填、收紧 NOT NULL、删冗余列 |

**建议：保留本分支。** 决定性理由是 §3.3——活库已经出现「冗余列 0 行有值」这种静默分叉，而 NOT NULL + 单一真源能从库层消灭这一整类 bug。

### D2 id-linkage 的处置 —— 已取消（初稿误判）

不需要任何操作：它在共同祖先 `baf3f70a7` 里，两条分支都有、dev 都没有。无论 D1 选谁，id-linkage 都随分支一起走。更正过程见 §4。

### D3 接口面统一到哪一套

建议统一到 `/api/v1/claims/reports/…`（本分支）。理由：报案是赔案的父资源，挂在 `/claims` 下与既有 `POST /api/v1/claims` 同源；且本分支「建案即建报案+首案」不需要独立的「登记报案」入口——多一个入口就多一处可能建出 0 赔案报案。

若产品确实需要「先登记报案、后补赔案」，则应恢复对方的两步式；这是**产品口径问题，不是技术口径问题**，需要产品确认。

### D4 活库清理 —— 脚本已交付并验证，执行时机待定

**已交付**：`scripts/migration/0140_synap_claim_drop_case_report_no_redundant.sql`，已在 `migration-manifest.json` 登记为 `order 146`（清单与脚本同批入库，M11 已过）。不回改已发的 0139。

**已验证**（隔离库，不碰共享活库）：

| 场景 | 结果 |
|---|---|
| 列存在且全空 | 真删，§3 终态断言通过 |
| 同库再跑一遍 | 幂等 no-op，不报错 |
| 列有数据 | `SIGNAL` 中止（`ERROR 1644: 0140 ABORT: … has data, migrate it first`），**数据未删、列仍在** |

**变异验证**（证明断言有判别力，不是「通过即正确」）：

- 守卫阈值 `> 0` 改成 `> 999` → 场景 3 立刻转红「未 fail-closed」；
- 删除条件 `@has_col = 1` 改成 `= 0` → §3 终态断言抛出 `0140 FAIL: … still present after drop`。

后者尤其说明为什么 §3 必须有：「语句没报错」与「目标达成」是两件事，动态 SQL 的条件写反会以无害的 `DO 0` 通过。

**未在共享活库执行，理由**（这是判断，不是拖延）：

- 执行前探测：`information_schema.processlist` 有 **90 个连到 `bone` 的连接**，全部 `Sleep`，最长约 1773 秒 ⇒ 有其他服务（可能是对方分支）正持有连接池。删列会让对方代码里 `SELECT c.report_no` 的读路径立刻 500。
- `t_claim_case.report_no` 当前 0 行有值，删除不丢数据；但 `DROP COLUMN` 不可逆，且影响他人。
- 迁移执行属部署动作，应由 `scripts/migrate.py` 或 CI 做，以保证 `schema_migration_journal` 与库状态一致。

**顺带发现的一处账本不一致**：账本里只有对方的 `0139_synap_claim_report_one_to_many.sql`（order 145、`source=apply`），**本分支的 `0139_synap_claim_report_case_split.sql` 没有记账**，但活库里 `report_id` 已是 `NOT NULL`（本分支 §7 的收紧生效）—— 说明本分支的 0139 曾被手工执行、未记账。下次 `migrate.py` 运行会重放它（脚本幂等，行为无害），但账本这条缺口应当在正式部署前补齐或说明。

`bone-init.sql` 与模块参考态 `V25` 本就没有该列（本方案最终态形状），无需改动。

---

## 6. 复现命令

```bash
# 迁移号对比
git ls-tree -r --name-only dev -- scripts/migration | grep -E '013[89]'
for B in comet/claim-id-linkage comet/claim-report-case-one-to-many; do
  git ls-tree -r --name-only "$B" -- scripts/migration | grep -E '013[89]'
done

# 0138 是否字节相同
git rev-parse 'comet/claim-id-linkage:scripts/migration/0138_synap_claim_claim_no_global_unique.sql'
git rev-parse 'comet/claim-report-case-one-to-many:scripts/migration/0138_synap_claim_claim_no_global_unique.sql'

# report_id 可空性
git show 'comet/claim-id-linkage:scripts/migration/0139_synap_claim_report_one_to_many.sql' | grep -n 'report_id'
git show 'comet/claim-report-case-one-to-many:scripts/migration/0139_synap_claim_report_case_split.sql' | grep -n 'report_id'

# id-linkage 归属：必须查三侧，只查 dev 会得出相反的错结论
git ls-tree -r --name-only dev  -- bone-frontend/apps/bone-claim-app/src/components | grep -E 'ClaimIdLink|PolicyNoLink'
git ls-tree -r --name-only HEAD -- bone-frontend/apps/bone-claim-app/src/components | grep -E 'ClaimIdLink|PolicyNoLink'
git ls-tree -r --name-only comet/claim-id-linkage -- bone-frontend/apps/bone-claim-app/src/components | grep -E 'ClaimIdLink|PolicyNoLink'

# 逐字节比对两侧同一文件
git rev-parse 'HEAD:bone-frontend/apps/bone-claim-app/src/components/ClaimIdLink.tsx'
git rev-parse 'comet/claim-id-linkage:bone-frontend/apps/bone-claim-app/src/components/ClaimIdLink.tsx'

# 分叉点与各自独占提交（比 diff --stat 更能定位「谁多了什么」）
git merge-base HEAD comet/claim-id-linkage
git log --oneline "$(git merge-base HEAD comet/claim-id-linkage)..HEAD"
git log --oneline "$(git merge-base HEAD comet/claim-id-linkage)..comet/claim-id-linkage"

# 0140 迁移在隔离库的三场景 + 变异验证（不碰共享活库）
python3 /tmp/verify_0140.py
```

> zsh 下取 git 引用里的文件必须写 `"${B}:${P}"`（花括号 + 独立变量）。写成 `"$B:scripts/…"` 会被当成参数修饰符 `:s/…/…/` 吃掉路径，报 `ambiguous argument`。

---

## 附录 A：本分支 Comet `verify` 停在 Action `:20` 的源码级定论

**这不是操作失误，是 0.5.2 的一个状态-恢复缺口。** 结论对后续任何人接手这个 change 都适用。

### A.1 发生了什么

`comet guard … verify --apply -- mvn …` 于 18:36–18:45 真实跑完检查：

- 收据日志 `cdbf6950-…log` 尾行 `Tests run: 1662, Failures: 0, Errors: 0, Skipped: 8` + `BUILD SUCCESS`
- manifest `a4c11f11-…manifest` 已写

但 SDK 在收尾清理 `.write.lock.contenders/*`（一次性 50 个删除）时被沙箱以 `SAFE_DELETE_BULK_CONFIRM_REQUIRED` 终止，**结果回填没落地**：

- `…/classic-sdk-checks/135f8cd…/sdk-aef7…json` 停在 `"status": "started"`，`receipts: []`
- Run 的 action `:20` 停在 `running`，`claim.token = 4ee7594b-…`

### A.2 五条恢复通道逐条封死（源码引用）

| 通道 | 源码约束 | 本 case |
|---|---|---|
| `record-outcome` | `classic-sdk-check-recovery.js:134` — `completed = record?.status === 'completed' && record.result ? … : null`；`:205` 仅 `completed` 为真才生成该模板 | `record.status = started` ⇒ 模板根本不生成 |
| `retry` + `reconciliation` | `runtime-action.js:159 / :386` — `resolution !== 'not-executed'` 即拒 | 已执行，声明 not-executed 属伪造 |
| `mark-unknown` | 引擎无强校验，但语义是「原尝试完全未执行」；且 `terminal()` (`runtime-action.js`) 只认 `succeeded/failed/cancelled`，`unknown` 不解除占用 | 已执行 |
| `acknowledge-stopped` | `runtime-action.js:209` — `if (!terminal(action)) throw 'ACTION_TERMINAL'` | `running` 非终态 ⇒ 拒 |
| `cancel` | `runtime-service.js` `cancel()` 把全部 action 置 `cancelled` 并把 `run.status` 置 `cancelled` | Run 级，连带封死 archive 四条路 |

### A.3 根因

`acknowledgeStopped` 在引擎层存在（`engine/runtime-service.js:1162`、`app/commands/runtime.js:15`），但**只有 Native 域调用它**（`native-sdk-checks.js` 的 `reconcileInterrupted` 回路）。Classic 域（`classic-sdk-check-recovery.js`）**没有任何调用点**。

⇒ Native 有「执行器已停、结果未回填」的收口回路，Classic 没有。Classic 只有「record 已 completed 才能回填」一条路，一旦进程在「收据写完、record 未写」的窗口里被杀，就永久卡死。

### A.4 处置

- **不 cancel、不 mark-unknown、不伪造回执**（前两者会让归档彻底不可达，后者是伪造证据）。
- 以**已提交的验证报告 + Git 提交**为最终事实。
- 已记录到项目长期记忆，供后续同类情况直接引用，不再重复试探。
