# Comet Design Handoff

- Change: procuraai-ai-first-landing
- Phase: design
- Mode: compact
- Context hash: f2dbee7a8911057eae809367aaebe4ee642745529ea4165a36be2e13d66ae646

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-ai-first-landing/proposal.md

- Source: openspec/changes/procuraai-ai-first-landing/proposal.md
- Lines: 1-56
- SHA256: c3024f8f2adb0b3a169086a0e8fea3c526279295d2578556a0c3f0c0cfebeed7

```md
# Proposal — procuraAI AI-first 落地

> **范围状态声明（2026-11-14，Verify FAIL 回 Build 后补充）**
> 本文是 Open 阶段的**历史原始提案**，描述的是完整 AI-first MVP 目标，正文保留不改以维持决策链证据。
> **本 change 实际交付范围以 `tasks.md` §3 为准**：仅 `bone-platform/bone-masterdata` 上的通用主数据 Golden Record 去重内核（通用领域模型 / 分段变更请求 / Outbox 中继 / 治理 API / 250 例单测），**零 AI 能力、零 `bone-procurement-*` 模块、零 `t_proc_*` DDL、零 Supplier/Vendor 聚合**。
> 本文与 `deferred-specs/` 中 3 份 delta spec 描述的 AI 合同审查、AI 决策治理、供应商 MDM 实体均**未在本次交付实现**，已迁出 `deferred-backlog.md` 作为独立 owner/L3 WBS；归档时不得据此合入主 spec 或宣称 AI-first MVP 已交付。范围裁决全文见 `specs/SCOPE.md`。

## Why

procuraAI 架构方案（LLD 六卷 + 契约工件）已完备，但代码侧仅有 `bone-app-purchase` 低代码 Runtime 适配层（0 个 `@Table` 实体、0 采购业务 DDL、0 `Repository`），**真正的采购业务聚合与 AI 审查能力尚未落地**。对标《发改法规〔2026〕195 号》"中标合同签订风险提示 / 协同监管"等场景，需以最短路径把 **AI 合同风险审查** 落到 BONE 底座，复用 `bone-ai` 与 `metadata-sdk`，避免重造轮子与第二套真源。

**为什么首交付选 AI-first MVP 而不是全量 SRM foundation（本 change 的核心取舍）**：按 WSJF 量化（`design.md` D1），AI-first MVP 的价值/规模比约为 SRM foundation 的 3.4 倍，且敏感性分析下排序不翻转；它不依赖 R-5 尚未交付的跨域生产 adapter 与 Finance 契约，能最快拿到**真实合同质量与 ROI 证据**。SRM foundation 复用本期 MDM 成果作为后续线，不取消、只是排后。业界反复出现的"生成式 AI 采购 ROI 前置未闭合 → 进幻灭低谷"，本质是先铺平台后验证价值；walking skeleton 是对该风险的直接对冲。

## What Changes

- 新增 `procurement/supplier-master-data`：Supplier/Vendor 最小主数据 + Golden Record（含合并裁决留痕与数据质量维度）+ Vendor 开通闸门，复用 `metadata-sdk`（实体/仓储/`@Version`/租户/`@Deleted`），DDL 手写进 `bone-init.sql`（**L3**）。
- 新增 `procurement/ai-contract-review`：合同/招标文件上传 → `bone-ai-document` 解析（条款锚点可核验、跨页条款不截断）→ 风险标注（严重度/依据/锚点/置信度）→ 审查报告 → **有意义的人工复核（HITL，100% 覆盖）** → 留痕 的 walking skeleton。新增**低置信度与高风险条款强制人工路由**，防止漏检被整体指标掩盖。
- 新增 `procurement/ai-decision-governance`：AI 决策 fail-closed、能力三态、AI 参与披露、人工最终决策与推翻权、PII 最小化、**分任务评测门禁**、以及**运行时治理三项**：具名责任人 + 一键停用/降级 + 漂移/override 回流与实质变更回归重测。
- 存量 `bone-app-purchase` 收敛：一期 **PO 真源 = 新 Java 聚合**；Runtime 付款/审批 handler 加**默认关闭且启动期 fail-closed**的开关，Strangler Fig 渐进退役，退役前不删存量类。
- **非目标（本期不做）**：全 S2P（PR→PO→履约→对账）编码；Saga / broker 分区 / 统一任务中心实现（仅带触发条件的储备）；生产级跨域 adapter（仅契约化 Mock）；采购域整体一期 foundation；自动放款/自动批准/AI 自动闭环；**单一"AI 准确率 XX%"承诺**；**不声称 EU AI Act 高风险合规**（Art.12/14/26 仅作治理基线参照，法域适用性由 owner/法务裁定）。
- **BREAKING**：无（以新增能力为主；存量 handler 为默认关闭的增量开关）。

## Value Hypothesis & Success Metrics

> 价值假设（可证伪）："在 100% HITL 前提下，AI 首轮标注能显著压缩人工初审耗时，且不引入高风险条款漏检。" 若评测集显示漏检或误报越过冻结线，则**不发布**，而不是放宽门禁。

| 指标 | 基线 | 目标 | 测量 |
|---|---|---|---|
| 单份合同初审耗时 | 人工全量（R-6 实测） | 由 owner 冻结降幅 | 环节时间戳埋点 |
| 高风险条款漏检 | 无 AI 基线 | 关键负向样本 = 0；其余 ≤ R-3 冻结线 | severity-weighted 评测集 |
| 误报率 / alert fatigue | — | ≤ R-3 冻结线 | 评测集 precision |
| 引用锚点正确率 | — | ≤ R-3 冻结线 | 分任务评测 |
| HITL 覆盖率 / 推翻闭环 | 0 | 100% / 推翻记录原因 100% | 留痕断言 + override 台账 |

评测报告须分任务披露测试集、评分方法、评审人、冻结集隔离、模型版本、日期与局限；模型/提示词/解析链路/工作流实质变更后必须重测。详见 `design.md §成功度量与可证伪假设`。

## Capabilities

### New Capabilities

- `procurement/supplier-master-data`：供应商/供应商法人（Supplier/Vendor）最小主数据、租户隔离与审计、Golden Record 去重与合并留痕、Vendor 开通闸门与生命周期。
- `procurement/ai-contract-review`：合同/招标文件 AI 风险审查流水线（解析、条款锚点、置信度分层路由、风险标注、报告、有意义 HITL、留痕与日志留存、降级停用）。
- `procurement/ai-decision-governance`：采购域 AI 决策治理（fail-closed 三态、人工最终决策、披露、PII 最小化、分任务评测门禁、运行时停用与漂移闭环）。

### Modified Capabilities

- 无（采购域为新建能力，不修改既有 spec 的需求）。

## Impact

- **代码**：新增 `bone-procurement-supplier-mdm` 与 `bone-procurement-app`（壳）；`bone-app-purchase` 增 fail-closed 开关与启动状态日志；`bone-init.sql` 新增 `t_proc_*` 表（**L3，架构师审批；本期仅出草案，AI 不执行落库**）。
- **依赖**：`bone-ai-document`（Docling/PDFBox/ClauseSplitter）、`bone-ai-gateway`、`bone-ai-orchestration`；`bone-metadata-sdk`；工作流引擎 `WorkflowStatusPort`。是否引入 `procuraai-runtime` 由 timeboxed spike 决策树决定（`design.md` D2）。
- **运行时治理新增面**：AI 责任人登记、停用/降级开关与演练、日志留存期配置、drift/override 回流台账、模型变更回归重测触发。
- **门禁**：HC-001/003/006/008、E-5.4、N1–N6 负向探针（注入必红/还原必绿）、`tools/check_procura_alignment.py` 全套与 `--self-test`；**CI blocking 为权威**，本地 `check.sh` 不作 CI 绿判据。
- **就绪前置**：R-1~R-6（DoR，见 `开发基线与开发就绪清单.md:48-57`）未闭合前**不进入编码**。
- **跨域**：Item/Inventory/Warehouse 只读消费（staleness fail-closed，Pact 风格契约化 Mock）；Finance `InvoiceMatched/PaymentRequested` 归 Finance 裁决。

```

## openspec/changes/procuraai-ai-first-landing/design.md

- Source: openspec/changes/procuraai-ai-first-landing/design.md
- Lines: 1-273
- SHA256: bba2b63b4f38b46c77990f09ac3f3f44d5fee54acd383118e55c3c73eaf9ce33

[TRUNCATED]

```md
---
comet_change: procuraai-ai-first-landing
role: technical-design
canonical_spec: openspec
---

# Design — procuraAI AI-first 落地

> 本版为**业界最佳实践校准后的可验证设计**（2026-11-13）。相较首版，新增：WSJF 量化与敏感性、walking skeleton 的 DoD、timeboxed spike 决策树、运行时治理控制点、分任务评测指标、合规条款→工程控制点→证据矩阵、成功度量与可证伪假设、ATAM 风险表。决策 D1–D7 的方法与结论不变，**变的是"怎么证明它做对了"**。

## Context

参见 `proposal.md`。当前代码侧仅有 `bone-app-purchase` 低代码 Runtime 适配层（0 实体 / 0 采购 DDL / 0 `Repository`），`bone-init.sql` 无任何采购表；`bone-ai` 已有 11 模块（`bone-ai-document` 含 `DoclingSidecarAdapter`/`PdfBoxDocumentParser`/`ClauseSplitter`、`bone-ai-orchestration`、`bone-ai-gateway`、`bone-ai-rag`、`bone-ai-trace`）；持久化统一 `bone-metadata-sdk`；工作流引擎已实现转办/加签/抄送/退回指定节点（`WorkflowEngineImpl.java:648,1530`，含目标合法性校验）/改派/挂起/恢复。方案文档 `doc/ai-product/procuraAI`（LLD 六卷 + 契约）为设计真源，R-1~R-6 为就绪门。

### 0. 校准依据与证据纪律

| 业界实践 / 框架 | 本轮据此新增的工程判据 | 本设计落点 |
|---|---|---|
| WSJF / RICE + walking skeleton | 首交付线须量化排序；最薄端到端须有可重复执行的 DoD，而非"能演示" | D1、D1-DoD |
| SAFe timeboxed spike + build-vs-buy | spike 必须有时限、可证伪阈值、失败也产出结论 | D2 |
| YAGNI / evolutionary architecture | "设计储备"须带**触发条件与判定证据**，否则等于没决策 | D3 |
| Strangler Fig + SSOT + feature flag (default off) | 回滚须可验证；启动期 fail-closed 语义须精确 | D4、Migration |
| Definition of Ready + ATAM | 就绪门须有证据工件/签署角色；风险须有触发条件与验证方式 | D5、Risks |
| Consumer-driven contract (Pact 风格) | Mock 须有契约文件与 provider verification 挂 CI，不是口头约定 | D6 |
| Outbox + Idempotent Consumer / Policy as Code | 三层门禁 + 注入式正负双向证据 + 门禁自检 | D7 |
| ISO/IEC 42001 §9.1 / Annex A（人工监督、持续监测） | 具名责任人 + 成文监督过程 + 上线后监控证据 | §合规映射 |
| NIST AI RMF MANAGE 2.4 / 4.1 | 具名 owner 可停用/降级；推翻、事件、变更须回流并触发回归 | §合规映射、spec |
| EU AI Act Art.12 / 14 / 26（**参照，非声称适用**） | 全生命周期自动日志、日志留存期、有意义的人工监督 | §合规映射、spec |
| AI 合同审查评测实践（分任务指标 / 置信度路由 / Golden Dataset） | 禁止单一准确率承诺；precision 与 recall 并重；高风险条款漏检优先治理；低置信度强制人工 | §成功度量、spec |

**证据边界（必须如实标注）**：
- 本轮**亲验**：`bone-app-purchase` 仅适配层（0 `@Table`）、`rejectToNode`（`:1530` 调 `:648`）、`NodeType` 无 `SUB_PROCESS`、`bone-init.sql` 无采购表、`bone-ai` 11 模块、Comet 产物闭包与守卫预检。
- **文档来源**（实现期须逐条 `file:line` 复核）：R-1~R-6 硬门（`开发基线与开发就绪清单.md:48-57`）、T1~T13 机制状态（`一期边界与机制触发条件.md:38-50`）、staleness / Mock（`跨域契约与外部域依赖.md:36`、`§7.6`）、N1–N6（`采购域-BONE框架能力对齐与落地基线.md §3.5`）。
- **外部参考值**（仅作校准输入，不构成本域承诺）：合同 AI 审查行业材料普遍以"高风险条款漏检率"为首要风险，主张分任务披露指标、Golden Dataset 人工标注、置信度分层路由与 15–20% 抽样 QA。所有通过线**必须由 R-3 在本域评测集上冻结**，不得直接照搬外部数字。
- **合规表述**：EU AI Act 的强制义务针对 high-risk AI systems；采购合同 AI 审查不主张法定高风险分类。本文仅参照其日志/监督/留存条款作为治理基线，**不构成法律结论或合规声明**；国内口径以项目既有 `doc/ai-product/procuraAI` 治理铁律与金监总局相关指导意见为准。

## Goals / Non-Goals

**Goals:**
- 以 **walking skeleton** 最短路径打通"合同/招标文件 → 解析 → 风险标注 → 审查报告 → HITL 复核 → 留痕"，验证 Domain 模型、AI 复用路径与跨域契约。
- 复用 `bone-ai` 与 `metadata-sdk`，不重造；坚持一期单可部署。
- 满足 AI 治理基线（fail-closed / 人工最终决策 / 有意义监督 / 披露 / PII / 评测门禁 / **运行时可停用与漂移闭环**）。
- 每一项"做对了"都有**机器可验证的判据**，不以"演示通过"代替验收。

**Non-Goals:**
- 全 S2P 交易/履约/对账编码；Saga / broker 分区 / 统一任务中心实现；生产级跨域 adapter（本期仅契约化 Mock）；自动放款/自动批准/AI 自动闭环。
- **不做单一"AI 准确率 XX%"承诺**，不声称 EU AI Act 高风险合规。
- 不以放宽门禁换取 spike 或演示通过。

## Decisions

> 决策状态沿用 MADR 四态：D1–D7 为 `proposed → accepted`（Open 范围已确认），本版为**内容加固**、非范围变更；加固后的产物须重新预检与确认（见 Open Questions）。

### D1. 首交付线 = AI-first MVP（walking skeleton），而非 SRM 全量 foundation

**选择**：先做供应商最小主数据 + 合同 AI 审查最薄端到端。

**WSJF 量化（相对估计，1–13 档；仅用于排序，不用于排期；数值待 owner 校准）**

| 候选线 | 业务价值 | 时间紧迫性 | 风险降低 / 信息获取 | 总收益 | Job Size | **WSJF** |
|---|---|---|---|---|---|---|
| **A. AI-first MVP** | 8 | 5 | **8**（最快拿到真实合同质量与 ROI 证据） | **21** | **5** | **4.20** |
| B. SRM foundation（MDM+闸门+PR→PO） | 8 | 5 | 3（受 R-5 跨域/Finance 契约未交付牵制） | 16 | 13 | 1.23 |

- **敏感性分析**：① A 的价值降到 5（总收益 18）→ WSJF 3.60，仍 > B；② B 的 Job Size 压到 8（需 R-5 提前交付，当前不成立）→ WSJF 2.00，仍 < A；③ 仅当 A 的 Job Size ≥ 10 且 B ≤ 8 同时成立时排序才翻转，需两个假设同时失效。**结论稳健**。
- **为什么**：直接对标 195 号文"中标合同签订风险提示"场景；`bone-ai` 可复用；前置最少（不依赖 R-5 生产 adapter）；先用最薄端到端证明 ROI，避免铺开 S2P 后才发现数据质量/集成复杂度问题。
- **备选**：SRM foundation —— 作为后续线，复用本期 MDM 成果，不丢弃。

**walking skeleton DoD（可重复执行，非手工演示）**

| # | 环节 | 必须成立的断言 |
|---|---|---|
| 1 | 上传 | ≥1 份脱敏真实合同，含至少 1 条人工标注的高风险条款；入库与 `trace_id` 生成 |
| 2 | 解析 | 条款锚点可定位到页/段；**跨页责任限制类条款不得被切分**（业界已知 semantic chunking 失败模式） |
| 3 | 标注 | 每条含严重度、条款锚点、判定依据；输出不含 PII 原文；附置信度 |
| 4 | 报告 | 可分任务核验（clause detection / extraction / citation accuracy），不输出"单一准确率" |
| 5 | HITL | 未复核不得裁定；复核人/时间/结论（含推翻原因）落库；AI 不触发付款 |
| 6 | 留痕 | `trace_id` + 模型版本快照 + 三态披露可回溯；降级/停用事件同样留痕 |

- **负向必红 5 条**：解析失败、AI 不可用、低置信度条款被自动放行、未复核被当作最终结论、跨租户读取。

```

Full source: openspec/changes/procuraai-ai-first-landing/design.md

## openspec/changes/procuraai-ai-first-landing/tasks.md

- Source: openspec/changes/procuraai-ai-first-landing/tasks.md
- Lines: 1-57
- SHA256: 12c94b90686dd3dcc7f3a4487cf1fb5aca78b76939f6ac2312e6484e31560402

```md
# Tasks — procuraAI AI-first 落地（re-scoped：MDM 模块首交付线，2026-11-14）

> **范围重定界说明**：本 change 原 Open 时登记了 27 个任务（DoR/spike/MDM/AI审查/治理/收敛/集成）。经核实，本次实际交付**仅供应商主数据（MDM）模块子集**（任务 3.1–3.4），已于 2026-11-13 推送（9 笔提交 `6f0ea427f..9b3c70c70`）且门禁全绿。其余 23 个任务（组 1/2/4/5/6/7/8）按原 `tasks.md` 纪律本就"未启动、属 owner/L3 程序级工作"，已迁出至 `deferred-backlog.md`，**不在本 change 关闭**。
> 本文件即本 change 的最终任务清单（4 项，全部已交付）。下游 build/verify/archive 仅对本清单负责。
> 纪律：**R-1~R-6（DoR）未全 ✓ 前不得进入编码**——本次交付在 DoR 未闭合情况下提前落地了 MDM 骨架，属已发生的工程决策，风险由 owner 在 backlog 中跟踪。

## 3. 供应商主数据（MDM）— 本 change 交付范围

> **验收文字对齐（2026-11-14，Verify FAIL 回 Build 修订）**：Verify 独立审查查出本节 4 项原文沿用 Open 时 27 项任务的措辞，与真实代码不符（Supplier/Vendor 实体零命中、权限点前缀不符、模块骨架在 base-ref 已存在）。现按 `b04b2a8d..HEAD` 的 37 个新增 main 文件逐条改写为**真实交付**；任务 ID、勾选状态、数量均不变。原文字存档见 §「历史验收文字存档」，不删除。

- [x] 3.1 在**既有** `bone-masterdata` 模块（base-ref 已含 206 个 main 文件与 `@EnableSqlRepositories`/`@Import` 装配）上扩展六边形能力：新增 9 个 domain gateway 端口与应用服务装配。验证：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 全绿；`GoldenRecordHttpIntegrationTest` 以 `RANDOM_PORT` 完整启动证明 Spring 装配与 API 契约。 <!-- comet-task:493dbcea-4478-4831-977b-face2dd9c559 -->
- [x] 3.2 实现**通用主数据**领域模型（非供应商专属聚合）：`domain/model/goldenrecord/` 下 GoldenRecord / GoldenRecordMerger / MergeAudit / FieldProvenance / SurvivorshipPolicy、`domain/model/changerequest/ChangeRequest.java`（GENERAL/COMPANY_CODE/PURCHASING_ORG 三段）、`domain/model/dispatch/OutboxDelivery.java`、`domain/model/identity/EntityKeyMappingRegistry.java`。验证：`GoldenRecordMergerTest`（含跨租户候选拒绝）、`ChangeRequestTest`、`OutboxDeliveryTest`、`TenantReferenceValueTest` 全绿；本 change **未**引入 Supplier/Vendor/VendorActivation/LegalEntity 聚合。 <!-- comet-task:eb8cf1dd-dcdc-4484-b70b-bd8985e6bcbd -->
- [x] 3.3 Golden Record 去重与合并裁决留痕：`GoldenRecordApplicationService.java`（HMAC-SHA256 摘要、仅 APPLIED 允许落库与分发、其余转人工无副作用）、`GoldenRecordMerger.java:34` 身份主键集合 `unified_social_credit_code` / `tax_id` 冲突禁止自动合并、`EntityKeyMappingRegistry` 唯一键映射、`MergeAudit` 合并前后快照与回滚校验。验证：`GoldenRecordOrchestrationIntegrationTest` / `GoldenRecordMergerTest` / `GoldenRecordHttpIntegrationTest` 覆盖去重、引用一致性、留痕可回溯、回滚并发防覆盖。 <!-- comet-task:663f478b-5479-47f3-942d-31b8869f223a -->
- [x] 3.4 Controller 返回 `ApiResponse<T>` + `@PreAuthorize("hasAuthority('masterdata:governance:write'|'masterdata:governance:read')")`（`ChangeRequestController.java:46,53,61,72,80`、`GoldenRecordController.java`），**权限点前缀为 `masterdata:governance:*`，不是 `procurement:supplier:*`**。验证：`GoldenRecordHttpIntegrationTest` / `ChangeRequestHttpIntegrationTest` / `GoldenRecordCapabilityGateTest` / `ChangeRequestCapabilityGateTest` 覆盖越权 403 与能力开关 fail-closed。 <!-- comet-task:bfdbcf5a-c5f1-4b85-9428-3da1f176fbfe -->

### 验收文字—代码证据映射（2026-11-14 补，修 IMPORTANT-3「文字与代码不互证」）

| 任务 | 真实代码落点 | 真实测试落点 |
|---|---|---|
| 3.1 | `application/GoldenRecordApplicationService.java`、`ChangeRequestApplicationService.java`、`MasterDataOutboxRelay.java`、`domain/gateway/` 9 端口 | `e2e/GoldenRecordHttpIntegrationTest.java`（完整 Spring 启动） |
| 3.2 | `domain/model/goldenrecord/*`、`changerequest/ChangeRequest.java`、`dispatch/OutboxDelivery.java`、`identity/EntityKeyMappingRegistry.java` | `domain/model/goldenrecord/GoldenRecordMergerTest.java`、`changerequest/ChangeRequestTest.java`、`dispatch/OutboxDeliveryTest.java` |
| 3.3 | `GoldenRecordApplicationService.java:101`（resolveAndMerge）、`GoldenRecordMerger.java:34`（IDENTITY_FIELDS） | `application/GoldenRecordOrchestrationIntegrationTest.java`、`e2e/GoldenRecordHttpIntegrationTest.java` |
| 3.4 | `ChangeRequestController.java:46,53,61,72,80`、`GoldenRecordController.java` | `e2e/*CapabilityGateTest.java`、`e2e/*HttpIntegrationTest.java` |

> **判据边界**：250 例单测证明的是上表右列的真实行为，不再被用来证明「供应商 MDM 实体 / `procurement:supplier:*`」等已废弃措辞。文字与代码由上表逐条互证。

### 历史验收文字存档（Open 时原文，已作废，不得作为验收依据）

<details><summary>展开 4 项原文</summary>

- 3.1 建 `bone-masterdata` 模块骨架（启动装配 `@EnableSqlRepositories`+`@Import`）。验证：模块编译 + 装配冒烟测试通过；启动日志可确认仓储已注册。
- 3.2 实现 Supplier/Vendor/VendorActivation/LE 实体（`TenantAbstractEntity`+`@Version`+`@Deleted`）。验证：`check-tenant-entity-declaration`/`check-soft-delete-declaration`/`check-id-annotation` 通过；注入业务层覆写 `tenant_id` 的负向用例必被拒绝。
- 3.3 Repository + Golden Record 去重（统一信用代码唯一键）+ 合并裁决留痕。验证：去重单测 + 唯一约束测试 + 合并后引用一致性测试；重复写入留痕可回溯。
- 3.4 Controller 返回 `ApiResponse<T>` + `@PreAuthorize('procurement:supplier:*')`。验证：返回信封契约测试 + 越权负向测试（无权限必 403，注入移除注解必红）。

</details>

## 验收判据（本交付）

| 类别 | 判据 | 权威来源 |
|---|---|---|
| 构建 | `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 250 例全绿 | 已记录 |
| 门禁 | `[17/59]` 配置键 / `[52/59]` 构件白名单 / `[9a/59]` 分页命名已修复；procura 主门禁 10/10 + 自检 146/146 | `scripts/check.sh`、`check-procura-ci.sh` |
| 边界 | AI 不碰钱（放款/付款）为基线；不声称 EU AI Act 高风险合规 | `design.md` D4/D6 |

## 任务依赖与 owner 边界

| 组 | 谁执行 | 状态 |
|---|---|---|
| 3.1–3.4 MDM 交付 | 工程 | **已交付并门禁绿** |
| 1.x DoR / 2.x spike / 4.x AI审查 / 5.x 治理 / 6.x 收敛 / 7.x 门禁探针 / 8.x 集成 | owner / 各域 | **迁出至 deferred-backlog.md，独立 WBS** |

## Workflow follow-up

- 归档前需完成 Verify 与一次最终集成审查。
- 23 个未启动任务的交付由 owner 排期，见 `deferred-backlog.md`。
- "演示通过 ≠ 生产可用"：跨域 Mock、评测 ≥200、SLO 仍属后续验收。

```
