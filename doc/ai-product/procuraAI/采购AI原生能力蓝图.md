# 采购 AI 原生能力蓝图（Proposal）

> **版本**：v0.1（2026-11-09，草案，待架构评审冻结）
> **状态**：**Proposal（设计提案，不进冻结，不在本期实现范围）**
> **上游**：`procuraAI-方案逻辑自洽与AI原生诊断-2026-11-09.md` §4（G1–G10 缺口）+ 本报告 §4.1（编排层）
> **治理铁律**：本蓝图所有新增 AI 能力**必须同样受 `D3-v2 §3` 约束**（三态 `ai_available`、fail-closed、人工终决、PII 禁入、评测集回归、证据不回传 output、版本溯源）。未经 D3-v2 冻结的能力码（C-F）不得标「① 复用 S」。
> **定位**：把当前「点状挂载」的 AI（仅 02 评标 / 03 审查）升级为「AI 原生」——AI 作为贯穿采购全链路的**一等公民能力**。本文是能力地图与编排骨架，**不是模块详设**，落地需逐能力立项、过评测集、owner 排期。

---

## 1. 为什么需要「AI 原生」而不是「加几个 AI 功能」

诊断确认：当前 AI 仅挂在 02（评标辅助 M1/M4）、03（审查/起草/问答/抽取/风控/绩效 M2/M5/M7）两卷，且严格限定在「人工决策前的辅助位」。**01 主数据 / 04 交易 / 05 履约 / 06 编排 四个模块完全没有 AI 设计**，M10 是独立 runtime，各模块各自接散点端口。

业界共识（Ariba / Coupa / SAP Ariba Category Management / Gartner 采购 AI 成熟度）：AI 原生的价值不在「单点智能」，而在**贯穿源（需求）→ 寻源 → 合同 → 交易 → 履约 → 支付 → 续约**的连续智能与数据飞轮。散点接入会重复造评测集 / 降级 / cost-quota / trace / HITL，**稀释现有合规红线**（D3-v2 §3）。因此本蓝图在 M10 runtime 之上补一层**统一的采购域 AI 编排层**（§3），让 G1–G10 共享治理基座。

---

## 2. 能力地图（G1–G10）

> 列说明：**why** 给业界对标与价值；**落点**给目标模块与触达的事件/端口；**阶段**给建议立项次序（详见 §4）；**治理**统一 = D3-v2 §3。

| # | 能力 | why（业界对标 + 价值） | 落点模块 | 触达事件 / 端口 | 阶段 |
|---|------|------------------------|----------|-----------------|------|
| **G1** | 需求预测与支出智能（Spend & Demand Intelligence） | 现 PR 从无到有手工发起。AI-native 基于历史 PO / 品类 / 项目 / 季节预测需求与支出、建议请购品类与预算占用、识别异常支出。对标 Ariba Guided Buying + Coupa Spend Insights——S2P 源头节流是核心价值，降低长尾请购、提升预算命中 | M01/M04 + 预算域 | `RequisitionSuggested` 事件 / `SpendForecastPort` | P3 |
| **G2** | 智能寻源与供应商推荐（Supplier Discovery） | 现仅有 M4 评标辅助，**无主动推荐**。基于品类 / 历史绩效 / 风险 / 产能推荐候选供应商与寻源策略（RFx / 目录 / 框架协议），缩短寻源周期、扩大合格池、避免单一来源 | M02 | `SourcingStrategySuggested` / `SupplierRecommendPort` | P2 |
| **G3** | 谈判辅助与定价智能（Negotiation & Pricing Co-pilot） | 现价格仅靠规则（04 §13.1 条件技术 / PPV）。给议价建议 / 底价建议 / 降本机会识别。采购降本（PPV/Savings）是 CPO 首要 KPI，AI 议价直接贡献利润 | M02/M04/M05 | `PricingAdvicePort`（只读建议） | P3 |
| **G4** | 智能对账与异常检测（Intelligent Reconciliation & Anomaly） | 现三单匹配只有规则容差 + 三通道台账（05 §13.1），无 AI 异常识别。ML 抓重复付款 / 价格异常 / 供应商串货 / 异常发票模式——财务舞弊与错误付款是采购最大风险敞口，Ariba Invoice Exception + Coupa Risk Alert 已验证 | M05/M06 + Finance | `AnomalyDetected` 事件 / `ReconAnomalyPort` | P2 |
| **G5** | 供应商风险动态画像与持续监控（Continuous Risk Intelligence） | 现只有事件触发的 `RiskAlert`（03），`vendor_profile` 是快照（01 §3）。持续聚合内外部信号（征信 / 舆情 / 履约 / 财务健康）做动态评分，替代「快照 + 事件」，支撑准入 / 续约 / 配额；供应链中断 / 破产是顶级风险，静态快照滞后 | M01/M07 | `SupplierRiskScoreUpdated` 事件 / `RiskIntelligencePort` | P2 |
| **G6** | 对话式采购助理 / 采购 Copilot（Conversational Assistant） | 现仅 03 的 M5 合同问答。跨 01–06：「问一句查单 / 催货 / 发起请购 / 解释对账差异」。降低使用门槛、提升 adoption（B2B 供应商 + 内部业务人员），是 AI-native 入口形态 | 跨域（独立 Copilot 适配层 + 各域 `QueryPort`） | `ProcurementCopilotPort` / 各域查询端口 | P3 |
| **G7** | 智能预算与支出异常监控（Spend Anomaly Guard） | 现预算仅占用 / 释放（04 §13.1⑤）。异常支出模式 / 预算击穿 / 部门超额预警。预算合规是央国企硬要求（195 号文 + 内控） | M04 | `BudgetBreachAlert` 事件 / `BudgetGuardPort` | P3 |
| **G8** | 合同履约与交付风险预测（Delivery Risk Prediction） | 现只有事后 `RiskAlert` + `PerformanceScore` 计分。预测交付延迟 / 质量风险 / 续约时机，把风险从「事后告警」前移到「事前预测」，降低断供 | M03/M05/M07 | `DeliveryRiskPredicted` 事件 / `DeliveryRiskPort` | P3 |
| **G9** | 智能续约 / 到期决策支撑（Renewal Intelligence） | 现义务日历仅「是否终止」人工触发（03 §13.1②）。续约策略推荐、备选供应商建议；续约决策是采购价值节点，当前几乎空白 | M03 | `RenewalAdvicePort`（只读建议） | P3 |
| **G10** | 品类策略与支出分析洞察（Category Strategy） | 现缺失。品类分层（Kraljic）、支出集中度、整合采购机会——战略采购起点，支撑 B19「先跑通 spend capture」路径 | 跨域 | `CategoryInsightPort` | P4 |

> **阶段说明**：P2 = 紧接本期二期（补齐风险/对账/寻源智能，复用现有 M10 runtime 与 D3-v2 §3）；P3 = 三期（预测/对话/Copilot，需数据飞轮与前端）；P4 = 远期战略层。**所有阶段的能力立项前必须先把对应能力码登记进 D3-v2 §1.1 映射表并过评测集（参照 §3.2）**。

---

## 3. 统一的采购域 AI 编排层（架构级，必建）

> 不建这层，G1–G10 继续散点接 `procuraai-runtime`，会重复造评测集 / 降级 / cost-quota / trace / HITL，并稀释 D3-v2 §3 红线。

```
                ┌─────────────────────────────────────────────┐
                │   采购域 AI 编排层（Proposal，新增抽象）        │
                │  ── 统一治理基座（D3-v2 §3 的执行面）          │
                │  • 能力注册表（capability registry）          │
                │  • 评测集复跑（每个能力自带评测集 + 关键负向） │
                │  • 降级 rules_only（AI 不可用 → 纯规则）       │
                │  • cost / quota 限额（按能力 + 租户）          │
                │  • trace_id 贯穿 + 证据链（不回传 output）     │
                │  • human_in_loop 路由（建议 → 人工终决）       │
                └───────────────┬─────────────────────────────┘
                                │ 统一端口 ProcurementAiOrchestrationPort
        ┌───────────────┬───────┴────────┬───────────────────┐
        ▼               ▼                ▼                   ▼
   G1-G3 (M01/02/04)  G4-G5 (M05/07)   G6 (Copilot 适配层)  G7-G10 (跨域)
        │               │                │                   │
        └───────────────┴────────────────┴───────────────────┘
                                │
                        ┌───────▼────────┐
                        │ M10 procuraai- │
                        │ runtime（LLM/  │
                        │ RAG/规则/评测） │
                        └────────────────┘
```

**职责边界**：
- 编排层不持有 LLM，只负责**治理与路由**；推理仍在 M10 runtime。
- 每个 Gx 能力在注册表登记：能力码（D3-v2 §1.1）、评测集路径、降级策略、quota、HITL 路由规则。
- 编排层对模块暴露**唯一端口** `ProcurementAiOrchestrationPort`（替代当前 02/03 各自 `AiReviewPort` 散点），模块只看到「建议 + 三态 `ai_available` + 证据引用」。

---

## 4. 增量落地阶段（建议）

| 阶段 | 范围 | 依赖 | 验收基线 |
|------|------|------|----------|
| **P2（优先）** | G2 智能寻源推荐、G4 智能对账异常、G5 风险动态画像 | 现有 M10 runtime + D3-v2 §3（已具备）；需 Finance ACL 交付（F-A）才能跑通 G4 端到端 | 每个能力 ≥1 评测集 + 关键负向不可删 + 注入必红 / 还原必绿（参照 synap-claim `eval/ai-decision-evalset.json` 模式） |
| **P3** | G1 支出预测、G3 定价 Copilot、G6 对话助理、G7 预算守卫、G8 交付风险、G9 续约决策 | P2 数据飞轮 + 前端 Copilot 壳（F-D）+ 预算域端口 | 同上 + 对话链路端到端 demo |
| **P4** | G10 品类战略洞察 | 支出数据湖就绪 | 战略报告可生成 |

> **关键纪律**：每新增一个 Gx，必须①登记 D3-v2 §1.1 码表、②自带评测集与负向探针、③接入编排层统一治理、④过门禁（A8 契约 + A10 合规 + AI 评测门禁）。**不守 D3-v2 §3 的能力一律不得上线**（fail-closed）。

---

## 5. 边界声明（与诊断一致）

- 本文为 **Proposal 设计提案**，**不进冻结、不在本期实现范围**；未提交 / 未 PUSH、未写 `bone-init.sql`、未接 CI、未改生产代码。
- G1–G10 的**实现**属 owner 边界（需立项 / 排期 / 数据 / 跨域交付），本文只给能力地图、触达点、编排骨架与治理约束；落地属 owner 职责。
- F-A（跨域 ACL 交付）、F-D（门户新建）是 G4/G6 真实前置，须在本期之外关闭。
- 本蓝图与诊断报告 `procuraAI-方案逻辑自洽与AI原生诊断-2026-11-09.md` 配套；诊断的 C-A~C-F 矛盾修复见各文档（已闭合），本蓝图是诊断 §4 的执行化延伸。
