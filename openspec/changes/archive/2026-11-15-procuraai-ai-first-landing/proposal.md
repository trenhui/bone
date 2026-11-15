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
