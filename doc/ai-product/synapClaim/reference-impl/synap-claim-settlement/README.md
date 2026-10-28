# synap-claim-settlement · 理算引擎参考实现（真实 TPA 场景 + 防假绿探针版）

本目录是《synapClaim 智能理算引擎设计（独立限界上下文）》+《synapClaim 理算引擎内部设计》+《synapClaim 理算引擎真实 TPA 场景测试用例与优化》+《synapClaim 理算引擎与理赔系统诊断与优化》的**可执行参考实现**：
把"架构决策 + 内部设计 + 真实场景用例 + 诊断优化"落成可编译、可运行、带变异/负向探针的 Java 模块，**证明算法与护栏确实生效，而非仅文档描述**。

## 与 tpa-saas 的关系（复用 proven 引擎为基线 + 新架构补强）
> 基线 `bone-tpa-intelligent-adjustment` 是 **pk-tpa-saas 生产验证、在用的 proven 引擎**，其策略模式与 min 聚合控额已被真实业务检验。本实现**复用其语义作为基线**，并按 synapClaim 六边形/端口防腐/多租户/监管快照约束做架构升级（非修补破损）。**tpa-saas 原代码未做任何改动。**

| 维度 | `pk-tpa-saas/bone-tpa-intelligent-adjustment`（proven 基线） | 本实现（synapClaim 架构升级） |
|---|---|---|
| 策略模式 | 三类责任 + 工厂分发 ✅ 复用 | `engine/strategy/Strategies` 同构 |
| 控额 min 聚合 | `LimitControlEngine.findMin` ✅ 复用 | `engine/LimitControl` 多维度取交（含年度限额维度） |
| 模块接线 | 生产环境已接线、在用 | 由 `ProbeSuite` + `ScenarioSuite` 实际驱动 `settle()`/`preAuthorize()` |
| 直付/公账 | 引擎内联 `DirectPaymentFeignClient`/`PooledAccountFeignClient` | 一律经 `port`，P6 结构保证 |
| 配置来源 | 实时读 `plan.version` 重算 | `FormulaSnapshot` 物化冻结，P2 证伪漂移 |
| 名称匹配 | 字符串费用名 | `feeType` 目录编码（强类型） |
| 跨案累计 | 单案内 min 聚合 | `AccumulationPort`（租户隔离 + 乐观锁 + 幂等），P10/P11/P18 |
| 可解释性 | 工程可读轨迹 | 规则 ID 溯源 + `explain()` 客户告知级说明（P9） |
| 商保直付预授权 | 基线无显式预授权步骤 | `preAuthorize()` + `hold/capture/void/TTL` 生命周期 + 保守比例（S9/S15/S16） |
| 拒赔建模 | 基线无拒赔结论 | `status`(PAY/PARTIAL/REJECT/HOLD) + `rejectType` + `denyReasons`（S11/S12/S18） |

## 构建与运行（JDK 21）

### 方式一：Maven 模块（生产级，已落地并测试通过 ✅）
本目录已转型为**可构建、可测试的标准 Maven 模块**，自带生产级 JDBC 端口与 JUnit 5 回归套件。
```bash
cd reference-impl/synap-claim-settlement
mvn -o clean test      # 离线（依赖已在本地仓库）；在线去掉 -o
```
测试套件（全部绿色，15 测试类）：
- `SettlementEngineProbeTest`      —— 驱动 `ProbeSuite.runAll()`：**正确引擎 30 条探针全绿 + 19 类变异全部被捕获**
- `ScenarioSuiteTest`              —— 驱动 `ScenarioSuite.runAll()`：**28 个真实 TPA 场景全部通过**
- `JdbcPortsIntegrationTest`       —— **6 个集成测试**验证 JDBC 持久化路径：责任限额跨案累计落库 / 冲正回退 / 预授权冻结-capture-void / 幂等 CAS 租约 / 累计并发乐观锁 CONFLICT(fail-closed)
- `SettlementConcurrencyStressTest`—— **4 个并发压测**（H2 内存库，单实例）：
  - A 多案并发共享年度限额：40 线程各独立案件 → 零丢失更新、零超赔、净赔付==占用、幂等重放不双计；
  - B 同请求并发重复提交：24 线程 → 幂等租约保证恰好一个胜者执行、仅落一条结论、net 一致；
  - C 冲正与并发理算交叉：撤销已占额案件 + 并发新案 → 累计账实一致、旧占用被释放；
  - D 串行锁判别力（防假绿）：开启 JVM 串行锁且**不重试**时，40 并发唯一案件一次成功、零 `CONFLICT` 异常。
- `ConcurrencyLockSeamTest`       —— **2 个接缝测试**证明并发锁是**可插拔端口**而非写死：注入的计数锁确被引擎调用（非 no-op）、同 key 严格串行（maxConcurrent==1）、且切换注入实例即生效（集群可换 Redis/DB 租约实现）。
- `JdbcPortsMySqlCompatTest`      —— **在 H2 `MODE=MySQL` 下真实执行** `SqlDialect.mysql()` 生成的全部生产 SQL（upsertCalc / upsertLedger / ensureAccumRow 的 `ON DUPLICATE KEY UPDATE` 形式）：跨案累计落库 + 幂等重算更新 + 冲正归零。补 `SqlDialectTest` 只断言文本不执行的缺口，证明"切换方言即上线 MySQL"非空话。
- `OutboxRelayTest`              —— **3 个测试**验证事务发件箱中继发件箱：健康投递置 `published=1` / 连续失败进入死信表（绝不静默丢失）/ 已投递不重投（at-least-once 幂等）。

### 方式二：单文件直跑（无 Maven，快速验证算法与护栏）
```bash
cd reference-impl/synap-claim-settlement
rm -rf out && mkdir -p out
find src/main -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.bone.synap.settlement.probe.ProbeSuite        # 30 条探针 + 19 类变异对照
java -cp out com.bone.synap.settlement.scenario.ScenarioSuite  # 28 个真实 TPA 场景用例
java -cp out com.bone.synap.settlement.diag.Diagnostics2       # 第二轮取证诊断（10 项缺陷可复现）
```
退出码 0 = 正确引擎全绿**且**全部变异被捕获；非 0 = 实现缺陷（假红）或探针失辨（假绿）。

## 生产级实现要点（本模块已落地）
1. **六边形 + 端口防腐**：`domain/engine` 为纯逻辑，绝不 import 任何 Spring/DB/实现；一切外部能力经 `port` 接入。
2. **生产 JDBC 端口**（`adapter/jdbc/JdbcBackedPorts`）实现全部 7 个端口：
   - 累计采用 `version` 乐观锁（CAS）：并发提交版本不符即 `CONFLICT`（fail-closed，禁静默覆盖）；
   - 按 `claimId` upsert：重算以新值替换旧值，不累加（诊断 N2）；
   - `reverse()` 冲正：回退累计占用 + 删除案件占用行（N8）；
   - 预授权 `frozen` 维度计入三值额度（initial − claim − frozen），`hold/capture/void/TTL` 全生命周期（N3 + 亿保即时结算）；
   - 幂等 `tryBegin` 唯一键租约：同键并发仅一个请求可取执行权（N7）。
   - DDL 见 `src/main/resources/schema.sql`（H2 语法；MySQL 仅需把 `MERGE INTO ... KEY` 改为 `INSERT ... ON DUPLICATE KEY UPDATE`）。
3. **装配**（`adapter/spring/SettlementEngineFactory`）：纯 Java 工厂，接受 `DataSource` 即可装配一个挂载 JDBC 端口、全部护栏开启的引擎；README 内附 Bone 平台 `@Configuration` 接线示意（C1/C2/C5/C6/C8/C9/C10/C11 端口绑定）。
4. **资金安全底座**：责任维度累计 + 冲正 + 预授权冻结 + 幂等/租户隔离键 + CAS 乐观锁 均已落地并集成测试覆盖。
5. **事件最终一致（事务发件箱，业界标准模式）**：引擎在 `settle()` 中经 `OutboxPort` 把领域事件与理算结论**同库同事务**写入 `t_settlement_outbox`（杜绝「写结论成功但事件丢失」）；`OutboxRelay` 轮询 `published=0` 的行经 `EventPublisher` 投递下游（消息中间件 / 对账 / 后督订阅），成功置 `published=1`，连续失败超阈值移入死信表 `t_settlement_outbox_dlq`（**绝不静默丢弃**，fail-closed）。投递为 at-least-once，下游须以 `claimId+event` 幂等去重。`OutboxRelayTest` 覆盖健康投递 / 死信 / 已投递不重投三路径。

### 并发正确性（单实例 + 可插拔集群锁）
- **引擎层可插拔串行锁（ConcurrencyLockPort）**：同保单同周期（`tenantId@policyId@periodKey`）的「读累计快照 → 计算额度 → 提交累计」整段临界区，经 `ConcurrencyLockPort.runLocked(key, ...)` 串行化，从根上消除"多请求读到同一剩余额度快照后双双提交导致超赔"的窗口。
- **默认 `JvmConcurrencyLock`**：单实例下行为与历史一致（每 key 一个监视器 + `synchronized`）。**集群部署只需把默认实现换成 Redis/DB 租约实现**（实现同一 `ConcurrencyLockPort` 接口），引擎编排代码零改动——`ConcurrencyLockSeamTest` 已断言"注入即生效、切换即生效"。
- **DB 乐观锁（version，CAS）作为纵深防御**：`commitUsage` 以 `UPDATE ... WHERE version=?` 提交，版本不符即 `CONFLICT`（fail-closed，绝不静默覆盖）。生产调用方应捕获 `ConcurrentAccumulationException` 并重试（重试会重读最新快照）。
- **有界等待 / fail-fast**：`runLocked(key, action, timeout)` 支持获取锁超时即抛 `ConcurrencyLockTimeoutException`，绝不无限期阻塞（避免线程耗尽 / 活锁）。默认 `JvmConcurrencyLock` 用 `ReentrantLock.tryLock(timeout)` 实现；长临界区由持有方后台心跳续期。
- **集群分布式锁参考实现 `DbLeaseConcurrencyLock`**：基于共享存储（DB 租约，INSERT 唯一约束抢锁 + 过期回收 + 看门狗续期，**不使用 SELECT ... FOR UPDATE**）实现跨实例同 key 互斥。`DbLeaseConcurrencyLockTest` 用两个共享同一 H2 库的 lock 实例（模拟集群两节点）证明跨实例串行化与超时 fail-fast。`SettlementEngineFactory.createWithDbLock(ds, flags)` 一行切换为集群锁。
- **诚实边界**：单 JVM 实例下正确性由上述两层保证。集群部署接入 `DbLeaseConcurrencyLock` 后跨实例同 key 串行化由 DB 租约保证；跨实例极端竞态仍依赖 DB 乐观锁 + 调用方重试兜底（双层不互斥）。

## 理算顺序铁律（顺序错即金额错）
```
原始费用 → 目录/合理必要筛选 → 发票层面免赔 → 第三方已付抵扣(不可获利)
        → 年度免赔(作用于费用基数) → 赔付比例 → 理算层面免赔 → 取整 → 多维度 min 控额
```
**免赔额必须先于赔付比例作用于"费用基数"**：费用 2 万 / 比例 80% / 年免赔 1 万 → 正解 8000；若错序（先比例后免赔）得 6000，系统性少赔 25%（P16 探针固化）。

## 真实 TPA 场景用例（普康 / 亿保 / 泰康条款 锚定，28 例）
- **S1** 门诊小额快赔（普康团险）→ (1000−100)×80% = 720
- **S2** 住院社保分割（泰康 A/B 比例）→ 2300@90% + 3500@60% = 4170
- **S3** 年度免赔累计（百万医疗 1 万）→ 首案 0(PARTIAL) / 次案 2000
- **S4** 有社保未结算降比（泰康§(三)）→ 5400+1800 = 7200
- **S5** 医保三大目录甲乙丙类（普康内置目录）→ 甲类 1000 / 乙类 810 / **丙类拒赔 0**
- **S6** 单/年度限额截断（封顶语义）→ 次限额 500；年限额 8000 两案 6000+2000
- **S7** 住院津贴 → 200×10 = 2000；**S8** 定额给付 → 100000
- **S9** 商保直付预授权（亿保）→ 授权 5000（仅计算，不占额）
- **S10** 预授权 ≥ 实际结算一致性 → 5000 ≥ 5000
- **S11** 整案拒赔—等待期 → REJECT，net=0
- **S12** 费用级拒赔—免责项目 → 正常 1600 / 免责 0
- **S13** 多保单独立理算 → 主险 800 + 附加险 800
- **S14** AI 加成需确认 → 未确认 800 / 已确认 900
- **S15** 预授权生命周期（本轮新增）→ hold 5000 → capture 3000 → 剩余释放 → void 归零
- **S16** 保守比例授权（本轮新增）→ 15000@80% 授权 **12000**（按 15000 授权将产生 3000 追收差额）
- **S17** 不可获利—社保统筹已付（本轮新增）→ (10000−5000)×80% = 4000，赔付+已付 ≤ 总费用
- **S18** 风控 P0 挂起（本轮新增）→ HOLD，net=0，**占额调用 0**
- **S19** 重复提交幂等（本轮新增）→ 两次结论一致，年免赔仅消费一次
- **S20** 跨租户隔离 → 同 policyId 不同 tenant 各 1000
- **S21** 责任年限额跨案累计（第二轮）→ 年限额 5000：首案 4000 / 次案 **1000**
- **S22** 补交发票重算冲正（第二轮）→ 6000→8000 重算赔付 **8000**，累计占用随之 **8000**（非残留 6000）
- **S23** 多次预授权不超年限额（第二轮）→ 3 次×6000 累计授权封顶 **10000**（三值额度 frozen）
- **S24** 撤件冲正（第二轮）→ 撤件前占用 3000 → 撤件后 **0**
- **S25** 未配费用子限额（0=不限制，第二轮）→ 正常赔付 **800**（不得全额拒付）
- **S26** 拒赔行告知书语义（第二轮）→ 丙类 `payout=0 / deduct=0 / rejectType=OUT_CATALOG`
- **S27** 分位守恒（第二轮）→ 3×1000@33.333333% 逐行合计 **1000.00** ≡ 总额口径
- **S28** 并发幂等租约（第二轮）→ 在途请求存在时第二个 fail-closed，**占额调用 0**

## 探针（防假绿）30 条 + 19 类变异
| 探针 | 断言性质 | 变异对照（关掉护栏必红） |
|---|---|---|
| P1 集成 / P6 端口隔离 / P7 占用不变式 / P8 归因完备 | 结构性 | — |
| P2 快照复算 | 源配置变更不影响已定结论 | `livePlan` |
| P3/P3b 负值钳零（含免赔>费用） | 赔付额不得为负 | —（类型层强制） |
| P4 控额 min | 多维度取交生效 | `noMin` |
| P5 AI 护栏 | 未确认建议不采用 | `ignoreConfirm` |
| P9 规则溯源 | 每行记录命中规则 | `noTrace` |
| P10/P11 跨案累计 | 年度免赔/限额跨案生效 | `noAccum` |
| P12 幂等 | 重复提交不双计 | `noIdem` |
| P13 租户隔离 | 额度不跨租户串用 | `noTenantScope` |
| P14 不可获利 | 第三方已付先抵扣 | `noIndemnity` |
| P15 比例护栏 | 比例 ≤100% | `noRatioClamp` |
| P16 免赔额顺序 | 先免赔后比例 | `dedAfterRatio` |
| P17 风控挂起 | P0 不出金额不占额 | — |
| P18 累计并发 | 乐观锁冲突 CONFLICT + fail-closed | — |
| P19 责任限额跨案累计 | 责任维度亦须跨案聚合 | `noLiabAccum` |
| P20 重算冲正 | 补交发票后累计 = 最新结论 | `noReversal` |
| P21 预授权冻结额度 | 累计授权 ≤ 年限额（三值额度） | `noPreAuthFrozen` |
| P22 预授权剩余责任限额 | 用剩余而非总额 | `totalLiabInPreAuth` |
| P23 无可赔费用授权 0 | 全目录外不得授权 | `authWhenAllRejected` |
| P24 拒赔行免赔额记 0 | 拒赔 ≠ 免赔 | `rejectDeductPollution` |
| P25 幂等 CAS 租约 | 并发在途请求 fail-closed | `noCAS` |
| P26 冲正通道 | 撤件可回退累计占用 | `noReversal` |
| P27 分位守恒（最大余数法） | 逐行合计 ≡ 总额口径 | `noApportion` |
| P28 限额 0 = 未配置 | 未配子限额不得全额拒付 | `zeroIsLimit` |

## 第二轮诊断发现并修复的缺陷（详见《诊断与优化》§9）
| # | 缺陷 | 实测（修复前） | 正解 | 级别 |
|---|---|---|---|---|
| **N1** | 责任限额未跨案累计 | 两案合计 8000（年限额 5000） | 次案 1000 | **P0 超赔 3000** |
| **N2** | 重算未冲正 | 重算净付 4000，累计仍 6000 | 净付 8000，累计 8000 | **P0 少赔 + 账实不符** |
| **N3** | 预授权不计冻结额度 | 累计授权 18000（年限额 10000） | ≤ 10000 | **P0 超授权** |
| **N4** | 预授权用责任总额 | 授权 5000 | ≤ 2000 | P1 |
| **N5** | 无可赔费用仍授权 | 授权 8000 | 0 | P1 |
| **N6** | 拒赔行把费用记成免赔额 | deduct=500 | deduct=0 | P1 告知书误导 |
| **N7** | 并发幂等无 CAS 租约 | find→reserve→save 窗口可双占额 | 第二个 fail-closed | P1 |
| **N8** | 无冲正通道 | 撤件后额度永久虚耗 | 可回退 | P1 |
| **N9** | 逐行取整分位不守恒 | 999.99 vs 1000.00 | 1000.00 | P1 对账不平 |
| **N10** | **限额 0 被当"限额为零"** | 净付 0.00 | 800.00 | **P0 静默少赔** |

> **N10 是最危险的一条**：它极其简单，却被第一轮 20 个场景用例全部掩盖——因为所有测试数据的 `feeSubLimit` 都"恰好"传了大额，**没有一条传入 0**。
> 教训：判定覆盖完备性的标准是"**边界值是否被覆盖**"，不是"用例数是否够多"。

## 本轮（第一轮）诊断发现并修复的缺陷（详见《诊断与优化》文档）
| # | 缺陷 | 实测（修复前） | 正解 | 级别 |
|---|---|---|---|---|
| D1 | 免赔额后置于比例 | 6000 | 8000 | **P0 少赔** |
| D2 | 免赔 > 费用产出负赔付 | **−3200** | 0 | **P0 负金额** |
| D3 | 重复提交双计免赔 | 首次 6000 → 重复 16000 | 6000 | **P0 超赔** |
| D4 | 跨租户额度串用 | T1 1000 / T2 **0** | 各 1000 | **P0 隔离违规** |
| D5 | 比例破 100%（AI 加成） | 1050 | 1000 | P1 超赔 |
| D6 | 不可获利原则缺失 | 赔付 8000 + 统筹 5000 > 1 万 | 4000 | P1 被保险人得利 |
| D7 | 风控未接入理算 | 高风险仍出金额 | HOLD 挂起 | P1 |
| D8 | 累计读-改-写竞态 | 并发双计 | 乐观锁 CONFLICT | P1 |
| D9 | 预授权按 100% 授权且无生命周期 | 15000（差额 3000 需追收） | 12000 + hold/capture/void | P2 |
| D10 | 拒赔仍消费控额 / 状态语义不完备 | — | 拒赔不占额 + PARTIAL/HOLD | P2 |

## 包结构
```
com.bone.synap.settlement
  Money                     金额值对象（BigDecimal，HALF_UP，clampNonNegative/clampMax）
  domain/   FormulaSnapshot(fingerprint) FeeConfig AdjustmentLine IndemnityCalculation ClaimContext
  engine/   SettlementEngine EngineFlags LimitControl PreAuthResult ConcurrentAccumulationException
  engine.strategy/ Strategies(Fixed/Allowance/Reimbursement)
  port/     Ports(Ledger/Trace/Catalog/Outbox/Accumulation/SettlementStore/PreAuth + InMemoryPorts)
            ConcurrencyLockPort   并发串行锁端口（默认 JvmConcurrencyLock；集群可换 Redis/DB 租约实现）
  adapter.jdbc/  JdbcBackedPorts   7 端口的 JDBC 生产实现（乐观锁 CAS / 冲正 / 预授权 TTL）
  adapter.spring/ SettlementEngineFactory   纯 Java 装配工厂（Bone 接线示意）
  probe/    ProbeSuite       30 探针 + 19 变异（runAll() 供 JUnit 驱动）
  scenario/ ScenarioSuite    28 真实 TPA 场景（runAll() 供 JUnit 驱动）
  diag/     Diagnostics2     第二轮取证诊断（10 项缺陷，修复后判定全 OK，可复现）
src/main/resources/schema.sql   H2/MySQL 双兼容 DDL
src/test/java/...               SettlementEngineProbeTest / ScenarioSuiteTest / JdbcPortsIntegrationTest
```
设计边界（六边形）：domain 纯逻辑，端口仅声明接口；实现（JDBC 端口 → C11 占额 / C4 快照 / C6 目录 / C10 直付通道 / Outbox）由 `adapter/` 注入。

## 已知限制（诚实披露，未做）
1. 第三方已付按可赔基数**比例分摊**；生产应按费用类别归因（社保内优先核销），需条款确认。
2. 年度限额按费用行**顺序截断（先到先得）**，未按比例摊薄；多费用类型并存时需条款确认优先级。
3. **S13 多保单仅独立理算**，未实现 ISO 834「比例分摊(pro-rata by limit)/等额分摊」——对应条款需产品确认后另开工。
4. 本模块已用 **H2 集成测试**验证 JDBC 持久化路径（CAS/冲正/预授权/TTL/幂等），但仍为内存库；生产接入 MySQL **仅需切换 `SqlDialect`**（H2 `MERGE...KEY` / MySQL `ON DUPLICATE KEY UPDATE`，由 `SqlDialect.auto(ds)` 按元数据自动识别，端口代码零改动）。`SqlDialectTest` 守住两方言 SQL 文本正确性，`JdbcPortsMySqlCompatTest` 进一步在 H2 `MODE=MySQL` 下**真实执行** MySQL 方言 SQL（upsert / 占位 no-op / 累计更新），证明"切换方言即上线 MySQL"非空话。真实 MySQL 实例回归仍建议生产接入时补一轮。
5. 已用 **H2 内存库**补齐并发证据：`SettlementConcurrencyStressTest`（40/24 线程共享限额、重复提交、冲正交叉）与 `ConcurrencyLockSeamTest`（锁可插拔、同 key 串行）全绿；**分布式锁参考实现 `DbLeaseConcurrencyLock` 已提供并测试通过**——`DbLeaseConcurrencyLockTest`（跨实例同 key 互斥 + 超时 fail-fast）+ `DbLeaseConcurrencyLockStressTest`（两引擎实例共享同一 H2 库、40 线程猛攻同一保单，跨实例零超赔 / 零丢失更新 / 零乐观锁冲突，锁失效即 fail-closed 转红），把分布式锁从「接缝级」证据补到「负载级」。真实 MySQL 并发压测仍建议生产接入时补一轮（验证行锁 / 隔离级别与租约表并发），但核心可插拔契约已闭环。
