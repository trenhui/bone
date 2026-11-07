## 1. L3 判据收紧（防局部假绿）

- [x] 1.1 `_ai_native_readiness.py` 的 `RUNTIME_MODEL_COVERAGE` 叠加 `provider_env` 双条件，三态 reason 与 `providerConfigured=` detail
- [x] 1.2 `--self-test` 调整参与率正向探针（provider 已配才翻转），新增「仅伪造参与率不得翻绿」负向探针
- [x] 1.3 `scripts/tests/test_ai_native_readiness_flywheel.py` 补双条件表征测试与自测整体通过断言

## 2. 证据窗口单一真源

- [x] 2.1 `aiGovernance.ts` 的 `governanceWindow()` 改为 UTC 日界 30 天算法，`AiGovernancePage` 去掉显式 `7`
- [x] 2.2 `aiGovernance.test.ts` 更新窗口断言，新增「同日多次调用恒定」「长度恰 30 天」
- [x] 2.3 E2E 顶部定义 `EVIDENCE_WINDOW`，A 组 runtime health 与 `result.runtime` 同源取值
- [x] 2.4 E2E B 组新增「面板声明窗口与 EVIDENCE_WINDOW 逐字一致」断言；C 组三条正则保持原样

## 3. hidden 路由深链入口

- [x] 3.1 `ReviewWorkbenchPage.tsx` 的 `PageHeader.extra` 增加「AI 治理」按钮，`navigate('/ai-governance')`
- [x] 3.2 E2E 改为真实点击按钮进入治理页，保留改 hash 作为回退负向自检
- [x] 3.3 运行 `claim-single-nav-probe.mjs` 确认菜单双向全等不受影响

## 4. provider 连通性探活（手动触发）

- [x] 4.1 `aiGovernance.ts` 增 `ProviderProbeReport` 类型、`providerProbe()`、`probeMeta()` 防假绿映射
- [x] 4.2 `AiGovernancePage.tsx` 增独立探活按钮与结果区块；默认加载与「刷新」均不触发探活
- [x] 4.3 `aiGovernance.test.ts` 补 `probeMeta` 负向用例（未知码/`NOT_CONFIGURED`/失败态）
- [x] 4.4 E2E 用请求计数断言「进入页面 0 次、点击后 1 次」探活请求

## 5. 校准曲线明细

- [x] 5.1 `aiGovernance.ts` 的 `CalibrationReport` 补 `buckets`/`slices` 及 `calibrationEvidenceMeta()`
- [x] 5.2 `AiGovernancePage.tsx` 按证据态渲染分桶/切片只读表格，无证据不画曲线
- [x] 5.3 `aiGovernance.test.ts` 补空证据与禁止态负向用例
- [x] 5.4 E2E 断言 `NO_EVIDENCE` 时不出现分桶表格

## 6. 文档口径同步

- [x] 6.1 `00-governance/01-文档治理规约.md` 治理端点数 5→6，登记探活端点
- [x] 6.2 `04-design/BONE-AI能力依赖图谱.md` §9.1 第 5 条改为 UTC 日界 30 天单一真源，补 D1 判据说明
- [x] 6.3 复核两份文档无其他滞后口径（窗口天数、端点数、探针数）

## 7. 验证与收口

- [x] 7.1 vitest（`bone-claim-app`）+ `tsc --noEmit` 通过
- [x] 7.2 Python 机检单测 + 复核器 `--self-test` 通过
- [x] 7.3 前后端 E2E 真实服务栈复跑通过，产物如实记录窗口与参与率
- [x] 7.4 `./scripts/check.sh`、`mvn -o -pl bone-apps/synap-claim clean test` 通过；文档六道机检 + `_gen_ai_index.py --check` 全绿
- [x] 7.5 复核 L3 就绪度：`l3Claimable=false`、7 项 BLOCKED 保持，输出最终结论

## 8. 第 10 轮循环复核增量（2026-11-07）

- [x] 8.1 `RUNTIME_MODEL_COVERAGE` 补 `runtime.probeVerdict == REACHABLE` 第三条件（`AI技术方案 §11.7`「配了 ≠ 通了」事故）
- [x] 8.2 E2E 复用同一次手动探活响应把受控判词写入产物，不额外发请求、不引入自动轮询
- [x] 8.3 `--self-test` 与 `RuntimeModelCoverageTest` 补「探活失败 / 判词缺失 / 判词伪造 / 摘除任一条件」负向钉子
- [x] 8.4 收敛文档族登记漂移：`docs.manifest.json` 两条、`07-delivery/README.md` 第 11 行、`guide` role 纳入规约与 `AI-INDEX` 生成器并重跑
- [x] 8.5 三条件口径同步到治理规约、能力依赖图谱、运行手册、技术设计与实施计划
- [x] 8.6 补齐并发会话半改造成的编译断裂：`ScenarioHarness.InMemoryInspectionRepository` 实现 `findDiffConcludedInWindow`（只动测试夹具，不动其生产逻辑），按规范跑 `spotless:apply`

## 验证记录（2026-11-07，命令 + 结果摘要）

| 命令 | 结果 |
|---|---|
| `python3 -m unittest -v scripts.tests.test_ai_native_readiness_flywheel scripts.tests.test_check_ai_native_calibration scripts.tests.test_ai_native_readiness_evidence_gates` | **54 tests OK**（飞轮 + 校准机检自检 + 并发会话 21 例证据门禁 + 第 11 轮 `MaturityLevelTest` 7 例） |
| `python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test` | PASS（含新增 4 条防假绿探针） |
| `npx vitest run apps/bone-claim-app` | 4 files / 71 tests passed |
| `npm run typecheck --workspace=bone-claim-app` | RC=0 |
| `mvn -o -pl bone-apps/synap-claim clean test` | Tests run **1224** / Failures 0 / Errors 0 / Skipped 6，BUILD SUCCESS（首跑因并发会话半改的 `findDiffConcludedInWindow` 接口 testCompile 失败，补齐夹具后复跑通过） |
| `node bone-frontend/e2e/synap-claim-e2e.cjs` | **PASS 37/37**，`real-services-no-mock`；窗口 `2026-10-09T00:00:00Z ~ 2026-11-08T00:00:00Z`，`nodes=104 / aiParticipated=8 / rate=0.0769 / probeVerdict=NOT_CONFIGURED`；终态对账两条断言通过 |
| `./scripts/check.sh` | ✅ 本地自检通过（59 步，RC=0） |
| `python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py` | `SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、**L0**、`l3Claimable=false`；`RUNTIME_MODEL_COVERAGE=PROVIDER_NOT_CONFIGURED` |
| 文档六道机检 + 生成器 | `_check_xref.py` / `_check_contract_drift.py` / `_check_ddl.py`（36 表）/ `_check_numbers.py`（51 禁用 0 命中、54 关键口径在位）/ `_check_ai_native_calibration.py --check` + `--self-test` / `_gen_ai_index.py --check` **全绿**；`_check_manifest.py` 的 M3 因并发会话刚 `git add` 的 `03-contract/第10轮循环复核-零DDL闭环落地.md` 未登记而判红（在途资产，不代为登记） |
| `node scripts/e2e/ui/claim-single-nav-probe.mjs` | 114 / 116，两项既存失败为「承保主体 / 主数据映射」页面标题 ≠ 菜单名 |

**合理性裁定（不改的两项，避免刷绿）**：`claim-single-nav-probe.mjs` 的 114/116 两项失败，页面标题属于并发 change `multi-insurer-biz-identity-catalog-mapping` 资产；把严格相等判据放宽为「包含」属放宽门禁，把页面标题改短会踩他人模块。两者都不是合理优化，故保持判据、登记归属，由该 change owner 收敛。与 hidden 路由无关。

**共享树编译回归（第 10 轮，已修）**：并发会话新增 `InspectionRepository.findDiffConcludedInWindow` 与对应 SQL，但漏改 `ScenarioHarness.InMemoryInspectionRepository`，导致 `mvn clean test` 在 testCompile 阶段报「未覆盖抽象方法」。本轮按 SQL 同窗同过滤补夹具实现（CONCLUDED + DIFF + 结论时间窗 + 500 上界），未触碰其生产逻辑、未改任何接口签名；格式化限定 `spotlessFiles=.*ScenarioHarness.*`，避免把并发会话在途 Java 文件卷进本次格式化。

**已收敛的外部漂移（第 10 轮）**：`2eb7c2781` 入库的 `03-contract/第9轮循环复核-合理性判定矩阵.md` 与 `07-delivery/11-L3外部前置运行手册.md` 已登记 `docs.manifest.json`，`07-delivery/README.md` 补 11 号索引，frontmatter 的 `role: guide` 已纳入治理规约 §三之二 与 `_gen_ai_index.py` 语料模型（AI-INDEX 单列 GUIDE 一节，不混进 ACTIVE 事实真源），重跑生成器后 M3 / xref 库存 / M7 全部通过。

**实跑修正（第 9 轮）**：E2E 首轮 32/34，两处窗口同源断言因 `toISOString()` 带 `.000Z`、后端 `Instant` 回显为秒级 `Z` 而判红。`governanceWindow()` 与 `EVIDENCE_WINDOW` 统一输出秒级 ISO（去掉毫秒）并加 1 条可证伪负向用例，复跑 34/34；未采用「比较时再归一化」的宽松做法。第 10 轮加探活判词断言后为 35/35。

## 9. 第 11 轮循环复核（2026-11-07）

- [x] 9.1 `current_level()` 收紧：`RUNTIME_MODEL_COVERAGE` 非 SATISFIED 时回落 L0 并带原因码，配置态全齐不得判 L1/L2
- [x] 9.2 `MaturityLevelTest` 7 例（L0/L1/L2 三态 + 「退化成只看配置态」源码变异可证伪）
- [x] 9.3 E2E 终态对账 + `probeVerdictCapturedAt` / `finalReadinessSnapshot` + 端点计数文案精度
- [x] 9.4 文档族收尾：`00`–`11` 编号、`aiRouting` 两条 guide 路由、全套清单补生成器 `--check`、重跑生成器
- [x] 9.5 口径同步到治理规约、能力依赖图谱、运行手册、技术设计、实施计划与验证记录

**终态结论**：`SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、**L0**、`l3Claimable=false`。本轮把「配置态就绪」与「运行态 AI 上场」彻底分开：即使将来有人配好 provider 并打开 `llm` 模式，只要三条件未齐，档位仍回落 L0 并写明 `PROBE_NOT_REACHABLE` / `MODEL_PARTICIPATION_ZERO` / `RUNTIME_EVIDENCE_REQUIRED` 之一。

**不代为收敛的在途资产**：`03-contract/第10轮循环复核-零DDL闭环落地.md`（并发会话已 `git add` 未提交，`doc_id: review.round10`）尚未登记 `docs.manifest.json`，故 `_check_manifest.py` 的 M3 判红。该文件 frontmatter 已用 `role: guide`，并发会话登记时可直接沿用第 10 轮已建立的 guide 真源；本 change 不碰其未提交内容，也不把这份红写成绿。本 change 的「第 N 轮」为自行批次记账，与并发会话的轮次编号无对应关系。

## 10. 第 12 轮循环复核（2026-11-07）

- [x] 10.1 登记并发会话已入库的 `03-contract/第10轮循环复核-零DDL闭环落地.md`（`review.round10`）：manifest + `aiRouting` + 重跑生成器，六道机检与 M7 全绿
- [x] 10.2 `--self-test` 补第四类「成熟度档位」3 条探针（两条防假绿 + 一条合规正例）与 1 例「退化档位令 self_test 判红」变异测试，55 例表征测试全绿
- [x] 10.3 三份文档去掉会漂移的探针/用例硬编码计数，统一为「四类探针、条数以脚本为准」
- [x] 10.4 索引锁释放后按路径式暂存本 change 28 个文件（未提交，未卷入并发会话在途文件）
- [x] 10.5 并发会话 3 个新提交（`b7f5d7d8e` 等）后复跑全套验证

**验证结果**：六道机检 + `_gen_ai_index.py --check` 在该时点全绿；55 例 unittest OK；`--self-test` 四类探针 PASS；E2E 37/37 PASS；vitest 71/71；`tsc` / IDE lints 0；`check.sh` 59 步通过；`mvn -o -pl bone-apps/synap-claim clean test` **FAIL**（见下）。收口之后并发会话又新建并暂存了 `03-contract/第11轮循环复核-事件消费方接线与分层收口.md`，未登记 manifest，`_check_manifest.py` 的 M3 因此再次判红（`_check_xref.py` 仍绿）。这是其 change 在途资产，沿用第 10 轮裁定不代为登记，也不把这一时点的红写成绿。**第 13 轮复核更新**：并发会话随后已自行登记 `review.round11`，M3 转绿；`mvn -o -pl bone-apps/synap-claim clean test` 亦转为 1234 run / 0 fail / 0 error、BUILD SUCCESS。本段保留原时点读数，不回写历史。

**在途回归（不越界修）**：并发会话三个未跟踪文件 `AiEvalSampleIndexApplicationService.java` / `AiEvalSampleIndexPort.java` / `AiEvalSampleIndexApplicationServiceTest.java` 造成 2 项失败——`ArchitectureTest.application_no_infra` 6 处违规（应用服务构造参数/字段/返回泛型直接依赖 `infrastructure.evaluation.AiEvalSampleProjection`），以及 `AiEvalSampleIndexApplicationServiceTest.consumedSamplesAreVisibleThroughService` 断言 `Expected size: 16 but was: 4`。其端口文件 javadoc 已写明「契约放 application 层、由基础设施实现」，说明是正在重构的中间态。本轮只对该端口做了单文件 `spotless:apply`（修复格式违规，零语义变更），未碰其业务逻辑；ArchUnit 与业务断言须由该 change owner 收口，不得靠放宽门禁或改测试预期消红。`check.sh` 的 `[3/59]` 就近 ArchUnit 按「已跟踪的 src/main/java 变更模块」筛选，未跟踪文件不进入变更集，故本轮 check.sh 通过而全量 Maven 判红——这是门禁对未跟踪文件的覆盖盲点，仅登记不改 CI 脚本（L3）。**第 13 轮复核更新**：上述 2 项失败已由并发会话自行收敛（`ArchitectureTest` 27/27、模块 1234/0/0），本 change 未介入其业务代码；但对「未跟踪文件不进就近 ArchUnit 变更集」的盲点判断不变。

**handoff 一致性裁定（不改）**：`.comet.yaml` 的 `handoff_context` 指向 `.comet/handoff/design-context.json`，该目录被 `.gitignore:219 .comet/` 有意忽略；`handoff_hash` 与该文件的 raw / strip / canonical / compact 四种 sha256 以及所有候选设计文档的 sha256 均不匹配，判定为 SDK 自有算法或本地陈旧记录。`comet state check synapclaim-ai-governance-hardening build` 全 5 项 PASS、不校验该字段，故不擅自改 Runtime-owned 状态、不强制入库被忽略文件；若后续 guard 报缺失，由 Comet runtime owner 按 SDK 算法重建。

**终态结论**：`SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、**L0**、`l3Claimable=false`。本轮无任何 BLOCKED 被翻绿，也未把并发会话的在途失败写成通过。

## 11. 第 13 轮循环复核（2026-11-07）

- [x] 11.1 发现并消除半入库：`09-records/evidence/` 3 份时点证据已登记 manifest 但 `git ls-files` 为 0，fresh clone 会断链；逐条比对 65 份登记文档后又发现 `05-plan/AI原生改造执行计划.md` 未入库。按路径式 `git add` 补 4 份，复验 **65/65 全部入库**，不改内容、不改 `.gitignore`、不提交。注：直接 `git ls-files` 会把中文路径做八进制转义，导致 58 份已入库文档被误报为未入库；校验须用 `git -c core.quotepath=false ls-files -z`
- [x] 11.2 复核并发会话收敛结果：Maven 1234/0/0 BUILD SUCCESS、六道机检 + M7 全绿、`review.round11` 已登记
- [x] 11.3 复核并发会话新增 `/blind-labels` hidden 路由与深链按钮未破坏本 change：E2E 37/37、vitest 83/83、`tsc` 0、nav probe 仍 114/116（两项既存不变）
- [x] 11.4 更新第 12 轮过时的时点读数，并给出编码侧收敛边界

**本轮验证**：`check.sh` 59 步通过；六道机检 + `_gen_ai_index.py --check` 全绿；55 例 unittest / `--self-test` 四类探针 PASS；E2E 37/37；vitest 83/83；`tsc` / IDE lints 0；Maven 1234/0/0 BUILD SUCCESS；nav probe 114/116（两项既存，不变）；manifest 65 份登记文档 0 份未入库。

**前端 `MM` 状态说明**：`ReviewWorkbenchPage.tsx` / `routes.tsx` 的 index 版本是本 change 的「AI 治理」深链，工作区另有并发会话新增的「盲标工作台」深链与 hidden 路由（未暂存）。本 change **不重新 `git add` 这两个文件**，避免把他人增量纳入本 change 的提交范围；提交时按路径式 `git commit -- <本 change 路径>` 隔离。

**编码侧收敛边界**：判据（12 项 / 三条件 / 档位 / 四类探针）、文档（六道机检 + M7）、前端（6 端点 + 防假绿 + hidden 路由）、后端（1234 例）、E2E（37/37 终态对账）、证据归档（3 份时点快照已入库）均已闭环。继续堆判据或断言边际收益递减并增加维护面；剩余 7 个 BLOCKED 全部是外部前置，不可编码替代：真实 provider/密钥、独立盲标金标准、冻结集评测、模型击败确定性基线、飞轮真实跑通、独立验证/内审、go-live 分册转正。建议本 change 在此收口，后续按 `07-delivery/11-L3外部前置运行手册.md` 由业务侧逐项解锁。

**终态结论**：`SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、**L0**、`l3Claimable=false`，本轮无任何 BLOCKED 被翻绿。

## 14. 第 14 轮增量（2026-11-07）：探活证据束防伪造

收口后复核发现两处真实漂移，按 GAP 推荐方案 A 在判据侧收敛：

- [x] 14.1 `_check_manifest.py` M3 判红：并发会话新增 `第11轮-GAP登记-provider双条件不同源.md` 未登记。补 front-matter + `review.round11.gap-provider` 条目 + `aiRouting`，重跑生成器（66/66 一致）
- [x] 14.2 GAP 原阻塞理由（判据脚本是并发会话在途文件）已随 `1ae2809c6` 入库消失；按方案 A 新增 `probe_evidence_valid()`，判据扩为四条件
- [x] 14.3 `--self-test` 补 4 条伪造鲁棒性探针 + 1 条档位负向探针 + 1 条「证据束齐备但判词非 REACHABLE」探针
- [x] 14.4 表征测试补 5 条证据束用例 + 4 条源码变异验证 + 1 条档位负向用例；**变异测试曾判出「证据束遮蔽探活条件被摘除」，已补探针修复判别力缺口**
- [x] 14.5 E2E 从同一次探活响应透传 `probeEvidence`，缺失字段写 `null` 不编造；加防编造断言与「复核器回显证据束结论」终态对账断言
- [x] 14.6 文档族四条件口径收敛（治理规约 / 依赖图谱 / 运行手册 / 运维手册 / GAP / 设计 / 计划 / spec）
- [x] 14.7 采集侧（`/ai-provider-probe` 返回 token / requestId）如实登记为 L2 业务契约 + 真实 provider 外部前置，本轮不改 Java 契约、不宣称「不可伪造」

**本轮验证**：表征测试 31/31 OK；`--self-test` PASS；六道机检 + `_gen_ai_index.py --check` 全绿；`node --check` E2E 通过；复核器仍 `SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、L0、`l3Claimable=false`，无 BLOCKED 被翻绿。

**边界说明**：本轮只收紧「如何判定模型上场」，不解锁任何外部前置。7 个 BLOCKED 之外新增的 `PROBE_EVIDENCE_*` 归因是**同一判据内**的 fail-closed 分支，不新增 BLOCKED 数量；真实 provider 接入前该判据恒 BLOCKED 属预期，合规正例探针证明判据可达 SATISFIED（非恒假）。

## 15. 第 15 轮实跑验证（2026-11-07）

把第 14 轮只做过 `node --check` 的 E2E 改动升级为真实服务栈实跑（MySQL / IAM / 网关 / synap-claim / shell / claim-app 端口全在，Playwright 可用）：

- [x] 15.1 `node bone-frontend/e2e/synap-claim-e2e.cjs` **PASS 39/39**（第 13 轮 37/37 + 本轮 2 条新断言），`real-services-no-mock`
- [x] 15.2 产物 `runtime.probeEvidence` 字段存在且为 `null`（`verdict=NOT_CONFIGURED`，未编造 token / requestId）；`finalReadinessSnapshot.probeEvidence=null`
- [x] 15.3 终态对账回显 `probeEvidence=PROBE_EVIDENCE_REQUIRED`，证明复核器消费证据束而非只读裸判词
- [x] 15.4 复核器消费新产物后仍 `SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、L0、`l3Claimable=false`；六道机检 + `_gen_ai_index.py --check`、31 例表征测试、`--self-test` 全绿

**结论**：第 14 轮的判据加固已获得真实运行期证据，不是只在单测里自洽。未解锁任何外部前置；采集侧 token / requestId 契约扩展与真实 provider 接入照 GAP §七由业务侧推进。本 change 编码侧维持收口。
