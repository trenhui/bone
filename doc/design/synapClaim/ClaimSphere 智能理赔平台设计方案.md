# ClaimSphere 智能理赔平台

**核心命题**：控制复杂度扩散速度，构建可演进的确定性理赔架构

**适用范围**：保险行业TPA、保司核心系统、健康险/意外险智能理赔平台建设

**方法论**：三阶段六步闭环法（3S6C）+ 确定性分层 + DDD领域驱动

**技术锚点**：Java 21 + TiDB + Temporal + Spring AI Alibaba

---

## 目录

1. [摘要与核心价值承诺](#1-%E6%91%98%E8%A6%81%E4%B8%8E%E6%A0%B8%E5%BF%83%E4%BB%B7%E5%80%BC%E6%89%BF%E8%AF%BA)

2. [行业洞察：痛点与趋势](#2-%E8%A1%8C%E4%B8%9A%E6%B4%9E%E5%AF%9F%E7%97%9B%E7%82%B9%E4%B8%8E%E8%B6%8B%E5%8A%BF)

3. [架构哲学与方法论全景](#3-%E6%9E%B6%E6%9E%84%E5%93%B2%E5%AD%A6%E4%B8%8E%E6%96%B9%E6%B3%95%E8%AE%BA%E5%85%A8%E6%99%AF)

4. [战略层：价值流与能力投资（Step 1）](#4-%E6%88%98%E7%95%A5%E5%B1%82%E4%BB%B7%E5%80%BC%E6%B5%81%E4%B8%8E%E8%83%BD%E5%8A%9B%E6%8A%95%E8%B5%84step-1)

5. [战术层：事件风暴与边界切割（Step 2–5）](#5-%E6%88%98%E6%9C%AF%E5%B1%82%E4%BA%8B%E4%BB%B6%E9%A3%8E%E6%9A%B4%E4%B8%8E%E8%BE%B9%E7%95%8C%E5%88%87%E5%89%B2step-25)

6. [领域模型设计（基于业界最佳实践）](#6-%E9%A2%86%E5%9F%9F%E6%A8%A1%E5%9E%8B%E8%AE%BE%E8%AE%A1%E5%9F%BA%E4%BA%8E%E4%B8%9A%E7%95%8C%E6%9C%80%E4%BD%B3%E5%AE%9E%E8%B7%B5)

7. [核心架构设计——简化资金状态机+决策图+确定性分层](#7-%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E8%AE%BE%E8%AE%A1%E7%AE%80%E5%8C%96%E8%B5%84%E9%87%91%E7%8A%B6%E6%80%81%E6%9C%BA%E5%86%B3%E7%AD%96%E5%9B%BE%E7%A1%AE%E5%AE%9A%E6%80%A7%E5%88%86%E5%B1%82)

8. [关键技术设计（含优化点）](#8-%E5%85%B3%E9%94%AE%E6%8A%80%E6%9C%AF%E8%AE%BE%E8%AE%A1%E5%90%AB%E4%BC%98%E5%8C%96%E7%82%B9)

9. [技术架构与工程实现](#9-%E6%8A%80%E6%9C%AF%E6%9E%B6%E6%9E%84%E4%B8%8E%E5%B7%A5%E7%A8%8B%E5%AE%9E%E7%8E%B0)

10. [数据库设计](#10-%E6%95%B0%E6%8D%AE%E5%BA%93%E8%AE%BE%E8%AE%A1)

11. [API协议设计（元数据驱动）](#11-api%E5%8D%8F%E8%AE%AE%E8%AE%BE%E8%AE%A1%E5%85%83%E6%95%B0%E6%8D%AE%E9%A9%B1%E5%8A%A8)

12. [前端架构（Vue 3 + Formily + G6）](#12-%E5%89%8D%E7%AB%AF%E6%9E%B6%E6%9E%84vue-3--formily--g6)

13. [存量系统平滑迁移——绞杀者模式](#13-%E5%AD%98%E9%87%8F%E7%B3%BB%E7%BB%9F%E5%B9%B3%E6%BB%91%E8%BF%81%E7%A7%BB%E7%BB%9E%E6%9D%80%E8%80%85%E6%A8%A1%E5%BC%8F)

14. [AI成本控制与可解释性](#14-ai%E6%88%90%E6%9C%AC%E6%8E%A7%E5%88%B6%E4%B8%8E%E5%8F%AF%E8%A7%A3%E9%87%8A%E6%80%A7)

15. [组织治理与风险控制（含适应度函数）](#15-%E7%BB%84%E7%BB%87%E6%B2%BB%E7%90%86%E4%B8%8E%E9%A3%8E%E9%99%A9%E6%8E%A7%E5%88%B6%E5%90%AB%E9%80%82%E5%BA%94%E5%BA%A6%E5%87%BD%E6%95%B0)

16. [实施路线图（9个月）](#16-%E5%AE%9E%E6%96%BD%E8%B7%AF%E7%BA%BF%E5%9B%BE9%E4%B8%AA%E6%9C%88)

17. [商业价值指标与成本分析](#17-%E5%95%86%E4%B8%9A%E4%BB%B7%E5%80%BC%E6%8C%87%E6%A0%87%E4%B8%8E%E6%88%90%E6%9C%AC%E5%88%86%E6%9E%90)

18. [总结](#18-%E6%80%BB%E7%BB%93)

    ---

## 1. 摘要与核心价值承诺

ClaimSphere 定位为保险行业的 **智能决策基础设施**，其本质是 **“同一笔理赔案件，在不同阶段、不同角色视角下的状态、证据与决策责任的持续演化与协同”**。本方案以 **“三阶段六步闭环法”** 为架构设计方法论，以 **“确定性分层”** 为核心架构原则，深度融合头部TPA最佳实践，形成从战略对齐到自动化治理的完整闭环。

**核心价值承诺**：

- STP 自动化率 ≥ 80%

- 直付结算时效 ≤ 30 秒

- 单案处理成本下降 ≥ 50%

- 新保司接入周期 ≤ 7 天

- 系统可用性 99.99%

- 资金零差错（ACID 保证）

- 决策可解释性 100%

  ---

## 2. 行业洞察：痛点与趋势

### 2.1 五大结构性痛点

|**痛点**|**具体表现**|**量化影响**|
|---|---|---|
|体验断裂|垫付-等待-报销流程3-7天|员工NPS仅40-50|
|效率瓶颈|人工审核为主，自动化率仅30%|单案成本15-25元|
|风控盲区|欺诈依赖人工，规则僵化|欺诈损失3-5%|
|账户黑箱|资金不透明|续约率75-85%|
|技术债沉重|单体架构，接入周期2-4周|故障恢复&gt;30分钟|


### 2.2 六大行业趋势

|**趋势**|**核心要求**|**领先实践**|
|---|---|---|
|即时结算标配|直付≤30秒|头部TPA即时结算|
|AI自动化普及|自动化率≥70%|多智能体80%+|
|低代码配置化交付|新业务接入≤7天|低代码平台|
|业财一体化强制|四流一致|账户+清算|
|数据安全与隐私|数据不出域|生物识别+联邦学习|
|AI可解释性|100%决策可追溯|决策图+SHAP|

### 2.3 竞争格局与行业实践（2026年公开报道核查）

|**实践**|**要点**|**对本方案的启示**|
|---|---|---|
|元保多Agent理赔审核（2026-07 每日经济新闻）|事件整理、核责、结论解读等多 Agent 协同已落地|“全链路 Agent”不是差异化本身；拼的是场景覆盖深度与确定性工程|
|腾讯微保商保直付（2026-03 新浪财经）|与微信支付等生态伙伴推进，深圳南山区已有医院试点|理赔提效同样来自**数据连接与流程改造**（医院/HIS/医保通道），非仅模型能力；直付通道是未来演进方向|
|Health Affairs 关于理赔AI的讨论|决策需考虑公平性、透明度、人工参与与拒赔责任，不能只追求自动化率|**自动化率不是唯一指标**；人责归属（AI 建议、人工/保司终审）是行业刚需，与本文确定性分层架构互为印证|

**竞争结论**：通用 AI 核赔平台不是蓝海。本方案的差异化来自：确定性分层（资金安全） + 可解释决策（监管审计） + 私有化交付（数据不出域），并在 MVP 阶段选择现有系统解决不好的细分场景打透。

### 2.4 市场空间口径（诚实边界）

> **赛道地位 ≠ 可触达市场**：理赔是保险业核心环节（赛道地位重要），但本方案的可触达市场（SAM）是"理赔处理费 + 减损分成 + 软件授权"，**不是行业总保费的 TAM**——对外沟通、融资材料一律以 SAM 口径表述，禁止拿行业总保费冒充本方案市场空间。

| 口径 | 含义 | 本文档取值 |
|------|------|------------|
| TAM（总市场） | 保险行业总保费/总赔付 | 行业公开数据（**仅作赛道背景，不作为市场规模**） |
| SAM（可服务市场） | 理赔处理费 + 减损分成 + 软件授权 | 单客户约 130–270 万/年（§17.3 首年 2–3 家客户收入 380–540 万口径） |
| SOM（可获得市场） | MVP 切入的"企业补充医疗·门诊·小额快赔"细分 | 首年 1–2 家试点 + Gate 0 数据验证 |

---

## 3. 架构哲学与方法论全景

### 3.1 三大底层原理

|**原理**|**核心命题**|**方法映射**|
|---|---|---|
|语义收敛|一个词在一个边界内只有一个含义|统一语言 + 限界上下文|
|变化隔离|高频变化与稳定核心解耦|频率刀切割 + 核心域保护|
|一致性分层|强一致留在最小核心，其余最终一致|一致性刀 + 事件驱动|


### 3.2 四层确定性架构（硬性边界）

|**层级**|**技术栈**|**职责**|**一致性**|**关键约束**|
|---|---|---|---|---|
|**L1 资金层**|Java + TiDB|简化资金状态机（硬预占→已支付）|**强一致(ACID)**|必须在`@Transactional`内，**严禁Temporal参与**|
|**L2 决策层**|Drools + JGraphT|规则判定、决策图执行|**最终一致**|AI节点必须配置`fallbackRule`|
|**L3 流程层**|Temporal.io|人工复核、跨天调查|**高可用**|只编排状态，API回调资金层|
|**L4 智能层**|Spring AI Alibaba|RAG、LLM编排|**可降级**|超时≤3s熔断|


**架构红线**：

- ❌ Temporal 严禁管理资金状态

- ❌ AI 推理失败不得阻断资金流程

- ✅ 资金状态变更必须在单个事务内完成

- ✅ 所有资金变更写入`funding_events`表

- ✅ 决策图AI节点必须配置降级规则节点

  ---

## 4. 战略层：价值流与能力投资（Step 1）

### 4.1 端到端价值链

```plaintext
理赔申请 → 报案受理 → 数据采集 → 智能审核 → 决策输出 → 理算支付 → 结案归档 → 续保运营
```

### 4.2 能力分级与投资矩阵

|**能力单元**|**总分**|**分级**|**投资策略**|**人月**|
|---|---|---|---|---|
|决策图引擎|5.0|🔴核心|自研重兵|6|
|简化资金状态机|5.0|🔴核心|自研重兵|2|
|动态规则引擎|5.0|🔴核心|自研|5|
|多智能体协同|4.7|🔴核心|自研|8|
|自动理算引擎|3.7|🔴核心|自研|5|
|低代码平台|4.0|🔴核心|自研|6|
|保司/医院对接|2.5|🟡支撑|自研+防腐层|3|
|支付通道|1.7|⚪通用|采购|0.5|


**战略结论**：核心域占比 ≤ 20%，80%资源投入决策中枢、规则引擎与多智能体。

---

## 5. 战术层：事件风暴与边界切割（Step 2–5）

### 5.1 事件风暴与红牌ADR（24h闭环）

|**编号**|**问题**|**ADR决议**|**技术方案**|
|---|---|---|---|
|H-001|规则僵化|动态规则库|规则库7天自动评估命中率/覆盖率并生成优化建议，运营确认后热更新|
|H-002|AI与规则冲突|决策图统一收敛|AI仅证据，规则确定性|
|H-003|电子发票风险|票据全生命周期追踪|区块链存证+异常标记|
|H-004|医保网络中断|熔断降级|企业全额垫付，事后补差|
|H-005|AI误判拒赔|先垫付后调查+后督智能体|自动复核24h内|


### 5.2 限界上下文（三刀法切割）

|**上下文**|**核心职责**|**禁止字段**|
|---|---|---|
|报案上下文|案件创建、资料收集|`risk_score`, `payable_amount`|
|证据上下文|证据结构化|`claim_status`, `decision`|
|规则上下文|规则定义、热加载|不关心AI证据细节|
|AI风控上下文|多智能体风险识别|不关心规则执行结果|
|决策上下文|**统一决策收敛**|**唯一决策事实源**|
|理算上下文|赔付金额计算|只接收决策结果|
|结算上下文|支付、对账|事件驱动，不直接改案件|
|外部集成上下文|保司/医院/医保适配|核心域不导入外部DTO|


### 5.3 防腐层（ACL）契约

|**外部系统**|**降级策略**|**超时/重试**|
|---|---|---|
|国家医保|企业全额垫付|2s，重试2次，熔断20s|
|保司核心|缓存最近数据|3s，重试2次|
|医院HIS|异步重试，降级员工垫付|5s，重试3次，指数退避|
|电子票据|区块链存证+异步补验|3s，重试2次|
|支付网关|多通道自动切换|10s，重试1次|


---

## 6. 领域模型设计（基于业界最佳实践）

### 6.1 核心聚合总览

```plaintext
classDiagram
    class ClaimApplication {
        +ApplicationId id
        +PolicyId policyId
        +InsuredPerson insured
        +ClaimType claimType
        +Money claimedAmount
        +ApplicationStatus status
        +LocalDateTime submittedAt
        +submit()
        +amend()
        +withdraw()
    }

    class ClaimCase {
        +ClaimId id
        +ApplicationId applicationId
        +String caseNumber
        +ClaimCaseStatus status
        +RiskLevel riskLevel
        +LocalDateTime openedAt
        +LocalDateTime closedAt
        +assignToAdjuster()
        +updateStatus()
        +close()
    }

    class EvidenceCollection {
        +EvidenceId id
        +ClaimId claimId
        +List~Document~ documents
        +List~MedicalFact~ medicalFacts
        +CollectionStatus status
        +LocalDateTime lockedAt
        +addDocument()
        +extractMedicalFacts()
        +lock()
        +isComplete()
    }

    class Document {
        +DocumentId id
        +DocumentType type
        +String url
        +OCRResult ocrResult
        +DocumentStatus status
        +upload()
        +classify()
        +ocr()
    }

    class MedicalFact {
        +FactId id
        +FactType type
        +String value
        +double confidence
        +String source
    }

    class Investigation {
        +InvestigationId id
        +ClaimId claimId
        +InvestigationType type
        +InvestigationStatus status
        +List~InvestigationTask~ tasks
        +LocalDateTime deadline
        +start()
        +addTask()
        +completeTask()
        +close()
    }

    class InvestigationTask {
        +TaskId id
        +TaskType type
        +String target
        +TaskStatus status
        +String result
        +LocalDateTime assignedAt
        +LocalDateTime completedAt
        +assign()
        +complete()
    }

    class DecisionGraph {
        +GraphCode code
        +int version
        +JSON definition
        +execute(EvidenceBag)
    }

    class DecisionExecution {
        +ExecutionId id
        +ClaimId claimId
        +GraphId graphId
        +DecisionResult result
        +RiskScore riskScore
        +List~DecisionTrace~ traces
        +LocalDateTime startedAt
        +LocalDateTime finishedAt
        +start()
        +recordTrace()
        +complete()
    }

    class RuleEvaluation {
        +EvaluationId id
        +ExecutionId executionId
        +List~RuleHit~ hits
        +EvaluationResult result
    }

    class RuleHit {
        +String ruleCode
        +String ruleName
        +boolean matched
        +Map~String,Object~ bindings
    }

    class IndemnityCalculation {
        +IndemnityId id
        +ClaimId claimId
        +ExecutionId executionId
        +Money totalAmount
        +Money deductible
        +Money copayAmount
        +Money payableAmount
        +FormulaSnapshot formula
        +IndemnityStatus status
        +calculate()
        +recalculate()
    }

    class FormulaSnapshot {
        +String formulaExpression
        +Map~String,Object~ parameters
        +LocalDateTime effectiveAt
    }

    class SettlementOrder {
        +SettlementId id
        +IndemnityId indemnityId
        +Payee payee
        +Money amount
        +PaymentMethod paymentMethod
        +PaymentStatus status
        +Invoice invoice
        +LocalDateTime paymentDeadline
        +execute()
        +confirm()
        +cancel()
        +refund()
    }

    class Invoice {
        +InvoiceId id
        +InvoiceType type
        +InvoiceStatus status
        +String invoiceNumber
        +Money amount
        +LocalDateTime issuedAt
        +issue()
        +cancel()
    }

    class Account {
        +AccountId id
        +PolicyId policyId
        +Money balance
        +Money usedAmount
        +deduct()
        +refund()
        +recharge()
    }

    class Policy {
        +PolicyId id
        +String policyNumber
        +String productCode
        +InsuredPerson insured
        +EffectivePeriod effectivePeriod
        +List~Coverage~ coverages
        +PolicyStatus status
        +isActive()
        +getCoverage()
        +checkBenefit()
    }

    class Coverage {
        +CoverageId id
        +String coverageCode
        +Money annualLimit
        +Money deductible
        +BigDecimal copayRatio
        +List~String~ scope
    }

    ClaimApplication "1" --> "1" ClaimCase
    ClaimCase "1" --> "1" EvidenceCollection
    EvidenceCollection "1" --> "*" Document
    EvidenceCollection "1" --> "*" MedicalFact
    ClaimCase "1" --> "0..1" Investigation
    Investigation "1" --> "*" InvestigationTask
    ClaimCase "1" --> "1" DecisionExecution
    DecisionExecution --> "1" DecisionGraph
    DecisionExecution --> "1" RuleEvaluation
    RuleEvaluation "1" --> "*" RuleHit
    ClaimCase "1" --> "1" IndemnityCalculation
    IndemnityCalculation "1" --> "1" FormulaSnapshot
    IndemnityCalculation "1" --> "1" SettlementOrder
    SettlementOrder "1" --> "1" Invoice
    ClaimCase "1" --> "1" Account
    ClaimApplication "1" --> "1" Policy
    Policy "1" --> "*" Coverage
```

### 6.2 核心聚合详解

#### 6.2.1 理赔申请聚合（ClaimApplication）

负责理赔申请的创建、修正和撤回，独立于后续审核流程，支持多渠道接入（自助理赔、直付等）。

**领域行为**：`submit()`, `amend()`, `withdraw()`

**不变量**：申请提交后不可修改索赔金额；已转案件的申请不可撤回。

#### 6.2.2 理赔案件聚合（ClaimCase）

案件全生命周期管理的聚合根，协调证据、调查、决策、理算、结算等子聚合。

**领域行为**：`assignToAdjuster()`, `updateStatus()`, `close()`

**不变量**：案件关闭后不可修改状态；高风险案件必须经过调查流程。

#### 6.2.3 证据采集聚合（EvidenceCollection）

统一管理来自直付渠道和自助理赔渠道的证据，证据链锁定后触发决策。

**领域行为**：`addDocument()`, `extractMedicalFacts()`, `lock()`, `isComplete()`

**不变量**：证据链锁定后不可修改；同一案件证据集唯一。

#### 6.2.4 调查聚合（Investigation）

处理高风险案件的深入调查，对应风控体系中的反欺诈、外部调卷等场景。

**领域行为**：`start()`, `addTask()`, `completeTask()`, `close()`

#### 6.2.5 决策执行聚合（DecisionExecution）

决策图引擎的执行载体，记录一次完整的决策过程，确保决策结果可追溯、可解释。

**领域行为**：`start()`, `recordTrace()`, `complete()`

**不变量**：每个案件同时只有一个活跃决策执行；决策结果一旦确定不可更改。

#### 6.2.6 理算计算聚合（IndemnityCalculation）

根据决策结果和医疗事实计算赔付金额，支持重新理算。

**领域行为**：`calculate()`, `recalculate()`

**不变量**：理算结果必须基于已锁定的决策执行；赔付金额 ≤ 索赔金额。

#### 6.2.7 结算单聚合（SettlementOrder）

管理支付指令与发票，支持支付确认、取消、退款等操作。

**领域行为**：`execute()`, `confirm()`, `cancel()`, `refund()`

**不变量**：支付成功后不可取消，只能退款；退款必须生成红冲发票。

#### 6.2.8 账户聚合（Account）

简化资金账户模型，仅维护余额和已用金额，通过事件溯源保障审计。

**领域行为**：`deduct()`, `refund()`, `recharge()`

**不变量**：余额不能为负；所有变更必须记录事件。

#### 6.2.9 保单聚合（Policy）

保单信息聚合，提供保险责任查询、有效性校验等能力。

**领域行为**：`isActive()`, `getCoverage()`, `checkBenefit()`

### 6.3 值对象设计

|**值对象**|**属性**|**说明**|
|---|---|---|
|`ClaimId`|String value|案件ID，格式 `CLM-{date}-{sequence}`|
|`ApplicationId`|String value|申请ID，格式 `APP-{date}-{sequence}`|
|`PolicyId`|String value|保单ID|
|`Money`|BigDecimal amount, Currency currency|金额，带货币单位|
|`RiskScore`|int score, double confidence|风险评分 + 置信度|
|`EvidenceBag`|Map&lt;String, Object&gt; data|决策图执行时的证据包|
|`DigitalSignature`|String algorithm, String signature, String publicKey|证据防篡改签名|
|`FormulaSnapshot`|String formula, Map&lt;String,Object&gt; params, LocalDateTime effectiveAt|理算公式快照|
|`Payee`|String name, String bankAccount, String bankName|收款方信息|
|`InsuredPerson`|String name, String idCardNo, String mobile, Date birthDate|被保险人信息|
|`EffectivePeriod`|LocalDate startDate, LocalDate endDate|有效期间|
|`Document`|DocumentId id, DocumentType type, String url, OCRResult ocrResult, DocumentStatus status|单证信息（可嵌入）|
|`MedicalFact`|FactId id, FactType type, String value, double confidence, String source|医疗事实（可嵌入）|
|`RuleHit`|String ruleCode, String ruleName, boolean matched, Map&lt;String,Object&gt; bindings|规则命中记录|
|`Invoice`|InvoiceId id, InvoiceType type, InvoiceStatus status, String invoiceNumber, Money amount, LocalDateTime issuedAt|发票信息|


### 6.4 领域事件清单

|**事件名称**|**触发时机**|**携带数据**|**消费者**|
|---|---|---|---|
|`ClaimApplicationSubmitted`|申请提交成功|applicationId, policyId, claimedAmount|案件上下文|
|`ClaimCaseOpened`|案件立案成功|claimId, caseNumber|证据、决策上下文|
|`DocumentsClassified`|单证分类完成|claimId, documentTypeCounts|证据上下文|
|`MedicalFactsExtracted`|医疗事实提取完成|claimId, factCount|证据上下文|
|`EvidenceLocked`|证据链锁定|claimId, evidenceCount|决策上下文|
|`InvestigationInitiated`|调查启动|claimId, investigationType|Temporal工作流|
|`InvestigationCompleted`|调查完成|claimId, conclusion|决策上下文|
|`DecisionMade`|决策完成|claimId, decisionResult, riskScore|理算、结算上下文|
|`IndemnityCalculated`|理算完成|claimId, indemnityId, payableAmount|结算上下文|
|`PaymentExecuted`|支付执行|claimId, settlementId, paymentStatus|对账、后督|
|`PaymentConfirmed`|支付确认|claimId, settlementId, transactionId|发票上下文|
|`InvoiceIssued`|发票开具|claimId, invoiceNumber, amount|通知服务|
|`CaseClosed`|案件关闭|claimId, finalStatus, closedAt|数据仓库、续保|
|`AccountDeducted`|账户扣减|accountId, amount|资金监控、对账|
|`AccountRefunded`|账户退款|accountId, amount|资金监控、对账|


### 6.5 领域服务接口

|**领域服务**|**方法签名**|**职责**|
|---|---|---|
|`ClaimApplicationService`|`ApplicationId submit(ApplicationDto dto)`|处理理赔申请提交|
|`DocumentClassificationService`|`DocumentType classify(Document doc)`|单证自动分类|
|`MedicalFactExtractionService`|`List<MedicalFact> extract(EvidenceCollection evidence)`|调用AI提取医疗事实|
|`EvidenceLockService`|`void lock(ClaimId claimId)`|锁定证据链，触发决策|
|`DecisionGraphService`|`DecisionExecution execute(ClaimCase claim, EvidenceBag evidence)`|执行决策图|
|`RiskAssessmentService`|`RiskScore assess(EvidenceCollection evidence)`|调用风控评估风险|
|`RuleEvaluationService`|`RuleResult evaluate(EvidenceCollection evidence)`|执行规则引擎|
|`IndemnityCalculator`|`IndemnityCalculation calculate(DecisionExecution decision, EvidenceCollection evidence)`|计算赔付金额|
|`SettlementExecutor`|`SettlementOrder execute(SettlementOrder order)`|执行支付指令|
|`InvoiceService`|`Invoice issue(IndemnityCalculation indemnity, SettlementOrder settlement)`|开具电子发票|


### 6.6 仓储接口（面向领域）

|**仓储**|**方法**|**说明**|
|---|---|---|
|`ClaimApplicationRepository`|`findById()`, `save()`|申请存储|
|`ClaimCaseRepository`|`findById()`, `findByNumber()`, `save()`|案件存储|
|`EvidenceRepository`|`findByClaimId()`, `save()`|证据存储|
|`InvestigationRepository`|`findByClaimId()`, `save()`|调查存储|
|`DecisionRepository`|`findById()`, `save()`, `findTraces()`|决策执行存储|
|`IndemnityRepository`|`findByClaimId()`, `save()`|理算存储|
|`SettlementRepository`|`findByIndemnityId()`, `save()`|结算存储|
|`AccountRepository`|`findByPolicyId()`, `save()`|账户存储|
|`PolicyRepository`|`findById()`, `findByNumber()`|保单存储|


### 6.7 与架构设计的映射关系

|**领域概念**|**架构模块**|**技术实现**|
|---|---|---|
|ClaimApplication, ClaimCase|报案上下文|TiDB 表|
|EvidenceCollection, Document, MedicalFact|证据上下文|TiDB 表|
|Investigation, InvestigationTask|流程层（Temporal）|Temporal Workflow + TiDB|
|DecisionExecution, DecisionTrace|决策上下文|TiDB 表|
|RuleEvaluation, RuleHit|规则上下文|Drools + TiDB|
|IndemnityCalculation|理算上下文|TiDB 表|
|SettlementOrder, Invoice|结算上下文|TiDB 表|
|Account|资金层|TiDB 表|
|Policy, Coverage|外部集成上下文|防腐层 + TiDB 缓存|
|DecisionGraph|决策图引擎|TiDB JSON + JGraphT|
|MedicalFactExtractionService|AI智能层|Spring AI Alibaba|
|RiskAssessmentService|AI风控上下文|Spring AI Alibaba|
|RuleEvaluationService|规则上下文|Drools|
|EvidenceLockService|证据→决策|RocketMQ 事件|


### 6.8 领域模型演进路线

|**阶段**|**范围**|**说明**|
|---|---|---|
|**V1（MVP）**|ClaimApplication, ClaimCase, EvidenceCollection, DecisionExecution, Account|支撑核心理赔决策流程|
|**V2**|IndemnityCalculation, SettlementOrder, Invoice|完善业财一体，支持即时结算|
|**V3**|Investigation, InvestigationTask, Policy聚合增强|支持风控调查和多保司接入|
|**V4**|引入 MedicalKnowledge 聚合|增强 RAG 医学知识库|


---

## 7. 核心架构设计——简化资金状态机+决策图+确定性分层

### 7.1 全景架构蓝图

```plaintext
flowchart TB
    subgraph 接入层
        GW[API网关 / Higress]
    end
    subgraph 核心业务层["核心业务层 (Java 21 + 虚拟线程)"]
        A[理赔应用服务] --> B[Spring AI Alibaba Agent编排]
        B --> C[决策图引擎 JGraphT + TiDB JSON]
        C --> D[Drools规则引擎]
        D --> E[简化资金状态机 TiDB事务内]
        B -.-> F[Temporal Workflow]
    end
    subgraph AI能力层
        G[Spring AI Alibaba统一入口]
        H[TiDB Vector Store]
        I[Qwen API / vLLM]
    end
    subgraph 存储层
        J[(TiDB 业务+JSON+向量+事件)]
        K[(RocketMQ 事件/死信)]
    end
    GW --> A --> J
    B --> G --> H --> I
    E --> K
    F --> K
    C --> J
```

### 7.2 决策执行状态机（五段式）

> 注意：本节是**决策执行**的编排状态机（证据采集→规则→AI→图执行→输出），与 7.3 的**资金状态机（简化两段式）**解耦，二者命名勿混淆。

|**状态**|**含义**|**一致性**|**超时**|**补偿**|
|---|---|---|---|---|
|证据就绪|证据链锁定|最终一致|30s|转人工|
|规则计算|规则并行|最终一致|5s|降级AI优先|
|AI评分|风控并行|最终一致|10s|缓存评分|
|图执行|决策图推理|**强一致**|15s|回滚规则|
|决策输出|发布事件|最终一致|3s|重试3次转人工|


### 7.3 简化资金状态机（两段式）

基于理赔场景并发量不大、资金流向单一的特点，采用简化的两段式状态机，直接使用数据库行锁保障强一致。

> 口径说明：软预占（余额充足性校验）与硬预占（行锁扣减）在同一事务内完成，合计视为“硬预占”阶段；与 PRD §6.2 的 Stage 1/2 对应。

|**状态**|**存储**|**超时**|**补偿**|
|---|---|---|---|
|**硬预占（行锁扣减）**|TiDB|事务期间|事务回滚|
|**已支付**|TiDB|永久|退款|


```plaintext
stateDiagram-v2
    [*] --> 硬预占_TiDB: 进入事务，行锁扣减
    硬预占_TiDB --> 已支付_TiDB: 支付成功，更新状态
    硬预占_TiDB --> [*]: 事务异常，自动回滚
    已支付_TiDB --> 退款: 发起退款
```

**实现代码（极简版）**：

```java
@Service
public class IndemnityService {
    @Transactional
    public void deductAndPay(String claimId, BigDecimal amount, String idempotencyKey) {
        // 幂等检查
        if (fundingEventMapper.exists(idempotencyKey)) return;
        
        // 硬预占：行锁扣减
        Account account = accountMapper.selectForUpdate(claimId);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }
        account.setBalance(account.getBalance().subtract(amount));
        account.setUsedAmount(account.getUsedAmount().add(amount));
        accountMapper.update(account);
        
        // 更新理赔单状态
        claimMapper.updateStatus(claimId, "PAID");
        
        // 记录资金事件
        fundingEventMapper.insert(claimId, "DEDUCTED", amount, idempotencyKey);
        
        // 调用支付网关（异步）
        paymentGateway.transfer(claimId, amount);
    }
}
```

**业财一致性保障**：通过 CDC 监听 `funding_events` 表，实时或 T+1 对账。

---

## 8. 关键技术设计（含优化点）

### 8.1 AI超时分层与动态降级

|**查询类型**|**判定条件**|**超时**|**降级**|
|---|---|---|---|
|简单|单文档|3s|规则兜底|
|标准RAG|2-5个文档|5s|缓存|
|复杂RAG|&gt;5个文档|8s|异步转Temporal|


### 8.2 决策图引擎

- 存储：TiDB JSON（节点、边、条件）

- 执行：JGraphT内存拓扑排序，P99&lt;50ms

- AI降级：每个AI节点配置`fallback_rule_node`

### 8.3 低代码配置体系（Bone框架）

|**配置层**|**内容**|**维护角色**|**生效方式**|
|---|---|---|---|
|字段层|表单字段定义|产品经理|实时|
|规则层|显隐/必填/校验|业务运营|T+1|
|联动层|跨字段复杂联动|低代码开发|分钟级|


### 8.4 Temporal Saga补偿

```java
@CompensationMethod
public void compensate(String claimId) {
    // 根据 funding_events 逆序补偿
    List<FundingEvent> events = getFundingEvents(claimId);
    for (int i = events.size() - 1; i >= 0; i--) {
        // 执行逆操作：退款、释放预占等
    }
}
```

### 8.5 TiDB Vector极限Fallback

> 说明：向量维度以所选 Embedding 模型为准（Qwen/BGE 常用 768/1024 维，建表统一为 768）；下列阈值按该维度测算。

|**条件**|**动作**|
|---|---|
|向量维度 &gt; 1024 或需超大规模召回|切换Milvus（维度/规模不受TiDB约束）|
|数据量 &gt; 1000万|切换Milvus|
|召回率 &lt;85% 持续24h|切换Milvus|
|查询失败3次|切换Milvus|


---

## 9. 技术架构与工程实现

### 9.1 技术栈

|**模块**|**选型**|**版本**|**用途**|
|---|---|---|---|
|核心框架|Spring Boot 3.2 + Java 21|-|虚拟线程高并发|
|AI框架|Spring AI Alibaba|1.0 GA|统一模型调用、RAG、Tool Calling|
|规则引擎|Drools + 动态库|8.x|规则热加载、决策表（KIE 已移交 Apache 基金会，规划期关注 10.x 迁移路线）|
|决策图|JGraphT + TiDB JSON|-|内存执行，JSON存储|
|工作流|Temporal.io|1.22+|长流程持久化、断点续传|
|数据库|TiDB 8.5 LTS|8.5|分布式SQL+JSON+Vector，HTAP一体化（8.0 系列亦兼容）|
|缓存|Redis Cluster|7.2|AI语义缓存、限流计数（非资金预占）|
|消息|RocketMQ|5.1|金融级可靠，死信队列|
|可观测|OpenTelemetry + Grafana|-|统一埋点、全链路追踪|
|网关|Higress|-|云原生限流、鉴权|


### 9.2 仓库策略（Mono-repo）

```plaintext
claimsphere/
├── cs-common/           # Bone框架
├── cs-domain/           # DDD领域层
├── cs-decision-graph/   # 决策图引擎
├── cs-ai-agent/         # AI多智能体
├── cs-rule-engine/      # 规则引擎
├── cs-integration/      # 防腐层
├── cs-console/          # 前端
└── cs-control-tower/    # 控制塔
```

### 9.3 部署架构

```plaintext
flowchart TB
    subgraph K8s集群[Kubernetes 集群 (3AZ)]
        subgraph 接入层
            GW[Higress Gateway 3副本]
        end
        subgraph 业务层
            API[理赔API HPA 3-10]
            DEC[决策图服务 3副本]
            RULE[规则引擎 3副本]
            TEMP[Temporal Worker 3副本]
        end
        subgraph AI层
            AI[Spring AI服务 2副本]
            VLLM[vLLM推理 GPU池]
        end
        subgraph 中间件
            MQ[RocketMQ 4主4从]
        end
    end
    subgraph 数据层
        TiDB[(TiDB集群)]
        Redis[(Redis Cluster)]
    end
    GW --> API --> DEC --> RULE
    DEC --> AI --> VLLM
    API --> TEMP --> MQ
    API --> TiDB
    API --> Redis
```

---

## 10. 数据库设计

### 10.1 决策图相关表

```sql
CREATE TABLE graph_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    graph_code VARCHAR(128) NOT NULL,
    version INT NOT NULL,
    definition JSON NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    UNIQUE KEY uk_graph (tenant_id, graph_code, version)
);

CREATE TABLE decision_execution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id CHAR(36) UNIQUE,
    claim_id VARCHAR(64),
    graph_code VARCHAR(128),
    result VARCHAR(32),
    risk_score DECIMAL(5,2),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    KEY idx_decision_claim (claim_id)
);

CREATE TABLE decision_trace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id CHAR(36),
    node_code VARCHAR(64),
    input_data JSON,
    output_data JSON,
    confidence DECIMAL(5,4),
    fallback_triggered BOOLEAN,
    KEY idx_trace_execution (execution_id)
);
```

### 10.2 资金事件溯源表

```sql
CREATE TABLE funding_events (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id VARCHAR(64),
    event_type VARCHAR(32),
    amount DECIMAL(15,2),
    idempotency_key VARCHAR(64),
    created_at TIMESTAMP,
    KEY idx_funding_claim (claim_id),
    UNIQUE KEY uk_funding_idem (idempotency_key)
);
```

### 10.3 向量知识库表（TiDB Vector）

```sql
CREATE TABLE knowledge_vector (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64),
    content TEXT,
    embedding VECTOR(768),
    metadata JSON,
    VECTOR INDEX idx_embedding USING HNSW (embedding)
);
```

---

## 11. API协议设计（元数据驱动）

### 11.1 获取表单元数据

```http
GET /api/metadata/form/{formCode}
```

### 11.2 表单提交

```http
POST /api/claim/submit
```

### 11.3 决策追溯

```http
GET /api/v1/decisions/{decisionId}/trace
```

---

## 12. 前端架构（Vue 3 + Formily + G6）

- 决策图可视化编辑器（AntV G6）：拖拽节点、配置条件、版本管理

- 低代码表单渲染（Formily）：后端Schema驱动

- 审核工作台：动态表单 + AI辅助提示 + 决策图缩略图

  ---

## 13. 存量系统平滑迁移——绞杀者模式

|**阶段**|**策略**|**关键技术**|
|---|---|---|
|影子验证|新系统旁路运行|数据同步、结果比对|
|双写同步|同时写入新旧系统|CDC（binlog）同步|
|灰度切流|1%→10%→50%→100%|灰度发布、一键回滚|

> 落地化：四层集成模式（数据/接口/流程/身份）、适配器模板库、Shadow→Assisted→Automated 三阶段、幂等与审计三件套等交付细节见《存量系统集成与快速部署（FDE）方案》。


---

## 14. AI成本控制与可解释性

### 14.1 分层推理策略

> 确定性原则：**所有金额计算（免赔额、赔付比例、封顶线、理算公式）必须由确定性代码/规则引擎完成，AI 仅提供证据提取、条款依据与审核建议**——与“简化资金状态机”同源，AI 不参与任何金额决策路径。

|**层级**|**模型**|**成本**|
|---|---|---|
|L1规则|Drools|¥0|
|L2轻量AI|Qwen-Turbo|\~¥0.001|
|L3深度AI|Qwen-Max|\~¥0.01|
|L4人工|专家|-|

### 14.4 模型供应商数据合规

- **数据处理约定**：与模型供应商（Qwen/OCR 厂商）签署 DPA：API 请求数据不用于模型训练、不转售
- **数据分级出境**：病历、身份证号等敏感个人信息只走本地 vLLM（不出域）；仅条款检索等非敏感数据允许云端 API（PIPL 个人信息出境评估）
- **最小权限**：AI 服务仅可访问当前案件证据，无跨案件/跨租户读取能力（租户隔离见 §6.1 Note3）
- **审计留痕**：所有 AI 调用记录（输入摘要、模型、版本、输出）入审计日志，满足金融级审计要求


### 14.2 语义缓存

- 技术：Redis + HNSW

- 相似度阈值：0.85

- TTL：7天（标准），30天（高频）

### 14.3 Explainable Trace

每个AI节点输入/输出存入`decision_trace`，支持SHAP值瀑布图、决策路径高亮。

---

## 15. 组织治理与风险控制（含适应度函数）

### 15.1 权责利铁三角

|**维度**|**机制**|
|---|---|
|权|业务规则委员会（业务60%+医学20%+技术20%）|
|责|SRE责任制，SLA 99.99%，MTTR&lt;15分钟|
|利|FinOps+价值共享，降本50%返还团队|


### 15.2 架构适应度函数（CI自动守护）

|**检查项**|**阈值**|**动作**|
|---|---|---|
|跨域字段映射率|&gt;40%|告警|
|核心域依赖外部SDK|存在|**阻断发布**|
|决策图覆盖率|&lt;100%|告警|
|账户余额非负|违反|熔断交易|
|动态规则库更新间隔|&gt;7天|自动触发更新|


### 15.3 季度架构复盘

|**检查项**|**阈值**|**改进**|
|---|---|---|
|ACL代码行数|&gt;500|二次切割|
|跨域同步调用（日）|&gt;20|改为异步|
|核心域测试覆盖率|&lt;85%|补充测试|
|红牌遗留数|&gt;0|24h闭环|


---

## 16. 实施路线图（9个月）

### 16.1 评测方法论（先建测试集，再谈自动化）

> **MVP 验收不是“Agent 能跑通”，而是“同一批真实案件上稳定减少审核人员重复劳动且不增加重大错误风险”**。交付前建立有正确答案的基准测试集（授权脱敏历史案件 + 人工审核结论），统一度量：

|**指标**|**定义**|**目标**|
|---|---|---|
|字段提取准确率|发票/病历关键字段与人工标注一致率|≥95%|
|规则匹配准确率|规则命中结果与人工核责结论一致率|≥90%|
|人工复核率|需人工介入案件占比|≤30%（MVP）→≤20%（稳定期）|
|单案处理时长|从提交到决策输出的耗时（人工/自动）|较人工基线降低≥50%|
|重大错误率|错赔/漏赔（金额或责任判断错误）|0 容忍，全部转人工兜底|

> 评测集须随规则版本迭代同步维护，覆盖材料缺失、规则冲突、异常费用等边界样本。

### 16.2 分阶段路线
|**阶段**|**周次**|**关键交付**|**验收标准**|
|---|---|---|---|
|**基座期**|W1-4|TiDB+RocketMQ集群；简化资金状态机+事件溯源|资金零差错|
|**核心期**|W5-10|Drools规则引擎；Spring AI Alibaba集成；核心链路压测|理算准确率&gt;95%，P99&lt;200ms|
|**智能期**|W11-16|决策图引擎；TiDB Vector RAG；Temporal长流程|自动化率&gt;70%，RAG召回率&gt;85%|
|**自治期**|W17-24|绞杀者模式迁移；灰度切流；AIOps混沌工程|自动化率≥80%，可用性99.99%|


---

## 17. 商业价值指标与成本分析

### 17.1 关键指标

|**指标**|**目标**|
|---|---|
|STP自动化率|≥80%|
|单案成本下降|≥50%|
|直付时效|≤30秒|
|新保司接入|≤7天|
|可用性|99.99%|


### 17.2 首年TCO（150万案件）

> 口径说明：人力按“等效全职人月”计（48人月 = 9个月路线图期内约 6 人核心团队全职 + 缺口由外包/顾问补齐）；GPU 预算已按本地化推理（vLLM）修正，敏感数据（病历/身份）不走云端 API。

|**项目**|**费用（万元）**|**备注**|
|---|---|---|
|云资源（TiDB+Redis+RocketMQ）|60|托管版，Redis仅用于缓存|
|OCR与AI推理（自研Synap Core优先，云端API兜底）|60|按量计费；敏感数据走本地vLLM，不出域|
|GPU推理（本地化，含信创评估）|50|本地卡或云弹性混合，支撑vLLM私有化|
|人力（48人月）|480|6-8人核心团队，等效48人月|
|运维|50|监控、备份、安全|
|**合计**|**700**|较传统方案降低约30%；OCR若完全复用自研Synap Core可再降20-30万|


### 17.3 ROI

> 口径说明：ROI =（收入 − 投入）/投入。投入按 17.2 首年 TCO 700 万计；收入按商业模式定价（SaaS 50-300 万/年、按单 1-5 元/单、私有化 300 万+）并假设首年落地 2-3 家客户测算。不同客户结构差异大，以下为示意口径：

|**阶段**|**投入（万元）**|**收入假设（万元）**|**ROI**|
|---|---|---|---|
|首年（投入期）|700|380-540（2-3家 SaaS/私有化首付）|-23% ~ -46%|
|次年（保守）|300（运维+扩展）|600-900（续费+新增1-2家）|100% ~ 200%|
|次年（乐观）|300|1200+（新增3家以上）|300%+|

> 结论：**自身 ROI 转正约需 18-24 个月（保守口径）**；对外建议以“客户侧 6-9 个月回收期”（见 PRD 1.4，客户视角净节省测算）为主卖点，避免将乐观口径写进对外承诺。

  ---

## 18. 总结

本方案以 **“三阶段六步闭环法”** 为方法论主线，以 **“确定性分层”** 为核心架构原则，系统性地完成了：

1. **战略层**：价值流扫描与能力投资

2. **战术层**：事件风暴、三刀法、防腐层

3. **领域层**：DDD聚合设计（覆盖完整理赔生命周期）

4. **技术层**：简化资金状态机、决策图引擎、Temporal、Spring AI Alibaba

5. **治理层**：逆向康威、适应度函数、季度复盘

   **核心金句**：

   > **“架构的本质不是设计系统，而是控制复杂度扩散的速度。TiDB 守底线，Temporal 管长流程，Spring AI Alibaba 统管智能化——保险智能理赔的极简可执行终局。”**

   ---

   **文档审批**：

   CTO：\_\_\_\_\_\_\_\_\_\_\_

   架构师：\_\_\_\_\_\_\_\_\_\_\_

   日期：2026年4月5日

   

