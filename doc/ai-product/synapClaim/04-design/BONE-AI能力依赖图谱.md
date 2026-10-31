---
doc_id: design.bone-ai-deps
role: ssot
currency: current
---

# synapClaim × BONE-AI 能力依赖图谱与增强方案（企业级串联）

> **版本**：v1.0　**日期**：2026-10-26　**归口**：理赔产品架构组
> **配套**：`platform/BONE-AI能力中台整体方案 v1.16`、`BONE-AI-RAG能力方案 v1.11`、`方案诊断复核-2026-10-24 v1.3`、《BONE-AI架构决策记录ADR包-2026-10-24》；`synapClaim` 工程落地版 / AI 技术方案 / Rulebook / 07-delivery/06 / 《业界对标整改台账》。
> **一句话**：synapClaim 应**最大化复用 BONE-AI 中台既有能力**（C1–C11），把"机制"交给平台；同时识别出平台**尚未覆盖的三类底层智能能力**（视觉反诈、图计算反欺诈、MRM 治理）与两类**需增强的既有能力**（C6 GraphRAG、AiAssess 多模态理解），按企业真实场景（信创/私有化/数据不出域/多租户/合规）落地并**串联进 5 节点决策图 + 直付通道 + 后督闭环**。

---

## 0. TL;DR

| 维度 | 结论 |
|---|---|
| **可复用（直接依赖）** | C1 文档解析、C2 规则+编排、C3 HITL、C4 证据链、C5 LLM 网关、C6 RAG（提至 V1.0）、C9 预警、C10 集成三态灰度、C11 额度台账（信任额度） |
| **平台缺口 → 须增强/新建** | ① 视觉反诈（C1 增强或新 C12-visual）② 图计算反欺诈（新 C12-graph）③ MRM 模型风险管理（C7 增强）④ GraphRAG 医学图谱检索（C6 增强）⑤ AiAssess 多模态理解（用法增强，依托 C5+C6） |
| **企业场景契合** | 信创（国产图库 Nebula/TuGraph、私有化 Qwen3.5/3.8-Docling/PaddleOCR）、数据不出域（网关 fail→确定性/PII 掩码/RAG 自托管 rerank）、多租户（BIGINT+字符串映射 ADR-4）、合规（195 号文/金发8号/TC260 ADR-1 + AI 不碰钱 + 类型护栏 + 证据链） |
| **串联形态** | 决策图由 5 节点扩为 **6 节点**：`EvidenceLoad(OCR+C1) → RuleCheck(RULE+C2) → AiAssess(LLM+C5+C6+视觉E1) → FraudScreen(图E2+C9) → Arbitrate(DECISION+C3) → Adjust(RULE+C2+C11)`；直付走 C10 三态灰度；后督 100% 走 C7+MRM |

---

## 1. synapClaim 可依赖的 BONE-AI 能力及理由

> 原则（整体方案 §2.1）：**跨域同构的机制进平台，领域资产留产品**。理赔的确定性内核（资金状态机、理算引擎、医学图谱、决策图模板）留产品；解析/编排/HITL/证据/网关/检索/预警/集成/台账机制复用平台。

| # | 平台能力 | synapClaim 落点（端口/节点） | 为什么依赖（理由） | 企业场景契合点 |
|---|---|---|---|---|
| C1 | 文档解析与 IR（`bone-ai-document`） | `DocumentParsePort`：数电票/影像/清单/病历/医保结算单 → `ParsedDocument`（字段+置信度+坐标） | 发票/清单/病历结构化是理赔起点；平台已就位最小 IR + Docling/PaddleOCR sidecar 契约，避免自研 OCR 管线 | 私有化 OCR（Docling MIT / PaddleOCR Apache，均合规）；数电票文本层解析准确率≥95% 已纳入 I1 退出标准 |
| C2 | 双引擎与编排（`bone-ai-rules` + `bone-ai-orchestration`） | `RuleEnginePort`（QLExpress 热更+灰度+回归门禁）、`OrchestrationPort`（决策图执行+HITL 五态） | 核责/理算/快赔/转人工/反欺诈规则 + 显式状态机是理赔确定性内核的"治理面"，平台已落地版本/灰度/shadow+回归门禁 | 规则热更+回归门禁是资金零差错与可审计的底座（已在 PRD/Rulebook 固化） |
| C3 | HITL 确认与回流（`bone-ai-orchestration` 子域） | `Arbitrate` 节点 → `HitlItem` 五态 + `reason_type` 回流 | 自动通过/建议拒赔须人工裁决；0.85 自动放行线 / 0.7 HITL 线 与平台置信度分级同构 | `AiSuggestion`/`Confirmed` 类型护栏（v1.15 已机械强制）保证"AI 不碰钱"——金融合规硬门槛 |
| C4 | 快照与审计追溯（`bone-ai-trace`） | `TracePort` → `claim_decision_trace` + 回放 | 每次决策落输入哈希+IR+引擎版本三元组，一键回放——金发8号"可解释+可人工复核"落地 | 证据链无旁路、同步落盘拒返，超开源网关基线 |
| C5 | LLM 网关（`bone-ai-gateway`） | `AiGatewayPort`：模型档位路由/配额/降档/调用审计/成本红线 | 医学评估、风控推理统一出口；熔断/重试/计量/PII 出口脱敏/内容安全全部收口 | 多模型兜底、成本红线 fail-closed、数据不出域（自托管 Qwen3.5/3.8-27B，Apache-2.0） |
| C6 | 受控 RAG（`bone-ai-rag`） | `KnowledgeRetrievalPort`：医学目录/条款语义匹配（**由二期提至 V1.0 Phase B**） | R-DIR 目录匹配、临床合理性、条款语义匹配须"带出处、无依据不编造"——平台 RAG 已落地混合检索+RRF+rerank+引用强制+GroundingGuard | 自托管 bge-m3 嵌入/rerank（数据不出域）；ACL 默认 deny + 写入侧 PII 掩码 |
| C7 | 评测与门禁（`bone-ai` 流水线） | 300 条基准集 + 分层采样 + CI 负向探针 + LLM-as-Judge | 规则/AI 变更须回归门禁，防"假绿"；与平台 `RagGoldenSetEvalTest`/faithfulness 双判官同源 | 评测集分层抽样（金额段×医院等级×渠道×风险标记），PSI>0.2 增量补入 |
| C8 | LLM 安全基线（`bone-ai-gateway`） | 入口 PII 脱敏 + 提示注入检测 + 输出内容安全（**复用网关，不自研**） | §7.2 三件套与网关 C8 同义；应复用 `PiiRedactor`/`PromptInjectionGuard`/`ContentSafetyGuard` 而非各自实现，避免策略漂移 | ADR-1：SynapClaim 对外，内容安全**默认开**；注入检测开+强误伤监控 |
| C9 | 提醒预警（`bone-ai-notification`） | 反欺诈告警 / 阈值预警 / SLA 超时升级 | R-FRAUD 命中、挂起>72h、额度触顶须多级预警+消警闭环 | Webhook 出站 + HMAC 签名 + 消警守卫，零新第三方依赖 |
| C10 | 集成适配与三态灰度（`bone-ai-integration`） | **直付通道**：医保/医院数据直连 Shadow→Assisted→Automated | V1.0 直付通道（对标优化整改 P0-1 已提）本质是"外部系统 AI 出站治理"——与 C10 三态灰度/SSRF 守卫/HMAC 回调校验同构 | 经保司/TPA 通道试点（非自建医保局网关），ReadOnly 旁路优先+幂等三件套 |
| C11 | 额度台账（控额内核，`bone-ai-ledger`） | **信任额度**：高信任客户免单证直赔的预授权/两阶段预留 | 产品侧保险责任/免赔/封顶语义留产品；但"信任额度"是域中立确定性内核，可直接复用 C11 两阶段预留（reserve/commit/release） | 域中立、可插拔；`available=limit-consumed-reserved-frozen`，未登记上限 fail-closed=0 |

> **复用纪律（ArchUnit 强制）**：产品域只经 `port` 调 `bone-ai-*`，禁 import 实现（工程落地版 §3.2.1）；依赖单向（AI 不碰产品域）。这与平台 §5 门禁一致，必须保留。

---

## 2. 平台断层（当前 synapClaim 设计 vs 企业级理赔要求）

| 断层 | 现状 | 后果 |
|---|---|---|
| RAG 推到二期 | `KnowledgeRetrievalPort` 标注"二期"；§7.3 注"一期无目录落地，仅 LLM 通用推理"；AI 技术方案 §6.4 "一期无 RAG 链路" | 临床合理性、目录匹配、条款语义匹配无法落地——正是"搭车收费/过度医疗/诊断-用药不匹配"渗漏主因 |
| 未接 C9/C10/C11 | 仅"挂起>72h 告警"顺带提 C9；C10/C11 全未出现在端口表 | 直付通道无三态灰度机制、信任额度无内核、反欺诈告警无统一触达闭环 |
| §7.2 安全三件套 vs C8 重复 | 自研"PII 遮蔽管道+数据槽位+交叉验证" | 与网关 `PiiRedactor`/`PromptInjectionGuard`/`ContentSafetyGuard` 双份实现，策略漂移、且漏接 ADR-1 内容安全默认开 |
| MRM 治理缺失 | 仅 300 条评测集 + 提及合规 | 监管总局《AI 安全开发应用指导意见》32 项 + 金发8号要求模型治理架构/漂移监控/偏差——平台 C7 无模型卡片/champion-challenger |
| 视觉反诈缺位 | Rulebook 仅 R-FRAUD-01~07（单证字段规则） | 翻拍/PS/跨案重复影像无法识别——国寿"识图"、泰康影像特征检索已实证为必备 |
| 图计算反欺诈缺位 | 无团伙网络/设备指纹/关系图谱 | 骗保团伙（占车险 90%+）是最大资金敞口；太保知识图谱+图计算（团伙准确率>60%）、泰康关系图谱+影像、众安图像+语音+关系图谱联合反诈已跑通 |
| GraphRAG 缺位 | C6 为 chunk 级检索，医学图谱留产品但无图遍历检索 | 临床合理性（诊断-用药-诊疗矛盾）需图谱推理，纯 chunk 检索召回不足 |

---

## 3. 底层智能能力要做的增强（及为什么）

### E1　视觉反诈能力（增强 C1 / 新建 C12-visual）
- **做什么**：影像指纹（perceptual hashing /  embedding 相似度）、PS/篡改检测（EXIF/重压缩痕迹/生成式伪造鉴别）、跨案件重复检索。
- **为什么**：单证上传范式是 PS 发票、重复报销、搭车收费的温床；平台 C1 只做 IR+OCR+脱敏回填，**无视觉判别**。国寿"识图"影像相似度识别百万级重复索赔已实证 ROI。
- **归属**：机制（指纹/篡改检测）进平台 C12-visual（跨域：理赔/采购凭证/财务票据通用）；**阈值与业务判定（同发票跨案）留产品**（R-FRAUD-08 已在 Rulebook 落地）。
- **信创/私有化**：模型私有化权重（禁云端），复用网关 C5 统一出口；推理部署独立 Deployment+HPA，与 OCR sidecar 同形态。

### E2　图计算反欺诈能力（新建 C12-graph）
- **做什么**：关系图谱（被保人-医院-医生-设备-账户多跳关联）+ 设备指纹 + 社区发现/团伙识别 + 一致性推理。
- **为什么**：理赔欺诈是团伙性、网络性重灾区（IAIS：全球 20–30% 赔款涉欺诈，我国约 20%；车险占 90%+）。现有 7 条规则只覆盖单案单维，无法识别"多案串联、多人同伙、设备共用"。太保/泰康/众安均以图谱把团伙识别准确率做到 >60%。
- **归属**：图存储+图计算引擎进平台 C12-graph（跨域反诈通用）；**图谱 schema 与团伙判定策略留产品**（R-FRAUD-09 已在 Rulebook 落地）。
- **信创/私有化**：图数据库选国产（**Nebula Graph / TuGraph / 华为 GraphBase**，满足信创）；禁 ES 第三套栈（与 RAG 选型纪律一致）。
- **接线**：作为决策图新节点 `FraudScreen`，Hit 即经 C9 发多级预警 + 挂起 `FRAUD`（工程落地版 §6.5）。

### E3　GraphRAG 医学图谱检索（增强 C6）
- **做什么**：在 C6 受控 RAG 之上增加**图谱遍历 + 混合检索**（实体-关系-属性查询 + chunk 召回融合），支撑"诊断→诊疗项目→药品→医保目录"一致性推理。
- **为什么**：临床合理性（诊断-用药不匹配、过度医疗）是医疗险渗漏主因；纯 chunk 检索召回不准，需图谱结构化推理。医学图谱本身是**产品资产**（整体方案 §2.2 明确留产品），平台只增强"图谱可检索"的机制。
- **归属**：GraphRAG 检索机制进 C6（端口扩展 `GraphRetrievalPort`）；图谱 schema/医学知识留产品（07-delivery/06 目录库 + 医学知识图谱）。

### E4　MRM 模型风险管理（增强 C7）
- **做什么**：模型卡片（Model Card）+ champion/challenger + 在线漂移监控（PSI/特征漂移）+ 公平性/偏差监测 + 可解释报告自动化。
- **为什么**：金融监管总局《银行业保险业人工智能安全开发应用指导意见》32 项 + 金发〔2026〕8号要求 AI 治理架构、数据治理、风险分级管控。平台 C7 有评测门禁但无"上线后持续治理闭环"。
- **归属**：MRM 治理闭环进 C7（模型卡片/漂移/AB challenger 为跨产品通用机制）；理赔特有偏差口径（如医院等级/渠道公平）留产品指标字典。

### E5　AiAssess 多模态语义理解（用法增强，依托 C5+C6）
- **做什么**：`AiAssess` 节点从"仅置信度打分器"升级为"非结构化材料语义理解层"——病历诊断抽取、条款语义匹配、医学合理性推理，输出 `AiSuggestion`（建议+证据引用+置信度）。
- **为什么**：2026 头部（平安 PingAnGPT 多模态、泰康数字员工、众安 AI 中台 70+ 机器人）已端到端；只当打分器低估价值且不符合"决策操作系统"定位（金额仍走代码，AI 升到理解层不破确定性边界——工程落地版 §4.1）。
- **归属**：不新增平台模块，是 synapClaim 对 C5（网关）+ C6（RAG）用法的深化（编排层 `AiAssess` 节点改造）。

---

## 4. 串联：把平台能力接入理赔全链路

### 4.1 增强决策图（5 节点 → 6 节点）

```
证据 → 解析 → 核责 → 理算 → 决策   （现状 5 节点）
                                       
增强为（V1.0 Phase B，不挤占 MVP I0–I4）：

EvidenceLoad (OCR,  C1)            → 材料解析 + 视觉反诈(E1 影像指纹/篡改) 入 R-FRAUD-08
   ↓
RuleCheck (RULE, C2)               → 核责 + R-DIR 目录匹配(经 C6 GraphRAG, E3) + 发票唯一性硬约束
   ↓
AiAssess (LLM, C5+C6+E5)          → 多模态语义理解(病历抽取/条款语义/临床合理性) → AiSuggestion(三档置信度)
   ↓
FraudScreen (图E2 + C9)            → 关系图谱+设备指纹+团伙识别(R-FRAUD-09/10/11) → 命中经 C9 多级预警 + 挂起 FRAUD
   ↓
Arbitrate (DECISION, C3)           → 确定性仲裁(R-DEC 优先级) + HITL 五态 → Confirmed
   ↓
Adjust (RULE, C2 + C11)            → 理算 + 信任额度两阶段预留(C11) → SettlementOrder
   ↓
直付通道 (C10 三态灰度)            → 医保/医院数据直连 Shadow→Assisted→Automated（V1.0 试点，经保司/TPA）
```

### 4.2 全链路接线与合规姿态

| 链路环节 | 平台能力 | 接线端口 | 合规/企业姿态 |
|---|---|---|---|
| 报案→材料 | C1 + E1 | `DocumentParsePort` + `VisualForensicsPort` | 私有化 OCR；视觉指纹入证据链 |
| 核责/目录 | C2 + C6(E3) | `RuleEnginePort` + `KnowledgeRetrievalPort`(+`GraphRetrievalPort`) | 引用强制/无依据不编造；ACL 默认 deny + 写入 PII 掩码 |
| AI 评估 | C5 + C8 | `AiGatewayPort`（PII 脱敏/注入检测/内容安全复用网关） | ADR-1 内容安全默认开（对外）；AI 不碰钱 |
| 反欺诈 | E2 + C9 | `GraphFraudPort` + `NotificationPort` | 团伙识别 → 多级预警 + 挂起 FRAUD |
| 裁决 | C3 + C4 | `OrchestrationPort` + `TracePort` | `AiSuggestion`→`Confirmed` 类型护栏；三元组快照回放 |
| 理算/额度 | C2 + C11 | `RuleEnginePort` + `LedgerPort` | 信任额度 reserve/commit/release；未登记 fail-closed=0 |
| 直付 | C10 | `IntegrationPort` | 三态灰度 + SSRF/HMAC + 幂等三件套 |
| 后督 | C7 + E4 | 评测流水线 + `ModelRiskPort`(MRM) | 自动通过/拒赔结论 100% AI 质检；模型漂移监控 |

### 4.3 端到端证据/可回放闭环
每个节点经 C4 `TracePort` 落 `claim_decision_trace`（输入哈希+IR+引擎版本三元组）；C5 网关每次真实触模发 `LlmCallRecorded`（失败也补发），响应对不上快照拒绝返回；C7 评测 + E4 MRM 形成"变更→回归→上线→漂移→回流"PDCA。

---

## 5. 企业真实场景契合点（落地约束）

1. **信创/私有化**：图库选 Nebula/TuGraph（国产）；OCR Docling/PaddleOCR（MIT/Apache）；模型 Qwen3.5/3.8-27B（Apache-2.0，已核定终局）；视觉/图模型权重私有化，禁云端辅路（与 RAG 数据不出域红线一致）。
2. **数据不出域**：网关 fail→确定性/人工（绝不 fail 到直连模型）；写入侧 PII 等长掩码（RAG 已落地）；RAG 嵌入/rerank 自托管；视觉/图推理同形态私有化部署。
3. **多租户**：平台 `tenant_id BIGINT` 硬隔离；非数值租户走 ADR-4 `TenantIdentifier` 字符串主键+映射层双轨（拒绝语义不变、理由从"非数值"改"未注册"）——支撑企业"单租户内多部门/多团队"共享形态。
4. **合规**：① 195 号文"辅助性定位"——AI 只出建议+证据，资金/责任/数值判定入口只收 `Confirmed`（C3 类型护栏已机械强制）；② 金发〔2026〕8号"可解释+可人工复核"——C4 证据链 + C3 HITL 五态；③ TC260/GB/T 45654——ADR-1 内容安全默认开 + 监看 + 测试题库（生成抽检≥1000 合格率≥90%）；④ 监管总局 AI 治理 32 项——E4 MRM 闭环。
5. **资金零差错**：金额计算/资金状态流转全在确定性代码（C2 规则 + 产品理算引擎）；AI 仅 `AiSuggestion`，C11 信任额度为域中立内核，未登记上限 fail-closed=0。

---

## 6. 已执行的文档优化（落点清单）

| 项 | 改动 | 落点 |
|---|---|---|
| **C6 RAG 提至 V1.0** | `KnowledgeRetrievalPort` 由"二期"改为"V1.0 Phase B"；R-DIR 目录匹配经 C6 落地 | 工程落地版端口表 + §7.3 注 + §8.3 R-DIR 落点 + AI 技术方案 §6.3/§6.4 |
| **接 C9/C10/C11** | 端口表新增 `NotificationPort`/`IntegrationPort`/`LedgerPort`，分别支撑反欺诈预警/直付三态/信任额度 | 工程落地版端口表 + 能力映射表 |
| **§7.2 安全三件套复用 C8** | 自研三件套改为"复用网关 `PiiRedactor`/`PromptInjectionGuard`/`ContentSafetyGuard`，ADR-1 内容安全默认开"，消除双份实现与策略漂移 | 工程落地版 §7.2 |
| **决策图扩 6 节点** | 新增 `FraudScreen` 节点（视觉 E1 + 图 E2 + C9 预警），R-FRAUD-08~11 由该节点承载 | 工程落地版 §7.3 + AI 技术方案 §6.3 |
| **后督 100% AI 质检** | `≥5%` 抽检 → 自动通过/拒赔结论 100% AI 质检，人工抽检补充 | 工程落地版 §7.6（对齐 PRD §7.3 已改） |
| **新能力登记** | E1 视觉反诈 / E2 图计算反欺诈 / E3 GraphRAG / E4 MRM 作为平台增强项登记，信创选型与接线端口标注 | 本主文档 §3 + 文档地图 T6 层 |

> **排期纪律（守住）**：E1/E2/E3/E4 与直付通道均为 **V1.0 Phase B 增量**，**不挤占 MVP I0–I4**（§3.3 容量矩阵未破）；反欺诈图/视觉与直付通道需另行架构评审与排期（参照 Gate 0 / 容量校验纪律）。

---

## 7. 风险与回退

- **图库/视觉模型引入运维税**：E1/E2 新增图数据库 + 视觉推理 Deployment——按"不为想象规模预付"纪律，先以 MVP 单证规则（R-FRAUD-01~07）兜底，Phase B 再接；图库选国产避免第三套栈。
- **RAG 规模**：C6 接医学目录前须完成 pgvector 索引化（R4，平台 §14 设计已闭环，实现待排期），否则 `max-scan` 3000 成业务硬顶。
- **MRM 不阻塞上线**：E4 模型卡片/漂移监控为上线后治理闭环，首版可先落"模型卡片+CI 负向探针"，champion/challenger 随灰度推进。
- **回退**：任一平台能力故障，synapClaim 决策图按 fail-safe 转人工（工程落地版 §4.1 安全兜底原则优先于 R-FAST 快赔）。

---

## 8. 下一步（可选）
1. 把 V1.0 Phase B 增量拆为可排期任务卡（人日/依赖/里程碑），重点 E2 图库选型 PoC + E1 视觉指纹基线。
2. 出 E4 MRM 指标字典与模型卡片模板（对接平台 C7 评测流水线）。
3. 细化 C10 直付通道三态灰度接入方案（保司/TPA 通道适配器 + ReadOnly 旁路 + 幂等三件套）。
