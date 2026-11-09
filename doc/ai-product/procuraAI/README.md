# procuraAI 专题包

采购域（SRM/PMS）架构设计文档，以及 procuraAI「AI 智能风险审查与合同智能处理」**AI 子能力**的 PRD 与配套历史文档。

> **资产状态与冲突裁决真源**：[文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md)（旧族三态登记 / 八级裁决链 / 模块注册表 / AI 合规裁决）。

| 文档 | 说明 |
|------|------|
| [采购总体架构方案.md](./采购总体架构方案.md) | **技术架构真源（v0.3）**：分层/模块、技术栈、部署单元映射、NFR·SLO、集成与事件 payload、ADR(MADR)、WBS、质量门禁；落地 BONE 底座并对标 Ariba/Coupa S2S、SAP MDG-S、MIRO(LIV)、Business Network。v0.3 修订见其 §0.1 |
| [采购领域架构设计方案.md](./采购领域架构设计方案.md) | **领域真源（v0.3）**：限界上下文/核心聚合（含 Supplier↔Vendor 角色分离）/生命周期与 Vendor 开通闸门/采购成本模型/供应商协同/跨域契约/工程治理/平台共建清单；procuraAI 作为 AI 子能力嵌入。v0.3 修订见其头部与 §12 |
| [采购域-BONE框架能力对齐与落地基线.md](./采购域-BONE框架能力对齐与落地基线.md) | **框架能力 / 落地约束真源（v0.1）**：BONE 能力三态矩阵（S 已实现 / D 规范承诺 / N 不存在）+ `file:line` 证据、SDK 持久化最小闭环、工作流能力校准、事件/集成/安全落地约束、门禁与负向探针、存量 `bone-app-purchase` 处置 |
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

## v0.3 收口修订（2026-11-09，依据全方案独立诊断 R1–R14）

> 诊断报告见 `outputs/procuraAI-全方案独立诊断与业界校准-2026-11-09.md`（评估类，不进文档族）。本轮**只做收口与补齐，不推翻已冻结业务结论、不写 DDL、不改 CI**：

| 建议 | 落点 |
|---|---|
| R1 / R13 资产三态登记 + 模块注册表 | [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) |
| R2 AI 合规裁决（撤销 ADR-06） | 同上 §5 + 旧 PRD / 旧 SVG 就地更正 + `03 卷 §11` M3-13 |
| R3 D3 降级 + D3-v2 + 唯一 payload | [采购AI-接口契约与数据字典-v2.md](./采购AI-接口契约与数据字典-v2.md) |
| R4 事件真源收敛 | 总体 §7.1 补齐 4 条冻结事件契约 |
| R5 拆自指环 + 门禁外部锚点 | 总纲 §16.2 / 附录 B + 门禁 A8 上游比对（CI 接入仍待 D6） |
| R6 一期边界与重试分层 | [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) |
| R7 WBS 重估 / R8 跨域契约 | 总体 §12 / [跨域契约与外部域依赖.md](./跨域契约与外部域依赖.md) |
| R9 / R10 / R12 | [平台能力共建RFC.md](./平台能力共建RFC.md) · [质量属性场景与ATAM.md](./质量属性场景与ATAM.md) · [架构师答辩包.md](./架构师答辩包.md) |
| R11 版本戳 / R14 探针覆盖 | 契约工件 `0.3.0`、SVG v0.3、门禁 A10 与全量负向反证 |

> **不做**（诊断 §6.4）：不删六卷 §13、不删旧文档、不废门禁、不提前写 `bone-init.sql`、不改 `.github/workflows/`。

## v0.3 续诊执行（2026-11-09，依据续诊报告 O1–O7）

> 续诊报告见 `outputs/procuraAI-全方案独立诊断续诊-R1R14收口后-2026-11-09.md`。本轮对 7 项建议分级执行：**可文档+工程闭合项真实落地**，**owner 边界项给可复制片段 + 诚实标注（不替 owner 拍板）**。

| 建议 | 性质 | 落点 / 状态 |
|---|---|---|
| **O1** 闭合事件 payload 空壳 | 契约完整性（真实闭合） | D3-v2 新增 §2.1 核心/INBOUND 事件字段级 schema（6 事件 + §2 的 `ContractReviewed`，合计 51 字段）；生成器 `generate_asyncapi.py` 渲染 `payload.properties`；门禁 **A8.6** 守护非空壳 + 负向反证（55/55 含新增） |
| **O4** 冻结 RA-2 多租户可见性矩阵 | 安全基线（草案冻结） | 总体 §9.1 + [跨域契约与外部域依赖.md](./跨域契约与外部域依赖.md) 升级为冻结基线（版本戳 + 审批签名栏，待签） |
| **O2** L38–L43 数值冻结流程化 | owner 边界（草案估算 + 冻结模板） | 质量属性场景与ATAM / 各卷 §13.3 填工程估算草案值 + 冻结流程模板（owner 签名行占位，待压测回溯） |
| **O3** D6 门禁接 CI | owner 边界（可复制片段） | [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) §5 片段落地为 `scripts/check-procura-ci.sh`（待平台组粘贴到 `check.sh`/`ci.yml`，不擅自改 CI） |
| **O5** P-RFC-1 闭环骨架 | owner 边界（署名/接入模板） | [平台能力共建RFC.md](./平台能力共建RFC.md) 补 RFC Owner 署名区块 + 跨域接入证据模板（标注待会签/待真实接入） |
| **O6** ATAM 排序会议程 | owner 边界（预排序草案） | [质量属性场景与ATAM.md](./质量属性场景与ATAM.md) 补正式排序会议程 + H/M 预排序表（待真实 stakeholders 确认） |
| **O7** 工程落地最小切片骨架 | 设计态（骨架，不建真实模块） | [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) 附录：Supplier MDM + VendorActivation + 1 条 PO 链路切片（目录树/实体/端口/事件/DDL 草案/handler 骨架，标注 Proposal） |

> **边界**：O2/O3/O5/O6 的「数值冻结」「CI 授权」「跨域接入证据」「排序会」属决策/授权/真实 stakeholder，文档只给片段 + 诚实标注，不伪造；O7 不擅自在 `bone-apps/` 建真实模块目录（避免越界污染主仓）；全族仍为 **Proposal 设计态**。

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
| [tools/check_procura_alignment.py](../../../tools/check_procura_alignment.py) | **A1–A10** + `--self-test` 全量注入式负向反证（条数以脚本输出为准） | 全套 + 自检双绿（CI blocking 接入为待决 D6，见 [一期边界与机制触发条件.md](./一期边界与机制触发条件.md) §5） |

> **状态说明**：LLD 族为**设计提案**，不是已实现代码；字段、端点、事件须经架构评审冻结后才写入 `bone-init.sql` 与上游 §7.1 / §12.2。真源裁决以 [文档资产登记与真源裁决.md](./文档资产登记与真源裁决.md) §4 的**八级裁决链**为准，LLD 冲突时回改本族。

## v0.3 续诊执行台账（O1–O7，2026-11-09 落地）

> 续诊报告的 7 条建议已按「能落地的真实落地、需要 owner 决策或交付的只给可复制片段并如实标注」的原则全部处置。当前门禁 A1–A10 全绿、自检 58/58、AsyncAPI 29 条零漂移、CI 脚本本地双绿。

| # | 建议 | 落点（file:section） | 验收 / 门禁守护 | 边界说明（不伪造、不越权） |
|---|------|----------------------|------------------|----------------------------|
| **O1** | 关闭 payload 空壳 | `D3-v2 §2.1`（6 核心事件 + ContractReviewed 共 58 字段）+ `asyncapi.yaml`（真实 properties）+ 生成器 | 核心事件 payload 真实 properties；门禁 **A8.6** 注入空壳→转红 | 已真实闭合，无边界项 |
| **O2** | L38–L43 数值冻结流程化 | 总纲 `§22.4` 单条冻结模板 + 跨卷冻结状态台账（owner 签名 / 压测链接占位） | 模板 + 台账就位 | 「门禁校验关键 SLO 不再标草案」属 R-2 owner 项（L38 冻结后启用），不伪造压测结果，当前仍标草案 |
| **O3** | D6 门禁接 CI | `scripts/check-procura-ci.sh` + 一期边界 `§5` 片段 | 脚本本地实测双绿 | 不擅自改 `ci.yml` / `check.sh`；是否 blocking 由平台组（D6）拍板 |
| **O4** | RA-2 可见性矩阵冻结 | 总体 `§9.1` Frozen-Draft v0.1 + 四角色行 + 会签栏占位 + 门禁 **A10.10** | 矩阵带版本戳 + 会签栏 + 四角色；注入抹除→转红 | 真实审批签名待 owner，未签=未生效 |
| **O5** | P-RFC-1 闭环 | `平台能力共建RFC.md §5.1` Owner 署名区块 + 跨域接入证据记录表 | 模板就位 | 实名署名 + ≥1 真实跨域接入 PR 待 owner / 跨域，不伪造 |
| **O6** | ATAM 排序会 | `质量属性场景与ATAM.md §7` 议程草案 + H/M 工程预排序 + 签字栏 + 门禁 **A10.11** | §7 含议程 + 预排序 + 待确认标注；注入删节→转红 | 最终优先级由真实 stakeholders 在评审会确认，不替 owner 拍板 |
| **O7** | 工程落地最小切片 | 一期边界 `§7` 骨架（目录树 / 实体 / 端口 / 事件 / `t_proc_` DDL 草案 / handler）+ 门禁 **A10.12** | 骨架含 `t_proc_` DDL + Proposal / 不写 `bone-init.sql` 标注；注入删节→转红 | **Proposal 不建真实模块、不写 `bone-init.sql`、不接 CI**；真实 DDL/handler/CI 红绿属 owner 执行（R-7），不伪造「已落地」 |

> 数值冻结、CI 接入、跨域接入证据与排序会这几项（R-2/R-3/R-5/R-6）以及 O4 的审批签名，本质上是需要决策、授权或真实交付的事项；文档只给出待决项和可复制片段，并如实标注边界。本次改动未提交、未改 `ci.yml`、未写 `bone-init.sql`、未废门禁。

## v0.3 诊断修复执行台账（C-A~C-F / F-E / AI-native，2026-11-09 落地）

> 依据逻辑自洽诊断的 P0/P1/P2 与 AI-native 建议执行；新增门禁 A10.3.1（C-B）、A10.4.1（C-A），各配自检反证。当前门禁 A1–A10 全绿、自检 60/60、AsyncAPI 29 条零漂移、CI 脚本本地双绿。

| # | 建议 | 落点（file:section） | 验收 / 门禁守护 | 边界说明 |
|---|------|----------------------|------------------|----------|
| **C-A** | `InvoiceMatched` 三处 payload 漂移 | 总体 §7.1 L306 + 04 §7 + 05 §7 全部对齐 D3-v2 §2.1（`match_id/po_no/match_status/matched_amount/exception_code/matched_at`，删除 `match_type/tolerance_applied/blocked_reason`） | 门禁 **A10.4.1** 校验总体 §7.1 字段↔D3-v2 §2.1；注入漂移→转红 | 已真实闭合三处副本；`tolerance_applied/blocked_reason` 标注为 Finance 侧内部回传、不在事件 payload |
| **C-B** | `ai_available` 类型自相矛盾 | D3-v2 §2 L58 由 `boolean` 改 `enum(AVAILABLE/UNAVAILABLE/UNKNOWN)`，与 §3/03 卷/总纲统一；`asyncapi.yaml` 同步重生成 | 门禁 **A10.3.1** 校验三态枚举；注入 boolean→转红 | 已真实闭合；`UNKNOWN` 不得压成「未参与」 |
| **C-C** | AI 能力码双轨撞名 | D3-v2 §1.1 新增「AI 能力码 ↔ 采购模块码」显式映射表（M1/M2/M4/M5/M7 ↔ M02/M04/M10） | 文档就位，消除悬空 | 已真实闭合，无边界项 |
| **C-D** | 分期泄漏（一期消费二期事件） | 01 §7 `RiskAlertRaised` 标「二期事件，一期接口预留、运行期不触发」；01/04/05 §7 的 INBOUND 事件标「依赖跨域 ACL 交付（F-A），未交付前接口预留、运行期不触发」 | 文档诚实标注 | 跨域 ACL 交付（F-A）仍属 owner，未伪造运行期触发 |
| **C-E** | INBOUND 字段名微观不一致 | 05 §7 `GoodsReceived`（`po_line_no/received_qty`+`site_id/tenant_id/supplier_id`）、`InvoiceSubmitted`（`supplier_id`）；04/05 §7 `InvoiceMatched`/`PaymentRecorded` 字段名对齐 D3-v2 §2.1 | 文档就位 | 已真实闭合 |
| **C-F** | 冻结态标记失真 | 02 §8 / 03 §8 把 procuraAI 由「① 复用 S」改「③ 待 D3-v2 冻结」 | 文档诚实标注 | AI 契约真源为 Proposal，未冻结不得标 S |
| **F-E** | 存量 `PurchaseOrderPayHandler` 硬隔离 | 能力基线 §7.2 约束 #3 加显式禁用开关（`procura.purchase-pay-handler.enabled=false` 默认）+ 启动期 fail-closed | 文档就位 | 代码开关属存量模块，须 owner 落地；当前仅文档约束，不伪造代码已改 |
| **AI-native** | G1–G10 能力地图 + 统一 AI 编排层 | 新增 `采购AI原生能力蓝图.md`（Proposal）：G1–G10 能力/why/落点/阶段 + 编排层架构 + 增量落地 + 全量受 D3-v2 §3 治理 | 文档就位 | **Proposal 不实现**；实现需逐能力立项 + 过评测集 + owner 排期，不替 owner 拍板 |

> 跨域 ACL 交付、SLO 冻结、CI 接入、门户新建（F-A~F-D）是硬前置，属于 owner 职责，本轮仅如实标注、未执行。诊断报告为评估类，落在 `outputs/`；本次修复是文档与门禁层面的，未写 `bone-init.sql`、未废门禁。

## 定位

> **范围澄清（2026-11-08）**：本目录原有方案聚焦"智能风险审查与合同智能处理"独立智能体（AI 子能力，已到 POC）。JD 要求的职责覆盖**完整 SRM/PMS 领域**（供应商主数据、生命周期、采购交易履约、跨域契约、工程治理、平台能力）。新增 [采购领域架构设计方案.md](./采购领域架构设计方案.md) 与 [采购总体架构方案.md](./采购总体架构方案.md) 两份设计，将 procuraAI 降为其中的"AI 风险/合同智能"子模块，并在供应商主数据（含 Supplier↔Vendor 角色分离）、生命周期与 Vendor 开通闸门、交易履约与采购成本、供应商协同、跨域事件契约、工程治理模式、平台能力复用+共建七个维度补齐完整设计。
>
> **真源约定（v0.3 收口，计数口径见资产登记 §4.1）**：**三份域内真源** = `采购领域架构设计方案.md`（领域语义）、`采购总体架构方案.md`（技术架构）、`采购域-BONE框架能力对齐与落地基线.md`（框架能力实证与落地约束）；**一份治理真源** = `文档资产登记与真源裁决.md`（资产状态 / 裁决链 / 模块注册表）。另有**平台约束真源** = `doc/architecture/Bone-DDD-最终实践方案.md`、`Bone-消息与事件规范.md`，**AI 契约真源** = `采购AI-接口契约与数据字典-v2.md`（D3-v2，取代已归档的 D3）。四份互不宣称「唯一 SSOT」；`门禁 SOURCE_DOCS` 的 3 份只是 A1 版本头校验范围，非真源总数。冲突一律按资产登记 §4.2 八级裁决链处理。
>
> **v0.2 修订（2026-11-08）**：依据独立诊断与业界校准，补齐 Vendor 建模、把三单匹配改为 Finance 裁决 + 最终一致、明确一期部署单元、补全采购成本模型与供应商协同、平台能力改「复用+共建」双轨、补 NFR·SLO 与 WBS 验收、修正门禁引用与 `contract_no` 语义。诊断/评估类报告按项目惯例落 `outputs/`，不进本文档族。
>
> **v0.3 修订（2026-11-08）**：基于 BONE **源码级能力实证**校正「纸面复用」——WF 引擎 README 的 v1 缺口清单已过时（退回/改派/转办/加签/抄送均已实现，真缺口只剩子流程与 SLA），撤回 4 条无谓降级；纳入仓库已有 `bone-app-purchase` 适配层；改为「复用 / 共建 / 自建」三轨；纠正审计总线、`RuntimeAccessGuard`、`QueryPort`、`@TenantScope`、`bp_processed_event` 等 6 类能力名称与状态误用；门禁改为 **CI blocking 与本地 `check.sh` 分离**表述。新增文档族机器门禁 `tools/check_procura_alignment.py`（随后续迭代扩展至 **A1–A10** + `--self-test` 全量负向反证），待接入 CI（D6）。
>
> **LLD v0.3 修订（2026-11-08）**：对 v0.2 逐卷独立诊断发现「骨架齐全但每卷可执行规格薄」——关键算法、容量 / SLO、FMEA、迁移回滚、数据保留、读模型新鲜度、测试门槛均未实例化。本轮按 B12–B17 六组业界锚点，为 01 Golden Record 治理 / 02 密封投标与评标治理 / 03 义务与 AI 评测闭环 / 04 条件技术与成本口径 / 05 X12 例外分类与门户安全 / 06 Outbox 运行纪律各补一篇差异化 §13（八子节），并新增门禁 **A9**（八子节 + 专属锚点 + 七类要素 + 探针 + 索引）与 3 条负向探针，全套 9/9 + 自检 22/22 双绿。数值冻结（L38–L43）与 A9 接入 CI（D6）仍留给 owner。

> **R8 契约工件闭环（2026-11-09）**：诊断 R8 的验收判据「`openapi` 端点数 ≥ 各卷 §6 的 55」此前未闭合——工件只有 22 条路径、六卷 §6 有 55 行声明，且全部路径模板参数均未声明（工件在 OpenAPI 3.1 语义下不可消费）。本轮补齐 34 条路径、2 个已有路径的 `post` 操作与全部 Path Item 级 `parameters`，并加 `x-endpoint-adjudication` 登记 **R8-a**（05 / 06 卷对账端点重复，收敛到 06 卷 ops 路径）与 **R8-b**（字面量 `finance` 与模板 `{system}` webhook 分工）。新增门禁 **A10.9**（外部锚点下界 55 + 5 个投影白名单 + 双向覆盖）与 3 条负向反证；工件现为 56 路径 / 59 操作，55 行声明零遗漏。仍为 Proposal 设计态，数值与冻结留给 owner。
>
> **LLD v0.2 修订（2026-11-08）**：针对 v0.1 的 5 类 P0 / 9 类 P1 缺口做深度优化——补齐事件契约注册表（29 条，遵循事件规范 §4「先登记再写 Producer」）、`partition_key` 双轨与 `aggregate_version` 乱序丢弃、INBOUND 事件外部生产方标注、错误码/权限码完整目录与白名单；新增金额/币种/汇率/税/UOM（ISO 4217）、Saga 隔离与三分类补偿、schema 演进与 Tolerant Reader、Replay `REBUILD/REDELIVER`、RFC 9457 与 cursor 分页、物理模型与软删唯一键、可观测与韧性、安全 SDL、状态机显式迁移表与可量化测试门槛、契约冻结流程与 RACI。产出 `openapi.yaml` / `asyncapi.yaml` / 生成器与关键链路时序 SVG，并新增机器门禁 A8（注册表 ↔ 工件零漂移、码字唯一性、topic 白名单、资产引用）。

对标国家发展改革委等八部门《关于加快招标投标领域人工智能推广应用的实施意见》（发改法规〔2026〕195号），聚焦企业采购侧（招标人/采购人视角）的 6 类 AI 场景落地：

招标文件检测 → 投标合规自查 → 智能辅助评标 → 中标合同签订风险提示 → 协同监管（履约预警）→ 智慧问答 → 投标响应（M6，借鉴内部投标小助手实践）→ 合同智能起草（M7，对标业界 CLM 趋势）。

> **195 号文口径（2026-10-14 对照原文）**：目标为“2026 年底重点场景在**部分省市**实现全覆盖、2027 年底全国推广”；场景 11“中标合同签订”直接点名关键权利义务条款风险提示与“减少阴阳合同”，是本方案最直接的政策依据；发文对象含各中央企业，但对央国企为“因地制宜探索应用”的指导性要求，**无考核条款**。

## 与 Bone 平台的关系

procuraAI 为**采购域内的 AI 子能力**（**非独立可交付产品**；产品定位裁决见资产登记 §7），复用 Bone 的元数据建模、低代码 Runtime 与文档解析能力作为底座，不修改 Bone 核心（Clean Core 原则：稳定封箱不动，易变外置隔离）。仅 `procuraai-runtime` 因 Python + GPU 技术栈不匹配而独立部署（B20 认可的正当拆分理由）。
