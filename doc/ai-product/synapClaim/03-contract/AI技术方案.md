---
doc_id: contract.ai
role: ssot
currency: current
---

# 智能理赔平台详细技术方案（bone-ai 落地版）

> 版本：v1.0　状态：技术方案（可评审）　作者：BONE 平台架构
>
> **本文定位**：在 `synapClaim`（业务领域与产品方案）、`pk-tpa-saas`（真实 TPA 智能核赔落地实现）与 `bone-ai`（已实现的 AI 能力中台）三者基础上，输出一份**可直接落地到代码**的智能理赔详细技术方案。它回答"怎么做"，把业务规则、领域模型、状态机、AI 编排落到 bone-ai 六个模块与具体技术选型上。
> **SSOT 边界（本文不定义契约）**：物理 DDL 以**工程落地版 §9** 为唯一权威、接口契约与数据字典以 `07-delivery/05` 为唯一真源、业务规则以 `07-delivery/04`（业务规则书）为唯一真源、领域模型与流程/事件以**工程落地版 §4 / §7** 为权威。本文仅做 **bone-ai 落地实现视角**（模块映射 / 编排 / 评测 / 里程碑），对契约与领域模型只做「实现映射 + 指向」，不重述，避免漂移。

## 文档版本历史

|**版本**|**日期**|**作者**|**变更说明**|
|---|---|---|---|
|v1.0|2026-10-14|BONE 平台架构|初版技术方案（可评审）|
|v1.1|2026-10-21|BONE 平台架构|结构治理：§8 数据模型删除内联 DDL、改指工程落地版 §9；文首明确「本文不定义契约」；§9.1/§10.2 对齐 07-delivery/04-05 真源；品牌统一 synapClaim|
|v1.2|2026-11-04|BONE 平台架构|**AI 原生目标态回写**：§1.1 定位由「AI 只产边缘建议」升级为「AI 承担感知与认知、确定性代码承担资金、人承担终审」的分阶段目标；§1.2 增补 P7–P9；新增 §1.4 成熟度分级与三层智能分工、§11.3 数据飞轮与 MRM；§12.2 补 AI 原生判据。P1「AI 不碰钱」与 §6.5 三重校验门禁**不变**，红线不放宽|
|v1.3|2026-11-04|BONE 平台架构|**AI 原生落地态回写**：① §1.4 把"目标态"与"代码实测态"分线——生产 `synap-claim` 已接线结构化认知契约（`AiGatewayPort.AiAssessment` + `AiAssessNodeHandler` 三态安全 + `DecisionTriage` 确定性三档），POC 主机 `ClaimService` 此前滞后，本轮现代化为同契约输出；② 新增 §1.5「AI 原生契约（代码引用）」作为 AI 输出的 SSOT；③ 知识层（受控 RAG）与学习层（飞轮/MRM）诚实定级 L0/L3 并给出路线，不再把目标态写成现状；④ `LocalAiGatewayPortAdapter` 补最小确定性认知，使演示轨迹非空。红线与三档分诊**不变**|
|v1.4|2026-11-04|BONE 平台架构|**AI 原生主链接线**：① 感知层接 `DocumentParsePort`（数电票文本层结构化解析，字段级置信度 + 原文坐标 + 完整性，主动解析端点）；② 知识层以租户医学目录准入核验替换恒 `available=false` 桩，无法判定仍 fail-closed；③ 决策层新增 `AiDiscrepancyRouting`，白名单差异码首次驱动**非资金**流程状态（自动通过 → 补件/复核），原因码可回放；④ 学习层把后督 DIFF 转成结构化评测样本事件；⑤ 新增 §1.6 落地规格、验收判据与未落地边界。§1.4 状态列、§1.5 不变量同步回写。**P1 红线、三档分诊、G0–G4 门禁不变；真实 provider、图像多模态、受控 RAG、MRM 仍未落地，不得据此宣称 L3**|
|v1.5|2026-11-04|BONE 平台架构|**复核建议落地**：① 发票验真 / 验重前移到材料阶段（票面要素 + 跨案去重，查实问题落 `FAIL`，本地核验通过仍落 `UNVERIFIABLE` 转人工，不放宽 R-FRAUD-06）；② 核责规则资产化（资源文件可替换 + 版本随命中留痕，配置不可信时回退）；③ 链路联动（案件级锁定事件 → 自动触发决策图，默认关闭、G3 前置）；④ §1.6 补验收判据、未落地边界与「建议采纳 / 未采纳理由」对照表。**红线与成熟度定级不变，仍不得对外宣称 L3**|

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

面向**门诊小额快赔**这一单点打透场景，构建一个「**AI 承担感知与认知、确定性代码承担资金、人承担终审、系统从每一次人工干预中学习**」的 AI 原生理赔平台（目标态与成熟度分级见 §1.4）。

> **资金红线（不变，P1）**：AI 只建议、不碰钱；资金走确定性代码；决策可解释、可回放、可治理。平台输出的是带完整证据链与置信度的建议/结论，最终赔付与资金流转由确定性业务代码 + 人工终审决定。
>
> **定位修正（v1.2）**：v1.0 的「AI 只建议」在实现层被压缩成决策图末端的一个布尔值，造成感知、知识、飞轮三层缺位。**AI 原生不等于放宽资金红线**——它要求 AI 成为收单 → 初审 → 采集 → 审核各段的主执行体（影像解析、结构化事实抽取、条款与临床合理性检索、差异提示与证据定位），并对自身输出的不确定性负责；确定性代码继续独占金额计算与资金状态机，人工继续独占拒赔与终审。资金权限不放宽，AI 的职责范围前移。

### 1.2 设计原则（源自三方融合）

| # | 原则 | 出处 | 落地含义 |
|---|---|---|---|
| P1 | **AI 不碰钱** | 工程落地版 §7.3（决策图执行） / POC 核心差异化 | AI 只产出建议与置信度；金额计算全部是确定性代码；资金状态机由业务代码 + 事务保障，AI 推理失败绝不阻断资金流程 |
| P2 | **确定性分层** | 工程落地版 §3.1（架构分层，四层确定性） | 规则/计算/状态机为确定性层；AI 仅为建议层；AI 与资金之间由决策图引擎强一致衔接 |
| P3 | **治理靠结构而非约定** | bone-ai（ArchUnit/HC-001） | 短路、熔断、计量、人工终审、回归门禁由框架结构强制，不依赖调用方自觉 |
| P4 | **人工终审权保留** | bone-ai HITL 五态 / synapClaim R-MAN | 拒赔/大额/低置信/风险标记必须人工；AI 置信度 < 阈值强制转人工 |
| P5 | **可解释可回放** | bone-ai trace + synapClaim 决策追溯 | 每次决策落证据链（输入摘要/规则命中/AI 建议/置信度），一键回放决策路径 |
| P6 | **旁路→辅助→自动化** | synapClaim MVP Shadow 路径 | 三态开关（Shadow 旁路 / Assist 辅助 / Auto 自动化），每一步可回退 |
| P7 | **度量先行 + 数据飞轮** | Google《Rules of ML》#1/#2、《The ML Test Score》 | 先建非 ML 基线与切片指标再接模型；人工复核结论经独立盲标回流为评测样本，冻结集不参与调优；置信度阈值由校准曲线决定，不由假设拍定 |
| P8 | **模型风险治理（MRM）** | NIST AI RMF 1.0 四函数 / SR 11-7 三道防线 | 模型清单 + 模型卡 + 独立验证 + 漂移监控 + champion-challenger；提示词/模型/规则版本随决策回放，可复算可投诉举证 |
| P9 | **渐进放量与错误预算** | Google SRE / Progressive Delivery | Shadow → Canary → Auto 按租户与金额段分桶；SLO、错误预算、自动回滚、租户级 kill switch 与回退演练为放量前置，三态开关只是入口不是机制 |

> **P7–P9 与 P1–P6 的关系**：P1–P6 守住"钱与合规不出错"，P7–P9 决定"系统能不能自己变好"。只有前六条时，能力增长只能靠增加规则与改代码（L1）；补齐后三条，能力增长才来自数据与模型迭代（L3）。三组原则互不冲突，**不得以 P7–P9 为由绕过 P1**。

### 1.3 场景边界（MVP 一期）

- **险种**：企业补充医疗险 · 门诊 · 小额
- **渠道**：员工自助理赔（上传发票 + 清单 + 医保结算单）
- **决策**：自动通过（≤500 元且无风险）/ 系统建议拒赔（人工确认）/ 转人工 / 补件
- **资金**：报销模式快赔（打款到员工账户，两段式状态机）
- **非目标**（二期+）：住院/意外/重疾、多智能体风控（C2 orchestration 状态机已覆盖编排，自主 Agent 循环仅限四条件试点）、低代码可视化编辑器。**医院端直付由二期提前至 V1.0 试点**（经保司/TPA 通道，复用 `bone-ai-integration` C10 三态灰度 Shadow→Assisted→Automated，非自建医保局/银行网关），单证 OCR 报销兜底保留。

### 1.4 AI 原生目标态与成熟度分级（v1.2 新增）

**三层智能 + 资金 + 学习的分工**

| 层 | 由谁承担 | 输入 → 输出 | 2026-11-04 核码状态（v1.4 复核） |
|---|---|---|---|
| 感知层 | 多模态模型 / OCR / 版面分析 | 影像 → 结构化单证事实（字段、坐标、置信度、完整性） | **一期已接线（文本层子集）**：`DocumentParsePort` + `TextLayerDocumentParsePortAdapter` 从数电票文本层抽字段，落 `extracted_json`（置信度 + 原文坐标 + 完整性），`POST /evidences/{id}/parse` 主动触发；无文本层即 `FAILED` 转人工。图像 / 翻拍 / 多模态模型未接入（Phase B） |
| 知识层 | 受控 RAG / 医学目录 / 临床合理性 | 事实 + 条款 → 证据定位、条款引用、合规差异提示 | **一期已接线（目录准入）**：`CatalogKnowledgeRetrievalPortAdapter` 消费感知层事实，在 `t_ref_disease` / `t_ref_drug` 做准入核验；`available=true` 仅在事实齐备且目录命中时，核验范围写进 `basis` 受控码。无事实 / 目录未命中 / 装配不全一律 `available=false`。语义检索、适应症图谱与受控 RAG 未集成（Phase B） |
| 决策层 | 确定性规则 + 分诊策略 | 事实 + 规则命中 → 通过 / 复核 / 人工 / 补件 | **AI 认知已能驱动非资金流程状态**：`AiGatewayPort.AiAssessment` 携带结构化认知 + 模型/提示词自溯源；`AiDiscrepancyRouting` 把白名单差异码映射为补件 / 复核档，原因码 `AI_DISCREPANCY:<码>` 落 `triage_reason`；`AiAssessNodeHandler` 三态安全与 `DecisionTriage` 确定性三档不变 |
| 资金层 | 领域聚合 + 事务 | 理算额 → 结算 / 支付 / 对账 | 已实现且为护城河，**AI 不得进入** |
| 学习层 | 评测集 + 标注回流 + MRM | 人工干预 → 下一版模型 / 提示词 / 阈值 | **回流触发器已接线**：`InspectionFeedbackRecorder` 订阅 `InspectionConcludedEvent`，DIFF 结论转 `AiEvalSampleCollectedEvent`（评测集 / 规则修正 / 控制修正三类 + 幂等指纹），经 `AiEvalSamplePort` 出站。样本消费方、独立盲标、校准曲线、champion-challenger 与 MRM 仍属 Phase B / V2 |

**成熟度分级（判定以代码为准，不以叙事为准）**

| 级别 | 形态 | 可证伪判据 | 本平台（v1.4 实测） |
|---|---|---|---|
| L0 | 规则自动化 | 无 AI 参与 | 未配真实 provider 时 LLM 节点降级纯规则；图像多模态、受控 RAG、MRM 仍未接入 |
| L1 | AI 辅助 | AI 产边缘建议，摘除后主链路不变 | **已越过**：v1.3 的 AI 认知只进决策轨迹，摘除不影响主链路 |
| L2 | AI 增强 | AI 承担感知 / 知识主负载，摘除即断流 | **部分达到，仍非完整 L2**：感知 / 知识 / 决策 / 学习四层均已接线且知识层不再恒转人工，但一期感知是文本层确定性解析、知识是目录准入核验，真实模型与受控 RAG 接入前，摘除 AI 后主链路仍能靠确定性路径运行 |
| L3 | AI 原生 | 概率输出驱动全流程，数据飞轮驱动能力演进 | 阶段 2–3 目标（真实模型 + 飞轮消费方 + MRM 全链路）；当前 AI 输出仅驱动非资金流程状态，资金 / 拒赔 / 理算仍由确定性代码与人工独占 |
| L4 | 自治智能体 | 多智能体协同、工具调用、自主规划 | 探索项，不进 MVP |

**演进门禁（按依赖顺序，不是重要性排序）**

| Gate | 内容 | Go/No-Go 判据 |
|---|---|---|
| G0 | 度量与契约基线 | 非 ML 基线四项（人工工时 / 字段准确率 / 理算准确率 / leakage rate）+ 单案 AI 成本可复现；契约测试覆盖所有模型客户端实现；冻结评测集哈希可校验 |
| G1 | 感知层 Shadow | 分层抽样下字段 / 分类指标**按切片**达标，抽取结果只记录不生效 |
| G2 | 规则与知识接线 | 规则真实执行且命中留痕；目录准入与临床检索可用，理算输入契约能消费病历事实 |
| G3 | 渐进放量 | SLO、错误预算、自动回滚、租户级 kill switch 与回退演练通过，才允许进入 Auto 态；真实 provider 在此才配置 |
| G4 | 飞轮与治理 | 独立盲标、校准曲线、模型清单与独立验证报告齐备 |

> **排序依据**：Google《Rules of ML》#1 / #2 要求先有非 ML 基线与指标埋点、再引入模型。跳过 G0 直接接模型，会让效率提升百分比既不可验证也不可回退。G0 不依赖任何外部 provider，可立即启动。
>
> **诚实定位（v1.4）**：四层主链均已接线，代码实现处于「L1 已越过、L2 部分达到」，未配真实 provider 时运行态仍以确定性路径为主、接近 L0。本节**不把目标态写成现状**；宣称态 / 契约态 / 实现态的分线由交付验收逐列对账，禁止先改文档宣告完成。对外仍按 L1/L2 分阶段表述，L3 须以 G0–G4 全通过为前提。

### 1.5 AI 原生契约（代码引用，v1.3 落地）

> **"原生"的判据不在模型多大，而在 AI 成为感知与认知的主执行体，且其输出能被业务、审计、评测三方消费。** v1.2 写的是原则，v1.3 落的是契约，v1.4 补上流程路由——以下三处代码是 AI 输出契约的**唯一真源**，文档只引用、不重述；输出如何驱动流程见 §1.6。

**① AI 输出契约：`AiGatewayPort.AiAssessment`**
`bone-apps/synap-claim/.../application/port/out/AiGatewayPort.java`

- 结构化认知：`facts`（受控码 + 值 + 证据定位）/ `evidenceRefs` / `clauseRefs` / `discrepancies`（差异提示受控码白名单）。
- 溯源自报：`modelVersion` / `promptVersion` 由**适配器自报**（真实上游模型 + 提示词契约版本），**不经调用方传入**——模型版本是决策复算、算法备案、投诉举证的先决条件，交给 HTTP 调用方填会静默留空。
- 不变量：认知字段 `fail-soft`（缺失即空列表），**绝不因"认知没给全"把一次可用评估判成不可用**；认知层进决策轨迹供核赔员/审计/评测样本消费，白名单内的差异码另经 §1.6 路由驱动非资金流程状态。**不参与金额、不产拒赔结论、不改分诊阈值与输入**（P1 不变）。

**② 确定性分诊：`DecisionTriage`**
`bone-apps/synap-claim/.../domain/model/decision/DecisionTriage.java`

- 三档：`< 0.7` 强制人工、`0.7–0.85` 复核、`≥ 0.85` 直出；OCR 置信度是**独立字段、独立判定**（§6.3）；`fail-safe` 任一无法判定即转人工。阈值来自 `TriageLimits`（外置），未配置时与历史常量逐位一致。

**③ AI 节点三态安全：`AiAssessNodeHandler`**
`bone-apps/synap-claim/.../application/handler/AiAssessNodeHandler.java`

- 知识能力不可用（`available=false`）落 `MANUAL_REQUIRED`，**绝不映射为拒赔**——"没接线"≠"对客户不利决定"（历史上二者被合并，导致知识库未接入被落成"系统建议拒赔"，已解耦）。
- 降级路径（`LLM_UNAVAILABLE`）也输出同一套认知键（空值），保证 `claim_decision_trace` schema 稳定，读侧不必判空分支。

**POC 主机对齐（本轮）**：`ClaimService`（`bone-ai/bone-ai-server/.../claim/ClaimService.java`）此前为"AI 只产布尔 + 置信度"旧范式，与 v1.2 叙事矛盾。本轮对齐契约：`underwrite` 从 `DocumentIR`（条款切片 / 页码 / span）**确定性派生** `clauseRefs` / `evidenceRefs` / `facts`（感知层结构化抽取），解析 LLM 结构化认知（同四字段），自报 `modelVersion` / `promptVersion`（标 `local-stub` 防误读），保持三档分诊与 P1 红线不变。

> **与 P1 红线关系**：契约向决策轨迹供"可解释材料 + 评测样本源"，并向流程路由供受控差异码；资金 / 拒赔 / 理算 / 分诊阈值仍由确定性代码 + 人工独占，AI 输出只做安全方向的单向收紧（自动通过 → 补件 / 复核）。

### 1.6 AI 原生落地规格（v1.4 代码契约）

> 本节写**已接线的规格**，不写愿景。代码位置、端口与输出契约是判据，文档不重述实现细节。未落地项集中在末表，避免把目标态混进现状。

**① 四层端口矩阵**

| 层 | 端口（application/port/out） | 一期实现（infrastructure） | 输入 → 输出 | 无法判定时的处置 |
|---|---|---|---|---|
| 感知 | `DocumentParsePort` | `TextLayerDocumentParsePortAdapter` | 单证原文 → `ParsedDocument`（字段 / 置信度 / 原文坐标 / 完整性 / 受控原因码） | `available=false` → `ocr_status=FAILED`，转人工补录；关键字段不齐 → `MANUAL` |
| 知识 | `KnowledgeRetrievalPort` | `CatalogKnowledgeRetrievalPortAdapter`（默认）；`LocalKnowledgeRetrievalPortAdapter` 仅在 `bone.claim.knowledge.mode=stub` 装配 | 感知层结构化事实 + 租户医学目录 → 目录准入三态 + `basis` 范围码 | 无事实 / 目录未命中 / 装配不全 → `available=false`，决策图转人工；**不映射为"临床不合理"** |
| 决策 | `AiGatewayPort` / `DecisionNodeHandler` | `AiGatewayPortAdapter` + `AiAssessNodeHandler` + `AiDiscrepancyRouting` | 事实 + 规则 + 模型认知 → 结论 / 档位 / 原因码 / 全链轨迹 | LLM 不可用 → `FALLBACK` 纯规则；能力缺失 → `MANUAL_REQUIRED` |
| 学习 | `AiEvalSamplePort` | `EventAiEvalSamplePortAdapter` | `InspectionConcludedEvent`（DIFF）→ `AiEvalSampleCollectedEvent`（三类归因 + 幂等指纹） | 投递失败只记 `[FEEDBACK_WRITE_FAILED]`，不回滚抽检结论 |

`clinicallyReasonable=true` 的准确含义是「在 `basis` 声明的受控范围（事实齐备性 + 目录准入）内未发现差异」，**不是模型临床推理结论**；适应症图谱与语义检索接入前不得扩大解释。

**② 流程路由契约（AI 认知 → 非资金状态）**

| 输入 | 前置条件 | 结论 | 档位 | 原因码 |
|---|---|---|---|---|
| `EVIDENCE_MISSING` | 当前为 `AUTO_APPROVE` | `NEED_MORE_EVIDENCE` | `MANUAL` | `AI_DISCREPANCY:EVIDENCE_MISSING` |
| 其余白名单码 | 当前为 `AUTO_APPROVE` | `MANUAL_REQUIRED` | `REVIEW` | `AI_DISCREPANCY:<码>` |
| 白名单外 / 自由文本 | 任意 | 忽略 | 不变 | 不变 |
| 任意码 | 当前非 `AUTO_APPROVE` | 保持原结论 | 不变 | 保持更精确的原因码 |

白名单唯一真源 `AiDiscrepancyCodes.ALL`（`AMOUNT_MISMATCH` / `DIAGNOSIS_FEE_MISMATCH` / `DRUG_INDICATION_MISMATCH` / `EVIDENCE_MISSING` / `DUPLICATE_ITEM` / `OTHER`），同时用于网关输出白名单、提示词枚举与流程路由；差异码语义变化须同步 bump `PROMPT_VERSION`（v1.4 为 `claim-assess-v4`）。

**③ 红线矩阵**

| AI 可以做 | AI 不得做 |
|---|---|
| 抽取事实、定位证据、提示差异 | 计算赔付金额、写入结算 / 支付状态 |
| 把自动通过拉回补件 / 复核档 | 产出拒赔结论、改分诊阈值与输入 |
| 供核赔员可解释材料与评测样本 | 在 `available=false` 时冒充"已核验"或"无风险" |

**④ 验收判据（可证伪）**

1. `mvn -o -pl bone-apps/synap-claim clean test` 全绿（当前 `Tests run: 919, Failures: 0, Errors: 0, Skipped: 5`），含感知 / 知识 / 路由 / 回流 / 验真 / 规则 / 联动七组用例；
2. 数电票文本层样例产出字段级置信度与原文坐标，坐标可回读原文；无文本层样例落 `FAILED`；
3. 知识层「无事实 / 仅票面事实 / 目录未命中」三类均返回 `available=false`；
4. AI 差异码把 `AUTO_APPROVE` 降为补件或复核档，且原因码落 `triage_reason` 可回放；非自动通过结论不被覆盖；
5. 后督 DIFF 按归因产出三类样本事件，指纹稳定，投递失败不抛回抽检事务；
6. 验真 / 验重：缺关键字段 → `available=false`；格式错误 / 金额差额 >1 元 / 跨案重复 → `FAIL`；本地核验通过 → `UNVERIFIABLE`（**绝不落 `PASS`**）；验真回填不重置 `ocr_status` 与置信度，锁定后拒写；
7. 规则资产化：外部规则集替换生效且版本随命中明细落 `rule_version`；配置缺失 / 损坏 / 为空回退内置集；
8. 链路联动：开关默认关闭；开启后一次锁定只触发一次决策；触发异常不抛回锁定事务；
9. 本族 6 道机检（`_check_manifest` / `_check_xref` / `_check_contract_drift` / `_check_ddl` / `_check_numbers` / `_gen_ai_index --check`）与 `scripts/check.sh` 全绿。

**⑤ 未落地边界（不得写成现状）**

| 能力 | 状态 | 前置门禁 |
|---|---|---|
| 真实 LLM provider | 未配置；未配时网关回落 `MockLlmClient` 被适配器显式拒绝，LLM 节点降级纯规则 | G3 放量演练通过后才配置 |
| 图像 / 翻拍件 OCR、多模态影像分类与旋转纠偏 | 一期只做数电票文本层，图像件转人工补录 | G1 感知层 Shadow + 平台 C1 文档 IR |
| 受控 RAG / GraphRAG / 适应症图谱 / 向量检索 | 一期为目录准入确定性核验 | G2 规则与知识接线、平台 C6 |
| 视觉反诈 E1 / 图计算反欺诈 E2 | 仍为 `available=false` 桩 | 平台侧能力交付 |
| 评测集消费方、独立盲标、ECE 校准、champion-challenger、MRM | 一期只完成回流触发器与样本分类 | G4 飞轮与治理 |
| 税局 / 电子票据平台验真通道 | 一期只做票面要素核验 + 跨案去重；本地通过仍落 `UNVERIFIABLE` 转人工 | G2 + 外部通道接入 |
| 规则 DB 热更 / 灰度 / 回归门禁 | 一期为资源文件级替换 + 版本留痕 | bone-ai C2 RuleAsset（Phase B） |
| 自动放款全链路 | 案件级触发已落地但开关默认关闭；`AUTO_APPROVE` 仍受 E1/E2 硬闸约束 | G3 渐进放量 |
| 理赔分析数据 / 运营大盘 | 评测样本导出已由平台侧承接；运营大盘仍缺 | G3 / 交付排期 |

**⑥ 复核建议落地对照（v1.5）**

| 复核建议 | 处置 | 判据 / 理由 |
|---|---|---|
| P0-1 感知层解析 | 已落地（文本层） | 字段级置信度、原文坐标、完整性；影像分类 / 旋转纠偏需平台 C1 文档 IR，标 Phase B，不用桩冒充 |
| P0-2 验真 / 验重前移 | 已落地 | 结论经既有 `verify_valid` → R-FRAUD-06 → 决策图硬闸生效，无需扩展决策上下文；不放宽「通道未接一律转人工」 |
| P0-3 分级反欺诈硬闸 | **未采纳** | 与 `capabilityMissing` 兜底及「AI 不碰钱」冲突。E1/E2 未交付时 `AUTO_APPROVE` 不可达是 G3 门禁的有意行为；放宽等于在能力未集成时放款 |
| P0-3 真实 provider / `domain.security` 接生产链 | 部分未采纳 | provider 是部署配置，代码不得伪造；`domain.security` 三件套接进生产链会与 §7.2「安全基线一律复用网关 C8、synapClaim 不重复实现」形成双份实现，保持不接线 |
| P1-1 知识层三层 | 已落地（目录准入） | 临床合理性与语义检索需平台 C6；`basis` 受控码写明核验范围，不冒充临床推理 |
| P1-2 规则资产化 | 已落地 | 资源文件可替换 + 版本留痕 + 损坏回退；DB 热更属 Phase B；风控维度按真实业务补（验真 / 验重），不按对标宣传口径凑数 |
| P1-3 链路联动 | 已落地（默认关闭） | 案件级事件一次触发，G3 前置开关；理赔分析数据由并发交付的评测样本导出承接，不重复实现 |
| P2-1 飞轮 | 已落地触发器 | 独立盲标 / ECE 校准 / MRM 需 G4 |
| P2-2 口径治理 | 已落地 | 对外按 L1/L2 分阶段表述；效率口径用 PRD 分阶段自动化率与单案人工工时下降，不套用竞品效率数字 |

**放量纪律**：E1/E2 未交付时 `fraudGatePassed` 恒为 `false`、`AUTO_APPROVE` 逻辑不可达，是 G3 门禁与「AI 不碰钱」共同约束下的**有意行为**，不是待修补的缺陷。放行路径是交付 E1/E2 并通过放量演练，不得靠放宽硬闸或删除 `capabilityMissing` 兜底实现。

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
| 共保额度 | shareLimit − 关联责任赔付之和 | 按 shareCode 绑定（此为引擎七层控额维度 `limits.sharedLimit()`；额度账户侧 `quota_account.SHARED_POOL` 是同一 `sharedCode` 的跨案占额落点，两者不互相替代） |
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

`APPROVED` / `REJECTED` / `REQUEST_MORE_EVIDENCE` 是**决策结果**（响应体 `decision` 字段，见 `07-delivery/05`），`CLOSED` 由 `fundingStage` 表达——**三者都不属于 case status 枚举**。把决策结果写进状态链会让「状态机」与「决策结果」两个维度混成一个，前端与对账两侧都只能靠约定解析。

内部细粒度状态（pk-tpa 状态码 7/10/11/13/…/50）只存在于 `claim_case.status`，经投影表映射为上表两值，不对外暴露（真源：`07-delivery/05` + 工程落地版 §6.1）。

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
| AI_NODE 医学 RAG（**V1.0 Phase B**，由二期提前） | `bone-ai-gateway` + `bone-ai-rag` | 切片→向量→RRF→rerank；**GraphRAG 图谱遍历**；权限过滤（ACL 默认 deny + 写入侧 PII 掩码）；MVP I0–I4 不含（见工程落地版 §7.3 注） |
| DECISION_NODE 转人工 | `bone-ai-orchestration` HITL | HitlItem 五态 + ReasonType 回流 |
| FRAUD_NODE 反欺诈（**V1.0 Phase B**，新增节点类型） | `bone-ai-graph`（E2）+ `bone-ai-notification`（C9） | 关系图谱+设备指纹+团伙识别（`R-FRAUD-09/10/11`）；命中经 C9 多级预警 + 挂起 `FRAUD` |
| VISUAL_NODE 视觉反诈（**V1.0 Phase B**，E1） | `bone-ai-visual`（C12-visual） | 影像指纹/篡改/PS 检测、跨案件重复检索（`R-FRAUD-08`） |
| LEDGER_NODE 信任额度（C11） | `bone-ai-ledger` | 高信任客户免单证直赔的预授权/两阶段预留 |
| 全过程 | `bone-ai-trace` | 每个节点落 decision_trace 节点序列；评测+MRM（E4）闭环 |

### 6.4 AI 超时分层与动态降级

| 查询类型 | 判定 | 超时 | 降级 |
|---|---|---|---|
| 简单（单文档） | — | 3s | 规则兜底 |
| 标准 RAG（2-5 文档）（**V1.0 Phase B**） | — | 5s | 缓存 |
| 复杂 RAG（>5 文档）（**V1.0 Phase B**） | — | 8s | 异步转 bone-ai-orchestration |

> RAG 链路由二期提前至 **V1.0 Phase B**（依《BONE-AI 能力依赖图谱》）；MVP I0–I4 仍不含 RAG（避免击穿容量矩阵），Phase B 增量另行架构评审；一期 LLM 调用统一走 `bone-ai-gateway` 的熔断/重试/token 计量。

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

> **数据与隐私红线**：`insured_id_hash` 用 HMAC-SHA256+盐，明文身份证号不上送、不落地（见 07-delivery/02 数据脱敏）；`consent_record_id`/`consent_withdrawn_flag` 留痕（PIPL 29 条）。

---

## 9. 接口契约

### 9.1 接口总览（以 07-delivery/05 为真源）

> 接口端点、对外枚举、数据字典以 `07-delivery/05`（接口契约与数据字典）为**唯一真源**（MVP 子集）；完整 OpenAPI 以**工程落地版** §10 为权威。本文不重复罗列端点，落地要点见 §9.2。

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

### 10.2 MVP 规则书全集（以 07-delivery/04 为真源）

> MVP 规则全集以 `07-delivery/04`（业务规则书 Rulebook）为**唯一真源**（R-AREA / R-DIR / R-DED / R-RAT / R-CAP / R-DEC / R-MAN / R-FRAUD），本文不重复罗列。

> **置信度口径对齐**：`07-delivery/04` 的 R-MAN-01「<0.85 转人工」为产品单档简化口径；本方案落地按**三档**——<0.7（或高风险）强制人工终审、0.7-0.85 复核档、≥0.85 且无风险直出，与 ClaimService 一致。规则冲突裁决（R-DEC 优先级）见 §10.1。

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
- **指标真源**：上表阈值与交付验收同源于 07-delivery/07 测试方案与性能基准；工程落地版 §11.2 为性能与 SLO 的唯一真源，本表不另立口径。
- **规则/模型/提示词版本绑定留痕**：每次发布绑定评测集版本，回放可复现。

### 11.2 可观测（bone-ai-trace + OpenTelemetry）

- **决策回放**：`decision_trace` 节点序列（证据→解析→核责→理算→决策），任意案件一键回放决策路径（对应 工程落地版 §7.3（决策图执行）/ §11.5（可观测性）决策追溯 / bone-ai trace 证据链）。
- **全链路追踪**：OpenTelemetry 统一埋点，Grafana 看板（自动化率/复核率/赔付趋势/时延）。
- **审计三件套**：请求摘要（≤200 字）+ 响应结果 + 耗时，错误可回放。
- **计量**：bone-ai-gateway `LlmUsageRecorded` 按租户/模型聚合 token 与成本，供预算/配额/成本红线消费。AI 预算配额（真源：工程落地版 §11.12）：**不设全局默认值**，按「月预算上限 = 月案件量 × AI 触发占比 × 0.5 元/案 × 1.5」逐租户推导后写入 `feature_toggle` 的 `aiBudget.soft`（=70%）/ `aiBudget.hard`（=100%），**未配置不得进入 Automated 态**；按案件成本红线 0.5 元/案不变。（原 500/2000 元默认值会在满负荷用量的 2.7% 处误触发「LLM→纯规则降级」，已废止，推导式见工程落地版 §11.12。）

### 11.3 数据飞轮与模型风险治理（v1.2 新增）

**数据飞轮（防反馈环）**

| 环节 | 做法 | 为什么 |
|---|---|---|
| 样本采集 | 人工复核结论、AI 结构化输出与规则命中明细随决策轨迹落库，作为候选样本 | 决策轨迹已是事实源，无需另建采集链 |
| 独立盲标 | 标注队列**屏蔽 AI 建议**，由核赔员独立给出结论；AI 建议与人工结论分字段存储 | 人工复核受 AI 建议影响（自动化偏差），直接当作 ground truth 会放大偏差（Hidden Feedback Loops） |
| 冻结集 | ≥300 条评测集哈希校验，永不参与训练与调优；每次发布绑定评测集版本 | 防止「用评测集调参再自评」的假绿 |
| 切片评估 | 按票据类型 / 医院等级 / 金额段 / 租户 / 翻拍件分别报指标，不只看总体 | 总体准确率会掩盖小样本切片失效（ML Test Score 数据类） |
| 阈值校准 | 用 ECE / Brier 重算置信度分档，再决定 0.7 / 0.85 与 OCR 阈值；变更须有对照实验 | 阈值来自假设而非数据时，自动化率与风险不可同时优化 |
| 回流迭代 | 误判样本入评测集、规则修正候选、欺诈正负例三向回流 | 让能力提升来自数据，而不是只靠增加规则 |

**MRM 三道防线**

| 防线 | 职责 | 最小产物 |
|---|---|---|
| 一线（开发 / 使用） | 模型与提示词开发、自测、运行监控、模型卡维护 | 模型卡（用途 / 数据 / 指标 / 局限 / 版本 / 退役条件）+ 自动化评测 |
| 二线（独立验证） | 独立于开发方的验证与挑战、模型清单维护、放量审批 | 验证报告 + 模型清单（风险分级 / owner / 验证状态 / 下次复验日） |
| 三线（内审） | 检查前两道防线是否真的运行 | 周期性审计报告与整改台账 |

- **可复算四元组**：每次决策可回放「输入 IR + 模型 / 提示词版本 + 输出 + token 成本」；缺任一项不得进入 Auto 态。
- **漂移与事件响应**：分数分布、切片指标、特征新鲜度设监控；超阈触发强制复验，并预先写明谁有权下线模型、回滚路径与监管留痕。
- **红线不变**：§6.5 三重校验上线门禁、P1「AI 不碰钱」、P4 人工终审权保留，均不因本节的能力升级而放宽。

> **v1.3 实现态与 L3 门禁（本轮回写，诚实分级）**
>
> 本节 v1.2 写的是**目标态**；本轮按代码实测把"已落地"与"待架构师审批"分线，禁止把目标态写成现状：
>
> | 能力 | 2026-11-04 实测 | 缺口与门禁 |
> |---|---|---|
> | 样本采集 | **已落地**：决策轨迹节点序列 + `AiAssessment` 自报 `modelVersion`/`promptVersion` + 结构化认知（`facts`/`evidenceRefs`/`clauseRefs`/`discrepancies`）随 `claim_decision_trace` 落库，已是候选样本源 | 缺"独立盲标队列"与"样本→评测集"回流管道（L3） |
> | 独立盲标 | **未建**：`LocalKnowledgeRetrievalPortAdapter` 为诚实 fail-closed 桩（`available=false`→转人工）；真实 C6 受控 RAG 未集成 | 受控 RAG 全链 + 盲标队列 = **L3 Phase B**，需架构师审批（新依赖 / DDL / CI 门禁） |
> | 冻结集 | **仅契约**：评测集哈希校验是 G0 判据，但冻结集存储与哈希校验器**尚未落地** | 评测集存储 + 哈希校验器 = **L3**（DDL / 独立存储） |
> | 切片评估 | **未建**：指标按整体口径，无票据类型 / 医院等级 / 金额段切片看板 | 切片看板 = L3（指标管道） |
> | 阈值校准 | **硬编码**：0.7 / 0.85 与 OCR 阈值来自 `TriageLimits`/历史常量，未接 ECE / Brier 校准 | 校准曲线产出 = L3（需评测集 + 离线管线） |
> | MRM 模型卡 / 清单 | **仅溯源**：模型 / 提示词版本随轨迹自报，但无模型卡登记库与独立验证报告台账 | 模型卡 + 模型清单 + 独立验证 = **L3**（DDL / 审计台账） |
>
> **L3 待架构师审批清单（AI 不代执行）**：① 受控 RAG（C6 GraphRAG）接入与盲标队列 DDL；② 冻结评测集存储 + 哈希校验器落 CI；③ 切片指标看板与校准曲线离线管线；④ 模型卡 / 模型清单 / 独立验证报告台账；⑤ 对应 CI 门禁（G0–G4 全绿）。以上任一落地前，对外表述维持 L1/L2 分阶段，**不得宣称 AI 原生（L3）**。
>
> **本轮已交付的最小契约基座**（非 L3，已落地）：AI 输出 SSOT（`AiGatewayPort.AiAssessment`）+ 确定性分诊（`DecisionTriage`）+ 三态安全节点（`AiAssessNodeHandler`）+ POC 主机与本地桩对齐同契约——使"可复算四元组"的**溯源字段**已就位，待 L3 补全样本回流与治理台账后即满足 G0–G4 准入。

### 11.4 评测样本出口与弱标签口径（v1.5 落地）

> §11.3 的实测表把「缺『样本→评测集』回流管道」列为 L3 缺口。本节交付的是这条管道的**上游出口**：让已经落库的决策事实能被评测侧取走。**下游（冻结集存储、盲标队列、切片看板）仍未建**，本文不据此宣称闭环完成。

**为什么先做出口**：AI 认知已能驱动流程（业务侧消费）、已随轨迹落库（审计侧消费），但没有任何通道把「AI 判断过什么、最后实际发生了什么」变成评测样本——评测侧等于零消费。没有出口，冻结集只能是手写骨架，准确率永远停在 `declared`。

**端点契约**

| 项 | 约定 |
|---|---|
| 路径 | `GET /api/v1/claims/eval/samples?from={iso}&to={iso}&limit={n}` |
| 权限 | 复用 `claim:claims:read`（**不新造权限码**：新码不落权限表会让端点对所有人 403） |
| 数据源 | `claim_decision_execution`（仅 `SUCCESS` / `FALLBACK`）× `t_claim_case`（终态标签） |
| 时间窗 | 必填，`from < to`，跨度 **≤31 天**；越界 **400**，不静默裁剪 |
| 条数 | `1..500`；SQL 侧 `LIMIT 500` 硬上界 + 服务层按请求截断，最坏只读 500 行 |
| 无数据 | 返回 `returned=0` 与空样本集（哈希仍稳定），不是 404 |

**弱标签口径（本节最容易被误用的地方）**

标签来自案件**最终状态**（`FINISHED` 已结案 / `CLOSED_REJECTED` 拒赔生效），它混合了人工判断、时效、客户撤诉等因素，**不等于核赔员的独立判定**。因此导出**只用于一致性统计与样本筛选**，不得当作金标准；金标准需要 §11.3 的独立盲标（屏蔽 AI 建议后重判）。

| AI 结论 | 案件终态 | 判定 | 依据 |
|---|---|---|---|
| `AUTO_APPROVE` | `FINISHED` | `AGREE` | 建议通过且已支付 |
| `AUTO_APPROVE` | `CLOSED_REJECTED` | `DISAGREE` | 建议通过但被拒 |
| `SUGGEST_REJECT` | `CLOSED_REJECTED` | `AGREE` | 建议拒赔且拒赔生效 |
| `SUGGEST_REJECT` | `FINISHED` | `DISAGREE` | 建议拒赔但最终赔付 |
| `MANUAL_REQUIRED` / `NEED_MORE_EVIDENCE` | 任意终态 | `UNKNOWN_NO_BINARY_VERDICT` | **AI 未给可比二元结论，不计入准确率** |
| 任意 | 非终态（如 `PRE_EXAMINED`） | `UNKNOWN_NOT_CONVERGED` | 标签未定 |

> **为什么 `MANUAL_REQUIRED` 不算一致**：把「AI 建议转人工、人工也处理了」记成「AI 判断对了」，会让准确率被「AI 没给结论」的样本持续抬高，且从报表上看不出异常——这是指标造假的经典形态，不是口径选择问题。

**聚合口径：返回体自带「这批数据能不能算一致率」**

导出体含 `agreementSummary`，按判定态计数并给出可计算率：

| 字段 | 含义 |
|---|---|
| `agree` / `disagree` / `unknownNoBinaryVerdict` / `unknownNotConverged` | 四种判定态的条数；四者之和恒等于 `returned`（不足即抛错，不输出自己都对不上账的聚合） |
| `evaluableCount` | `agree + disagree`，真正能进入一致率计算的样本数 |
| `agreementRate` | `agree / evaluableCount`（4 位小数）；**`evaluableCount=0` 时为 `null`** |
| `rateMeaning` | 固定说明串：弱标签一致率，不得直接当模型准确率对外报 |

> **为什么 `evaluableCount=0` 要显式给出、且 `agreementRate` 用 `null` 而非 `0`**：这是两件不同的事实——`0%` 是「模型全错」，`null` 是「这批判据根本不成立」。多数报表把 null/NaN 静默变成 0，于是「当期一条可比结论都没有」被写成「一致率 0%，严重退化」，触发一轮并不存在的排障；**反向更危险**：调度器把空结果当「达标」，从此没人发现评测已经空转。
>
> 不是假设。当期 dev 库实测（2026-11-04，31 天窗口）：`claim_decision_execution` 84 条收敛记录 **100% 为 `FALLBACK`，`decision_result` 全为 `MANUAL_REQUIRED`** ⇒ `evaluableCount=0`、`agreementRate=null`。不看这个字段，导出物看起来「有 84 条样本」，实际一条也不能用于准确率；若把它们当正例计入，一致率会显示成 100%。

**脱敏与泄漏防护**

- 样本键 = `sha256(域前缀 + claimId)` 前 16 位：可去重、不可直接反查案件号（导出物会离开系统边界）。
- 不导出报案号、被保人假名化 ID、轨迹原文 JSON（避免 PII 与体积膨胀）。
- 返回体带 `splitPolicy=eval-only`：这些样本**已被 AI 判断过**，用于训练会让模型在自己的判断上自我强化；与冻结集的 train/tune 互斥约束同一立场。
- `samplesHash = sha256(规范化样本集)`：用于核对两次导出是否同源、是否被中间环节篡改。它**不是**冻结集哈希（不承担「样本改动即拒绝评测」的职责）。

**三条样本源的分工（避免重复建设）**

| 来源 | 采集什么 | 偏向 | 状态 |
|---|---|---|---|
| 后督差异回流（`AiEvalSampleCollectedEvent`） | 质检判定为差异的案件，按 `EVAL_SET` / `RULE_FIX` / `CONTROL_FIX` 三向分流 | **负例定向** | 已落地（事件 + 端口） |
| 版本化回归集（`eval/ai-decision-evalset.json`） | 手写 30 例确定性回归，带 `expected` 与监管依据 | **防退化** | 已落地（含 A1–A9 门禁） |
| 本节弱标签导出 | 时间窗内全部收敛决策 + 终态一致性 | **分布全量（含正例）** | 本轮落地 |

> 只看负例会高估错误率、只看回归集会漏掉真实分布漂移——三者互补，不应合并成一套。

**仍未做（不得据此宣称 L3）**：冻结集存储与哈希校验器落 CI、独立盲标队列、切片指标看板与校准曲线。样本出口只解决「取得到」，不解决「标得准」。

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
| AI 原生准入（G0–G4，见 §1.4） | 非 ML 基线 + 切片指标 + 契约测试进入 CI；冻结集哈希可校验；独立盲标与校准曲线产出；模型清单与独立验证报告齐备；放量演练（SLO / 错误预算 / 自动回滚 / kill switch）通过。**任一缺失不得对外宣称 AI 原生**，只能按 L1/L2 分阶段表述 |

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
