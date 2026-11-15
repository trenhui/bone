---
comet_change: procuraai-ai-first-landing
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-15-procuraai-ai-first-landing
status: final
---

# Design — procuraAI AI-first 落地

> **范围修订声明（2026-11-14，已由用户显式重新批准，SDK proposalHash `e414b3db…`）**
>
> 本文 D1–D7 及后续章节是 2026-11-13 的**原始完整 AI-first MVP 技术设计**，作为设计储备保留不改。但**本 change 的实际交付范围**以 `tasks.md` §3 与 `specs/SCOPE.md` 为准，二者不一致时以本声明裁决：
>
> - 实际交付仅为 `bone-platform/bone-masterdata` 上的**通用主数据 Golden Record 去重内核**（既有模块扩展 9 端口、通用领域模型、分段变更请求、Outbox 中继、治理 API、250 例单测）；**零 AI 能力、零 `bone-procurement-*` 模块、零 `t_proc_*` DDL、零 Supplier/Vendor 聚合**。
> - 本文描述的 AI 合同审查流水线、AI 决策治理、HITL 评测、评测集 ≥200 例、SLO 冻结、供应商 MDM 实体**均未实现**，属 `deferred-backlog.md` 的 23 项 owner/L3 WBS；3 份对应 delta spec 已全文未改迁出 `specs/` 至 `deferred-specs/procurement/`，本 change `deltaCount: 0`，归档不得把未实现需求合入主 spec。
> - 验收文字已按真实代码改写（`tasks.md` §3，含「验收文字—代码证据映射」表），权限点为 `masterdata:governance:*`；`application.yml` 摘要盐值已去 dev 占位默认值，由构造器在能力启用时 fail-closed。
> - **CI 绿 ≠ AI 可上线**；文档族 X1–X4 字段级契约为纯叙述 `DesignOnly`，不在 CI 三入口判据集合内。
>
> 本声明对应 Verify 独立审查 CRITICAL-1/CRITICAL-2/IMPORTANT-1/2/3 的修复轮，证据见 Verify r2 报告。

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
- **Exit**：脚本化 E2E 一次跑完 6 断言 + 5 负向，留痕可导出；**演示通过 ≠ 生产可用**（跨域 Mock、评测集 ≥200、SLO 仍属 S2 验收）。

### D2. AI 复用 `bone-ai`，以 timeboxed spike 决定是否引入 Python runtime

**选择**：用 `bone-ai-document` + `orchestration` + `gateway` 跑通真实合同审查，spike 结论决定运行时。一期坚持单可部署（12-factor）。

**Spike 契约（防止"跑到通过为止"）**

| 项 | 规定 |
|---|---|
| Timebox | **≤ 5 人日硬上限**；到期即出结论，不延期、不以"快好了"放宽 |
| 输入 | ≥3 份脱敏真实合同，覆盖：可解析 PDF、扫描件/表格、跨页条款；每份有人工 golden 标注 |
| 观测 | 条款边界召回、高风险条款漏检数、抽取字段正确率、引用锚点正确率、P95 解析延迟、失败模式清单 |
| 通过线 | **由 R-3 在本域评测集冻结**；spike 阶段只做"能否承接"的定性+观测判断，**不得自定通过线** |
| 交付 | spike 报告（含成功与失败样例、失败模式、成本对比），无论结论如何都必须产出 |

**决策树**

1. `bone-ai-document` 解析/切分与 `gateway` 编排均满足观测要求 → **不引入** `procuraai-runtime`，单可部署。
2. 解析/切分不足但编排、网关、降级可用 → **仅替换 document adapter**（ACL 内），仍不引入第二部署单元。
3. 关键能力缺失且确需 GPU / 独立 Python 栈 → **提 ADR**：补部署拓扑、运维归属、成本、数据出境/PII 边界、SLO 评审后方可引入；**属范围变更，需重新确认**。

- **理由**：存量已具备解析/编排/网关；过早引入第二部署单元与一期单应用单库冲突，且运维/合规成本前置。
- **备选**：直接引入 Python+GPU runtime —— 高风险、收益未证。
- **反模式**：spike 失败 → 放宽门禁 / 降低评测通过线。**明确禁止**。

### D3. 一期分布式机制最小集 + 储备项的触发条件

**选择**：仅 T1 本地事务 / T2 Outbox（**限跨单元必要事件**）/ T3 幂等 / T4 HMAC（外部 Webhook）/ T5 幂等四层启用；同库同进程不发事件。

| 储备机制 | 一期状态 | **触发才启用** | 判定证据 |
|---|---|---|---|
| T6 Saga | 设计储备 | 出现跨库/跨服务事务且本地事务被证明不可行 | ADR + 本地事务不可行证明 |
| T11 分区 | 设计储备 | 单表/单库容量触及 R-6 冻结 SLO 阈值 | 容量实测报告 |
| T13 统一任务中心 | 设计储备 | ≥2 域需统一待办 **且** 平台 `TaskCenterPort` 已交付 | 平台共建 RFC 落地 |

- **理由**：YAGNI + 演进式架构；把"储备"写成带触发条件与证据的判定，避免既没实现又说不清何时实现。WBS 中每机制须标注状态列。

### D4. 存量 `bone-app-purchase`：Strangler Fig + SSOT + fail-closed flag

**选择**：一期 PO 真源 = 新 Java 聚合；状态只经 `WorkflowStatusPort` 单出口；Runtime 付款/审批 handler 加默认关闭开关；存量类退役前不删改。

- **"启动期 fail-closed"精确语义**：开关**缺失、解析异常、显式非 true** 三态均拒绝启动或拒绝装配该 handler；不是"读取失败就当 false 继续"。启动日志须打印每个 handler 的生效状态。
- **PO 真源裁决**：存量 `PurchaseWorkflowSubmit/Decision/WithdrawHandler` 属 legacy 演示路径，不得写入新聚合的状态字段；新增聚合状态回写统一经 `WorkflowStatusPort`，避免两份采购真源。
- **分阶段退出**：Phase 0（本期）flag + 文档化边界 → Phase 1（二期）影子读比对 → Phase 2 退役（独立 WBS）。
- **回滚可验证三条**：① flag 关闭后新链路端点不可达；② 存量行为回归测试全绿；③ `t_proc_*` 为新增表，不参与任何存量 SQL（schema 向后兼容）。
- **备选**：直接迁移重写 —— 风险高、收益低，放弃。

### D5. 就绪门 = DoR，R-1~R-6 不闭合不编码；DDL 属 L3

| 门 | 证据工件 | 签署角色 | 机器校验 |
|---|---|---|---|
| R-1 架构冻结 | 评审记录 + HLD/LLD/D3-v2/OpenAPI/AsyncAPI 版本戳 | 架构评审组 | 版本戳存在且与资产登记一致 |
| R-2 DDL | `t_proc_*` 字段级 DDL 草案 | **L3 架构师审批；AI 只出草案，不执行落库** | `bone-init.sql` 未被 AI 直接修改 |
| R-3 评测 | 模型选型决策记录 + 评测集 ≥200（含对抗/偏见）+ 冻结通过线 | owner/算法 | 计数与通过线校验脚本 |
| R-4 数据分级 | 分级目录 + 标注规范 + 训练/验证/冻结集三分离 + override 回流状态机 | 数据 owner | 冻结集零污染校验 |
| R-5 跨域 ACL | Finance 契约、Item/Inventory/Warehouse 只读端口、门户排期确认 | 各域 owner | 对接确认记录 |
| R-6 NFR | 容量/SLO/告警数值冻结 | 架构 + SRE | SLO 文档进入验收判据 |

- **理由**：Definition of Ready + ATAM + fail-closed；任一未 ✓ 不进 Build，避免"先写后返工"。

### D6. 跨域只读 + staleness fail-closed；Mock 契约化

**选择**：Item/Inventory/Warehouse 只读消费，快照 `staleness` 时阻断需新鲜数据操作（`跨域契约与外部域依赖.md:36`）；`InvoiceMatched/PaymentRequested` 归 Finance 裁决。

- **Mock 不是口头约定**：一期以 **Pact 风格契约文件**固化请求/响应与 `staleness` 字段语义，provider verification 挂 CI；契约变更须双方签署。新鲜度阈值取自 R-6 冻结 SLO，不得硬编码在业务代码。
- **边界**：Mock 设计态可跑 ≠ 生产端到端；生产 adapter 与 Finance 契约交付为独立 WBS 行。

### D7. 门禁 Policy as Code + 注入式正负双向证据

| 层 | 权威度 | 内容 |
|---|---|---|
| **CI blocking** | **权威** | `ci.yml` 的 `ProcuraAI*` blocking 步骤 + `backend-quality` |
| 本地 `check.sh` | 参考（**CI 零调用**） | 本地/pre-commit，不得把"check.sh 绿"当"CI 绿" |
| 文档门禁 | 域内 | `tools/check_procura_alignment.py` A1–A10 + `--self-test` |

- N1–N6 六条负向探针逐条执行 **注入→必红→还原→必绿**，缺任一方向的证据不视为通过。
- **评测门禁自身须有负向自检**：注入关键负向样本必红、还原必绿；门禁恒绿视为失效。
- **变更门禁集合须同步三入口**（`.github/workflows/ci.yml` / `scripts/check.sh` / `check-procura-ci.sh`）与门禁常量，否则转红；本期不新增 CI 判据。

## 合规映射矩阵（框架条款 → 工程控制点 → 证据）

| 框架 / 条款 | 要求 | 本 change 工程控制点 | 证据工件 | 产物落点 |
|---|---|---|---|---|
| ISO/IEC 42001 §9.1 / Annex A（人工监督、持续监测） | 成文监督过程、具名角色、上线后持续度量 | 三态能力码 + 具名 AI 责任人 + 复核/推翻留痕 | 责任人登记 + 审计记录 | `ai-decision-governance` spec |
| NIST AI RMF MANAGE 2.4 | 具名 owner 可停用/脱离/降级未达标系统 | 一键停用开关 + 降级转人工 + 停用演练 | 停用演练记录 | 同上（新增需求） |
| NIST AI RMF MANAGE 4.1 | 上线后监控、申诉/推翻/事件/变更管理闭环 | drift 告警 + override 回流台账 + 模型/工作流实质变更触发回归重测 | 监控面板 + 回流台账 | 同上（新增需求） |
| EU AI Act Art.12 / Art.26（参照） | 全生命周期自动日志、日志留存 | `trace_id` 全链路留痕 + **留存期可配置** + 降级/停用事件入日志 | 日志留存策略与抽样回溯 | `ai-contract-review` spec |
| EU AI Act Art.14（参照） | 有意义的人工监督（理解、时间、权限） | 复核界面展示依据/条款锚点/置信度；有推翻权；不制造人为紧迫 | HITL 验收用例 | `ai-contract-review` spec |
| 项目治理铁律 / 金监总局口径 | AI 仅辅助、人工最终决策、AI 不碰钱 | 无自动批准/付款路径；100% HITL | 负向测试 | 三个 spec 共有 |

> 该矩阵是**工程控制点清单**，不是合规认证结论。法域适用性与高风险分类由 owner/法务裁定。

## 成功度量与可证伪假设

| 指标 | 基线 | 一期目标 | 测量方式 | 为什么是它 |
|---|---|---|---|---|
| 单份合同初审耗时 | 人工全量（R-6 实测） | 由 owner 冻结降幅 | 环节时间戳埋点 | 价值证明（ROI 而非 Demo） |
| **高风险条款漏检** | 无 AI 基线 | 关键负向样本漏检 = **0**；其余 ≤ R-3 冻结线 | 评测集（severity-weighted） | 漏检的法律/财务代价不对称 |
| 误报率 / alert fatigue | — | ≤ R-3 冻结线 | 评测集 precision | 只追 recall 会淹没复核人 |
| 引用锚点正确率 | — | ≤ R-3 冻结线 | 分任务评测 | 结论可审计的前提 |
| 人工推翻率 / 回流闭环率 | 0 | 推翻记录原因 = **100%** | override 台账 | 漂移闭环输入 |
| HITL 覆盖率 | 0 | **100%**，无例外绕过 | 留痕断言 | 合规红线 |

**明确禁止**：只披露一个"准确率"数字。评测报告必须分任务披露：测试集构成与来源、评分方法、评审人、**冻结集与训练/验证集隔离**、模型/提示词版本、评测日期与已知局限。模型、提示词、解析链路或工作流**实质变更后必须重测**（NIST MANAGE 4.1 的变更管理要求）。

## Risks / Trade-offs（ATAM 风格）

| 风险 | 敏感点 / 权衡 | 触发条件 | 缓解 | 验证方式 |
|---|---|---|---|---|
| 就绪门被绕过导致返工 | 速度 vs 返工成本 | 任一 R 未 ✓ 却出现编码任务勾选 | DoR fail-closed | 任务表前置断言 + 门禁扫描 |
| 双状态机 / 两份采购真源 | 存量兼容 vs SSOT | 存量 handler 写入新聚合状态字段 | 单出口端口 + flag 默认关 | 无双写测试 + 启动状态日志 |
| AI 运行时过早引入 | 能力 vs 运维复杂度 | spike 未出结论即新增部署单元 | D2 决策树 + ADR 门槛 | 部署拓扑评审记录 |
| **高风险条款漏检被整体指标掩盖** | 自动化率 vs 安全 | 只报整体准确率、不报分任务与 severity | 分任务指标 + severity-weighted 通过线 | 注入高风险漏检样本必红 |
| **HITL 退化为橡皮图章** | 效率 vs 有意义监督 | 复核界面不展示依据/无推翻入口 | Art.14 参照的三条件 | HITL 验收用例 + 推翻演练 |
| 运行时无停用/漂移闭环 | 上线前评审 vs 上线后治理 | 生产无具名 owner、无停用开关、模型变更不重测 | §合规映射三项控制点 | 停用演练 + 回归重测记录 |
| 跨域 Mock 被误当生产就绪 | 演示速度 vs 真实性 | Mock 用例通过即宣称端到端 | D6 契约化 + staleness 阻断 | 契约 provider verification + 陈旧快照阻断测试 |
| 假绿 / 门禁恒绿 | 检查成本 vs 可信度 | 门禁对注入负向无反应 | D7 双向证据 + 门禁自检 | 注入必红、还原必绿 |

## Migration Plan

1. 先闭合 R-1~R-6（DoR），证据工件归档。
2. 跑 D2 timeboxed spike，产出"是否引入 Python runtime"结论（不通过则停在单可部署）。
3. 建 `bone-procurement-supplier-mdm` + `bone-procurement-app` 骨架与 `t_proc_*` DDL 草案（**L3 审批后**方可落库）。
4. walking skeleton 按 D1-DoD 上线（AI 合同审查 + 100% HITL + 运行时停用/留痕）。
5. 存量 `bone-app-purchase` 开关默认关，Strangler Fig 渐进退役（Phase 0→2）。
6. **回滚验证**：flag 关闭 → 新链路不可达 + 存量回归全绿 + 新增 DDL 不影响存量 SQL。

## Open Questions（留给 owner，不擅自决定）

- R-3 各分任务通过线的**具体数值**与严重度权重（本设计只约束"必须冻结且分任务"，不代填数字）。
- 日志留存期、停用开关的具名 owner 与演练频率。
- 低置信度路由阈值与高风险条款的强制人工清单（须在本域样本上标定，不照搬外部值）。
- Spike 样本来源、脱敏与 PII 审批路径。
- `procuraai-runtime` 是否/何时引入（由 D2 决策树结论决定）。
- 采购域一期 DDL 与事件 payload 的最终冻结。
- **本版产物加固后的重新确认与阶段推进**（当前 Run `continuation.mode: wait`，守卫预检哈希须重取）。

## 附录 A — 本轮校准记录与校验结果（2026-11-13）

### A.1 校准来源（检索于 2026-11-13，二手来源仅作实践输入）

| 来源 | 提炼的实践输入 |
|---|---|
| Atlan《Runtime AI Governance vs Reviewing Models Before Deployment》 | EU AI Act Art.9/12/26 运行时日志与上市后监控；NIST MANAGE 4.1（监控/申诉/推翻/事件/变更）、MANAGE 2.4（具名 owner 可停用）；ISO 42001 审的是上线后治理是否持续运行 |
| Entrans《AI Governance Frameworks Compared》 | TEVV 全生命周期评测；人工监督需真实 override 能力；漂移/事件/第三方模型管理 |
| Tech Jack Solutions《HITL vs HOTL》 | EU AI Act Art.14 有意义的人工监督 = 能理解、有时间、有权限推翻；反对橡皮图章 |
| VISO TRUST《AI Model Audit Trails》 | 审计链四层：模型版本、决策日志、人工监督记录、数据溯源；ISO 42001 §9.1 持续度量 |
| Alice Labs《AI Contract Analysis 2026》 | 15–20% 抽样 QA；高风险条款与低置信度强制人工；错误日志回流；漏检率是首要风险 |
| Prophy《Experimental Assessment…》(5,247 份合同/12 名专家) | 高风险条款召回随严重度下降；FNRP / severity-weighted F1；三层 HITL |
| Talking Tree / LegalPDF《Accuracy Benchmarks》 | 单一准确率掩盖能力差异；分任务披露测试集/评分方法/评审人/日期；precision 与 recall 并重防 alert fatigue |
| Sequere《Legal contract review…》 | 语义分块拆断跨页责任限制条款；置信度阈值按条款类型标定 |

完整来源 URL、缺口→优化映射与证据边界见随附校准报告（Open 阶段守卫禁止项目内非 artifact 写入，报告存于项目外 `/tmp/procuraAI-落地优化执行方案-业界最佳实践校准版-2026-11-13.md`；阶段推进后应归档到 `outputs/`）。

### A.2 缺口 → 优化落点（14 项摘要）

| 首版缺口 | 本轮优化 | 落点 |
|---|---|---|
| WSJF 只是定性断言 | 评分表 A=4.20 / B=1.23 + 三项敏感性 | D1 |
| skeleton 只有环节清单 | 6 断言 + 5 负向必红 + 脚本化 E2E；演示 ≠ 生产 | D1-DoD |
| spike 无时限/失败判据 | ≤5 人日硬上限 + 三分支决策树；禁降通过线 | D2 |
| 储备机制无触发条件 | T6/T11/T13 各带触发与判定证据 | D3 |
| fail-closed 语义模糊 | 三态拒绝装配 + 启动日志 + 回滚三条 | D4 |
| DoR 只有口号 | R-1~R-6 证据工件/签署角色/机器校验 | D5 |
| Mock 口头约定 | Pact 风格契约 + provider verification 进 CI | D6 |
| **只做上线前评审** | 具名责任人 + 停用/降级 + 演练 + drift + 回流 + 变更重测 | 治理 spec |
| **评测门禁过粗** | 分任务 / severity-weighted / 关键漏检=0 / 冻结集隔离 / **门禁自检恒绿即失效** | 治理 spec |
| **HITL 可能橡皮图章** | 三条件 + 低置信度与高风险条款强制人工 | 审查 spec |
| 未防条款切分失败 | 跨页条款不截断；引用不成立即转人工 | 审查 spec |
| 主数据无质量维度 | DAMA 四维 + 合并留痕 + 审计列不可覆写 | 主数据 spec |
| 无价值假设 | ROI 假设 + 6 项可证伪度量；禁单一准确率 | proposal / §成功度量 |
| 风险表无触发/验证 | ATAM 风格 8 条 | §Risks |

### A.3 实跑校验

| 校验 | 结果 |
|---|---|
| `comet state artifacts procuraai-ai-first-landing --json` | `exitCode 0`，`problems: []`，无 issues — **闭包通过** |
| `comet classic openspec … status` | `proposal / specs / design / tasks` 全部 `done`；`applyRequires: ['tasks']` |
| 任务数 | `grep -c '^- \[ \]'` = **27**（加固前后一致）；`planMapping.status: ready, unmappedCount: 0` |
| 产物语言 | `configuration.language: zh-CN` |
| **守卫预检** | `exitCode 1`，`CLASSIC_COMMAND_FAILED: Classic SDK has a running Action; wait for its original executor` — **在途 claim 阻断，非产物问题** |
| 项目内 `outputs/` 与新增 md | Open 阶段守卫拒绝 `source writes`；**未绕过**，完整报告落项目外 |

**阻断说明**：上一轮 `guard open --apply` 派生的 `full.open.revalidate`（`actionId: procuraai-ai-first-landing:4`）仍为 `running`，`continuation.mode = wait` / `waitReason = executor`。按自动衔接协议，确认原执行已停止之前不得标记 unknown 或重新派发，因此本轮**未**伪造收据、未标记 stopped、未重派、未推进阶段。旧 `approvalHash 1cba4209…` 绑定加固前内容，产物变更后必须重新预检取新哈希，不得沿用。

### A.4 下一步

1. 重新确认加固后的产物（验收标准已变，流程硬门，不代为确认）。
2. 让原执行器在可回执的终端完成 `full.open.revalidate`；或由用户明确授权、在确认原执行已停止后清理该孤儿 claim（仅本 change）。
3. 重跑 `comet guard procuraai-ai-first-landing open --json` 取新 `approvalHash`，确认后应用守卫推进 Design。
4. 按 `tasks.md` 第 1 组推进 R-1~R-6 DoR；任一未 ✓ 不进编码。
