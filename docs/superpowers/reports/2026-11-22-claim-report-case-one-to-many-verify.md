# 验证报告：报案与赔案拆分（1:0..N）

- **变更**：`claim-report-case-one-to-many`
- **提交**：`69e66f88b`（73 文件，+7115/−308）
- **基线**：`2a2e40134`
- **验证时间**：2026-11-22
- **验证命令**：`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test`

## 1. 验证范围与判定口径

本次判定不看「文件是否存在」，只看**行为是否被证据钉住**。四类判据：

| 类别 | 判据 | 为何不能用更弱的证据 |
|---|---|---|
| 单元/集成 | 全模块测试通过且新增用例有判别力（变异验证） | 「新增了测试」不等于「测试会红」 |
| 数据层 | 活库结构 = 实体元数据 = 三处 DDL 真源 | 内存替身不碰真表，漏列只在首个写入方接入时以 1054 暴露 |
| 端到端 | 真库 + 真 HTTP 跑通主链路 | 内存替身里 `report_id` 恒有值、行数恒不重复，测不出「挂错报案」 |
| 门禁 | 与本次改动相关的门禁逐条绿；基线存量红须逐项举证归属 | 「全绿」若来自未接线的门禁，等于没保护 |

## 2. 独立验证证据

### 2.1 后端测试

```
mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test
→ Tests run: 1662, Failures: 0, Errors: 0, Skipped: 8   BUILD SUCCESS
```

8 例跳过逐项有据，实际执行 **1654** 例：`ClaimLifecycleE2ETest` 3 例（整类由 `LocalE2EEnvironment` 守卫，仅 `-De2e.local=true` 时运行，见 2.3「端到端」）、`SettlementGuardContractTest` 5 例（本次未触碰的存量跳过）。

> **口径校正**：本报告初稿写的 `1661 / Skipped: 7` 是补入第 3 个 E2E 用例之前的一次手工跑数。Comet build 与 verify 两次 check 的收据（`db994036-878a-…log` / `cdbf6950-878a-…log`）**均为 1662 / 8**，以收据为准。

关键子集：

| 测试类 | 用例数 | 结果 | 覆盖的判定 |
|---|---|---|---|
| `ArchitectureTest` | 28 | 全绿 | R9「一事务一聚合」——`ClaimIntakePort` 收口后仍严格约束其余场景 |
| `BizNoCodecTest` | 17 | 全绿 | 双射（1 万 round-trip / 10 万零碰撞）、前缀隔离、`CL→RP` 改写后校验位仍合法 |
| `ClaimReportTest` | 14 | 全绿 | 号派生于 id、`close()` 幂等、状态机只两值 |
| `ClaimCaseAppendCaseTest` | 6 | 全绿 | 1:N 归属、幂等重放不新增、幂等键跨报案 400、租户锚点 |
| `ClaimReportApplicationServiceTest` | 7 | 全绿 | 继承而非覆盖、CLOSED 422、缺失 404、`allCasesFinished` 派生 |
| `ClaimReportControllerTest` | 11 | 全绿 | 201/404/422/400 四态映射、信封 code=201、幂等键 required、授权面 |
| `AppendCaseReqTest` | 5 | 全绿 | 保单维度字段出现即点名拒绝，无关未知字段不误伤 |
| `ClaimIntakePortAdapterTest` | 3 | 全绿 | 报案先于赔案写入、任一失败即回滚语义 |
| `EntityTableDdlCoverageTest` | 3 | 全绿 | 实体↔init 建表一致、`uk_tenant_invoice` 未被报案维度稀释 |

### 2.2 前端

```
tsc --noEmit -p apps/bone-claim-app/tsconfig.json   → 0 error
vitest run --dir apps/bone-claim-app                 → 23 files / 214 tests passed
```

其中新增：进度页 6 例（赔案号与报案号各取自己来源、报案号缺失降级、同案清单、CLOSED 禁用）、申请页 6 例（**真不渲染**事故字段、请求体不含保单维度、CLOSED 禁用、报案不存在禁用）、幂等键 3 例。

### 2.3 端到端（真库 + 真 HTTP）

```
mvn -o -pl ... -am test -Dtest=ClaimLifecycleE2ETest -De2e.local=true
→ Started ClaimLifecycleE2ETest in 5.474 seconds
→ Tests run: 3, Failures: 0, Errors: 0
```

贯通链路：建案 → 报案详情（事故事实 + 1 笔赔案 + `allCasesFinished=false`）→ 追加第二笔（201、同报案号、新赔案号）→ 同幂等键重放（复用同一笔、`caseCount` 仍为 2）→ 400（携带 `policyNo`，`errorCode=SC_CLAIM_REPORT_CONTEXT_MISMATCH`）→ 404（`SC_CLAIM_REPORT_NOT_FOUND`）。

> 顺带修活一套已停用的门禁：该 E2E 因需本地库而不进 CI，`Idempotency-Key` 成为必传头之后两个既有用例恒 400 却无人发现。本次补齐后 3/3 通过。

### 2.4 数据层

迁移 `0139` 在本地 MySQL 实跑：

- 正向一次 + 重跑一次，两遍幂等；640 赔案 → 640 报案，`report_id` 无一 NULL，无悬空引用，状态分布 `CLOSED 324 / REPORTED 316`。
- **负向验证**：故意建同名异形 `t_claim_report` 后重跑，迁移在 §1 形状断言处以 `ERROR 1054 Unknown column 'plan_id'` 中止——证明 `CREATE TABLE IF NOT EXISTS` 的静默 no-op 陷阱确实被拦住。
- 活库列级对账：`t_claim_report` 18 列 / 实体 13 个业务列**全部命中**（无「实体有活库无」的写入必炸项）；`t_claim_case.report_id` 存在且 `NOT NULL`；唯一键 `uk_report_no(report_no)`、`uk_claim_no(claim_no)`、`uk_tenant_invoice(tenant_id, invoice_code, invoice_no, deleted)`——**不含 `report_id`**。

### 2.5 门禁（逐条实跑）

| 门禁 | 结果 |
|---|---|
| `check-migration-manifest.py` | 152 文件 / 145 可执行 / order 1..145 连续 |
| `check-migration-init-coverage.py` | init 200 张 / 终态存活 108 / 待并入 0 / 列缺口 0 |
| `check-ddl-doc-sync.py` | 200 = 200 |
| `check-ddl-duplicate-column.py` / `check-ddl-required-columns.py` | 通过（存量豁免已登记） |
| `check-error-code-landing.py` / `-duplication.py` / `check-i18n-sync.py` | 严格层新增违规 0；错误码 406/台账 375/待覆盖 295 |
| `check-sclaim-naming.py` | G1–G5 合规 |
| `check-doc-code-symbols.py` / `check-ddd-doc-drift.py` | 通过 |
| `doc/ai-product/synapClaim/tools/check_all.py` | 18/21 通过；3 项为存量红（见 §4） |
| `scripts/e2e/impact.py --staged --strict` | 触及 2 端点 / 覆盖 2 端点 |
| `check-data-realism.py`（R1–R5）/ `scripts/e2e/lint.sh` | 无新增违规；py_compile + node --check OK |

## 3. 反向验证：变异测试

「测试通过」不足以证明测试有判别力，故对两处最关键断言做变异：

| 变异 | 期望 | 实测 |
|---|---|---|
| 把 `report_id` 塞进 `uk_tenant_invoice`（发票唯一性被报案维度稀释 ⇒ 同票可跨赔案重复赔付） | 转红 | ✅ `EntityTableDdlCoverageTest.invoiceUniqueKeyIsNotScopedByReport` 失败并输出后果说明 |
| 短路 `appendCase` 的「幂等键归属校验」（`if (false)`，静默把赔案挂到当前报案） | 转红 | ✅ `ClaimCaseAppendCaseTest.rejectsKeyOwnedByAnotherReport` 失败 |

两处均已复原并复跑至绿。

## 4. 已知基线存量红（与本改动无因果关系）

本 worktree 的 HEAD 基线自身存在 5 项红点，均已确认其路径不在本次改动集内：

| 编号 | 红点 | 归属举证 |
|---|---|---|
| R1 | `bone-procurement-app/src/services/qualificationApi.ts` 2 处 ID 声明为 number | 采购域文件，本次 0 改动 |
| R2 | `scripts/check-orphan.py` 已接线但未在 `gate-liveness-allowlist.json` 登记（R6） | 本次未增删门禁脚本 |
| R3 | 种子与活库漂移：`iam_menu` 活库 300 行 ≠ 种子 288 行 | 共享活库被其它会话灌数；本次不改种子文件 |
| R4 | `scripts/tests` 存量 10F/1E：`check-sclaim-naming.py` 未登记 `scripts/README.md` 且缺单测 | 该脚本在此前提交中引入，非本次 |
| R5 | procuraAI 供应链基线登记 uses=20 / timeout=[0]，而 `ci.yml` 为 23 / 15 | 另一并行会话正在改 CI；本次不动以免冲突 |

提交时以「步骤级 ❌ 子集校验」放行：`check.sh` 全量 59 步照跑，仅当红点全部命中上述已登记白名单才放行，出现任何未登记红点即阻断。

## 5. 独立审查发现与处置

| 发现 | 风险 | 处置 |
|---|---|---|
| R9 架构门禁在 `submit()` 处报「一事务两聚合」 | 绕过方式（把端口挪出 `domain.repository`）会永久削弱规则 | 抽 `ClaimIntakePort` 把唯一的跨聚合写点显式化，其余场景仍受严格约束；门禁 28/28 转绿 |
| 进度页「报案号」标签实际取 `claim_no` | 用户据此报号查不到；对账脚本会写错字段 | 改为「赔案号」+ 独立「报案号」，并用测试钉住（协错即红） |
| 追加赔案请求可携带 `policyNo` 被静默丢弃 | 「看着生效、其实没生效」 | `@JsonAnySetter` 捕获 + 点名拒绝（400），前后端测试各钉一侧 |
| 建案响应的对外状态是两处硬编码字面量 | `toExternal()` 映射一改即两处漂移且无信号 | 改由 `ClaimStatus.ACCEPTED.toExternal()` 派生，消除第二个真源 |
| 「`CREATE TABLE IF NOT EXISTS` 对同名异形表是静默 no-op」 | 表结构不合同时迁移"成功"、后续断言全错位 | 迁移 §1 加形状断言，并以同名异形表实测会被中止 |

## 6. 残留风险

1. **本地活库残留列**：`t_claim_case.report_no`（`NULL` 允许、0 行有值、注释「冗余展示」）存在于本机 bone 库，但**不在 `bone-init.sql`/迁移/实体任何一处**，代码也不读它。属早前并行实验残留，是「报案号有两个真源」的物证。清理脚本已交付：`scripts/migration/0140_synap_claim_drop_case_report_no_redundant.sql`（清单 order 146），在隔离库完成三场景 + 两处变异验证；**共享活库尚未执行**（探测到 90 个连接池连接，删列会让并行分支的读路径 500），留给部署流程。投影 SQL 取的是 `r.report_no`（`t_claim_report`），不是本列。
2. **E2E 不进 CI**：`ClaimLifecycleE2ETest` 依赖本地 MySQL，只在本机 `-De2e.local=true` 下运行。本次已实跑，但回归保护依赖人工触发。
3. **前端幂等键稳定性**：`stableAppendCaseKey` 以「报案号+发票代码+发票号+金额」构造，改单价会换键。若渠道侧会因重试而微调金额，仍可能产生第二笔赔案（发票唯一键会拦下，表现为 500 而非幂等命中）。
4. **报案关闭入口未开放**：`ReportStatus.CLOSED` 只能由后续用例或运维置位，本次不提供关闭 API（已在契约「刻意不做」清单登记）。

## 7. 结论

变更的验收口径全部达成：全模块 1662 测试 0 失败（8 例按环境跳过，实际执行 1654）、前端 214 测试 0 失败且类型零错、真库真 HTTP 端到端 3/3、迁移两遍幂等并带终态断言、两处变异验证证明新断言有判别力、与本次改动相关的门禁逐条绿。5 项基线存量红已逐项举证归属，均不在本次改动集内。

**判定：通过。**

## 8. 交付状态（2026-11-22T20:05）

分支 `comet/claim-report-case-one-to-many`，已推双远程（Gitee + GitHub），两端 SHA 与本地一致：`3a3440c99`。

```
3a3440c99 feat(claim): 新增 0140 清理孤儿列 report_no，并更正评审材料一处事实误判
dd0b42e5d docs(claim): 补报案 1:0..N 双分支实施方案评审材料
e90aebb75 docs(claim): 校正验证报告测试计数为 1662/8（以 Comet 收据为准）
609bf7352 chore(comet): 记录 verify 报告绑定与 action 序号（Run 停在 verify 待恢复）
440e260f5 docs(claim): 验证报告按约定移入 docs/superpowers/reports
438d42e49 docs(claim): 补 1:0..N 拆分验证报告（证据/变异验证/存量红归属）
69e66f88b feat(claim): 报案与赔案拆分为 1:0..N，新增报案聚合与追加赔案链路
```

**Comet Run 未进入 Archive**，停在 `phase=verify`、`verifyResult=pending`、`archived=false`。

原因不是「没跑检查」，而是检查跑成了、结果没回填：18:45 的 verify check 收据日志尾行是 `Tests run: 1662 … BUILD SUCCESS`，但 SDK 在收尾清理 `.write.lock.contenders/*` 时被沙箱终止，`sdk-aef7….json` 停在 `status=started`，Action `:20` 悬在 `running`。

五条恢复通道均已按源码逐条确认封闭（`record-outcome` 要求 `record.status==='completed'`；`retry` 要求 `resolution='not-executed'`；`acknowledge-stopped` 要求 `terminal(action)`；`cancel` 是 Run 级；`mark-unknown` 语义为「未执行」），根因是 **Classic 域从未调用 `acknowledgeStopped`**——Native 有 `reconcileInterrupted` 收口回路，Classic 没有。完整取证见 `2026-11-22-claim-report-case-branch-review.md` 附录 A。

处置：**不 cancel、不 mark-unknown、不伪造回执**（前两者会让归档彻底不可达，后者是伪造证据）。交付事实以本节 Git 提交 + 本报告为准。

## 9. 验证后修正：`0139` 守卫重写与复验（2026-11-22T20:45）

§1–§7 的证据对应的是**重写前**的 `0139`。该文件在本轮之后被修改过一次，故补本节记录差异与复验，避免报告指向一个已不存在的文件版本。

### 改了什么

`0139` 原有 7 处 fail-closed 守卫采用 `SET @sql = IF(cond,'DO 0','SIGNAL …'); PREPARE …; EXECUTE`。**MySQL 的 `PREPARE` 不能执行 `SIGNAL`**（报 1295「not supported in the prepared statement protocol」）——中止行为还在，但报错信息与意图完全无关，只在异常路径暴露。改为存储过程（本仓 63 个迁移的既有写法，先例 `0133`）：

| 位置 | 改后 | 备注 |
|---|---|---|
| §7 收紧 NOT NULL 前 | `sp_pre_0139()` | 中止前 `SELECT` 打印前 50 条 NULL 行 |
| §10 终态断言 | `sp_verify_0139()` | 5 条断言；悬空 / 丢前缀两类会先打印违规行 |
| §10.5 报案号无重复 | **删除** | `uk_report_no` 唯一键 + `report_no NOT NULL` ⇒ 结构上不可触发；保留只会让人误以为「唯一性还有第二道防线」 |

`§1` 的形状探测与 `§4`/`§8` 的条件式 DDL 仍用 `PREPARE`（执行的是 DDL/SELECT 而非 `SIGNAL`，属正确用法，未动）。

### 复验证据（隔离库 `bone_v0139`，脚本 `/tmp/verify_0139.py`）

- **A. 幂等**：连跑两遍均无异常；报案 2 行（不重复插）、`report_id` 无 NULL、`is_nullable=NO`、`uk_report_no` 严格单列 `report_no`、报案号全带 `RP` 前缀、`batch_no` 注释已纠正 —— 7 项全绿。
- **B. 守卫逐个触发**（每条都断言错误码为 `1644` 且消息含可读关键词，8 项全绿）：

| 场景 | 期望消息关键词 |
|---|---|
| 收紧前存在 NULL 行 | `still NULL` |
| 无主赔案（10.1） | `without report_id` |
| 悬空 `report_id`（10.2） | `dangling` |
| 列仍可空（10.3） | `still nullable` |
| 唯一键缺失（10.4） | `uk_report_no missing` |
| 报案号丢 `RP` 前缀（10.6） | `RP prefix` |
| 诊断 SELECT 位于 SIGNAL 之前（结构） | 文本位置比较 |
| 诊断 SELECT 能取到违规行 | 返回 1 行 `XX` |

- **C. 变异验证**：把 §6 回填条件由 `report_id IS NULL` 改为 `1 = 0` → 脚本在**第 17 条语句**以 `1644` + `0139 ABORT: t_claim_case.report_id still NULL (see list above); backfill before NOT NULL` 中止。对照：旧写法在此处只会给 `1295`，操作者无法从信息里判断是「有赔案没汇报案」。还原后复跑 15/15 全绿。

### 未改动的同族文件（已发布，不可回改）

全仓 146 个迁移中，命中该坏写法的共 **5 个**：`0139`（本节已修）、`0138`（4 处）、`0021` / `0022` / `0023`（各 1 处）。后四者**已在环境记账**（`schema_migration_journal` 存有 sha256），改一字即报 drift —— 与仓库既有口径同源（见 `check-ddl-duplicate-column` 对 0046 的处理说明）。故不存在「全仓清扫」这条路径，这四个文件的坏写法登记为**已知限制**：中止会发生，只是信息不可读。

### 门禁（真实退出码）

`check-migration-manifest` RC=0（153 文件 / order 1..146 连续）、`check-ddl-doc-sync` RC=0（200=200）、`check-ddl-duplicate-column` RC=0、`check-ddl-required-columns` RC=0。
