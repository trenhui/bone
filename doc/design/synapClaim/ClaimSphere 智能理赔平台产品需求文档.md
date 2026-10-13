---

# ClaimSphere 智能理赔平台产品需求文档（PRD）

**发布日期**：2026-04-05

**文档状态**：终版

**产品负责人**：产品委员会

**目标市场**：保险TPA、保险公司、健康服务提供商

**核心价值**：自动化率≥80% \| 直付≤30秒 \| 新保司接入≤7天 \| 可用性99.99% \| 资金零差错

---

## 文档版本历史

|**版本**|**日期**|**作者**|**变更说明**|
|---|---|---|---|
|V1.0|2026-04-01|产品委员会|初版完整 PRD|
|V1.1|2026-04-05|产品委员会|融合最佳实践，增加确定性分层架构|


---

## 目录

1. [执行摘要](#1-%E6%89%A7%E8%A1%8C%E6%91%98%E8%A6%81)

2. [市场与用户分析](#2-%E5%B8%82%E5%9C%BA%E4%B8%8E%E7%94%A8%E6%88%B7%E5%88%86%E6%9E%90)

3. [产品定位与价值主张](#3-%E4%BA%A7%E5%93%81%E5%AE%9A%E4%BD%8D%E4%B8%8E%E4%BB%B7%E5%80%BC%E4%B8%BB%E5%BC%A0)

4. [产品范围与约束](#4-%E4%BA%A7%E5%93%81%E8%8C%83%E5%9B%B4%E4%B8%8E%E7%BA%A6%E6%9D%9F)

5. [用户故事（核心场景）](#5-%E7%94%A8%E6%88%B7%E6%95%85%E4%BA%8B%E6%A0%B8%E5%BF%83%E5%9C%BA%E6%99%AF)

6. [核心架构与设计原则](#6-%E6%A0%B8%E5%BF%83%E6%9E%B6%E6%9E%84%E4%B8%8E%E8%AE%BE%E8%AE%A1%E5%8E%9F%E5%88%99)

7. [功能需求](#7-%E5%8A%9F%E8%83%BD%E9%9C%80%E6%B1%82)

8. [业务流程](#8-%E4%B8%9A%E5%8A%A1%E6%B5%81%E7%A8%8B)

9. [领域模型](#9-%E9%A2%86%E5%9F%9F%E6%A8%A1%E5%9E%8B)

10. [领域事件与事件驱动设计](#10-%E9%A2%86%E5%9F%9F%E4%BA%8B%E4%BB%B6%E4%B8%8E%E4%BA%8B%E4%BB%B6%E9%A9%B1%E5%8A%A8%E8%AE%BE%E8%AE%A1)

11. [接口定义](#11-%E6%8E%A5%E5%8F%A3%E5%AE%9A%E4%B9%89)

12. [数据字典](#12-%E6%95%B0%E6%8D%AE%E5%AD%97%E5%85%B8)

13. [非功能性需求](#13-%E9%9D%9E%E5%8A%9F%E8%83%BD%E6%80%A7%E9%9C%80%E6%B1%82)

14. [产品路线图](#14-%E4%BA%A7%E5%93%81%E8%B7%AF%E7%BA%BF%E5%9B%BE)

15. [成功指标（KPI）](#15-%E6%88%90%E5%8A%9F%E6%8C%87%E6%A0%87kpi)

16. [风险、依赖与假设](#16-%E9%A3%8E%E9%99%A9%E4%BE%9D%E8%B5%96%E4%B8%8E%E5%81%87%E8%AE%BE)

17. [商业模式](#17-%E5%95%86%E4%B8%9A%E6%A8%A1%E5%BC%8F)

18. [附录](#18-%E9%99%84%E5%BD%95)

    ---

## 1. 执行摘要

### 1.1 产品定位

ClaimSphere 是保险行业首个 **“智能决策操作系统”（Claim OS）**，以“决策图”为核心，将AI概率推理与规则确定性统一收敛，实现理赔全流程的自动化、智能化与配置化。

### 1.2 核心矛盾与解法

保险理赔面临\*\*确定性（规则）与不确定性（AI）\*\*的结构性冲突：

- 传统规则引擎：僵化但资金安全，难以应对复杂医疗场景

- 纯AI方案：灵活但不可控、不可解释，无法通过金融监管审计

  **ClaimSphere 解法**：

  ```plaintext
  资金安全层（100%确定性 Java 内核） → 决策收敛层（规则+AI融合） → 效率增强层（AI原生）
  ```

  通过 **确定性分层架构**，将AI的不确定性与金融核心的确定性完美解耦。

### 1.3 关键指标承诺

|**指标**|**目标值**|**行业基线**|**提升幅度**|
|---|---|---|---|
|自动化率（STP）|≥80%|30%|2.7x|
|直付时效（P99）|≤30秒|3-7天|10,000x|
|新保司接入周期|≤7天|2-4周|3x|
|单案处理成本|≤8元|15-25元|↓50%|
|资金差错率|0|0.01%|100%|
|系统可用性|99.99%|99.9%|金融级|


### 1.4 商业价值（ROI）

- 成本下降 ≥50%（人工成本从40%降至20%）

- 时效提升 10倍（从天级到秒级）

- 欺诈损失下降 ≥30%（多智能体风控）

- 投资回正周期：6-9个月（客户视角，测算口径见 §17 ROI测算）

  ---

## 2. 市场与用户分析

### 2.1 行业痛点

|**痛点**|**表现**|**量化影响**|
|---|---|---|
|体验断裂|垫付-等待-报销流程3-7天|员工NPS仅40-50|
|效率瓶颈|人工审核为主，自动化率仅30%|单案成本15-25元|
|风控盲区|欺诈依赖人工，规则僵化|欺诈损失3-5%|
|账户黑箱|资金不透明|续约率75-85%|
|技术债沉重|单体架构，新保司接入2-4周|故障恢复&gt;30分钟|


### 2.2 用户角色与核心场景

|**角色**|**核心场景**|**使用频率**|**关键痛点**|
|---|---|---|---|
|员工|提交理赔申请、查询进度、咨询AI助手|按需|垫付压力大、流程不透明|
|核赔员|审核自动标记的案件、复核AI低置信案件|每日|重复劳动、规则记忆困难|
|TPA运营|配置保司规则、管理决策图、监控自动化率|每周|新保司接入慢、规则变更周期长|
|保司管理员|接入新保司、配置责任范围、查看理赔报表|按需|资金不透明、数据分散|
|IT管理员|系统部署、监控告警、日志审计|持续|系统稳定性、可扩展性|


### 2.3 核心用户旅程（员工端）



```plaintext
flowchart LR
    A[登录APP] --> B[选择理赔类型]
    B --> C[上传单证（拍照/相册）]
    C --> D[AI自动识别并填充]
    D --> E[用户确认]
    E --> F[提交]
    F --> G[实时决策]
    G -->|通过| H[资金实时到账]
    G -->|拒赔| I[展示原因+申诉入口]
    G -->|人工| J[核赔员处理]
    H --> K[查看电子发票+决策解释]
```

---

## 3. 产品定位与价值主张

### 3.1 产品定位

ClaimSphere 是保险行业首个 **“智能决策操作系统”（Claim OS）**，以“决策图”为核心，将AI概率推理与规则确定性统一收敛，实现理赔全流程的自动化、智能化与配置化。

### 3.2 核心价值主张

|**用户角色**|**价值承诺**|
|---|---|
|员工（被保险人）|直付≤30秒，零垫付零等待；透明展示赔付明细，可追溯决策原因|
|核赔员|AI辅助提取证据，置信度评分；低风险自动通过，高风险人工复核|
|TPA/保司管理者|新保司接入≤7天；自动化率≥80%，单案成本↓50%；决策100%可解释|
|IT负责人|确定性分层架构，资金强一致；低代码配置，变更分钟级生效|


---

## 4. 产品范围与约束

### 4.1 包含范围

- 理赔申请、单证采集、智能审核、决策、理算、支付、结案、后督

- 多保司多租户管理

- 规则引擎、决策图配置、低代码表单

- **存量系统集成**（与客户既有理赔/保单/报案/资金系统打通，旁路→辅助→自动三阶段，详见《存量系统集成与快速部署（FDE）方案》）

### 4.2 不包含范围

- 核保系统（保单承保）

- 核心财务总账系统（仅提供对账接口）

- 医院HIS直连（提供标准API，由保司或TPA负责对接）

### 4.3 约束条件

- 必须遵守《个人信息保护法》及保险行业数据安全规范

- 所有资金操作须满足金融级审计要求（日志保留≥7年）

- 支持信创环境：国产数据库（TiDB）、操作系统（麒麟）、CPU（鲲鹏/海光）

  ---

## 5. 用户故事（核心场景）

采用“作为…我希望…以便…”格式，驱动功能设计。

|**编号**|**角色**|**故事**|**优先级**|
|---|---|---|---|
|US-01|员工|我希望上传发票照片后，系统能自动识别金额和医院，以便我无需手动填写|P0|
|US-02|员工|我希望提交后30秒内知道理赔结果，以便我及时安排就医资金|P0|
|US-03|员工|我希望看到拒赔的具体原因和依据的条款，以便我决定是否申诉|P0|
|US-04|核赔员|我希望系统能标出高风险和AI置信度低的案件，以便我优先处理|P0|
|US-05|核赔员|我希望看到决策图的每个节点执行轨迹，以便我快速理解AI的推理过程|P0|
|US-06|TPA运营|我希望通过拖拽方式配置理赔规则，而不需要写代码，以便快速响应保司需求|P0|
|US-07|TPA运营|我希望新保司接入能在7天内完成，以便快速上线新客户|P0|
|US-08|保司管理员|我希望实时查看各保司的自动化率、赔付金额趋势，以便评估运营效果|P1|
|US-09|IT管理员|我希望系统出现故障时能在15分钟内自动恢复，并且数据不丢失|P0|

### 5.1 MVP 核心需求条目（BDD 规格）

> 以下 8 条为 MVP 纵向切片（门诊小额快赔）的核心需求，按“需求ID + 用户故事 + BDD验收 + 业务规则 + As-Is/Gap”规格展开，作为迭代开发（见《MVP 可落地执行方案》）的直接输入；其余需求见 §7 功能表。

**RQ-MVP-001 材料解析与字段提取**

|**维度**|**内容**|
|---|---|
|用户故事|US-01：作为员工，我希望上传发票照片后系统自动识别金额和医院，以便无需手动填写|
|BDD 验收|Given 一张清晰的二级公立医院门诊发票 When 上传后 Then 30 秒内返回医院/金额/日期/诊疗项目字段，且每个字段带置信度与原文证据位置；Given 发票模糊或翻拍 When 上传 Then 提示重传并标记 OCR 置信度<阈值|
|业务规则|发票/清单/病历三类材料自动分类；金额以“发票价税合计”为准；字段缺失不允许默写（宁缺勿猜，缺则转人工/补件）|
|As-Is / Gap|现状：核赔员逐张录入，单案录入 5-10 分钟，易错；目标：字段提取准确率 ≥95%，单案录入归零|

**RQ-MVP-002 AI 智能填报与人工修正**

|**维度**|**内容**|
|---|---|
|用户故事|US-01：作为员工，我希望系统自动填充申请信息且我能修正，以便减少填写工作量|
|BDD 验收|Given 解析完成 When 提交前 Then 表单自动填充且可编辑；Given 用户修正字段 When 保存 Then 以人工修正值为准并记录差异日志|
|业务规则|自动填充字段与人工核对一致率 ≥90%；用户修正行为入审计（防模型错误静默通过）|
|As-Is / Gap|现状：手工填写错漏率高；目标：自动填充 + 可修正 + 修正留痕|

**RQ-MVP-003 秒级决策（快赔路径）**

|**维度**|**内容**|
|---|---|
|用户故事|US-02：作为员工，我希望提交后 30 秒内知道理赔结果，以便及时安排就医资金|
|BDD 验收|Given 金额≤500 元且材料完整且无风险标记且责任明确 When 提交 Then 自动通过并进入支付；Given 任一条件不满足 When 提交 Then 返回“需人工审核”并展示原因；95% 请求 ≤30 秒返回|
|业务规则|快赔路径条件四选一不满足即转人工；结果类型：自动通过/自动拒赔/需人工审核三类|
|As-Is / Gap|现状：小额案件仍走人工 3-7 天；目标：秒级结案，员工体验质变|

**RQ-MVP-004 拒赔解释与申诉**

|**维度**|**内容**|
|---|---|
|用户故事|US-03：作为员工，我希望看到拒赔的具体原因和条款依据，以便决定是否申诉|
|BDD 验收|Given 自动拒赔 When 通知 Then 输出 ≥3 条可读理由 + 条款原文引用 + 证据位置；Given 员工发起申诉 When 提交 Then 进入 APPEALING 状态并在 48h 内响应（见 §8.5）|
|业务规则|拒赔必答（《保险法》要求）；申诉期间原拒赔决定不产生新费用；复议结果留痕|
|As-Is / Gap|现状：拒赔理由笼统，客诉高发；目标：拒赔有理有据，申诉可追溯|

**RQ-MVP-005 高风险/低置信转人工**

|**维度**|**内容**|
|---|---|
|用户故事|US-04：作为核赔员，我希望系统标出高风险和低置信案件，以便我优先处理真正需要人力的案件|
|BDD 验收|Given 案件带风险标记或 AI 置信度低于阈值或金额≥1万 When 进入审核 Then 出现在核赔员待办首位并展示风险标签；Given 普通案件 When 进入审核 Then 不干扰人工节奏|
|业务规则|风险标记：重复报销/金额异常/时间轴矛盾/规则冲突；大额≥1万元双人复核；转人工案件带上下文（原始材料+AI 输出+中间态）|
|As-Is / Gap|现状：人工平均用力，简单案件挤占人力；目标：人工聚焦异常与复杂案件，复核率 ≤30%|

**RQ-MVP-006 理算引擎（确定性计算）**

|**维度**|**内容**|
|---|---|
|用户故事|US-02/US-04：作为员工/核赔员，我希望赔付金额计算准确且可解释，以便信任结果|
|BDD 验收|Given 责任范围、免赔额、赔付比例、封顶线、自费剔除配置 When 案件理算 Then 金额由确定性代码计算且输出计算过程（每步公式+取值）；Given 年度累计已达封顶 When 理算 Then 返回剩余可赔额度并提示|
|业务规则|免赔额：年累计 300 元起付（可配置）；赔付比例：社保范围内 90%；封顶：单保单年度 2 万；自费药品/保健品/美容不赔付；AI 不参与任何金额计算 |
|As-Is / Gap|现状：人工套用公式易错漏，口径不统一；目标：计算零差错、过程可审计|

**RQ-MVP-007 自动支付（两段式资金状态机）**

|**维度**|**内容**|
|---|---|
|用户故事|US-02：作为员工，我希望自动通过后赔款快速到账，以便及时安排资金|
|BDD 验收|Given 快赔路径自动通过 When 执行支付 Then 走软预占→硬预占→已支付状态机并生成不可变流水；Given 支付失败 When 重试 Then 幂等键（报案号）防重复支付；Given 已支付案件需退款 When 发起 Then 走红冲发票流程|
|业务规则|资金操作必须经确定性核心层（Temporal 严禁直接操作资金）；大额≥1万支付前双人复核；流水不可篡改，日志≥7年|
|As-Is / Gap|现状：人工触发支付、对账靠月度手工；目标：自动支付 + 实时对账 + 全程审计|

**RQ-MVP-008 审计留痕（审计三件套）**

|**维度**|**内容**|
|---|---|
|用户故事|US-05：作为核赔员，我希望每个 AI 判断都能追溯，以便回答客户质疑和监管检查|
|BDD 验收|Given 任意 AI/规则节点执行 When 处理 Then 记录输入摘要（≤200字）+ 模型/规则输出 + 平台动作结果三类日志；Given 审计查询 When 按案件ID检索 Then 可回放完整决策路径（证据→解析→核责→理算→决策）|
|业务规则|资金/决策日志保留≥7年，普通操作≥180天；日志不可篡改（可选用第三方存证）；人工修正与 AI 建议差异留痕|
|As-Is / Gap|现状：人工审核无轨迹，拒赔解释靠记忆；目标：每个结论可回放、可问责、可复盘|


---

## 6. 核心架构与设计原则

### 6.1 确定性分层架构

融合 **AWS 确定性架构**、**蚂蚁 DDD**、**Guidewire** 最佳实践，将系统划分为三层：



```plaintext
flowchart TB
    %% 样式定义
    classDef probabilistic fill:#e3f2fd,stroke:#1565c0,stroke-width:2px,color:#0d47a1,r:10
    classDef gateway fill:#fff8e1,stroke:#f57f17,stroke-width:2px,color:#e65100,r:10
    classDef core fill:#fce4ec,stroke:#c62828,stroke-width:2px,color:#b71c1c,r:10
    classDef external fill:#f5f5f5,stroke:#9e9e9e,stroke-width:1px,color:#424242,r:8
    classDef note fill:#e8f5e9,stroke:#2e7d32,stroke-width:1px,stroke-dasharray:5 5,color:#1b5e20

    subgraph External ["🌐 外部系统 / 用户"]
        direction LR
        User[👤 员工/核赔员]
        Medical[🏥 医保/医院接口]
        Payment[💳 支付网关]
        PolicySys[📄 保单系统]
    end
    class External external

    subgraph Probabilistic ["🔮 不确定性层 – AI 增强与效率优化"]
        direction TB
        A1["📚 RAG 知识检索<br/>TiDB Vector（超限切 Milvus）<br/><i>权限感知租户隔离</i>"]
        A2["📊 风险评分模型<br/>GBDT/LLM 可插拔<br/><i>实时风控</i>"]
        A3["🏥 医疗 NLP<br/>ICD-10 编码识别<br/>合理用药分析"]
        A4["🕵 多智能体风控<br/>Multi-Agent Fraud<br/><i>协同检测</i>"]
        A5["📄 OCR 结构化<br/>发票/病历/清单<br/><i>证据提取</i>"]
    end
    class Probabilistic probabilistic

    subgraph Gateway ["⚖ 确定性网关层 – 决策收敛与风险控制"]
        direction TB
        B1["🔒 置信度熔断器<br/>• AI置信度 < 0.85 → 强制人工<br/>• 金额 > 5000元 → 强制人工"]
        B2["🧠 决策图执行引擎<br/>• 规则节点 (Drools) – 确定性逻辑，最终否决权<br/>• AI节点 (Spring AI Alibaba) – 概率性推理，提供建议<br/>• 支持并行、短路、人机回环"]
        B3["🔗 证据链校验<br/>• 不可篡改哈希链<br/>• 来源可追溯"]
        B4["📋 低代码规则引擎<br/>• DRL/Excel 热部署<br/>• 灰度发布/回滚"]
    end
    class Gateway gateway

    subgraph Core ["💰 确定性核心层 – 资金安全与事务一致性"]
        direction TB
        C1["⚙ 简化资金状态机<br/>软预占 → 硬预占 → 已支付<br/><i>事务内行锁 + 事件溯源</i>"]
        C2["🔄 Temporal 工作流引擎<br/>• 长流程编排（人工复核/跨天调查）<br/>• 断点恢复 / 幂等重试<br/><i>仅编排状态，严禁直接操作资金</i>"]
        C3["🗄 TiDB 分布式事务<br/>• Percolator 模型<br/>• 金融级 ACID + 行锁"]
        C4["📊 资金账本<br/>• 实时余额/流水<br/>• 对账中心"]
    end
    class Core core

    subgraph Outputs ["📢 输出与集成"]
        direction LR
        O1[💬 用户通知]
        O2[📑 电子发票]
        O3[🔍 审计日志]
        O4[⛓ 区块链存证]
    end
    class Outputs external

    %% 数据流
    External -->|“申请 + 证据”| Probabilistic
    External -->|“保单数据”| Core
    Probabilistic -->|“AI建议 / 风险评分 / 提取事实”| Gateway
    Gateway -->|“终审决策 / 理算指令”| Core
    Core -->|“资金操作结果 / 案件状态”| Outputs
    Core -.->|“回调”| External
    Gateway -.->|“低置信度触发”| Probabilistic
    Core -.->|“Saga 补偿”| Gateway

    Note1[“<b>核心安全原则</b><br/>AI 仅提供建议，<br/>资金操作必须经确定性核心层”]
    class Note1 note
    Note2[“<b>可解释性保障</b><br/>决策图执行轨迹全程记录，<br/>满足金融监管审计”]
    class Note2 note
    Note3[“<b>多租户隔离</b><br/>TenantId 贯穿所有聚合根，<br/>数据/配置完全隔离”]
    class Note3 note
    Note4[“<b>AI 人责归属</b><br/>AI 仅提供审核建议，<br/>终审责任归核赔员/保司”]
    class Note4 note

    Note1 ~~~ Gateway
    Note2 ~~~ Gateway
    Note3 ~~~ Core
    Note4 ~~~ Gateway
```

### 6.2 简化资金状态机（两段式）

> **设计取舍**：理赔资金流向单一（审核通过后单笔支付）、并发量低，采用**软预占→硬预占→已支付**两段式即可满足资金安全与审计（TiDB 事务内行锁 + `funding_events` 事件溯源）。
> 五段式（预分配/分配）面向信贷分账户等复杂账务场景，本方案不采用，避免过度设计；如未来扩展账户池/分期赔付，可按 V3+ 演进预留。

所有资金操作必须经过以下三个阶段，确保资金安全零差错：

|**阶段**|**状态**|**业务含义**|**技术实现**|**回滚策略**|
|---|---|---|---|---|
|Stage 1|软预占|余额充足性校验|TiDB `SELECT FOR UPDATE`（事务内）|事务回滚自动释放|
|Stage 2|硬预占|行锁扣减冻结金额；大额（≥1万元）触发双人复核|TiDB 行锁（事务内）|事务回滚自动释放；复核不通过回滚|
|Stage 3|已支付|支付网关成功，生成不可变资金流水|调用支付网关 + 事务提交，写 `funding_events`|不可逆，仅可发起退款（生成红冲发票）|


状态转换图：

```plaintext
stateDiagram-v2
    [*] --> SOFT_RESERVED: 决策通过，启动理算
    SOFT_RESERVED --> HARD_RESERVED: 余额充足，行锁冻结
    SOFT_RESERVED --> [*]: 余额不足 → 转人工/垫付评估

    HARD_RESERVED --> HARD_RESERVED: 大额（≥1万元）双人复核
    HARD_RESERVED --> PAID: 支付网关成功，生成不可变流水
    HARD_RESERVED --> SOFT_RESERVED: 复核不通过/支付失败 → 事务回滚释放

    PAID --> [*]: 结案，区块链存证
    PAID --> REFUNDED: 发起退款（红冲发票）
```

### 6.3 决策图引擎

- **节点类型**：`START`、`RULE_NODE`（Drools）、`AI_NODE`（Spring AI Alibaba）、`DECISION_NODE`（仲裁）、`END`

- **执行特性**：拓扑排序并行、证据传递、短路优化、人机回环

- **AI降级**：每个AI节点可配置 `fallback_rule_node`，超时或失败自动回退规则

### 6.4 设计原则

- **配置优先于编码**：业务差异通过元数据配置，非硬编码

- **决策集中化**：所有终审必须经过决策图，输出单一事实源

- **事件驱动解耦**：上下文间通过领域事件（RocketMQ）通信

- **幂等与补偿**：所有写操作基于幂等键，Saga处理长事务

- **可观测性**：OpenTelemetry统一埋点，全链路追踪

  ---

## 7. 功能需求

### 7.1 功能模块全景

```plaintext
mindmap
  root((ClaimSphere))
    员工端
      理赔申请
      进度查询
      账户余额
      AI助手
      我的保单
    核赔工作台
      待办任务
      案件审核
      决策追溯
      后督巡检
      批量操作
    运营配置台
      保司配置
      决策图编辑器
      规则引擎
      低代码表单
      自动化率看板
      模型管理
      集成中心
        适配器管理
        数据同步监控
        结果回写配置
        三态开关(Shadow/Assisted/Automated)
    管理后台
      租户管理
      用户权限
      审计日志
      对账中心
      监控告警
```

### 7.2 员工端功能

|**功能**|**描述**|**关联用户故事**|**优先级**|**验收标准**|
|---|---|---|---|---|
|理赔申请|支持门诊、住院、药品、体检等类型；拍照上传单证，AI自动分类、OCR提取字段|US-01|P0|上传3张不同发票，识别准确率≥95%|
|AI智能填报|根据上传影像自动填充医院、诊断、费用等，用户可修正|US-01|P0|自动填充字段与人工核对一致率≥90%|
|实时决策|提交后秒级返回“自动通过”、“自动拒赔”、“需人工审核”结果|US-02|P0|95%的请求在30秒内返回|
|决策解释|展示决策图路径、每条规则命中原因、AI证据置信度|US-03|P0|每个拒赔案件至少提供3条可读理由|
|账户余额|实时显示可用额度、已用额度、冻结金额|-|P1|刷新延迟≤1秒|
|AI助手|自然语言问答（如“我这次感冒能赔吗？”），基于RAG检索条款|-|P1|常见问题解答准确率≥85%|
|电子发票|支付成功后自动生成电子发票，可下载、分享|-|P1|支付后5秒内生成发票|
|进度追踪|展示案件状态流转（证据采集→审核→理算→支付→结案）|-|P1|状态更新延迟≤10秒|


### 7.3 核赔工作台功能

|**功能**|**描述**|**关联用户故事**|**优先级**|**验收标准**|
|---|---|---|---|---|
|待办任务|展示需要人工复核的案件（AI置信度低、高风险、金额超限）|US-04|P0|列表加载≤1秒|
|案件审核|查看证据、AI建议、规则命中记录，支持通过/拒赔/补充材料|US-04|P0|审核操作响应≤2秒|
|决策追溯|可视化展示该案件的决策图执行路径，每个节点输入输出|US-05|P0|路径图支持缩放、节点详情弹窗|
|后督巡检|系统自动抽检已结案案件，生成风险报告；支持人工抽检|-|P1|每日抽检比例≥5%|
|批量操作|支持批量通过低风险案件|-|P2|单次批量最多100件|


### 7.4 运营配置台功能

|**功能**|**描述**|**关联用户故事**|**优先级**|**验收标准**|
|---|---|---|---|---|
|保司配置|接入新保司时配置租户、API密钥、责任范围、免赔额等|US-07|P0|新增保司≤30分钟配置完成|
|决策图编辑器|可视化拖拽配置决策节点，支持版本管理、模拟测试|US-06|P0|创建新决策图≤10分钟|
|规则引擎|支持Excel/DRL导入规则，在线编辑、测试、热发布（30秒生效）|US-06|P0|规则变更生效时间≤30秒|
|低代码表单|配置理赔申请表单字段、校验规则、联动关系，实时生效|US-06|P0|新增表单字段无需前端发版|
|自动化率看板|展示整体/各保司自动化率、直付成功率、平均处理时长等|US-08|P1|数据刷新间隔≤5分钟|
|模型管理|上传OCR/NLP/风控模型，A/B测试，灰度发布|-|P2|模型切换无需重启服务|


### 7.5 管理后台功能

|**功能**|**描述**|**优先级**|**验收标准**|
|---|---|---|---|
|租户管理|多保司隔离，独立数据、配置、计费|P0|租户间数据零泄露|
|用户权限|RBAC角色管理（核赔员、运营、管理员）|P0|权限变更实时生效|
|审计日志|记录所有关键操作（决策变更、规则修改、资金操作）|P0|资金/决策相关日志保留≥7年（金融监管要求），其余操作日志≥180天，不可篡改|
|对账中心|每日自动对账，展示差异订单，支持导出|P1|对账准确率100%|
|监控告警|系统SLO监控（延迟、成功率、可用性），钉钉/邮件告警|P1|告警延迟≤1分钟|


---

## 8. 业务流程

### 8.1 端到端直付时序（融合分层架构）

```plaintext
sequenceDiagram
    participant User
    participant Gateway as 确定性网关层
    participant AI as 不确定性层
    participant Core as 确定性核心层

    User->>Gateway: 提交理赔申请 + 证据
    Gateway->>AI: 调用 OCR/风险评分/RAG
    AI-->>Gateway: 返回结构化事实、置信度、风险分
    Gateway->>Gateway: 证据链校验 + 决策图执行
    alt 置信度≥0.85 且 金额≤5000
        Gateway->>Core: 发送理算指令
        Core->>Core: 简化资金状态机（事务内行锁）
        Core-->>User: 支付成功 (≤30秒)
    else 置信度<0.85 或 金额>5000
        Gateway->>User: 转人工复核
    end
```

### 8.2 核赔员审核流程

```plaintext
flowchart TD
    A[登录工作台] --> B[查看待办列表]
    B --> C[选择案件]
    C --> D[查看AI建议+规则命中]
    D --> E{决策}
    E -->|通过| F[自动理算支付]
    E -->|拒赔| G[填写拒赔理由]
    E -->|补充材料| H[通知员工补件]
    F --> I[结案]
    G --> I
    H --> J[等待员工补件后重新进入队列]
```

### 8.3 新保司接入流程

```plaintext
flowchart LR
    A[创建租户] --> B[配置API密钥]
    B --> C[导入保单数据]
    C --> D[配置责任范围]
    D --> E[选择决策图模板]
    E --> F[配置低代码表单]
    F --> G[测试验证]
    G --> H[上线]
```

### 8.4 决策图发布流程（灰度）

```plaintext
flowchart LR
    A[编辑决策图] --> B[保存草稿]
    B --> C[模拟测试]
    C -->|通过| D[提交审批]
    D -->|通过| E[灰度发布（10%流量）]
    E --> F[观察30分钟]
    F -->|无异常| G[全量发布]
    F -->|异常| H[自动回滚]
```

### 8.5 申诉与复议流程

> 拒赔不是终点：被保险人对拒赔结论有异议时可发起申诉，进入人工复议通道（满足《保险法》对拒赔说明理由与及时核定的要求，避免客诉升级）。

```plaintext
flowchart LR
    A[拒赔通知（含理由与条款依据）] --> B[员工发起申诉]
    B --> C[自动复核：重放决策轨迹 + 相似历史案件匹配]
    C -->|证据充分| D[复议通过 → 重新进入理算支付]
    C -->|证据不足| E[资深核赔员人工复议（48h内响应）]
    E -->|维持原判| F[出具最终复议意见书（可向监管/仲裁机构申诉）]
    E -->|改判| D
```

**配套规则**：申诉案件进入 `APPEALING` 状态（见 §12.1 枚举），原拒赔决定在复议期间不产生新费用；复议结果必须留痕（决策轨迹+复议意见），审计可追溯。

---

## 9. 领域模型

### 9.1 限界上下文划分

|**上下文**|**聚合根**|**职责**|**对应微服务**|
|---|---|---|---|
|理赔申请上下文|ClaimApplication|管理用户提交的申请及明细|application-service|
|证据管理上下文|EvidenceCollection|管理单证、OCR、医疗事实提取|evidence-service|
|案件管理上下文|ClaimCase|案件全生命周期管理（状态流转、分配）|case-service|
|决策引擎上下文|DecisionExecution, DecisionGraph|执行决策图，融合规则与AI|decision-service|
|理算资金上下文|IndemnityLifecycle, SettlementOrder|简化资金状态机（两段式）、支付、对账|indemnity-service|
|规则治理上下文|BusinessRule|规则配置、版本管理、热部署|rule-service|
|用户与权限上下文|User, Tenant|认证、授权、租户隔离|uac-service|
|通知上下文|Notification|发送消息、邮件、APP推送|notification-service|
|审计上下文|AuditLog|记录关键操作、资金事件|audit-service|


### 9.2 核心领域类图（聚合根及关键实体）

```plaintext
classDiagram
    class ClaimApplication {
        +UUID applicationId
        +PolicyId policyId
        +UserId applicant
        +ClaimType claimType
        +Money claimedAmount
        +ApplicationStatus status
        +UUID evidenceCollectionId
        +submit()
        +withdraw()
    }
    class ClaimApplicationItem {
        +UUID itemId
        +UUID applicationId
        +LineType lineType
        +String itemCode
        +Money amount
        +Money miPaid
        +BigDecimal copayRatio
    }
    class ClaimCase {
        +UUID caseId
        +UUID applicationId
        +CaseStatus status
        +RiskScore riskScore
        +IndemnityStage currentStage
        +open()
        +requestDecision()
        +startIndemnity()
    }
    class ClaimDetail {
        +UUID detailId
        +UUID caseId
        +UUID applicationItemId
        +Money payableAmount
        +calculatePayable()
    }
    class EvidenceCollection {
        +UUID collectionId
        +List~Document~ documents
        +List~MedicalFact~ medicalFacts
        +lock()
    }
    class DecisionExecution {
        +UUID executionId
        +UUID caseId
        +UUID graphId
        +JsonObject inputFacts
        +DecisionType finalDecision
        +executeNode()
        +converge()
    }
    class DecisionGraph {
        +UUID graphId
        +String version
        +List~DecisionNode~ nodes
        +publish()
    }
    class IndemnityLifecycle {
        +UUID lifecycleId
        +UUID caseId
        +IndemnityStage currentStage
        +Money reservedAmount
        +Money paidAmount
        +softReserve()
        +hardReserve()
        +confirmPaid()
    }
    class SettlementOrder {
        +UUID settlementId
        +UUID indemnityId
        +Money amount
        +PaymentStatus status
        +execute()
    }
    class BusinessRule {
        +UUID ruleId
        +String ruleCode
        +String droolsDrl
        +publish()
    }

    ClaimApplication "1" --> "*" ClaimApplicationItem
    ClaimApplication "1" --> "1" ClaimCase
    ClaimCase "1" --> "*" ClaimDetail
    ClaimCase "1" --> "1" EvidenceCollection
    ClaimCase "1" --> "1" DecisionExecution
    ClaimCase "1" --> "1" IndemnityLifecycle
    DecisionExecution --> DecisionGraph : uses
    IndemnityLifecycle "1" --> "1" SettlementOrder : generates
    DecisionExecution --> BusinessRule : invokes
```

### 9.3 核心实体职责说明

|**实体**|**核心职责**|**关键业务规则**|
|---|---|---|
|**ClaimApplication**|用户提交的理赔申请|提交后不可修改金额；可撤回|
|**ClaimCase**|理赔案件聚合根，贯穿全流程|状态机流转；高风险自动触发调查|
|**EvidenceCollection**|证据管理，锁定后不可变|锁定后触发决策|
|**DecisionExecution**|执行决策图，记录每个节点的输入输出|输出终审决策及置信度|
|**IndemnityLifecycle**|简化资金状态机（软预占→硬预占→已支付）|阶段不可跳跃；金额递减；幂等；大额双人复核|
|**SettlementOrder**|支付指令|支付成功后生成电子发票|
|**BusinessRule**|业务规则（Drools DRL）|支持热部署、版本管理、灰度|


---

## 10. 领域事件与事件驱动设计

所有跨上下文通信均通过**领域事件**（RocketMQ）实现，保证最终一致性。

### 10.1 核心领域事件表

|**事件名称**|**发布上下文**|**订阅上下文**|**携带数据**|
|---|---|---|---|
|`ClaimApplicationSubmitted`|理赔申请|案件管理|applicationId, policyId, evidenceCollectionId|
|`EvidenceLocked`|证据管理|案件管理|evidenceCollectionId, medicalFacts|
|`DecisionRequested`|案件管理|决策引擎|caseId, inputFacts|
|`DecisionConverged`|决策引擎|案件管理|caseId, decisionResult, confidence|
|`IndemnityStarted`|案件管理|理算资金|caseId, decisionId, totalAmount|
|`StageChanged`|理算资金|案件管理、审计|indemnityId, fromStage, toStage|
|`FundsDeducted`|理算资金|案件管理、通知|settlementId, amount, transactionId|
|`CaseClosed`|案件管理|审计、通知|caseId, finalStatus, closedAt|


### 10.2 事件处理可靠性保障

- **消息持久化**：RocketMQ 同步刷盘，保证不丢失

- **幂等消费**：每个事件携带 `idempotencyKey`，消费者基于 Redis 去重

- **死信队列**：消费失败重试 3 次后进入死信队列，人工介入

- **事件溯源**：关键事件（如资金阶段变更）同时写入事件存储（TiDB），支持回溯

  ---

## 11. 接口定义

### 11.1 外部接口（供保司/TPA调用）

#### 11.1.1 提交理赔申请

**Endpoint**：`POST /api/v1/claims`

**Request**：

```json
{
  "policyId": "POL20240001",
  "claimType": "OUTPATIENT",
  "documents": [
    {"type": "INVOICE", "url": "https://xxx.com/invoice.jpg"},
    {"type": "MEDICAL_RECORD", "url": "https://xxx.com/record.jpg"}
  ]
}
```

**Response**：

```json
{
  "code": 0,
  "data": {
    "applicationId": "APP-20260405-001",
    "status": "PROCESSING",
    "estimatedDecisionTime": "2026-04-05T10:00:00Z"
  }
}
```

#### 11.1.2 查询理赔结果

**Endpoint**：`GET /api/v1/claims/{applicationId}`

**Response**：

```json
{
  "code": 0,
  "data": {
    "applicationId": "APP-20260405-001",
    "status": "APPROVED",
    "decision": {
      "result": "APPROVE",
      "amount": 1280.00,
      "reason": "符合保单第3.2条保障范围",
      "traceUrl": "/api/v1/decisions/DEC001/trace"
    },
    "payment": {
      "status": "SUCCESS",
      "transactionId": "TXN123456",
      "paidAt": "2026-04-05T10:00:05Z"
    }
  }
}
```

#### 11.1.3 获取决策追溯

**Endpoint**：`GET /api/v1/decisions/{executionId}/trace`

**Response**：

```json
{
  "code": 0,
  "data": {
    "executionId": "DEC001",
    "nodes": [
      {"code": "START", "type": "START", "duration": 5},
      {"code": "OCR_NODE", "type": "AI", "input": {...}, "output": {...}, "confidence": 0.95, "duration": 120},
      {"code": "RULE_DISEASE", "type": "RULE", "output": "PASS", "duration": 10},
      {"code": "DECISION", "type": "DECISION", "output": "APPROVE", "duration": 8}
    ],
    "finalDecision": "APPROVE",
    "totalDuration": 143
  }
}
```

### 11.2 内部接口（供前端调用）

#### 11.2.1 获取动态表单Schema

**Endpoint**：`GET /api/metadata/form/{formCode}`

**Response**：

```json
{
  "code": 0,
  "data": {
    "formCode": "CLAIM_ENTRY",
    "schema": {
      "type": "object",
      "properties": {
        "policyNo": {"type": "string", "title": "保单号", "x-component": "Input"},
        "medicalType": {"type": "string", "title": "就诊类型", "x-component": "Select", "enum": ["门诊","住院"]}
      }
    }
  }
}
```

---

## 12. 数据字典

### 12.1 核心枚举值定义

|**枚举**|**可选值**|**说明**|
|---|---|---|
|`ClaimType`|OUTPATIENT, INPATIENT, DRUG, PHYSICAL\_EXAM|理赔类型|
|`ApplicationStatus`|DRAFT, SUBMITTED, PENDING\_CORRECTION, CONVERTED\_TO\_CASE|申请单状态|
|`CaseStatus`|PENDING\_DECISION, MANUAL\_REVIEW, INVESTIGATING, APPEALING, INDEMNIFYING, CLOSED\_APPROVED, CLOSED\_REJECTED|案件状态（APPEALING=申诉/复议中）|
|`IndemnityStage`|SOFT\_RESERVED, HARD\_RESERVED, PAID|资金阶段（简化两段式；退款状态见 PaymentStatus）|
|`DecisionResult`|APPROVE, REJECT, MANUAL\_REQUIRED|决策结果|
|`PaymentStatus`|PENDING, SUCCESS, FAILED, REFUNDED|支付状态|


### 12.2 核心值对象

|**值对象**|**字段**|**约束**|
|---|---|---|
|`Money`|amount(BigDecimal), currency(String)|amount ≥ 0, currency=CNY|
|`RiskScore`|score(int), level(enum), features(Map)|score∈[0,100]|
|`TenantId`|value(String)|非空，格式 `T_` + 6位数字|


---

## 13. 非功能性需求

### 13.1 性能指标

|**指标**|**目标值**|**测量方式**|
|---|---|---|
|直付接口响应时间（P99）|≤200ms|APM监控|
|决策图执行时间（P99）|≤200ms|日志埋点|
|系统吞吐量|1000 TPS|压测|
|并发用户数|500核赔员同时在线|模拟登录|
|RAG检索延迟（P95）|≤150ms|APM监控|
|OCR识别时间（单张）|≤3秒|日志埋点|


### 13.2 可用性与可靠性

|**指标**|**目标值**|**实现方式**|
|---|---|---|
|系统可用性|99.99%（年停机≤52分钟）|多AZ部署、负载均衡|
|RTO（故障恢复时间）|&lt;15分钟|K8s自动重启、数据库主从切换|
|RPO（数据丢失）|&lt;1分钟|同步复制、CDC备份|
|资金操作零差错|100%|ACID事务、行锁、幂等设计、Saga补偿|


### 13.3 安全性

- **身份认证**：SSO、OIDC、MFA

- **数据加密**：传输TLS 1.3，存储AES-256

- **权限隔离**：租户级数据隔离，字段级权限控制（OPA）

- **审计合规**：普通操作日志保留≥180天；资金/决策相关审计日志保留≥7年（金融监管要求），资金事件不可篡改（可选用第三方区块链存证）

- **信创兼容**：全Java技术栈，支持国产数据库（TiDB）、操作系统（麒麟）

### 13.4 可扩展性

- **新保司接入**：配置化完成，≤7天

- **新模型接入**：支持热插拔，无需重启

- **新渠道接入**：API网关快速对接

  ---

## 14. 产品路线图

|**阶段**|**时间**|**核心功能**|**验收标准**|
|---|---|---|---|
|**MVP**|M1-M3|基础理赔申请、规则引擎、自动理算、资金扣减、核赔工作台|支持门诊理赔；**验收=同一批真实案件稳定减少核赔员重复劳动**：字段提取准确率≥95%、规则匹配准确率≥90%、人工复核率≤30%（评测方法论见设计方案 §16.1）|
|**V1.0**|M4-M6|决策图引擎、AI OCR/NLP、直付结算、低代码表单配置|支持住院理赔，自动化率≥70%|
|**V2.0**|M7-M9|多智能体风控、Temporal长流程、RAG条款检索、决策图可视化编辑器|支持复杂案件，自动化率≥80%|
|**V3.0**|M10-M12|多租户SaaS、AI助手、开放API、医院端直付（演进）|支持多保司接入，新租户≤7天|

> **直付演进**：行业已出现医院端商保直付试点（如腾讯微保×深圳南山区医院，2026-03）。本方案 MVP 为“报销模式快赔”（打款到员工账户）；医院端直付依赖 HIS/医保通道对接，列为 V3+ 演进方向。


---

## 15. 成功指标（KPI）

|**指标**|**目标**|**测量方式**|
|---|---|---|
|端到端自动化率|≥80%|自动结案数/总案件数|
|直付结算时效|≤30秒|从提交到支付成功时间|
|单案处理成本|下降≥50%|总运营成本/案件数|
|新保司接入周期|≤7天|从合同签署到首笔理赔|
|系统可用性|99.99%|监控系统记录|
|员工NPS|≥70|调研问卷|
|核赔员人效|提升3倍|人均处理案件数/天|
|AI决策采纳率|≥70%|人工采纳AI建议数/总AI建议数|
|字段提取准确率|≥95%|AI提取字段与人工标注一致率（基准测试集）|
|规则匹配准确率|≥90%|规则命中与人工核责结论一致率|
|人工复核率|≤30%（MVP）→≤20%（稳定期）|需人工介入案件占比|
|单案处理时长|较人工基线降低≥50%|从提交到决策输出耗时（人工/自动）|
|重大错误率|0|错赔/漏赔（金额或责任判断错误），全部转人工兜底|
|资金差错率|0|对账差异订单数/总订单数|


---

## 16. 风险、依赖与假设

### 16.1 主要风险

|**风险**|**概率**|**影响**|**缓解措施**|
|---|---|---|---|
|AI模型误判导致错赔|中|高|强制高金额人工复核；规则引擎二次校验；设置置信度阈值|
|医保接口不稳定|中|高|熔断降级为企业垫付；多通道备用；本地缓存|
|新保司配置复杂|低|中|提供配置模板和向导式界面；提供沙箱环境|
|用户数据安全|低|极高|等保三级认证；数据加密；隐私计算；操作审计|
|Temporal长流程状态丢失|低|高|Temporal自动持久化；幂等回调；死信队列|
|TiDB Vector召回率不足|中|中|备用Milvus；混合检索（向量+关键词）|


### 16.2 外部依赖

- 国家医保电子凭证接口（稳定性未知，需熔断）

- 电子票据平台（税局）（开票成功率依赖税局系统）

- 支付网关（银联/支付宝/微信）（需支持Saga回滚）

- 模型供应商（Qwen/OCR厂商）（DPA约定：API数据不用于训练、敏感数据不出境；病历/身份走本地vLLM）

- 医院HIS系统（直付需对接，初期采用垫付模式）

### 16.3 关键假设

1. **假设1**：保司提供的保单数据质量满足基本要求（保单号唯一、保额有效）。

2. **假设2**：OCR对主流医院的发票识别准确率可达95%以上（基于训练数据集）。

3. **假设3**：AI置信度阈值0.85可平衡自动化率与风险，后续可根据数据调整。

4. **假设4**：Temporal工作流引擎能支撑年千万级案件量（需压测验证）。

   ---

## 17. 商业模式

|**模式**|**描述**|**定价参考**|**适用客户**|
|---|---|---|---|
|SaaS订阅|按年收费，含基础案件量|¥50万\~300万/年|中小TPA、保司|
|按单计费|按实际处理案件数收费|¥1\~¥5/单|业务量波动大客户|
|私有化部署|一次性授权+年度运维费|¥300万+|大型保司、金融集团|


**ROI测算（客户视角）**：

- 人工成本降低≥50%（按10人核赔团队，年薪15万，年节省75万）

- 理赔时效提升10倍（从3天到30秒，客户满意度大幅提升）

- 欺诈损失降低≥30%（年赔付1亿元、欺诈损失率按3%计即300万，降低30%后年节省约90万）

- 合计年收益约165万（人工75万 + 欺诈90万），扣减SaaS年费（50万）后净省约115万/年

- **投资回收期**：6~9个月（按 SaaS 年费模式、首年无大额实施费测算；私有化部署按 300 万授权费计，回收期约 2.5-3 年，需客户自行权衡）

- **投资回收期**：6\~9个月

  ---

## 18. 附录

### 18.1 术语表

|**术语**|**解释**|
|---|---|
|决策图|融合规则与AI证据的有向无环图，统一决策收敛|
|简化资金状态机|软预占→硬预占→已支付（事务内行锁+事件溯源），确保资金操作零差错|
|证据链|从原始影像到结构化事实的完整路径，不可篡改|
|后督智能体|自动抽检结案案件的AI智能体|
|低代码配置|通过JSON Schema配置表单，无需编码|
|RAG|检索增强生成，用于条款检索|
|Temporal|长流程工作流引擎，支持断点续传和Saga|
|置信度熔断器|当AI置信度低于阈值或金额超限时，强制转人工复核|


### 18.2 参考文档

- ClaimSphere 智能理赔平台技术方案（2026-04-05，与本文档同步）

- ClaimSphere 存量系统集成与快速部署（FDE）方案（2026-10-12）

- ClaimSphere MVP 可落地执行方案（2026-10-12，迭代计划/任务拆解/业务闭环）

- 行业TPA智能化转型白皮书

- 三阶段六步闭环法架构设计方法论

- 保险行业数据安全规范

  ---

  **文档状态**：终版

  **产品负责人**：\_\_\_\_\_\_\_\_\_\_\_

  **业务方确认**：\_\_\_\_\_\_\_\_\_\_\_

  **技术负责人**：\_\_\_\_\_\_\_\_\_\_\_

  **日期**：2026年4月5日

  **核心金句**：

  > **“ClaimSphere 不做又一个理赔系统，我们构建保险行业的‘决策操作系统’——让理赔像呼吸一样自然，让AI像医生一样专业，让业务创新像搭积木一样简单。”**

