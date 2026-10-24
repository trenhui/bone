# 智能理赔平台详细技术方案（bone-ai 落地版）

> 版本：v1.0　状态：技术方案（可评审）　作者：BONE 平台架构
>
> **本文定位**：在 `synapClaim`（业务领域与产品方案）、`pk-tpa-saas`（真实 TPA 智能核赔落地实现）与 `bone-ai`（已实现的 AI 能力中台）三者基础上，输出一份**可直接落地到代码**的智能理赔详细技术方案。它回答"怎么做"，把业务规则、领域模型、状态机、AI 编排落到 bone-ai 六个模块与具体技术选型上。
> **SSOT 边界（本文不定义契约）**：物理 DDL 以**工程落地版 §9** 为唯一权威、接口契约与数据字典以 `delivery/05` 为唯一真源、业务规则以 `delivery/04`（业务规则书）为唯一真源、领域模型与流程/事件以**工程落地版 §4 / §7** 为权威。本文仅做 **bone-ai 落地实现视角**（模块映射 / 编排 / 评测 / 里程碑），对契约与领域模型只做「实现映射 + 指向」，不重述，避免漂移。

## 文档版本历史

|**版本**|**日期**|**作者**|**变更说明**|
|---|---|---|---|
|v1.0|2026-10-14|BONE 平台架构|初版技术方案（可评审）|
|v1.1|2026-10-21|BONE 平台架构|结构治理：§8 数据模型删除内联 DDL、改指工程落地版 §9；文首明确「本文不定义契约」；§9.1/§10.2 对齐 delivery/04-05 真源；品牌统一 synapClaim|

---

## 目录

- [1. 方案定位与设计原则](#1-方案定位与设计原则)
- [2. 总体架构与模块映射](#2-总体架构与模块映射)
- [3. 领域模型与聚合](#3-领域模型与聚合)
- [4. 责任与理算引擎](#4-责任与理算引擎)
- [5. 核心状态机](#5-核心状态机)
- [6. 决策图引擎与 AI 编排](#6-决策图引擎与-ai-编排)
- [7. 资金状态机与业财一致性](#7-资金状态机与业财一致性)
- [8. 数据模型](#8-数据模型)
- [9. 接口契约](#9-接口契约)
- [10. 规则治理](#10-规则治理)
- [11. 评测与可观测](#11-评测与可观测)
- [12. 落地里程碑与 Go/No-Go](#12-落地里程碑与-gono-go)
- [13. 风险与对策](#13-风险与对策)
- [14. 与现有资产的对位表](#14-与现有资产的对位表)

---

## 1. 方案定位与设计原则

### 1.1 一句话定位

面向**门诊小额快赔**这一单点打透场景，构建一个"**AI 只建议、不碰钱；资金走确定性代码；决策可解释、可回放、可治理**"的智能理赔平台。平台输出的是带完整证据链与置信度的**建议/结论**，最终赔付与资金流转由确定性业务代码 + 人工终审决定。

### 1.2 设计原则（源自三方融合）

| # | 原则 | 出处 | 落地含义 |
|---|---|---|---|
| P1 | **AI 不碰钱** | 工程落地版 §7.3（决策图执行） / POC 核心差异化 | AI 只产出建议与置信度；金额计算全部是确定性代码；资金状态机由业务代码 + 事务保障，AI 推理失败绝不阻断资金流程 |
| P2 | **确定性分层** | 工程落地版 §3.1（架构分层，四层确定性） | 规则/计算/状态机为确定性层；AI 仅为建议层；AI 与资金之间由决策图引擎强一致衔接 |
| P3 | **治理靠结构而非约定** | bone-ai（ArchUnit/HC-001） | 短路、熔断、计量、人工终审、回归门禁由框架结构强制，不依赖调用方自觉 |
| P4 | **人工终审权保留** | bone-ai HITL 五态 / synapClaim R-MAN | 拒赔/大额/低置信/风险标记必须人工；AI 置信度 < 阈值强制转人工 |
| P5 | **可解释可回放** | bone-ai trace + synapClaim 决策追溯 | 每次决策落证据链（输入摘要/规则命中/AI 建议/置信度），一键回放决策路径 |
| P6 | **旁路→辅助→自动化** | synapClaim MVP Shadow 路径 | 三态开关（Shadow 旁路 / Assist 辅助 / Auto 自动化），每一步可回退 |

### 1.3 场景边界（MVP 一期）

- **险种**：企业补充医疗险 · 门诊 · 小额
- **渠道**：员工自助理赔（上传发票 + 清单 + 医保结算单）
- **决策**：自动通过（≤500 元且无风险）/ 系统建议拒赔（人工确认）/ 转人工 / 补件
- **资金**：报销模式快赔（打款到员工账户，两段式状态机）
- **非目标**（二期+）：住院/意外/重疾、医院端直付、多智能体风控、低代码可视化编辑器

---

## 2. 总体架构与模块映射

### 2.1 全景架构

```plaintext
┌─────────────────────────────────────────────────────────────┐
│ 接入层   员工端/工作台/保司对接   API网关(限流/鉴权;选型待定)    │
├─────────────────────────────────────────────────────────────┤
│ 业务层   synap-claim 单模块（DDD 四层）· 聚合 A1–A8            │
│          A1 Case / A2 Evidence / … / A8 Appeal（落地版 §4.1） │
│          工作台复用 bone 低代码 UI Schema；决策图自建 DAG     │
├─────────────────────────────────────────────────────────────┤
│ AI 能力层 bone-ai-gateway(LLM/熔断/计量)   bone-ai-rules      │
│          bone-ai-orchestration(管线)    bone-ai-document     │
│          bone-ai-trace(证据)           bone-ai HITL(人工终审)  │
├─────────────────────────────────────────────────────────────┤
│ 存储层   metadata-sdk(业务表, H2离线/生产MySQL8/TiDB) 事件总线(进程内) │
│          Redis(缓存)                                     │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 能力 → 模块映射（核心复用 bone-ai）

| 业务能力 | 落地模块 | 复用的 bone-ai 能力 | 需新增 |
|---|---|---|---|
| 单证解析 / OCR / 医疗事实提取 | `bone-ai-document` | PDFBox 文本抽取 + ClauseSplitter 切片 | OCR 适配器（Docling sidecar / PaddleOCR）（二期）、发票字段提取、医保三段拆分 |
| 核责规则（责任范围/目录/免赔/比例/封顶） | `bone-ai-rules` | QLExpress + RuleAsset 存库热更 + 回归门禁 + 灰度 | 规则书模板（R-AREA/R-DIR/R-DED/R-RAT/R-CAP/R-DEC/R-FAST/R-FRAUD） |
| LLM 评估 / 医学知识 RAG | `bone-ai-gateway` | OpenAI 兼容客户端 + Resilience4j 熔断/重试 + token 计量 | RAG 链路（切片/双路/RRF/rerank）（二期）、医学目录库（二期） |
| 决策流程编排 / 决策图执行 | `bone-ai-orchestration` | 最小管线 + 规则预检短路 + HITL 五态子域 | 决策图引擎（新增：自建轻量 DAG 执行器 + 节点 fallback） |
| 证据链 / 决策回放 / 审计 | `bone-ai-trace` | LlmCallRecorded/LlmUsageRecorded → 快照 | decision_trace 节点序列（证据→解析→核责→理算→决策） |
| 人工终审 / 复核工作台 | `bone-ai-orchestration` HITL | HitlItem 五态 + ReasonType 回流 + 处置链 | 拒赔通知书五要素、大额双人复核 |
| 规则灰度 / 回归门禁 | `bone-ai-rules` | RuleRegressionGate（正负例）+ shadow | 评测集（≥300 基准条）绑定规则版本 |
| 资金 / 支付 / 结算 | A7 `Settlement` 聚合（同模块，无独立服务） | 落地版 §4.1 已定义 | 两段式资金状态机、幂等、乐观锁、进程内事件总线 + afterCommit（二期 RocketMQ）、CDC 对账 |

> **关键架构红线**：`bone-ai-orchestration` **严禁管理资金状态**。资金状态由 A7 `Settlement` 聚合根推进（`SettlementOrder` / `FundingEvent`），并发走 `@Version` 乐观锁——**业务层禁 `SELECT ... FOR UPDATE`**（落地版 §11.1）。

### 2.3 模型选型与路由

- **部署形态**：敏感数据（病历/身份）走**本地 vLLM**，非敏感数据（条款检索）走**云端 API**，按客户合规要求切换（工程落地版 §3.8；FDE §四同口径）。
- **分层路由**：结构化字段抽取走**小模型**，复杂医学评估走**大模型**；两者统一经 `bone-ai-gateway` 的 LLM 客户端端口出口，计量/熔断由平台统一收口，不绕开网关自建客户端。

---

## 3. 领域模型与聚合

### 3.1 聚合总览（融合 synapClaim DDD 与 pk-tpa-saas 责任模型）

> **聚合清单唯一真源**：工程落地版 §4.1 的 A1–A8（该节已明确「聚合数由 9 收敛为 8：原 `RuleAsset` 聚合撤销」）。下表按 A 编号对齐，本表不再维护第二套聚合划分。

| # | 聚合根 | 职责 | 关键行为 | 参考实现 |
|---|---|---|---|---|
| A1 | `ClaimCase`（含 `ClaimItem`/`ClaimStakeholder`） | 赔案全生命周期（申请与案件合一） | `submit/amend/withdraw/assignToAdjuster/updateStatus/close` | 工程落地版 §4.1 / §4.3 |
| A2 | `EvidenceCollection` | 证据链采集与锁定 | `addDocument/extractMedicalFacts/lock` | 工程落地版 §4.1 / §4.3 |
| A3 | `DecisionExecution` | 决策图执行载体（只追加） | `start/recordTrace/complete` | 工程落地版 §4.1 / §4.3 |
| A4 | `PolicyBundle`（`Policy`/`Plan`/`Coverage`/`Liability`/`LiabilityMapping`；`LiabilitySharing` **二期**） | 责任与赔付配置（版本化） | `checkBenefit/calcLimit` | **pk-tpa-saas** PlanDTO/CoverageDTO/LiabilityConfig |
| A5 | `PersonalQuota`（`QuotaAccount`/`QuotaTransaction`） | 个人额度账务 | `freeze/unfreeze/confirm` | **pk-tpa-saas** PersonalQuotaService |
| A6 | `IndemnityCalculation` | 理算计算（公式快照） | `calculate/recalculate` | **pk-tpa-saas** AdjustEngine |
| A7 | `Settlement`（`SettlementOrder`/`FundingEvent`/`Invoice`） | 结算单与支付指令 | `execute/confirm/cancel/refund` | 工程落地版 §4.1 / §4.3 |
| A8 | `Appeal` | 申诉与 48h 复议 | `submit/decide` | 工程落地版 §4.1 |

> 注：审计能力不设独立聚合——落地版由 `claim_case_track`（append-only）+ `domain_event_outbox` 承载（§9.1），本表列出的 A8 为 `Appeal`。

> **本文早前版本列出的 `ClaimApplication` 与 `Account` 两个聚合已撤销**：① 申请与案件同属 `ClaimCase`——理赔业务表里没有 `claim_application`，单独建聚合会形成"有聚合无表"的空转；② 资金账户语义由 `PersonalQuota` 的额度账本承载（`quota_account`），本域不另建 `Account`，否则与额度账本构成两套余额真源。

### 3.2 责任模型（吸收 pk-tpa-saas，MVP 一期落地）

责任是核责与理算的核心。基于 pk-tpa-saas `LiabilityConfig` 提炼一期最小字段：

```java
// 责任（核责与理算单元）
Liability {
  String code;                 // R01…
  LiabilityType type;          // REIMBURSEMENT 医疗报销 / FIXED_AMOUNT 定额给付 / ALLOWANCE 津贴给付
  LiabilityLimit limit;        // 控额方式 + 责任额度 + 就诊类别额度(门诊急诊/住院/药店/特诊)
  PayPercent payPercent;       // SAME 同一比例 / DIFFERENT 笛卡尔积因子
  Deduct deduct;               // 免赔(方式/模式/形式/周期/对象/抵扣)
  TimesLimit timesLimit;       // 次期限额(按次/按日/按月)
  List<Restrict> restricts;    // 适用对象(性别/年龄/职业) + 适用限定(医院/药品/诊疗/病种黑白名单)
  int waitingPeriod;           // 等待期
  String nextLiabilityUuid;    // 后付责任链
}
```

**要点**（来自 pk-tpa-saas 实际语义）：
- **赔付比例笛卡尔积**：`PayPercentTypeEnum.DIFFERENT` 按因子（社保/医院等级/医院性质/承担费用/年龄/总费用/理算金额/赔案累计/赔付次数）取不同比例，用 `RangeTypeEnum` 区间判定。
- **免赔多维度**：`DeductEnum` 覆盖方式(绝对/相对)、模式(统一/个人/直付)、形式(金额/比例/天数)、周期(按年/按次)、对象(理算金额/发票费用)、抵扣(医保及三方/仅三方/均不可)。
- **后付责任链**：当前责任额度/个人/直付额度用尽时按 `NextLiabilityTypeEnum`（先付为0才赔后付/超出部分后付也赔/超出比例外不赔）触发链式赔付；**链长 ≤3 层、禁止循环闭环**。

### 3.3 理算公式（确定性，AI 不碰钱）

| 责任类型 | 公式 | 三段控额 |
|---|---|---|
| 医疗报销 | `min(金额×比例, 费用可用额度)`；发票免赔 `min((金额-免赔)×比例, 额度)`；理算免赔 `min(金额×比例-免赔, 额度)` | `Min(x, 费用可用额度, 责任可用额度)` |
| 津贴给付 | `min(日津贴×天数×比例, 津贴可用额度)`；免赔天数 `min(日津贴×(天数-免赔天数)×比例, 额度)` | 同上 |

---

## 4. 责任与理算引擎

### 4.1 理算执行主链路（吸收 pk-tpa-saas AdjustEngine）

```plaintext
校验(操作人权限/挂起/保单ACTIVE)
  → 数据准备(发票绑定责任, 按就诊日期升序+id 排序保证顺序累计)
  → 逐发票逐责任:
       checkAdjustable(对象/范围/等待期/医保/出险区间)
       → limitControl(限额聚合取各维度最小值)
       → strategyFactory.getStrategy(type).adjustmentInvoice(context)
       → checkAdjustRecord(归因: 合理金额0/免赔致0/控额致0)
  → 后付责任链(额度用尽触发, while 链式)
  → 结果聚合(按 accountType 分公/个账) → 更新个人额度(FREEZE) → 直付冻结
```

### 4.2 限额聚合引擎（核心：任一维度耗尽即截断）

一次聚合所有维度可用额度，最终取**最小值**作为该责任的 `liabilityLimit`（pk-tpa-saas `LimitControlEngine`）：

| 维度 | 可用额度 = | 备注 |
|---|---|---|
| 责任额度 | 责任总额度 − 该责任 uuid 历史赔付 | LIABILITY / LIABILITY_VISIT / LIABILITY_PERSONAL |
| 就诊类别额度 | 子额度 − 同责任同就诊类别历史 | LIABILITY_VISIT |
| 个人额度 | 初始额度 − 该人历史理赔 | PERSONAL / LIABILITY_PERSONAL |
| 计划额度 | planLimit − 该计划历史 | |
| 险种额度 | coverageLimit − 该险种全部责任赔付之和 | |
| 共保额度 | shareLimit − 关联责任赔付之和 | 按 shareCode 绑定 |
| 次期限额 | 按时间窗口筛选历史累计 | FORCE 截断 / ALARM 仅告警 |

**最终**：`liabilityLimit = min(全维度可用额度).max(0)`。

### 4.3 个人额度账务状态机

`QuotaStatusEnum`：`FROZEN 已冻结`（理算在途）→ `CONFIRMED 已确认扣减`；`UNFROZEN 已解冻` / `ROLLBACKED 已回滚`（重新理算前释放）。
- **冻结**：理算完成后 `calculatePersonalQuota(FREEZE)`，从历史记录（CONFIRMED+FROZEN）重算 `quotaClaim` 与 `quotaFrozen`。
- **释放**：重新理算前 `UNFREEZE` 并从库删记录；直付维度走 `Quota.freeze()/unfreeze()` 原子增减（乐观锁 + `QuotaTransaction.xid` 留痕）。

---

## 5. 核心状态机

### 5.1 决策执行五段式（编排状态机，与资金解耦）

| 状态 | 一致性 | 分段预算 | 到点动作（补偿） |
|---|---|---|---|
| 证据就绪（OCR 批量并发，≤5 张并发） | 最终一致 | ≤8s | 转人工 |
| 规则计算 | 最终一致 | ≤3s | 降级 AI 优先 |
| AI 评分 | 最终一致 | ≤6s | 缓存评分 |
| 图执行（决策图推理） | **编排确定性（同拓扑同输入走同路径）**；LLM 节点输出不保证可复现，以 trace 回放为准 | ≤8s | 回滚规则 |
| 决策输出 | 最终一致 | ≤2s | 重试 3 次转人工 |
| — | — | 合计 27s + 余量 3s = **30s** | 全局 deadline 到点统一转人工 |

> **超时预算必须自上而下分解，不能各段自定**：端到端 P95 承诺 30s（PRD §13.1），分段预算之和不得超过它。本文早前版本写作 30/5/10/15/3（合计 63s）——单「证据就绪」一段就吃掉全部 SLO，等于没有预算分配。调度器须持有**全局 deadline（30s）**，任一段超时即按上表降级；「自动通过路径含资金打款」的 P95 60s 是另一口径（工程落地版 §11.2），不可与建议返回的 30s 互比。
>
> **OCR 段预算口径（与 PRD §13.1「单张 ≤5s」的算术关系）**：证据就绪段 ≤8s 指**提交时已上传材料齐备**案件的解析等待；单张 OCR ≤5s 为单份材料解析耗时。案件常含多张发票 + 清单 + 医保结算单（>3 份），串行必然超预算，因此：① OCR 批量**并发**（≤5 张并发，队列削峰，工程落地版 §11.6）；② 提交时材料未齐的案件走「先受理后补件」异步路径，证据就绪段从**最后一份材料上传完成**起算，不消耗提交→结论的 30s 预算；③ 超限即触发本段降级（转人工补录，§7.2）。
>
> **>5 份材料降级与 30s 口径边界**：单案 OCR 并发 ≤5 张；材料 >5 份时分批解析或超批转人工补录，避免证据就绪段 ≤8s 预算被单案多材料击穿。**单案 >5 份的案件不承诺 30s 建议结论时效**（单案上限 20 份时最坏 4 批 × 8s = 32s > 全局 deadline）：提交时材料未齐走「先受理后补件」异步路径；提交时已齐 >5 份走分批异步 + 结论通知，不计入 30s SLO 分子（口径卡见工程落地版 §11.2）。若需对多材料案件保 30s，须将单案限额收紧至 ≤10 份或提升 OCR 并发——属容量决策，不在本方案默认假设内。

### 5.2 状态机落地到 bone-ai-orchestration

> 定位说明：五段式状态机为**新增落地项**（落到 orchestration 新增的 `decisiongraph` 子域）。当前 bone-ai-orchestration 仅有最小线性管线 + HITL 五态子域，无现成五段式状态机；一期可先简化为顺序管线 + 全局超时兜底转人工，再逐步拆五段。**简化不降低验收判据**：M2 的 P95 ≤30s 与全局 deadline 30s 在顺序管线形态下同样成立（各段预算之和 ≤27s + 3s 余量自上而下分配，调度器持有全局 deadline），§12.2 M2 行不因实现简化而放宽。每段：
- **证据就绪**：`bone-ai-document` 解析完成 + `EvidenceLocked` 事件后触发，缺件→补件/转人工。
- **规则计算**：`bone-ai-rules` 核责规则并行；规则命中 R-FRAUD（验真失败/重复报销）→ 转人工；R-AREA 命中 → 建议拒赔。
- **AI 评分**：`bone-ai-gateway` 调 LLM（经熔断/重试/计量）；置信度按**三档**分派：< 0.7（或高风险）强制人工终审、0.7-0.85 进复核档、≥ 0.85 且无风险直出。
- **图执行**：决策图引擎强一致推理（见 §6），AI 节点失败 fallback 到规则节点。
- **决策输出**：发布 `DecisionMade`，落 trace，重试 3 次仍失败转人工。

### 5.3 案件状态机（业务视角）

**对外只有两个状态值**：`PENDING_EVIDENCE → REVIEWING`。

`APPROVED` / `REJECTED` / `REQUEST_MORE_EVIDENCE` 是**决策结果**（响应体 `decision` 字段，见 `delivery/05`），`CLOSED` 由 `fundingStage` 表达——**三者都不属于 case status 枚举**。把决策结果写进状态链会让「状态机」与「决策结果」两个维度混成一个，前端与对账两侧都只能靠约定解析。

内部细粒度状态（pk-tpa 状态码 7/10/11/13/…/50）只存在于 `claim_case.status`，经投影表映射为上表两值，不对外暴露（真源：`delivery/05` + 工程落地版 §6.1）。

---

## 6. 决策图引擎与 AI 编排

### 6.1 决策图定义（metadata-sdk 持久化，自建轻量 DAG 执行器）

> 落地性修正：JGraphT 本地离线仓库**不可用**（`org/jgrapht` 目录不存在），一期不引通用图库，改**自建轻量 DAG 执行器**（约 200-400 行，入度为零拓扑执行）。节点类型固定 ≤5 类、层数 ≤3，无需通用图库，天然支持节点 fallback 与短路。二期如有通用图建模需求再评估 JGraphT。

- **存储**：`graph_definition`（tenant_id, graph_code, version, definition JSON，UK(tenant,graph_code,version)），用 metadata-sdk `@Table/@Column` 建模（HC-001 唯一持久化抽象）。
- **执行**：自建 DAG 执行器，入度拓扑排序，P99 < 50ms；节点间证据传递；短路优化（某分支已定案则跳过后续节点）。
- **节点类型**：≤5 类（本文以角色名 `RULE_NODE / AI_NODE / DECISION_NODE` 示意，命名口径见下方）；每个 AI 节点配置 `fallback_rule_node`（AI 超时/失败自动回退规则，防资金路径被 AI 拖垮）。

> **节点命名口径**：本文 §6.2 示例用**角色名**示意，**实现名以工程落地版 §7.3 为准**——`EvidenceLoad`（证据装载与解析）、`RuleCheck`（核责）、`AiAssess`（AI 评估）、`Arbitrate`（确定性仲裁）、`Adjust`（理算）；`START`/`END` 是 DAG 入口/出口表达，不占节点数。对外 `node_type` 取值仅 `OCR`/`RULE`/`LLM`/`DECISION` 四类（05 契约 §4.4），落 `claim_decision_trace.node_type`；内部节点名落 `node_name`。
- **人机回环**：DECISION_NODE 命中转人工条件 → 产出 HITL 待办（`hitl_item` 落库），人工裁决后回写继续。

### 6.2 决策图示例（门诊小额快赔）

```plaintext
START
  → RULE_NODE 验真(R-FRAUD-06)        失败→DECISION 转人工(票据验真失败)
  → RULE_NODE 核责(R-AREA/R-DIR)      不属责任→DECISION 建议拒赔(人工确认)
  → AI_NODE 医疗事实/风控评估(置信度三档)
       <0.7 或高风险→DECISION 强制人工终审
       0.7-0.85→DECISION 复核档
       ≥0.85 且无风险→继续
  → RULE_NODE 理算(免赔/比例/封顶)     确定性计算
  → DECISION_NODE 快赔判定(≤500 且无风险)
        → 自动通过 → 资金状态机
        → 否则转人工复核
END
```

### 6.3 bone-ai 六模块在决策图中的角色

| 决策图节点 | 调用模块 | 说明 |
|---|---|---|
| RULE_NODE 核责 | `bone-ai-rules` QLExpress | 规则热更、灰度、回归门禁 |
| AI_NODE 评估 | `bone-ai-gateway` | 熔断/重试/计量；置信度三档（<0.7 强制 / 0.7-0.85 复核 / ≥0.85 直出） |
| AI_NODE 医学 RAG（**二期**） | `bone-ai-gateway` + `bone-ai-document` | 切片→向量→RRF→rerank；权限过滤；一期无 RAG 链路（PRD §7.6） |
| DECISION_NODE 转人工 | `bone-ai-orchestration` HITL | HitlItem 五态 + ReasonType 回流 |
| 全过程 | `bone-ai-trace` | 每个节点落 decision_trace 节点序列 |

### 6.4 AI 超时分层与动态降级

| 查询类型 | 判定 | 超时 | 降级 |
|---|---|---|---|
| 简单（单文档） | — | 3s | 规则兜底 |
| 标准 RAG（2-5 文档）（二期） | — | 5s | 缓存 |
| 复杂 RAG（>5 文档）（二期） | — | 8s | 异步转 bone-ai-orchestration |

> 一期无 RAG 链路（PRD §7.6），RAG 两行仅作二期预算参考；一期 LLM 调用统一走 `bone-ai-gateway` 的熔断/重试/token 计量。

### 6.5 LLM 调用安全边界与提示词治理

- **上线门禁（三重校验，缺一不得启用 `AiAssess`）**：送模型前 PII 脱敏 + 指令与数据分离 + 输出 Schema/白名单/OCR 交叉三重校验，任缺其一即不得启用 `AiAssess` 节点（工程落地版 §7.2）。
- **提示词资产化**：提示词按模板/版本管理（对应 `adj_calculation.prompt_version` 列，工程落地版 §9.4），与规则版本/模型版本绑定留痕，随决策回放。
- **提示词回归门禁**：prompt injection 回归样本随每次提示词变更重跑，对齐规则侧 `RuleRegressionGate`。

---

## 7. 资金状态机与业财一致性

### 7.1 简化两段式资金状态机

基于理赔并发量不大、资金流向单一，采用简化两段式，由 A7 `Settlement` 聚合根在单事务内推进 + `@Version` 乐观锁保障强一致（落地版 §6.4 资金状态机 / §11.1 并发控制）。

```plaintext
stateDiagram-v2
    [*] --> 软预占: 余额充足性校验（SOFT_RESERVED）
    软预占 --> 硬预占: 单事务扣减, 乐观锁版本冲突重试≤3（HARD_RESERVED）
    硬预占 --> 已支付: 支付成功（PAID）
    硬预占 --> 复核冻结REVIEW_FREEZE: 大额(≥1万)或风险标记, 双人复核
    复核冻结REVIEW_FREEZE --> 硬预占: 复核通过
    复核冻结REVIEW_FREEZE --> 已释放RELEASED: 复核不通过, 释放预占
    硬预占 --> 已释放RELEASED: 撤销/放弃, 释放预占
    硬预占 --> 支付失败FAILED: 支付失败, 回冲硬占
    已支付 --> 退款REFUNDED: 发起退款(红冲发票)
```

> 资金七态（`SOFT_RESERVED`/`HARD_RESERVED`/`REVIEW_FREEZE`/`PAID`/`RELEASED`/`FAILED`/`REFUNDED`）真源为工程落地版 §6.4/§7.5，本图与其一致；补偿事件经发件箱投递，保证对账一致。

### 7.2 核心实现（幂等 + 事务 + 进程内事件总线）

> 落地性修正：资金持久化统一走 metadata-sdk（HC-001 唯一持久化抽象），不写直连 Mapper；支付指令投递一期走**进程内事件总线 + `afterCommit`**（bone-ai 现有 `DomainEventPublisher`），RocketMQ 二期引入（本地 jar 可用但未集成）。

```java
@Service
public class SettlementApplicationService {          // A7 Settlement 聚合的应用服务（落地版 §4.1）
  private final SettlementOrderRepository orderRepo;  // metadata-sdk repository
  private final FundingEventRepository fundingRepo;   // metadata-sdk repository
  private final DomainEventPublisher events;          // 进程内事件总线

  @Transactional
  public void reserve(String claimNo, BigDecimal amount, String stage, int attempt) {
    // 幂等键口径：{claimNo}:{stage}:{attempt}（attempt=占额周期序号；落地版 §7.5/§9.4：uk(tenant_id, idempotency_key)）
    String idempotencyKey = claimNo + ":" + stage + ":" + attempt;
    if (fundingRepo.existsByIdempotencyKey(idempotencyKey)) return;

    // 软预占：余额充足性校验（SOFT_RESERVED）
    SettlementOrder order = orderRepo.findByClaimNo(claimNo);
    if (order.getAvailable().compareTo(amount) < 0) throw new InsufficientBalanceException();

    // 硬预占：聚合根内改状态，@Version 乐观锁（禁 SELECT FOR UPDATE，落地版 §11.1）
    order.reserve(amount);       // 聚合内不变量校验
    orderRepo.save(order);       // 版本冲突 → 重试 ≤3 次后转人工

    // 资金与案件状态分属不同聚合：此处只写资金流水，案件状态由 A1 Case 消费事件推进
    fundingRepo.save(FundingEvent.of(claimNo, "HARD_RESERVED", amount, idempotencyKey));

    // 支付网关严禁事务内同步调用 → afterCommit 投递，失败进重试队列
    events.publish(new PaymentInstruction(claimNo, amount, idempotencyKey));
  }
}
```

### 7.3 资金一致性保障

| 机制 | 说明 |
|---|---|
| 幂等 | 所有资金写操作带 `idempotencyKey`，唯一索引 + duplicate-key 捕获，防二次出金 |
| 事务内 | 资金状态变更必须在**单个事务**内（聚合根内 `@Version` 乐观锁，禁 `SELECT ... FOR UPDATE`），AI/编排层严禁改资金状态 |
| 事件投递 | 支付指令事务提交后 `afterCommit`（`@TransactionalEventListener`）投递到进程内事件总线，失败重试队列；二期 RocketMQ 对账 |
| 对账 | CDC 监听 `funding_events` 实时或 T+1 对账；资金差错率目标为 0（生产出款口径） |

### 7.4 大额双人复核

金额 ≥ 1 万元（或规则标记风险）触发：资金状态机进入 **`REVIEW_FREEZE`（复核冻结）**态，生成复核待办（HITL 人工终审），需两个 reviewerId 双人复核通过后才 `HARD_RESERVED → PAID`；复核不通过回滚冻结。`funding_event.stage` 增加 `REVIEW_FREEZE` 值以表达此中间态。

---

## 8. 数据模型

> **持久化统一原则（HC-001）**：一期全部表实体用 `bone-metadata-sdk` 的 `@Table/@Column` 注解建模 + repository 接口持久化，**不写直连 JdbcTemplate/MyBatis Mapper**。存储方言可切：离线开发/演示用 metadata-sdk 内置 **H2 嵌入式**（本地仓库内置 h2database，离线可编译可运行），生产再切 MySQL/TiDB（方言可切），满足"离线可跑 + HC-001 不破"。下列 SQL 仅为表结构语义参考，落地为 metadata-sdk 实体。

### 8.1 核心表族速览（字段级以工程落地版 §9 为准）

> **DDL 真源**：物理表结构（35 张表、字段、索引、约束）以**工程落地版 §9**为**唯一权威**；本文不重复 `CREATE TABLE`，以免两套 DDL 漂移。下表仅列本方案涉及的核心表族与职责，字段级以工程落地版 §9 为准。

| 表族 | 代表表（工程落地版 §9 为准） | 职责 |
|---|---|---|
| 理赔与证据 | `claim_case` / `claim_item` / `claim_evidence` / `claim_medical_fact` | 报案、明细、单证、医疗事实（脱敏哈希） |
| 决策与回放 | `claim_decision_execution` / `claim_decision_trace` / `claim_rule_hit` | 决策图执行、节点轨迹、规则命中留痕（可回放） |
| 责任与理算 | `adj_plan` / `adj_coverage` / `adj_liability` / `adj_calculation` | 责任/限额版本化、理算快照（`formula_snapshot`） |
| 资金与结案 | `settle_funding_event` / `settle_order` / `quota_account` / `quota_transaction` | 资金事件溯源、支付指令、个人额度账务 |
| 集成与一致性 | `domain_event_outbox` / `idempotency_record` / `integration_sync_log` | 发件箱、幂等、存量同步 |

> 决策图 / 规则资产 / 人工终审（HITL）三张表由 **bone-ai 中台**承载（`graph_definition` / `rule_asset` / `hitl_item`），**不在工程落地版 §9 的 35 张理赔表内（清单见工程落地版 §9.1）**；其物理结构以 bone-ai 对应模块为准。

> **数据与隐私红线**：`insured_id_hash` 用 HMAC-SHA256+盐，明文身份证号不上送、不落地（见 delivery/02 数据脱敏）；`consent_record_id`/`consent_withdrawn_flag` 留痕（PIPL 29 条）。

---

## 9. 接口契约

### 9.1 接口总览（以 delivery/05 为真源）

> 接口端点、对外枚举、数据字典以 `delivery/05`（接口契约与数据字典）为**唯一真源**（MVP 子集）；完整 OpenAPI 以**工程落地版** §10 为权威。本文不重复罗列端点，落地要点见 §9.2。

### 9.2 关键契约要点

- **提交理赔**：`policyNo`、`insuredIdHash`（服务端 HMAC 计算）、`claimType=OUTPATIENT`、`items[]`、`invoiceNo`；请求头 `Idempotency-Key`（必填；创建类取客户端业务键——报案号由服务端生成，客户端不可能预知，工程落地版 §10.3）；响应 `claimId + status`。错误码：400 参数 / 409 业务键冲突 / 422 业务规则拒绝（等待期内等）/ 503 依赖不可用（可重试）。
- **上传材料**：`doc_type` ∈ {INVOICE, LIST, MEDICAL_RECORD, **MEDICAL_SETTLEMENT(医保结算单)**}；`ocr_confidence` 独立字段（< 0.7 触发转人工）。
- **审核决策**：`decision` ∈ {APPROVED, REJECTED, REQUEST_MORE_EVIDENCE}；拒赔必填 `comment`（拒赔五要素）；大额 ≥1 万须双人复核；操作全量入审计日志。决策图输出契约另立 `decisionResult` ∈ {AUTO_APPROVE, SUGGEST_REJECT, MANUAL_REQUIRED, NEED_MORE_EVIDENCE}，与人工 `decision` 分两个字段/两种落点（工程落地版 §7.3）。
- **通用强制**：① 所有写接口必带 `Idempotency-Key` 请求头（服务端去重返回首次结果）；② 仅网络类错误可重试（间隔 1s ≤2 次），业务类（422/409）不重试转人工；③ 认证：员工端/工作台 OIDC/SSO，对接方 API Key+HMAC，TenantId 贯穿；④ 审计三件套（请求摘要 ≤200 字 + 响应 + 耗时）；⑤ 分页 page/pageSize（默认 20 上限 100）。

---

## 10. 规则治理

### 10.1 规则生命周期（融合 bone-ai-rules + synapClaim 规则书）

```
草稿 → 评测集回归(bone-ai RuleRegressionGate 正负例) → 灰度(shadow) → 发布(30s 热生效)
        ↑ 任一回归失败即拒绝发布，逐条回显 label
```

- **RuleAsset**（存库热更）：code/expression/status/grayPercent/hitBlock/currentVersionNo，DSL 用 `qlexpress4:4.1.3`（bone-ai-rules 实际引入坐标，勿与 QLExpress 3.3.2 混淆）。
- **回归门禁**：发布前跑正负例基准集（`RegressionCase` input/expectHit/label），任一不符拒绝发布；求值错误（运行时缺陷）恒计失败，不借负例混过。
- **灰度/Shadow**：`grayPercent` 控制新规则流量；Shadow 旁路评估不打断线上（对应旁路→辅助→自动化三态）。
- **冲突裁决**（synapClaim R-DEC 优先级）：`P0 R-FRAUD(转人工) → P1 R-CAP(不再自动赔付) → P2 R-DIR(自费剔除) → P3 R-AREA(拒赔带依据) → P4 R-DED/R-RAT(正常理算) → P5 材料缺失(补件不阻塞)`，裁决表留痕。

### 10.2 MVP 规则书全集（以 delivery/04 为真源）

> MVP 规则全集以 `delivery/04`（业务规则书 Rulebook）为**唯一真源**（R-AREA / R-DIR / R-DED / R-RAT / R-CAP / R-DEC / R-MAN / R-FRAUD），本文不重复罗列。

> **置信度口径对齐**：`delivery/04` 的 R-MAN-01「<0.85 转人工」为产品单档简化口径；本方案落地按**三档**——<0.7（或高风险）强制人工终审、0.7-0.85 复核档、≥0.85 且无风险直出，与 ClaimService 一致。规则冲突裁决（R-DEC 优先级）见 §10.1。

---

## 11. 评测与可观测

### 11.1 评测体系（评测集先行，先建基准再谈自动化）

| 指标 | 目标 | 口径 |
|---|---|---|
| 字段提取准确率 | ≥95% | 发票/清单/医保结算单字段 |
| 规则命中准确率 | ≥90% | 核责/免赔/比例/封顶 |
| 人工复核率 | ≤30% | 自动通过外需人工的占比（**评测集口径**；生产口径锚定 Phase C，PRD §15） |
| 决策建议时延 | 95% ≤30s | 提交→输出建议 |
| 资金差错率 | 0（生产出款目标） | 幂等/对账 |
| 拒赔解释覆盖 | 100% | 拒赔必答五要素 |

- **基准集**：≥300 条脱敏样例（门诊发票+清单+人工结论），其中 ≥10 条为客户出题盲测样本；Ground truth 由客户确认。
- **指标真源**：上表阈值与交付验收同源于 delivery/07 测试方案与性能基准；工程落地版 §11.2 为性能与 SLO 的唯一真源，本表不另立口径。
- **规则/模型/提示词版本绑定留痕**：每次发布绑定评测集版本，回放可复现。

### 11.2 可观测（bone-ai-trace + OpenTelemetry）

- **决策回放**：`decision_trace` 节点序列（证据→解析→核责→理算→决策），任意案件一键回放决策路径（对应 工程落地版 §7.3（决策图执行）/ §11.5（可观测性）决策追溯 / bone-ai trace 证据链）。
- **全链路追踪**：OpenTelemetry 统一埋点，Grafana 看板（自动化率/复核率/赔付趋势/时延）。
- **审计三件套**：请求摘要（≤200 字）+ 响应结果 + 耗时，错误可回放。
- **计量**：bone-ai-gateway `LlmUsageRecorded` 按租户/模型聚合 token 与成本，供预算/配额/成本红线消费。AI 预算配额（工程落地版 §11.6/§11.12 已固化）：软配额 500 元/月/租户（超阈告警）、硬配额 2000 元/月/租户（超阈该租户 LLM→纯规则降级）、按案件成本红线 0.5 元/案，阈值经 `feature_toggle` 的 `aiBudget.soft/hard` 按租户配置。

---

## 12. 落地里程碑与 Go/No-Go

> **实施计划唯一真源**：《synapClaim MVP 可落地执行方案》§三（迭代 I0–I4）/ §五（里程碑 M1–M5 与 Go/No-Go）。**本节不再复制里程碑表**——此前本文与 MVP 方案各维护一份，已出现「Phase B 4-6 周 vs 4-8 周」的漂移。本节只保留 AI 侧的技术判据与收窄建议。

### 12.1 工作量与收窄建议

4 人团队（架构/底座/理赔/招投标），合计 ≈150 人日；`Gate 0(2周) + 迭代10周(I0-I4) + Shadow 4周`。

> **工作量提醒（verifier 复核）**：本方案同时含决策图引擎、A7 资金域、RAG 全链、OCR 适配器四件新增大型件 + 300 条评测集，4 人 ≈150 人日（10 周）偏紧。建议一期收窄：先做「决策图（自建 DAG）+ 资金域 + 规则评测」主链路，一期 OCR 适配器延后，先用 PDFBox 仅覆盖数电票文本层案件；图像/翻拍案件转人工补录并在案件上标记 `ocr_confidence<阈值`（对齐 PRD RQ-MVP-001 验收口径）；图像 OCR 随 Docling/PaddleOCR 适配器二期交付；RAG 全链放二期，避免里程碑超支。

### 12.2 AI 侧必须与里程碑绑定的判据

MVP 方案给业务口径，本节补技术口径——两者缺一即「验收通过但系统不可信」：

| 里程碑 | AI 侧附加判据 |
|---|---|
| M2 纵向切片 | 建议返回 P95 ≤30s（分段预算见 §5.1，全局 deadline 30s）；决策 trace 可按 `decisionExecutionId` 完整回放 |
| M3 评测达标 | 评测集 ≥300 条与**规则版本 + 模型 + 提示词**三者绑定留痕；「零重大错误」定义为**金额错赔 / 应赔而拒**（不含表述类缺陷） |
| M4 Shadow 上线 | 与 pk-tpa-saas 双跑差异可归因到具体节点（解析 / 规则 / AI / 理算），差异率可解释而不只是数值 |
| M5 Phase B | 推翻率、申诉率、复议推翻率进看板；任一超阈即回滚对应规则版本或置信度阈值 |

### 12.3 关键路径与迭代

`I0 数据(脱敏样本) → I1 解析(OCR/字段/三段拆分) → I2 核责理算(责任/限额/公式) → I3 工作台评测(HITL/评测报告) → I4 部署试点(Shadow/资金mock)`

**关键成功要素**：① 授权样本获取是最大风险项（Gate 0 止损）；② 评测集先行；③ 每周可演示的垂直切片；④ 规则/模型/提示词版本绑定留痕；⑤ AI 不碰钱作为核心差异化持续强化。

---

## 13. 风险与对策

| 风险 | 等级 | 触发条件（可观测） | 责任方 | 对策 | 回退路径 |
|---|---|---|---|---|---|
| 授权样本不足/数据质量差 | 高 | Gate 0 样本量低于计划；或标注一致性<80% | 底座 + 理赔 | Gate 0 止损；评测集先行；脱敏样本内置回放 | Gate 0 不通过即暂停项目，不进入 I0 |
| AI 误判导致错误拒赔/错放 | 高 | 生产错赔>0；或评测集准确率跌破验收线 | 理赔 | 拒赔必须人工确认（R-MAN-03）；置信<0.7（或高风险）强制人工终审、0.7-0.85 复核档、≥0.85 直出；证据链可回放追责 | 退回 Assisted：AI 只出建议，人工终审，关闭直出 |
| 资金差错（重复出金/超额） | 高 | 对账差异>0；或幂等键冲突异常 | 架构 + 理赔 | idempotencyKey 唯一索引；资金单事务 + 乐观锁；进程内总线 + afterCommit 投递 + CDC 对账（二期 RocketMQ）；AI 层禁碰资金 | 停支付网关（降级 mock），资金出款转人工 |
| 规则冲突/热更出错 | 中 | 规则回归失败；或灰度期差异率超阈值 | 底座 | 优先级裁决表；回归门禁；灰度/shadow；版本绑定留痕 | 回滚规则版本（灰度逆向），停用该规则集 |
| 票据造假/重复报销 | 中 | 验真通道不可用；或命中 R-FRAUD | 理赔 | 票面验真（R-FRAUD-06）失败一律转人工；同人同日同院同额检测 | 关闭自动通过，案件全量转人工 |
| OCR 低置信漏字段 | 中 | ocr_confidence<0.7 占比>5% | 底座 | ocr_confidence<0.7 强制转人工（独立字段，不与 AI 决策置信混淆） | 切人工录入通道，OCR 结果不落库 |
| 存量系统集成摩擦 | 中 | 同步失败率>5% | 交付 | 防腐层（ACL）；旁路 CDC 同步；TenantId 贯穿 | 退回文件交换兜底 + 只读旁路 |

> **责任方（owner）**：架构=技术架构与一致性 · 底座=AI 平台与数据链路 · 理赔=理赔业务与规则 · 交付=集成实施与客户对接 · 安全合规=数据与合规准入。

---

## 14. 与现有资产的对位表

| 维度 | synapClaim（方案） | pk-tpa-saas（实现） | bone-ai（已落地） | 本方案动作 |
|---|---|---|---|---|
| 领域模型 | DDD 聚合 + 领域事件 | Plan/Coverage/Liability + 限额引擎 | `ClaimService` 理赔编排（`bone-ai-server` 的 POC 示例，**非生产模块**；理赔编排一期落 `synap-claim` 本域，bone-ai 仅提供 gateway/rules/document/orchestration/trace/HITL 能力） | 融合，责任模型取 pk-tpa，聚合取 synapClaim |
| 决策编排 | 五段式状态机 + 决策图 | AdjustEngine 理算主链路 | orchestration 最小管线 + HITL | 新增自建轻量 DAG 决策图引擎 + 五段式状态机（落 orchestration decisiongraph 子域） |
| 规则 | 规则书 R1.0 + 冲突裁决 | QLExpress 枚举驱动 | RuleAsset + 回归门禁 + 灰度 | 规则书模板化 + 评测集绑定 |
| 资金 | 两段式 + 幂等 + Outbox | A7 Settlement（SettlementOrder + FundingEvent） | （无资金域） | 复用同模块 A7 聚合（metadata-sdk 持久化 + 进程内总线投递），禁入 AI 层 |
| 证据/审计 | decision_trace 决策追溯 | ClaimProcessedEvent | trace 证据快照可回放 | trace 扩展 decision_trace 节点序列 |
| 人工终审 | 复核工作台 + 拒赔五要素 | 理算挂起/双人复核 | HITL 五态 + ReasonType | 大额双人复核 + 拒赔通知书 |
| AI 接入 | 统一 LLM 客户端端口 | — | gateway 熔断/重试/计量 | 复用 gateway 统一出口（计量/熔断经平台收口）；RAG 二期 |

---

## 附录：术语与决策记录（ADR 摘记）

| 决策 | 结论 | 依据 |
|---|---|---|
| AI 与资金解耦 | 资金状态机归 A7 Settlement 聚合，AI 层禁碰 | P1 原则，防 AI 失败阻断资金 |
| 规则引擎选型 | QLExpress 唯一（不引 Drools/KIE） | bone-ai 已定 + 轻量热更 |
| 决策图存储 | metadata-sdk 持久化 + 自建轻量 DAG 执行器（一期）；JGraphT/TiDB JSON 留二期评估 | JGraphT 离线不可用；节点 ≤5 类无需通用图库 |
| 置信度阈值 | 三档：<0.7（或高风险）强制人工、0.7-0.85 复核、≥0.85 直出；OCR<0.7 独立触发 | 与 ClaimService 一致；R-MAN-01 的 <0.85 是单档简化口径 |
| 资金投递 | 一期进程内事件总线 + afterCommit；二期 RocketMQ | rocketmq-client 离线可用但 bone-ai 未集成 |
| 存储持久化 | 一期 metadata-sdk @Table/@Column + H2 方言；生产切 MySQL/TiDB | HC-001 唯一持久化抽象；H2 离线可跑 |
| 幂等 | idempotencyKey 唯一索引 + duplicate-key | 防二次出金 |
| 隐私 | insured_id_hash HMAC-SHA256+盐，明文不落地 | synapClaim 02 / PIPL 29 |
