# synapClaim 智能理算引擎设计（独立限界上下文，复用并修正 tpa-saas）

> **定位**：本文件是 synapClaim **理算（Settlement）子域**的工程化设计真源。回答"是否应独立设计一个智能理算引擎、参考 `pk-tpa-saas/bone-tpa-saas` 的 `bone-tpa-intelligent-adjustment` 实现"这一决策，并给出**可落地形态 + 平台边界 + 复用/修正清单 + 决策图接线 + 防假绿探针**。
> **关联**：《工程落地版》§5 责任与理算模型 / §3.3 端口表 / §7.3 决策图 `Adjust` 节点 / §8 演进路线；《依赖 BONE-AI 能力图谱》§4 串联（C2/C4/C11）。
> **纪律**：本文档为**设计层**，落地以《工程落地版》§9 DDL / §10 OpenAPI 为权威；规则 ID 以 `delivery/04` 为权威。

---

## 1. 结论：建议独立设计，但有严格形态约束

**建议：YES —— 把理算设计为 synapClaim 内一个独立限界上下文（独立 module + 六边形架构 + 端口边界），但绝不是以下三者：**

| 否认项 | 原因（证据） |
|---|---|
| ❌ **整体照搬 `bone-tpa-intelligent-adjustment` 模块** | 该模块是 **pk-tpa-saas 生产验证、在用的 proven 引擎**（三类责任策略 + min 聚合控额已被真实业务检验）。但 synapClaim 采用**六边形 + 端口防腐 + 多租户 + 监管快照**架构，与 tpa-saas 的进程内直连/实时读风格不同；整体搬运会把其直连直付/公账、实时读 `plan.version` 等**与 synapClaim 架构约束不符**的实现一并带进来。故**复用其策略模式与控额语义作为基线**，在新模块内按 synapClaim 约束重构（端口隔离、快照固化）。 |
| ❌ **拆成独立微服务/进程** | 理算必须与会案件（ClaimCase）、责任（AdjLiability）、结算（SettlementOrder）、额度（LedgerPort/C11）在**同一事务边界**内写结果、占额度。独立进程引入分布式事务 + 延迟。synapClaim 现有"domain 纯逻辑 + port 防腐"架构已天然支持**进程内 module 隔离**，无需跨进程。 |
| ❌ **把理算塞进 `bone-ai-rules`（C2）当规则推理** | 理算 95% 是**确定性金额算术**（免赔累加、比例乘算、min 取交、公账/个账分账）。金融"同输入同输出可复算"要求精确 `BigDecimal` 语义，DSL 表达算术既不可控又破坏快照可复算。C2 只适合其**前置的条件/因子求值**（见 §4）。 |

**正解形态**：synapClaim 内 `synap-claim-settlement` 模块（hex 架构），以 `SettlementEngine` 门面编排"规则求值(C2) → 确定性算术(本域) → 冻结快照 → 占额(C11) → 审计(C4) → 事件(Outbox)"，**以生产验证的 tpa-saas 理算引擎为基线复用其策略与控额语义**，作为决策图 `Adjust` 节点的**唯一理算事实源**（避免双源漂移）。

---

## 2. 参考实现实证：可复用 vs 须修正

对 `bone-tpa-intelligent-adjustment` 逐文件取证：

### 2.1 可复用（模式级，不是代码级）

| 模式 | 参考位置 | 映射到 synapClaim |
|---|---|---|
| **三类责任策略 + 工厂分发** | `engine/strategy/LiabilityAdjustmentStrategy`（接口：`checkAdjustable`/`adjustmentInvoice`/`buildAdjustFormula`/`getSupportedType`）+ `LiabilityAdjustmentStrategyFactory`（按 `LiabilityTypeEnum` 注入 `Map` 分发） | 定额(FIXED_AMOUNT)/津贴(ALLOWANCE)/报销(REIMBURSEMENT) 三类策略 → 《工程落地版》§5.1 流水线。工厂分发可插拔新增责任类型，符合"维度可插拔"要求。 |
| **报销策略确定性流水线** | `ReimbursementLiabilityAdjustmentStrategy.adjustmentInvoice()`：逐费用段 `currentAmount→免赔→笛卡尔因子求赔付比例→payout=(current-deduct)*ratio→四舍五入(HALF_UP, 计算位6/结果位2)→费用子限额→min(当前,责任限额,费用限额)` | 与 §5.1 `责任匹配→…→次限额→责任额度→年封顶→累计占额` **逐环节对应**，可直接作为本域策略实现蓝本。 |
| **min 聚合多维度控额** | `limit/LimitControlEngine.limitControl()`：个人/责任/计划/险种/共保/就诊类别/次日/三方直付 各维度算 `limit-historySum`，最终 `quotaDetailMap.values().stream().min()` | 与 §5.4 "多维度控额取交集合 `min`" **完全一致**，语义可整段移植。 |
| **可读公式轨迹** | `buildNumericFormula()` 生成每笔 `FormulaFactor`（feeType/money/ratio/deduct/limit）的可读公式串 + 记录 `version`/`tenantId` | 直接服务"可复算/审计"需求 → 升级为**物化快照**（见 §3.2）。 |

### 2.2 须在 synapClaim 新架构下补强（复用基线 + 架构升级，非修补破损）

> 说明：`bone-tpa-intelligent-adjustment` 是**生产验证、在用的 proven 引擎**，其策略模式与 min 聚合控额直接作为 synapClaim 理算引擎的复用基线。以下各项是 synapClaim 因**六边形 / 端口防腐 / 多租户 / 监管快照**约束，对基线做的**架构升级**（不是因为参考引擎有缺陷）：

| 项 | 参考实现现状 | synapClaim 补强方案 |
|---|---|---|
| **端口隔离（外部系统经 port）** | `LimitControlEngine` `@Autowired DirectPaymentFeignClient` / `PooledAccountFeignClient` 并 `queryAccount()` **在引擎内直连直付/公账池** | 严禁引擎触达外部系统。直付/公账额度改为经 `LedgerPort`(C11)/`IntegrationPort`(C10) 在**应用层**获取后注入上下文；引擎只读 `context` 内存值（端口铁律）。 |
| **公式快照固化** | 全模块无 `snapshot`；`Reimbursement…` 读 `context.getLiability()`（live `LiabilityConfig` from `plan.version`）重算 | 理算时**物化 `formula_snapshot`**（已解析的免赔/比例/限额/目录版本/因子值），钉入赔案，源版本被改仍可自包含复算（§5.2）。满足金融"同输入同输出可复算"与监管追溯。 |
| **目录编码匹配** | `AdjustUtil.checkElementSection`（§5.2.1 提及）按名称匹配 | 改为**目录编码匹配** `claim_item.item_code` + `catalog_version`（§8.2），名称漂移即漏判是错算高发源。 |
| **trace/outbox** | 模块无 `TracePort`/`OutboxPort` 痕迹 | 理算完成写 `TracePort`(C4) 快照 + 经 `OutboxPort` 发事件（额度变更/结算单），满足"业务落库+事件投递原子性"。 |
| **跨案累计** | 单案内 min 聚合，**无保单级年度免赔/限额跨案累计** | 新增 `AccumulationPort`：年度免赔额 / 年度限额在保单+账期维度跨案聚合（见《理算引擎内部设计》§9 + 参考实现 P10/P11）。 |
| **可解释性/规则溯源** | 公式轨迹仅工程可读，无规则 ID 映射 | 每条费用行记录命中规则 ID（`R-DED-01`/`R-RAT-01`/`R-CAP-01`/`R-DIR-01`），可生成客户告知级说明（《内部设计》§9 + 参考实现 P9）。 |
| **商保直付预授权** | 基线无显式预授权步骤（直付场景缺预算授权） | 新增 `preAuthorize(ctx, snap, estimatedTotal)`：就诊前核验 + 预算授权 = `min(预估−剩余免赔, 剩余年限额, 责任限额, 计划剩余)`，授权不占额、结算时再占（《内部设计》§2.1/§9.5 + 参考实现 S9/S10）。对应亿保「智慧E保即时结算」。 |
| **拒赔建模** | 基线无拒赔结论（永远付一点） | 新增 `settleStatus`(PAY/PARTIAL/REJECT) + `rejectType`(目录外/免责) + `denyReasons`(等待期/免责/指定医院外)：整案 REJECT 净付 0 且不占额；费用级拒赔登记 rejectType（《内部设计》§9.6 + 参考实现 S5/S11/S12）。对应亿保前置核验 + 泰康责任免除。 |
| **领域模型归属** | 模型在 `bone-tpa-sdk.adjustment` | synapClaim 模型归本域，`infrastructure.client` 转换上游 DTO，禁泄漏 `tpa`/`saas`/`pk` 术语（§3.3 铁律②）。 |

---

## 3. 目标架构：端口驱动 + 平台边界

```
┌─────────────────── synap-claim-settlement（独立 module，六边形）──────────────────┐
│                                                                                  │
│  domain/                                                                          │
│   ├ SettlementEngine（门面：编排下列步骤，纯逻辑）                                  │
│   ├ strategy/  FixedAmount / Allowance / Reimbursement  （复用 §2.1 策略模式）       │
│   ├ LimitControl  （复用 min 聚合，但只读 context 内存，不触外部）                    │
│   ├ IndemnityCalculation（A6 聚合：AdjLiability/AdjustmentLine/FormulaSnapshot）    │
│   └ model/  LiabilityConfig† / InvoiceAdjustmentContext / Quota（**本域值对象**）     │
│                                                                                  │
│  application/                                                                     │
│   └ SettleClaimUseCase  → 编排 port：                                              │
│      ① RuleEnginePort(C2)   求"适用性/因子值/目录编码"（规则求值层）                 │
│      ② SettlementEngine    确定性算术（本域，DSL 之外）                            │
│      ③ LedgerPort(C11)     占/冲额度（SOFT→…→CONFIRMED）                           │
│      ④ TracePort(C4)       写理算快照（可回放/审计）                                │
│      ⑤ OutboxPort          发 SettlementCalculated / QuotaChanged 事件             │
│                                                                                  │
│  infrastructure.client/  ← 防腐层：转换 bone-tpa-sdk / 上游 DTO → 本域值对象        │
│  infrastructure.persistence/ ← metadata-sdk 映射（AdjLiability/SettlementOrder…）   │
└──────────────────────────────────────────────────────────────────────────────────┘
   †LiabilityConfig 来源：C2 规则库下发（配置驱动），但**落盘即物化为 FormulaSnapshot**
```

### 3.1 平台边界（关键"串联"点）

| 层 | 归属 | 理由 |
|---|---|---|
| **条件/因子/目录求值** | `bone-ai-rules`(C2) 规则 DSL | 责任适用性(`restrictOutInsure`)、赔付比例笛卡尔因子(`payPercent` by 社保/医院等级)、目录编码匹配——这些是**规则**，适合配置化 + 热更 + 回归门禁（《对标优化》P0-2）。参考 `CartesianProductGetter.valueGetter()` 正属于此步。 |
| **金额算术** | synapClaim 本域（native） | 免赔累加、比例乘算、`min` 取交、公账/个账分账——精确 `BigDecimal(10,2)` HALF_UP，**不可入 DSL**（破坏可复算）。 |
| **额度** | `LedgerPort`(C11) | 占额/冲额/信任额度，由平台额度台账统一，反诈命中即熔断回退（《MRM与直付通道增强设计》）。 |
| **审计** | `TracePort`(C4) | 理算快照不可变、可回放，对接后督 100% AI 质检。 |

> **对《工程落地版》§8 "理算语义全部迁入 bone-ai-rules" 的精炼**：改为**"求值层迁入 C2，算术层留本域"**的清晰切分——既享受规则热更/回归门禁，又保住财务精度与可复算。这是比参考实现更优的业界实践（Guidewire / SAP MDG / 平安产险均采"规则管适用、引擎管金额"双层）。

---

## 4. "智能"的正确边界（AI 不碰钱）

参考实现的"智能"= 配置驱动多态，**非 AI/ML**。synapClaim 可在**输入侧**引入真·AI，但严格置于护栏之后：

| 增强点 | 位置 | 护栏 |
|---|---|---|
| **责任适用性推理** | AI 依据诊断/费用段/保单，建议"哪些责任适用"，写入 C2 规则上下文作为先验 | 仅建议；最终适用性由 `RuleEnginePort` 规则判定；类型 `AiSuggestion`，须 `Confirmed` 显式确认方可计入。 |
| **费用项→目录编码** | 多模态/LLM 将自由文本费用项映射到 `item_code`+`catalog_version` | 映射结果进 C2 目录匹配；低置信转人工；**不改写确定性算术**。 |

**硬不变量**：AI 产出**永不直接产生金额**；任何赔付额必须来自本域算术 + `FormulaSnapshot`；符合"AI 不碰钱 + 类型护栏 + 证据链"（《依赖图谱》§5 合规姿态）。

---

## 5. 决策图接线（6 节点 `Adjust` 节点）

```
… → AiAssess(LLM+C5+C6+E5) → FraudScreen(图E2+C9) → Arbitrate(DECISION+C3)
      → Adjust(RULE+C2+C11)  ← 【本引擎为唯一理算事实源】
            SettleClaimUseCase:
              RuleEnginePort(C2) 求值 → SettlementEngine 算术
              → LedgerPort(C11) 占额 → TracePort(C4) 快照 → OutboxPort 事件
      → （结算 A7 由下游监听事件驱动，经 C10 直付三态灰度）
```

- `Adjust` 节点 handler = `SettleClaimUseCase`，**必须**调用本引擎；**禁止**在别处另写一套理算（防双源漂移，单事实源原则）。
- 与 `FraudScreen` 串联：反诈 P0 团伙强制转人工时，`SettleClaimUseCase` 在占额前短路返回 `PENDING_MANUAL`。
- 与 `LedgerPort`(C11) 串联：理算完成占 `SOFT`，`SettlementOrder` 创建占 `SOFT`（§5.3 时序），`CONFIRMED` 后转实占；拒赔/失败回冲。

---

## 6. 防假绿探针（继承取证铁律）

每个探针必须"删掉实现哪一行会红"：

1. **单事实源探针**：集成测试**实际驱动** `SettleClaimUseCase` 走完 6 节点；断言 `AdjustmentLine` 已落库 + `LedgerPort` 被调用。若引擎未被接（双源漂移），测试即红。
2. **快照复算探针**：用 `version=V1` 配置理算 → 改源配置为 `V2` → 用 `FormulaSnapshot` 复算 → 断言金额**不变**（证伪"实时读漂移"）。
3. **负向金额探针**：换一张**真实合规**发票 → 断言金额改变且不为 0；构造"费用段全为 0" → 断言 `payout=0` 不报错。
4. **控额 min 探针**：设个人/责任/计划三维限额分别为 1000/800/500 → 断言实际赔付 `=500`（`min` 生效）。
5. **AI 护栏探针**：注入 `AiSuggestion`（未 `Confirmed`）→ 断言**不计入**金额；注入超置信目录映射 → 断言转人工、不产生金额。
6. **端口隔离探针**：`LimitControlEngine` 单元测试**不依赖**任何 Feign/DB（参考实现之错的反面）；`LedgerPort` 用内存桩。

---

## 7. 落点清单（回写《工程落地版》）

| 目标章节 | 变更 |
|---|---|
| §3.3 端口表 | 明确 `SettlementEngine` 经 `RuleEnginePort`(C2)/`LedgerPort`(C11)/`TracePort`(C4)/`OutboxPort`；新增 `synap-claim-settlement` module 归属说明。 |
| §5.1 / §5.4 | 注明流水线由本引擎 `strategy`+`LimitControl` 实现，复用 tpa-saas 模式但修正 §2.2 债务。 |
| §5.2 | 强化 `formula_snapshot` 为"物化 + 自包含复算"硬不变量（对 tpa-saas 实时读债务的修正）。 |
| §7.3 `Adjust` 节点 | 标注"唯一理算事实源 = `SettleClaimUseCase`"，禁止双源。 |
| §8 演进 | 将"理算语义全部迁入 C2"精炼为"求值层 C2 + 算术层本域"。 |

---

## 8. 不做清单（Don't copy from tpa-saas）

1. 不整体照搬模块（复用策略/控额基线 + 新架构重构）；2. 不在引擎内调直付/公账 Feign（端口隔离）；3. 不实时读 `plan.version` 配置（物化快照）；4. 不名称字符串匹配（目录编码）；5. 不漏 trace/outbox（审计+原子事件）；6. 不把 `BigDecimal` 算术塞进规则 DSL；7. 不把 AI 产出直接当金额（护栏）；8. 不做单案孤立理算（补保单级跨案累计）；9. 不把年度限额当"免赔额"相减（封顶语义，详见《真实 TPA 场景测试用例》§11）；10. 不把拒赔当成"少付一点"（须显式 REJECT 结论，§9.6）。

> **下一步（可选，未执行）**：将 `synap-claim-settlement` module 拆为带人日/里程碑的任务卡（策略三件套 + LimitControl + FormulaSnapshot 物化 + 端口桩 + 6 探针）；优先 P0 做"快照复算探针 + 端口隔离"以先除参考实现两大债。
