---
status: Active
layer: entry
truth-for: 文档族索引与导航入口（不承载变更史）
updated-on: 2026-11-12
---

# procuraAI 专题包

采购域（SRM/PMS）架构设计文档，以及 procuraAI「AI 智能风险审查与合同智能处理」**AI 子能力**的 PRD 与配套历史文档。

> **资产状态与冲突裁决真源**：[文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md)（旧族三态登记 / 八级裁决链 / 模块注册表 / AI 合规裁决）。

> **AI / 人工编码入口**：先读 [AGENTS.md](./AGENTS.md)（真源顺序 / 门禁命令 / 禁改清单）；历轮执行台账见 [CHANGELOG.md](./CHANGELOG.md)（**当时快照**）。
> **开发基线**：`detailed-design/` 六卷 + `采购AI-接口契约与数据字典-v2.md` + 契约工件为唯一开发基线，见 [开发基线与开发就绪清单.md](./开发基线与开发就绪清单.md)；旧 POC 族仅作历史背景。
> **AI 导航索引**：[llms.txt](./llms.txt) 是纯导航（真源 / 契约 / LLD / 归档分组链接），行为约束仍以 AGENTS.md 为准；开口项与决策状态见 [decisions/DECISION-REGISTER.md](./decisions/DECISION-REGISTER.md)。

| 文档 | 说明 |
|------|------|
| [采购总体架构方案.md](./采购总体架构方案.md) | **技术架构真源（v0.3）**：分层/模块、技术栈、部署单元映射、NFR·SLO、集成与事件 payload、ADR(MADR)、WBS、质量门禁；落地 BONE 底座并对标 Ariba/Coupa S2S、SAP MDG-S、MIRO(LIV)、Business Network。v0.3 修订见其 §0.1 |
| [采购领域架构设计方案.md](./采购领域架构设计方案.md) | **领域真源（v0.3）**：限界上下文/核心聚合（含 Supplier↔Vendor 角色分离）/生命周期与 Vendor 开通闸门/采购成本模型/供应商协同/跨域契约/工程治理/平台共建清单；procuraAI 作为 AI 子能力嵌入。v0.3 修订见其头部与 §12 |
| [采购域-BONE框架能力对齐与落地基线.md](./采购域-BONE框架能力对齐与落地基线.md) | **框架能力 / 落地约束真源（v0.3）**：BONE 能力三态矩阵（S 已实现 / D 规范承诺 / N 不存在）+ `file:line` 证据、SDK 持久化最小闭环、工作流能力校准、事件/集成/安全落地约束、门禁与负向探针、存量 `bone-app-purchase` 处置 |
| [采购AI-智能风险审查与合同智能处理-PRD.md](./采购AI-智能风险审查与合同智能处理-PRD.md) | 主 PRD（v0.9） |
| [采购AI-智能风险审查与合同智能处理-技术方案.md](./采购AI-智能风险审查与合同智能处理-技术方案.md) | 技术方案（v0.8.4，对应 PRD v0.9） |
| [采购AI-商业化卖点与FDE落地交付方案.md](./采购AI-商业化卖点与FDE落地交付方案.md) | 商业化与 FDE 落地交付（v0.4：+商业模式与定价） |
| [采购AI-企业项目立项评估.md](./采购AI-企业项目立项评估.md) | 企业项目立项评估（评审视角：价值/风险/路径/Go-No-Go） |
| [采购AI-方案包完整性评估与文档补齐计划.md](./采购AI-方案包完整性评估与文档补齐计划.md) | 方案包完整性评估与文档补齐计划（落地缺口 P0/P1/P2） |
| [采购AI-POC试点方案.md](./采购AI-POC试点方案.md) | POC 试点方案（D1：客户选择/样本/基线/闸门） |
| [采购AI-原型交互设计.md](./采购AI-原型交互设计.md) | 原型交互设计（D2：审查报告锚点/处置流/HITL） |
| [采购AI-接口契约与数据字典-v2.md](./采购AI-接口契约与数据字典-v2.md) | 接口契约与数据字典（D3-v2：字段级契约/核心表/事件/AI 合规，取代已移除的 D3-v1） |
| [采购AI-项目计划与排期.md](./采购AI-项目计划与排期.md) | 项目计划与排期（D4：WBS/里程碑/人力/依赖/风险） |
| [assets/procuraAI-业务闭环.svg](./assets/procuraAI-业务闭环.svg) | 端到端业务闭环图（标前/标中/标后） |
| [assets/procuraAI-技术架构.svg](./assets/procuraAI-技术架构.svg) | 技术架构图（三层防御 + HITL） |
| [assets/procuraAI-部署架构.svg](./assets/procuraAI-部署架构.svg) | 私有化部署架构图 |
| [assets/procuraAI-审查流水线时序.svg](./assets/procuraAI-审查流水线时序.svg) | 审查任务流水线时序图（含降级分支） |
| [assets/procuraAI-集成路线图.svg](./assets/procuraAI-集成路线图.svg) | 存量系统渐进式集成路线图（FDE 交付节奏） |
| [assets/procuraAI-采购域上下文地图.svg](./assets/procuraAI-采购域上下文地图.svg) | 采购域上下文地图 v0.3（Strategic DDD：共享内核/上下游/ACL、Supplier↔Vendor、Finance 裁决边界、平台三轨） |
| [assets/procuraAI-采购总体逻辑架构.svg](./assets/procuraAI-采购总体逻辑架构.svg) | 采购总体逻辑架构 v0.3（C4 容器视图：分层/模块/一期部署单元/独立 AI 运行时/平台底座/外部系统与跨域事件方向） |
| [assets/procuraAI-BONE能力对齐图.svg](./assets/procuraAI-BONE能力对齐图.svg) | BONE 能力对齐图 v0.3（三态实证 → 三轨分派 → 采购域一期落地与机器门禁） |
| [assets/procuraAI-关键链路时序.svg](./assets/procuraAI-关键链路时序.svg) | 关键链路时序 v0.3（三条 sequence：S2P 端到端 / 三单匹配跨域 Finance 裁决 / Outbox→Inbox→DLQ→Replay 补偿；总纲 §21.5） |

## 治理与专题文档

> 下表是上方资产索引中治理类文档的**导览子集**（按读者意图分组），不是第二份状态登记；四态与冲突裁决的唯一真源是 [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) §3。

| 文档 | 性质 / 管辖 |
|------|-------------|
| [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) | 治理真源：四态 / 八级裁决链 / 模块注册表 / AI 合规裁决 |
| [采购AI-接口契约与数据字典-v2.md](./采购AI-接口契约与数据字典-v2.md) | AI 子能力契约真源（D3-v2） |
| [跨域契约与外部域依赖.md](./跨域契约与外部域依赖.md) | Item / Inventory / Warehouse 只读依赖 |
| [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) | 分布式机制一期状态与启用条件 |
| [平台能力共建RFC.md](./平台能力共建RFC.md) | 统一待办聚合 schema / 跨域复用证据 |
| [质量属性场景与ATAM.md](./质量属性场景与ATAM.md) | 效用树 / 质量属性场景 / 敏感点与权衡点 |
| [架构师答辩包.md](./架构师答辩包.md) | 派生：JD→证据→状态总表 |
| [采购AI原生能力蓝图.md](./采购AI原生能力蓝图.md) | Proposal：G1–G16 能力地图（不进本期实现） |
| [AI接入判定与AI原生内核补充设计.md](./AI接入判定与AI原生内核补充设计.md) | Proposal：AI 接入四条件 / N1–N8 |
| [法源射程分层与自治边界-ADR.md](./法源射程分层与自治边界-ADR.md) | Proposal：法源两档治理 / 六条红线（待法务） |
| [开发基线与开发就绪清单.md](./开发基线与开发就绪清单.md) | 生效：唯一开发基线 + Sprint 0 就绪清单 |
| [AGENTS.md](./AGENTS.md) · [CHANGELOG.md](./CHANGELOG.md) | AI 协作合同 · 变更史（当时快照） |
| [llms.txt](./llms.txt) | AI 导航索引（纯索引，不承载约束与结论） |
| [decisions/DECISION-REGISTER.md](./decisions/DECISION-REGISTER.md) | 建议 → 决策四态登记与开口项清单（导航投影，不参与裁决） |

## 原有 POC 资产状态登记（8 份，状态列由 A10 守护）

| 文档 | 状态 | 裁决摘要 |
|------|------|----------|
| [采购AI-智能风险审查与合同智能处理-PRD.md](./采购AI-智能风险审查与合同智能处理-PRD.md) | 历史归档 | ADR-06 已撤销；AI 行为以总体 §5.1 为准 |
| [采购AI-智能风险审查与合同智能处理-技术方案.md](./采购AI-智能风险审查与合同智能处理-技术方案.md) | 受限引用 | 仅审查流水线/降级可作背景；DDL 与独立库结论不得引用 |
| [采购AI-商业化卖点与FDE落地交付方案.md](./采购AI-商业化卖点与FDE落地交付方案.md) | 受限引用 | 自动归档售前话术失效 |
| [采购AI-企业项目立项评估.md](./采购AI-企业项目立项评估.md) | 历史归档 | 阶段 0 立项记录 |
| [采购AI-方案包完整性评估与文档补齐计划.md](./采购AI-方案包完整性评估与文档补齐计划.md) | 历史归档 | 已被后续修订覆盖 |
| [采购AI-POC试点方案.md](./采购AI-POC试点方案.md) | 历史归档 | POC 阶段记录 |
| [采购AI-原型交互设计.md](./采购AI-原型交互设计.md) | 历史归档 | 现行 UI 以 05 卷为准 |
| [采购AI-项目计划与排期.md](./采购AI-项目计划与排期.md) | 受限引用 | 降为 POC 阶段排期参考，非 32 周真源 |

> 四态定义与完整裁决依据见 [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) §2/§3；当前**无「作废」文档**，A10 仍实现「作废文档不得被新族引用」判据。

## 模块详细设计（LLD v0.3，Proposal / 待架构评审冻结）

> 按 HLD 的 9 个 Maven 模块 + 存量适配层拆为 6 卷，采用统一 12 节模板（C4 L3 组件视图 / 字段级 DDL / 状态机 / OpenAPI / AsyncAPI / 负向探针 / DoD）。业界对标：SAP MDG-S 三层主数据治理、ANSI X12 850→855→856→810 协同链路、C4 L3 + arc42、API-first、SAP MIRO/LIV、Saga 隔离与补偿、Event Contracts / AsyncAPI、RFC 9457 / cursor、ISO 4217 Money 语义。
>
> **v0.3 真源细化**：总纲新增 **§23 逐卷可执行规格基线** 与 **附录 F 专题 / A9 判据索引**（业界锚点 B12–B17、待决 L38–L43）；6 卷各增 **§13 本卷专题深化**（13.1 差异化核心算法 → 13.8 测试金字塔），把 §14–§22 通用基线实例化为容量预算 / SLO 与告警 / FMEA 降级 / 上线四闸门与回滚代价 / 数据保留 / 只读投影新鲜度 / 质量门槛。**所有数值均为草案假设，冻结前不得写成承诺 SLA**；本轮不新增事件 / 错误码 / 权限码，不改已冻结业务结论。
> **v0.2 真源细化**：`detailed-design/00-总纲与统一约定.md` 是 LLD 统一约定与**附录 A–F 契约注册表 / 专题索引**真源；`detailed-design/contracts/openapi.yaml` 是 REST 契约机器真源；`detailed-design/contracts/asyncapi.yaml` 是事件契约机器真源（由 `contracts/generate_asyncapi.py` 从总纲附录 B 生成，**禁止手改**）；`contracts/generate_asyncapi.py --check` 做注册表 ↔ 工件零漂移校验。各卷 §7 只是速查视图，冲突以**总纲附录 B（生成源）**为准并重生成 AsyncAPI（裁决链见资产登记 §4.2 第 8 条，AsyncAPI 是派生物）。

| 卷 | 文档 | 覆盖模块 |
|----|------|----------|
| 00 | [总纲与统一约定](./detailed-design/00-总纲与统一约定.md)（v0.3） | 统一模板、分层命名、DDL 必备列基线、API/事件/权限/错误码规范、DoD；**§14–§23 深度基线**（§23 逐卷可执行规格）（Money/ISO 4217、Saga 隔离、schema 演进、RFC 9457/cursor、物理模型、可观测、SDL、状态机落点与时序图、冻结/RACI）；附录 A 错误码 / B 事件注册表 / C 权限码 / D 表清单 / E 资产索引 / F v0.3 专题与 A9 判据索引 |
| 01 | [供应商主数据模块详细设计](./detailed-design/01-供应商主数据模块详细设计.md)（v0.3） | `bone-procurement-supplier-mdm`：Supplier/Vendor/VendorActivation/LE/Site/Contact/Factory/SupplierItem/SupplyRelationship、Golden Record |
| 02 | [寻源与准入模块详细设计](./detailed-design/02-寻源与准入模块详细设计.md)（v0.3） | `sourcing` + `qualification`：RFx/密封 Bid/授标、背调、资质、准入审批 |
| 03 | [合同风险绩效与AI模块详细设计](./detailed-design/03-合同风险绩效与AI模块详细设计.md)（v0.3） | `contract` + `risk` + procuraAI 接入：CLM、CAPA、计分卡、AI fail-closed |
| 04 | [采购交易与成本模块详细设计](./detailed-design/04-采购交易与成本模块详细设计.md)（v0.3） | `transaction`：PR/PO/价格链/PPV/Savings/Transfer/RTV |
| 05 | [履约协同与门户模块详细设计](./detailed-design/05-履约协同与门户模块详细设计.md)（v0.3） | `fulfillment` + `portal`：Ack/ASN/GR/自助开票/IR 例外/三通道对账、B2B 门户 |
| 06 | [编排事件与平台接入详细设计](./detailed-design/06-编排事件与平台接入详细设计.md)（v0.3） | `app` 壳 + 存量 `bone-app-purchase` 适配层：Orchestrator、Outbox/Inbox/DLQ、Replay、HMAC、审计、门禁 |

### LLD 契约工件与资产

| 资产 | 职责 | 门禁 |
|------|------|------|
| [contracts/openapi.yaml](./detailed-design/contracts/openapi.yaml) | OpenAPI 3.1：六卷 §6 全量端点、Path Item 级路径参数、Money/ProblemDetails schema、`x-error-catalog`、`x-retry-policy`、`x-endpoint-adjudication` | A8 关键路径与错误码双向一致 + **A10.9 六卷 §6 ↔ 工件方法级双向全覆盖** |
| [contracts/asyncapi.yaml](./detailed-design/contracts/asyncapi.yaml) | AsyncAPI 3.0：附录 B 全量 29 事件的 channel + payload 视图 | A8 注册表 ↔ 工件零漂移 |
| [contracts/generate_asyncapi.py](./detailed-design/contracts/generate_asyncapi.py) | 从附录 B 单一真源生成 AsyncAPI；`--check` 零漂移 | A8 子进程校验退出码 |
| [assets/procuraAI-关键链路时序.svg](./assets/procuraAI-关键链路时序.svg) | §21.5 三条 sequence 与故障分支 | A3 SVG 可解析 / A8 各卷 §2·§9 引用 |
| [tools/check_procura_alignment.py](../../../tools/check_procura_alignment.py) | **A1–A10** + `--self-test` 全量注入式负向反证（条数以脚本输出为准） | 全套 + 自检双绿（**已接 ci.yml 四条 ProcuraAI* blocking + check.sh G0，D6 已闭合**；见 [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) §5） |
| [AI接入判定与AI原生内核补充设计.md](./AI接入判定与AI原生内核补充设计.md)（v0.3 / Proposal） | AI 接入四条件判定 / A·B·C 分级清单 / 统一端口裁决 / AI 原生内核 N1–N8 | **A10.19**（真源存在 + 四条件 + 三档 + N1–N8 + 红线不稀释） |
| [法源射程分层与自治边界-ADR.md](./法源射程分层与自治边界-ADR.md)（v0.3 / Proposal，待法务结论） | 法源射程两档治理 + 六条不可稀释红线 + 升级审批 | **A10.19**（两档 + 红线 + 三处真源引用） |
| [开发基线与开发就绪清单.md](./开发基线与开发就绪清单.md)（v1.0 / 生效） | 开发基线声明（LLD 六卷 + D3-v2 + 契约工件为唯一开发基线）+ Sprint 0 六项就绪检查清单（R-1~R-6 全 ✓ 才可启动编码）+ 开发阶段 P1 必备文档（D-1~D-5）+ 业界对标补强方向（C-1~C-4，待 F1–F4 验收后评审，不新增 Gx 卡片） | 人工核对清单（未注册机器门禁；登记入资产表 §3.1，依据业界诊断报告第五章） |

> **状态说明**：LLD 族为**设计提案**，不是已实现代码；字段、端点、事件须经架构评审冻结后才写入 `bone-init.sql` 与上游 §7.1 / §12.2。真源裁决以 [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) §4 的**八级裁决链**为准，LLD 冲突时回改本族。

## 定位

> **台账阅读约定（2026-11-10）**：各轮执行台账已迁至 [CHANGELOG.md](./CHANGELOG.md)，其中自检条数、CI 接入与提交状态均为**各轮当时快照**，不代表当前状态。当前判据状态一律以本文件「当前态证据」、[一期边界与机制触发条件.md](./一期边界与机制触发条件.md) §5 与 `ci.yml` 的 `ProcuraAI* (blocking)` 步骤为准；门禁 **A10.16** 守护陈旧表述不得回流（**D6 已闭合**）。

> **真源约定**：真源分层（三份域内真源 + 一份治理真源 + 平台约束真源 + AI 契约真源）以 [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) §4.1 为唯一权威口径；`SOURCE_DOCS` 仅 A1 校验范围、非真源总数；冲突一律按八级裁决链处理。

> **范围澄清**：本目录覆盖完整 SRM/PMS 领域（供应商主数据、生命周期、交易履约、跨域契约、工程治理、平台共建），procuraAI 是其中的 AI 风险 / 合同智能子能力，不是独立产品。历轮修订说明见 CHANGELOG。

对标国家发展改革委等八部门《关于加快招标投标领域人工智能推广应用的实施意见》（发改法规〔2026〕195号），聚焦企业采购侧（招标人/采购人视角）的 6 类 AI 场景落地：

招标文件检测 → 投标合规自查 → 智能辅助评标 → 中标合同签订风险提示 → 协同监管（履约预警）→ 智慧问答 → 投标响应（M6，借鉴内部投标小助手实践）→ 合同智能起草（M7，对标业界 CLM 趋势）。

> **195 号文口径（2026-10-14 对照原文；2026-11-11 逐字校准）**：目标为“2026 年底**三个具名重点场景**在**部分省市**实现全覆盖、2027 年底**更多重点场景**在全国范围内推广应用”（原文为「更多重点场景」，**不是**「全国推广 / 全域全覆盖」）；场景 11“中标合同签订”直接点名关键权利义务条款风险提示与“减少阴阳合同”，是本方案最直接的政策依据；发文对象含各中央企业，但对央国企为“因地制宜探索应用”的指导性要求，**无考核条款**。

## 与 Bone 平台的关系

procuraAI 为**采购域内的 AI 子能力**（**非独立可交付产品**；产品定位裁决见资产登记 §7），复用 Bone 的元数据建模、低代码 Runtime 与文档解析能力作为底座，不修改 Bone 核心（Clean Core 原则：稳定封箱不动，易变外置隔离）。仅 `procuraai-runtime` 因 Python + GPU 技术栈不匹配而独立部署（B20 认可的正当拆分理由）。


## 当前态证据

> **当前态证据清单（第十九轮起唯一真源）**：`gateEvidenceManifest: {"checks": 10, "criteriaMax": "A10.28", "indexCount": 9, "positiveControls": 6, "probes": 146, "seedCaseCount": 27}`
>
> 约定：以上各轮日志里的 `N/N` 数字均为**当时快照**，当前值一律以本行为准。本行由 A10.28 与 `--manifest` 的运行时真值逐字段比对（主判据数 / 反证数 / 正例数 / 判据上限 / 评测集例数 / 证据索引数）：写假、缺字段、真源扩容未同步、历史计数超过当前真值均转红；更新方式为 `--sync-manifest`（仅开发期显式调用，CI 不调用）。CI 日志由 `--self-test` 末尾的 `[MANIFEST]` 行留存机器可读证据。

> 当前值：主门禁 **10/10**、`--self-test` **146/146**、正例 6；评测集当前 **27 例 seed**（真源 `eval/ai-decision-evalset.json`，A10.25 守护）。历史轮次 `N/N` 见 CHANGELOG，均为当时快照。

## 变更史

20 轮执行台账与建议落点记录见 [CHANGELOG.md](./CHANGELOG.md)。本文件不承载过程记录，避免索引与变更史互相污染。
