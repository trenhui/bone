# synapClaim 理算引擎内部设计（算法 · 端口契约 · 业界基准 · 防假绿探针）

> **定位**：本文件是《智能理算引擎设计（独立限界上下文）》的**内部实现层**——在"独立 module + 端口边界"决策落地后，给出引擎**门面编排、三类责任策略算法、LimitControl 聚合、FormulaSnapshot 物化契约、端口签名、决策图接线与防假绿变异探针**。架构形态与平台边界见前置文档；领域模型与口径以《工程落地版》§5 / §7.3 / §7.4 为权威真源。
> **业界基准锚定**为本文件强制项：每个核心设计点必须能对应到可核查的行业实践，避免"自创不可证伪"的设计。

---

## 1. 业界基准锚定（设计点 ↔ 行业实践）

| synapClaim 设计点 | 行业基准 | 证据锚 |
|---|---|---|
| 规则层(C2) + 算术层(本域) 双层分离 | Guidewire **Rules Engine（适用性/ eligibility）+ Calculation Engine（金额）** 分离；SAP MDG 决策与计算解耦 | 规则热更/回归门禁不污染金额精度；金额走确定性引擎 |
| 策略工厂 + 插件化责任类型 | 平安产险/众安"责任插件化"；保险核心系统普遍 Strategy+Factory | 新增责任类型零侵入（§7.4 实证） |
| min 聚合多维度控额 | ISO 834 比例分摊 + 各再保/团险"多维度限额取交集最小"；太保/泰康限额矩阵 | 任一维度触顶即截断，防超赔 |
| FormulaSnapshot 物化 | SOX/NAIC Model Audit Rule **"同输入同输出可复算"**；金融台账不可变快照 | 配置变更后旧案可自包含复算，支撑争议复议 |
| 三值额度模型（initial−claim−frozen） | 团险/企业补充医疗公账池通用记账；ACORD 额度语义 | 冻结与已用互斥统计，杜绝双计 |
| 准入 checkAdjustable + 归因 checkAdjustRecord 两段 | 理赔"事实认定—归因"双轨（拒赔五要素）；监管可解释性要求 | 零赔付必须可归因（§7.4） |
| 端口隔离 + Outbox 原子事件 | 六边形架构（Cockburn）；微服务"业务落库+事件投递原子性" | 引擎不触外部系统，防腐+可测 |

---

## 2. 引擎总体编排（SettlementEngine 门面）

门面 `SettleClaimUseCase` 编排**单事务内**以下步骤，顺序不可调换（对应《工程落地版》§7.4 聚合事务）：

```
1. [C2 RuleEnginePort.eval] 求"责任适用性 + 因子值 + 目录编码"
   - 输入：ClaimContext(出险/发票/保单 plan_version) + 责任集
   - 输出：ApplicableLiability[]（含锁定 rule_version / liability_version / catalog_version）
   - 失败 → MANUAL_REQUIRED（不进金额计算）

2. [本域] 构建充血上下文 AdjustmentContext
   - 载入已解析的三段费用（统筹/自付一/自付二/丙类/第三方，来自 §5.1 前置输入）
   - 注入历史赔付（AdjLiability 同责任/个人/计划历史 AdjustmentLine）

3. [本域] 逐责任调 Strategy.adjustmentInvoice(context)
   - 输出 AdjustmentLine（六成分：fee_calculate/ratio_payout/fee_payout/fee_deduct/fee_occupy/fee_remain）
   - 累加 payout，按 account_type 分账（§5.4）

4. [本域] LimitControl.limit(context) → 各维度可用额，取 min；FORCE 截断，ALARM 仅告警

5. [C11 LedgerPort.reserve] 两阶段预授权 QuotaReservation(SOFT)
   - 失败/反诈命中（FraudScreen P0）→ 短路返回 PENDING_MANUAL，不占额

6. [C4 TracePort.snapshot] 写 FormulaSnapshot（物化，§5）
   - 与 IndemnityCalculation 同事务落库

7. [OutboxPort] 发 SettlementCalculated / QuotaReserved 事件
   - 事务提交后异步投递（§3.3 铁律④）
```

> **铁律**：步骤 3–4 为纯函数式确定性计算，**不碰 DB/AI/网络**；步骤 1/5/6/7 全部经端口。这使得步骤 3–4 在单测中可脱离 Spring 运行（§3.3 端口铁律延伸）。

### 2.1 直付预授权编排（亿保「智慧E保即时结算」场景，置于步骤 1 之前）
```
0. [本域 + AccumulationPort] preAuthorize(ctx, snap, estimatedTotal)
   - 仅计算，不占额、不提交累计（预授权 ≠ 结算）
   - 授权额度 = min(预估费用 − 剩余年度免赔, 剩余年度限额, 责任限额剩余, 计划剩余)，负值钳零
   - 失败（授权 0）→ 直付通道降级为「先垫付后报销」或挂起人工
```
> 预授权与结算一致性护栏：结算净额必须 ≤ 预授权额度（S10 用例固化）。

---

## 3. 聚合与值对象（domain model）

```
IndemnityCalculation (A6 聚合根)
 ├ claimId / planVersion(锁定) / liabilityVersion(锁定) / catalogVersion(锁定)
 ├ lines: List<AdjustmentLine>        // 每责任一条
 ├ formulaSnapshot: FormulaSnapshot  // §5 物化
 ├ totalPublic / totalIndividual     // §5.4 分账合计（不变式 total=public+individual）
 ├ settleStatus: PAY / PARTIAL / REJECT          // 理算结论（拒赔建模，§9.6）
 ├ denied: boolean / denyReasons: List<String>   // 整案拒赔原因（等待期/免责/指定医院外）
 ├ ruleTrace: Map<feeType, List<ruleId | rejectType>>  // 可解释性/规则溯源
 └ status: FROZEN → CONFIRMED → PAID / ROLLBACKED

AdjustmentLine（六成分，落 adj_calculation_line）
 ├ liabilityUuid / liabilityVersion
 ├ fee_calculate   // 计算基准额（剔除不赔后）
 ├ ratio_payout    // 按比例应付 = (基准−免赔)×ratio
 ├ fee_payout      // 实付（经控额 min 后）
 ├ fee_deduct     // 免赔扣减
 ├ fee_occupy     // 占额
 ├ fee_remain     // 剩余额度
 ├ rejectType     // 拒赔类型（CATALOG_EXCLUSION 目录外 / EXCLUSION_CLAUSE 免责项目 / null=正常赔付）；§9.6
 └ formulaHash    // 六成分 + 输入 的哈希，逐行可复算

FormulaSnapshot.FeeConfig（报销型单费用配置，§5）新增 `rejectType`：非空表示该费用项不赔，payout=0 并登记 rejectType
                （对应普康内置医保三大目录「丙类不赔」、泰康「责任免除项目」）。
```
年度免赔额 / 年度限额语义铁律：
- 年度免赔额 = 相减语义：保险实付 = max(0, 净额 − 剩余免赔额)，免赔额度累加 consumed。
- 年度限额   = 封顶语义：保险实付 = min(净额, 剩余年限额)，占用额 = 实际应付（取交而非相减）。
  误用"相减"处理年限额会导致年限额内首案即被扣为 0 赔付（已被真实场景用例 S6b/S10 捕获并修复，见 §11）。

AdjustmentContext（充血上下文，贯穿发票×责任双层，§7.4 实证）
 ├ liabilityLimit / remainingDeduct / quotaMap / alarmQuotaMap
 ├ invoiceFeeMap / invoiceFeeHistoryPayoutMap
 └ nextLiabilityFlag（后付责任链穿透）
```

**FormulaSnapshot 物化契约（防漂移核心）**：冻结以下不可变副本，而非仅存 `rule_version` 引用——
`{ liabilityVersion, catalogVersion, deductConfig, payPercentMatrix(已求值), liabilityLimit, feeTypeLimits, accountType, quotaInitial(各维度), 三段费用输入值, formulaHash }`。理由：源 `plan.version` 被改后，旧案凭快照**自包含复算**金额不变（§5.2 实证债修正）。

---

## 4. 三类责任策略算法（精确伪码）

### 4.1 FixedAmountLiability（定额给付，R-DIR/R-CAP 适用）
```
adjustmentInvoice(ctx):
  base = ctx.liability.fixedAmount                 // 定额，无比例
  payable = min(base, ctx.liabilityLimit)          // 受责任额度(min)
  return line(fee_calculate=base, ratio_payout=base, fee_payout=payable, fee_deduct=0, fee_occupy=payable, fee_remain=liabilityLimit−payable)
```
> 津贴/定额责任**无免赔、无比例乘算**，直接受限额截断。

### 4.2 AllowanceLiability（津贴，按天/次）
```
adjustmentInvoice(ctx):
  days = ctx.invoice.stayDays (or times per TimesLimit定义)
  base = days × ctx.liability.dailyAllowance
  payable = min(base, ctx.liabilityLimit, ctx.timesLimit.amount)  // 责任额度 ∩ 次限额(min)
  return line(..., fee_payout=payable)
```
> 次限额由 `TimesLimit`（按天/次/项目）维度参与 min 聚合（§5.2.1 ②）。

### 4.3 ReimbursementLiability（报销，主体，R-DED/R-RAT/R-CAP）
```
adjustmentInvoice(ctx):
  for feeType in ctx.liability.liableFeeTypes(ordered):   // §5.1 specificOrder：合理金额→自付二→总自费→丙类→超限价
     origin = ctx.invoiceFeeMap[feeType]                  // 来自三段拆分(§5.1 前置)
     if origin < 0: origin = 0
     // ① 免赔（发票层或理算层，依 liabilityDeduct.deductTarget，§5.2.1 ①）
     deduct = DeductCalculator.calc(ctx, origin)
     current = origin − deduct
     // ② 赔付比例（笛卡尔因子求值，来自 C2 规则层已求值矩阵）
     ratio = ctx.payPercentMatrix[feeType]                // e.g. 社保内 90% / 社区 95%
     payout = (current × ratio/100).setScale(6, HALF_UP)
     // ③ 四舍五入（结果位 2，HALF_UP）
     payout = payout.setScale(2, HALF_UP)
     // ④ 费用子限额 ∩ 责任限额 ∩ 历史已用(min)
     feeLimit = ctx.feeTypeLimits[feeType] − ctx.invoiceFeeHistoryPayoutMap[feeType]
     liabilityLimit = ctx.liabilityLimit − ctx.liabilityPayAmount
     payout = min(payout, liabilityLimit, max(feeLimit,0))
     // ⑤ 累计占额 + 后付责任链穿透判定
     ctx.liabilityPayAmount += payout
     if feeLimit == payout: ctx.nextLiabilityFlag = true
     emit line(fee_calculate=origin, ratio_payout=payout_preLimit, fee_payout=payout, fee_deduct=deduct, fee_occupy=payout, fee_remain=...)
  return aggregated lines
```
> 与参考 `ReimbursementLiabilityAdjustmentStrategy.adjustmentInvoice()` 逐环节对应，但**修正**：因子值由 C2 规则层预求值注入（不在引擎内求值），外部额度经端口注入（不内联 Feign），全程不触网络。

---

## 5. LimitControl 算法（min 聚合，含三值模型）

```
limit(ctx):
  quotaDetail = {}
  for dim in [PERSONAL, LIABILITY, PLAN, COVERAGE, SHARING, VISIT_TYPE, TIMES, DIRECT_PUBLIC, DIRECT_PERSONAL]:
     if dim not applicable(ctx): continue
     limit_i   = ctx.quotaInitial[dim]         // 维度初始额（来自 C11 / 配置锁定）
     used_i    = ctx.historyPayout[dim]        // 历史已赔付（同维度）
     avail_i   = max(limit_i − used_i, 0)
     if dim.controlType == FORCE:  quotaDetail[dim] = avail_i
     else (ALARM):                  alarmQuota[dim] = avail_i   // 仅告警，不截断
  // 三值模型修正：PERSONAL/PLAN 等用 (initial − claim − frozen)，避免双计（§5.3）
  ctx.quotaMap = quotaDetail
  finalQuota = min(quotaDetail.values())       // 各适用维度取交集合 min
  return max(finalQuota, 0)
```
> 维度可插拔（一期 4 维，二期扩共保/直付余额，§5.3）；新增维度**不改主链路**（仅扩 `dim` 枚举 + 初始额来源）。`min` 语义与参考 `LimitControlEngine.limitControl()` 一致，但读取的是**端口注入的内存值**，且 FORCE/ALARM 分流明确。

---

## 6. 决策图 Adjust 节点接线（§7.3）

| 项 | 值 |
|---|---|
| node_name | `Adjust` |
| node_type（对外） | `RULE`（Phase B 前以 RULE 承载，扩展后 `FRAUD`/`ADJ` 另议，不新增自定义类型） |
| 触发 | 决策图 `Arbitrate` 之后（链路：证据→解析→核责→AI评估→反欺诈→决策→**理算**） |
| handler | `SettleClaimUseCase`（**唯一理算事实源**，禁止他处另写理算） |
| 端口 | `RuleEnginePort`(C2) 求值 → 本域算术 → `LedgerPort`(C11) 预授权 → `TracePort`(C4) 快照 → `OutboxPort` |
| 失败分支 | 理算异常/反诈 P0 → 挂起 `MANUAL_REQUIRED`（§7.3） |
| 插入点 | `claim_flow_config.adjustment_order` 驱动 Shadow/Assisted/Automated（§5.5，不硬编码） |

---

## 7. 防假绿变异探针（继承取证铁律）

每个探针断言"删掉实现哪一行会红"，且**真实执行**（非仅断言调用）：

| # | 探针 | 变异点（删/改即红） |
|---|---|---|
| P1 | **单事实源**：集成测试实际驱动 `SettleClaimUseCase` 走完 6 节点，断言 `AdjustmentLine` 落库 + `LedgerPort.reserve` 被调用 | 若引擎未被接（双源漂移），测试即红 |
| P2 | **快照复算**：用 `V1` 配置理算 → 改源为 `V2` → 用 `FormulaSnapshot` 复算 → 断言金额不变 | 若实现实时读 `plan.version`（参考债务），红 |
| P3 | **负向金额**：合规真实发票 → 金额改变且 ≠0；构造费用段全 0 → 断言 `payout=0` 不报错 | 若 `(current×ratio)` 漏 `max(0)` 守卫，红 |
| P4 | **控额 min**：个人/责任/计划 = 1000/800/500 → 断言实赔 = `500` | 若 `min` 取错维度或漏维度，红 |
| P5 | **AI 护栏**：注入 `AiSuggestion`(未 `Confirmed`) → 断言**不计入**金额；超置信目录映射 → 断言转人工、无金额 | 若 AI 输出直接入算术，红 |
| P6 | **端口隔离**：`LimitControl`/`Strategy` 单测**零 Feign/DB 依赖**（用内存桩） | 若引擎内联调直付/公账（参考债务），红 |
| P7 | **分账不变式**：`total == public + individual` 全用例断言 | 若分账分支漏账，红 |
| P8 | **归因完备**：零赔付 `AdjustmentLine` 必带 `rejectType`（受控码，非空非自由文本） | 若归因缺失，红 |
| P9 | **可解释性/规则溯源**：每条费用行 `ruleTrace` 含命中规则 ID（`R-RAT-01` 等），`explain()` 文本含费用与金额 | 若 `recordTrace=false`（规则溯源缺失），红 |
| P10 | **跨案累计·年度免赔**：同保单两案，首案消抵全部年度免赔、次案仅余剩余免赔（如 1000→首案 net=0、次案 net=800） | 若 `applyAccumulation=false`（跨案累计缺失），红 |
| P11 | **跨案累计·年度限额**：同保单两案，首案用尽年度限额、次案仅余剩余限额（如 1000→首案 net=0、次案 net=800） | 若 `applyAccumulation=false`（跨案累计缺失），红 |

> 探针 P2/P6 直接对冲 synapClaim 架构约束下的两大补强点（实时读漂移、内联 Feign→端口）；P1 对冲双源漂移；P9–P11 覆盖可解释性与保单级跨案累计（业界最佳实践「金额可解释」「年度免赔/限额累计」）。门禁要求：P1–P11 **全部转红**方说明"测到了"，否则判定假绿。参考实现 `reference-impl/synap-claim-settlement` 已实跑验证：正确引擎 P1–P11 全绿，且 `noMin`/`livePlan`/`ignoreConfirm`/`noTrace`/`noAccum` 五类变异均被捕获。

---

## 8. 与《工程落地版》落点

| 章节 | 落点 |
|---|---|
| §5.1 / §5.3 / §5.4 | 注明流水线由本引擎 `Strategy`+`LimitControl` 实现，复用 tpa-saas 模式但修正 §2.2 债务（见《智能理算引擎设计》） |
| §7.3 `Adjust` 节点 | 标注 handler=`SettleClaimUseCase` 唯一事实源 + 端口序列（C2→本域→C11→C4→Outbox） |
| §7.4 `IndemnityCalculation` | 补 `AdjustmentLine` 六成分 + `FormulaSnapshot` 物化字段清单（本文件 §3）+ 策略三件套算法（本文件 §4） |
| §3.3 端口表 | 补 `SettlementEngine` 经 `RuleEnginePort`(C2)/`LedgerPort`(C11)/`TracePort`(C4)/`OutboxPort` 的四段调用契约 |

---

## 9. 增强补遗（业界最佳实践完善项）

基于「proven 参考引擎为基线 + synapClaim 架构约束补强」的路线，补充四项业界最佳实践能力，均已落地于 `reference-impl/synap-claim-settlement` 并经 P9–P11 实跑验证：

### 9.1 跨案累计（保单级年度免赔 / 年度限额）
单案理算不足以覆盖保单级聚合约束。新增 `AccumulationPort`：以 `policyId + periodKey` 为键，跨案聚合「已用免赔额 / 已用限额」，理算时消费剩余额度并回写。
- 年度免赔额（**作用于费用基数、先免赔后比例**）：从"符合约定的医疗费用（可赔基数）"中扣除，再对余额按比例给付；首案先消抵，次案仅余剩余免赔（P10 / S3）。
  > ⚠️ 曾把年度免赔错当"赔付额减项"（先比例后免赔），导致系统性少赔（费用 2 万/80%/免赔 1 万：错解 6000、正解 8000）。已修复并由 P16 探针 + `dedAfterRatio` 变异固化（详见 §12）。
- 年度限额（**封顶语义**）：首案实付 = min(净额, 剩余年限额)，次案仅余剩余限额可用（P11 / S6b）。
  > ⚠️ 曾误将年度限额按"相减"处理导致首案即被扣为 0 赔付；已由真实场景用例 S6b/S10 捕获并修复为封顶语义（详见 §11）。
- 三值额度模型（initial−claim−frozen）保证不双计；与 `LedgerPort`(C11) 信任额度联动，反诈命中即熔断回退。
- 业界基准：Guidewire Accumulation / ISO 年度免赔累计 / ACORD 保单级限额。

### 9.2 可解释性 / 规则溯源
每条 `AdjustmentLine` 记录命中规则 ID（`ruleTrace`：`R-DED-01`/`R-RAT-01`/`R-CAP-01`/`R-DIR-01`），并生成客户告知级 `explain()` 文本。
- 监管硬性要求（保险条款告知、理赔结论可解释）；客户沟通可直接引用。
- 每条金额均可回溯到「哪条规则 + 哪个因子值」，满足 SOX/NAIC 可审计（P9）。
- 业界基准：Guidewire "every payment line item explainable"。

### 9.3 决策图 `Adjust` 节点与前序节点的契约衔接
`Adjust` 节点非孤立，输入契约来自决策图前序节点：
- `FraudScreen`（E2+C9）→ 若反欺诈 P0（高置信团伙）则 `Adjust` 直接挂起，不进入算术；预警经 `NotificationPort`(C9) 触达。
- `Arbitrate`（DECISION+C3）→ 决策结论（`PAY`/`PARTIAL`/`REJECT`）作为总闸：`REJECT` 净赔付 0；`PARTIAL` 按比例缩放各 `AdjustmentLine`。
- `AiAssess`（LLM+C5+C6+E5）→ 仅以 `AiSuggestion`（责任适用/目录编码/加成）经 `Confirmed` 护栏进入算术，**AI 永不产生金额**。

### 9.4 幂等重算与快照版本化
- **幂等键 = `claimId + formulaHash(snapshot) + inputHash(ctx)`**：命中即返回首次结论，**不重复占额、不双计累计**（P12 / S19）。
  - 仅凭 `claimId` 不够：补交发票、AI 建议状态变化、快照变更都属**新的理算请求**，必须重算。
  - 仅凭 `snapshotVersion` 不够：源配置变更必须体现为新指纹，否则幂等会掩盖快照冻结缺陷（P2 依赖此性质）。
- 补充证据/重开案件：新快照版本重新走 `SettleClaimUseCase`，旧结论标记 superseded，不覆盖，保证可追溯对账。
- 累计提交同时具备 `DUPLICATE`（同 claimId 不累加）与 `CONFLICT`（乐观锁版本不一致，fail-closed 抛异常转人工）两种结果（P18）。

### 9.5 商保直付预授权（亿保核心能力，§2.1）
`preAuthorize(ctx, snap, estimatedTotal)` 在就诊前给出"本次最多可直付额度"，结算时再真实占额。
- 授权额度 = `min(预估费用 − 剩余年度免赔, 剩余年度限额, 责任限额剩余, 计划剩余) × 保守赔付比例`，负值钳零。
  > **保守比例**：授权须按最低可能的赔付比例折算（报销型取各费用类型比例最小值）。按 100% 授权会使医院端直付额高于实际结算，差额需向患者追收（S16：15000@80% → 授权 12000 而非 15000）。
- **预授权生命周期** `PreAuthPort`：`hold(ttl)` 冻结（三值模型 frozen）→ `capture(实际额，剩余自动释放)` → `void(撤销)` → `isExpired`（到期释放），杜绝冻结额度长期占用（S15）。
- **预授权不占额、不提交累计**（区别于结算），避免重复占用；结算净额必须 ≤ 预授权额度（一致性护栏，S10）。
- 授权为 0 时直付通道降级为「先垫付后报销」或挂起人工，不阻塞就医。
- 业界基准：亿保「智慧E保即时结算」就诊前核验 + 预算授权；HMO/PPO 预授权（prior authorization）通用模式。

### 9.6 拒赔建模（亿保前置核验 + 泰康责任免除）
真实 TPA 必须有"拒赔"结论，而非永远付一点。两类拒赔：
- **整案拒赔**：`ClaimContext.denyReasons`（等待期内 / 免责条款 / 指定医院外等）→ `settleStatus=REJECT`，净赔付 0，**不提交跨案累计**、占额 0，Outbox 发 `REJECTED:` 事件。对应亿保直付「前置核验」与泰康条款第八条责任免除。
- **费用级拒赔**：`FeeConfig.rejectType`（目录外 `CATALOG_EXCLUSION` / 免责项目 `EXCLUSION_CLAUSE`）→ 该行 payout=0 并登记 `rejectType`，`ruleTrace` 记录拒赔原因。对应普康内置医保三大目录「丙类不赔」与泰康「责任免除项目」。
- `settleStatus ∈ {PAY, PARTIAL, REJECT, HOLD}`：`PAY`=应付；`PARTIAL`=有削减（费用级拒赔/限额截断）或当期应付为 0；`REJECT`=整案拒赔；`HOLD`=风控 P0 挂起（不出金额、不占额度，P17/S18）。
- 业界基准：监管可解释性要求（拒赔须告知原因码，禁自由文本）；理赔"事实认定—归因"双轨。

---

## 10. 不做清单（Don't）

1. 不在引擎内调直付/公账 Feign（端口隔离，P6）。
2. 不实时读 `plan.version` 配置（物化快照，P2）。
3. 不把 `BigDecimal` 算术塞进规则 DSL（双层分离，§1 基准）。
4. 不名称字符串匹配目录（编码匹配，§5.2.1 ④）。
5. 不双源记额度（三值模型，§5.3）。
6. 不产生"双理算事实源"（唯一 handler，P1）。
7. 不做单案孤立理算（补保单级跨案累计，§9.1）。
8. 不把年度限额当"免赔额"相减（封顶语义，§9.1/§11）；不把拒赔当成"少付一点"（须显式 REJECT 结论，§9.6）。
9. **不把免赔额后置于赔付比例**（免赔作用于费用基数，P16/§12）。
10. **不产出负赔付金额**（类型层钳零，P3b）。
11. **不允许赔付比例破 100%**（含 AI 加成后，P15）。
12. **不重复消费免赔/限额**（幂等键 + 乐观锁，P12/P18）。
13. **不让额度跨租户串用**（键含 tenantId，P13）。
14. **不让被保险人因理赔获利**（第三方已付先抵扣，P14）。
15. **不给风控高风险案件出金额**（HOLD fail-closed，P17）。

> **参考实现已落地（2026-10-26 ✅，已修正"孤儿模块"误判）**：`reference-impl/synap-claim-settlement/`（JDK21 可编译运行）已将本设计落成可执行代码——`ProbeSuite` **P1–P30 共 30 条探针全绿**，且 **19 类变异 19/19 全部被捕获**；`ScenarioSuite` **28 个真实 TPA 场景用例（普康/亿保/泰康条款锚定）全部通过**。构建与验证见该目录 `README.md`。
>
> **第二轮诊断（2026-10-26，见《诊断与优化》§9）**：在第一轮"全绿"之上再取证，又坐实 **10 项缺陷（含 4 个 P0）**——责任限额未跨案累计（超赔 3000）、重算未冲正（少赔 + 额度账实不符）、预授权不计冻结（超授权 1.8 倍）、**限额 0 被当作"限额为零"全额拒付（被 20 个场景用例的测试数据掩盖，典型假绿）**，另有预授权责任总额口径、无可赔费用仍授权、拒赔行免赔额语义污染、并发幂等无 CAS 租约、无冲正通道、逐行取整分位不守恒 6 项。全部已修复，并各配一条回归探针（P19–P28）+ 一个"关掉必红"变异开关 + 一个业务场景（S21–S28）。可复现取证见该目录 `diag/Diagnostics2.java`。基线 `bone-tpa-intelligent-adjustment` 为**生产验证、在用的 proven 引擎**，本实现复用其策略模式与 min 聚合控额语义，并按 synapClaim 六边形/端口防腐/多租户/监管快照约束做架构升级（跨案累计 + 可解释性 + 端口隔离 + 快照固化 + 直付预授权 + 拒赔建模 + 幂等/租户隔离/不可获利/风控 fail-closed/乐观锁）。
> **下一步（可选，未执行）**：将 `synap-claim-settlement` 拆为生产 module 任务卡；**P0 先做「幂等键 + 租户隔离键 + 乐观锁累计」**（资金安全底座），再落地 P2（快照复算）+ P6（端口隔离）+ P9（规则溯源）+ S9/S15/S16（直付预授权接入 C10 三态灰度）+ S11/S12/S18（拒赔/挂起与 Arbitrate 节点契约衔接）。

---

## 11. 真实 TPA 场景用例（驱动本轮优化，防假绿的业务级验证）

基于普康（普康宝内置医保三大目录 + 医保分割信息自动理算）、亿保（智慧E保即时结算 / 直付预授权 / 鹰感风控）、泰康（甲乙丙类差异比例 / 年免赔累计抵扣 / 有社保未结算降比 / 等待期·责任免除拒赔）的真实条款，设计 **14 个确定性场景用例**，全部落 `reference-impl/.../scenario/ScenarioSuite.java` 并带期望值断言。用例清单、期望值与"用例倒逼的引擎优化"见专文：

**`synapClaim 理算引擎真实 TPA 场景测试用例与优化（普康·亿保）.md`**

用例直接捕获了本轮最重要的真实缺陷：年度限额误用"相减"导致首案赔付 0（S6b/S10），以及补齐了 proven 基线缺失的直付预授权（S9/S10）与拒赔建模（S11/S12/S5）能力。

---

## 12. 诊断与优化（取证式复核，本轮）

对「本设计方案 + 参考实现」做了一次**不看文档宣称、直接取数**的复核：先写诊断探针打印实测值，再与业界正解比对。结果坐实 **4 个 P0 缺陷**与 6 个 P1/P2 缺口，全部已修复并为每条护栏补了「关掉即红」的变异开关。清单、根因、业界基准与回归证明见专文：

**`synapClaim 理算引擎与理赔系统诊断与优化（业界最佳实践）.md`**

其中金额语义类缺陷（免赔额错序 / 负赔付 / 比例破 100%）直接修正了本文 §2 编排与 §5 算法描述，编排权威顺序以该文 §4 为准：
`幂等/风控 → C4 快照 → C2 求值 → 第三方已付抵扣 → 年度免赔(基数级) → 比例 → 理算免赔 → 取整 → 多维度 min 控额(含年度限额) → C11 累计(乐观锁) → 占额 → Outbox`。
已知未做项（多保单 ISO 834 分摊、第三方已付归因粒度、生产落库与并发压测等 5 项）在该文 §6 如实披露。
