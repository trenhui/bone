---
comet_change: procuraai-ai-contract-review-skeleton
role: technical-design
canonical_spec: openspec
---

# 设计：procuraAI 合同智能审查 walking skeleton

## Context

见 `proposal.md#why`。当前状态：采购域已落地 MDM + PR→PO 骨架（`bone-procurement-*`、`bone-masterdata`），AI 底座已存在且可用——`bone-ai-document`（`DocumentParseApplicationService` / `ClauseSplitter` / `PdfBoxDocumentParser` / `HttpOcrClient`，69 个 main 文件）、`bone-ai-gateway`（`LlmGatewayApplicationService` / `HeuristicInjectionGuard` / `JsonOutputValidator`，46 个）、`bone-ai-trace`（12 个）。契约真源：D3-v2 §2 `ContractReviewed` 7 字段 payload、§3 AI 合规、评测集 `ai-decision-evalset.json`（27 例 seededDraft）。本 change 须遵守：分层 `adapter→application→domain←infrastructure`、持久化只用 `bone-metadata-sdk`、`ApiResponse<T>` 统一响应、实体继承 `TenantAbstractEntity` + 审计列 `java.util.Date`、能力开关 fail-closed、AI 仅辅助人工终决。

## Goals / Non-Goals

**Goals:**
- 交付一条端到端可运行的合同审查链路（上传→解析→风险报告→HITL→披露→留痕→幂等消费），以源码与测试证明 spec 中的可观察行为。
- 复用现有 `bone-ai-*` 能力，不新建 AI 底座；采购域只做审查编排、契约映射与合规护栏。
- 把评测集负向用例（CONTRACT / DISCLOSE / PROMPT-INJ / AI-3/4/5）变成源码可达的注入必红探针。
- 建立可回滚的能力开关与生产持久化边界（默认关闭、关闭即不注册）。

**Non-Goals:**
- 不实现 F1–F4 地基层全量（数据集 / 标注规范 / 漂移闭环）、组 5 决策治理、G1–G16 能力卡、真实跨域 ACL、法务结论、模型选型冻结、`t_proc_*` 新表生产落库（L3）、评测集扩容至 ≥200。
- 不修改 Bone 核心、不引入 MyBatis/JPA、不写 `bone-init.sql` 生产迁移、不打开自动放行 / 自动归档。

## Decisions

**D1 — 审查能力落在 `bone-procurement` 聚合器内的新模块 `bone-procurement-contract`，而非扩展 `bone-masterdata`。**
理由：合同审查属采购交易域（M04），与供应商主数据（M01）职责不同；独立模块保持 DDD 限界上下文清晰，ArchUnit 分层独立可验。备选：塞进 `bone-masterdata`——被否，会造成主数据模块承载 AI 审查横切职责、污染其能力开关与权限边界。

**D2 — AI 能力全部通过端口（port）调用 `bone-ai-*`，采购域不直接依赖 LLM SDK。**
定义 `ContractParsePort`（→ DocumentParseApplicationService）、`ContractReviewPort`（→ LlmGatewayApplicationService + 注入防护 + 输出校验）、`ReviewTracePort`（→ bone-ai-trace）。备选：采购域内直接调 LLM——被否，违反 Clean Core 且重复造降级/注入防护。生产适配器注入真实 bean，测试/演示用受控内存实现；`ai_available` 由 LLM 节点自报回填，缺省 `UNKNOWN` fail-closed。

**D3 — 审查报告走"快照引用 + 事件摘要"，不建新的大字段表。**
`ContractReviewed` payload 严格 7 字段，风险明细以 `risk_items_hash` 摘要 + `review_snapshot_ref` 引用表达，明细与 output 原文不进事件、不进采购域库。审查记录落库复用现有 `t_proc_*` 实体字段扩展草案（仅设计态）；新表 DDL 属 L3，本 change 不执行生产迁移，用内存/受控仓储跑通链路。备选：把 output 原文落地——被否，违反 PII 禁入与 D3-v2 §2 禁止项。

**边界声明（诚实口径）**：walking skeleton 的事件闭环由 `ContractReviewApplicationService.override` 在进程内直接调用 `ReviewEventAssembler.assemble` → `ContractReviewedConsumer.consume` 完成，以 `reviewId` 幂等挂结论；**生产 MQ producer/listener 与 Outbox/Inbox 未接线、属 L3**，不得把进程内直连表述为已交付的生产事件能力。消费侧逻辑本身已按幂等 / UNKNOWN 隔离 / 追溯链实现，可直接被未来 listener 复用。

**D4 — HITL 用置信度 + 后果严重度的确定性路由规则，而非让 LLM 决定是否需要人工。**
低置信 / 高后果 / 命中负向用例 → 人工复核。规则在应用服务层确定性执行，LLM 只给建议。备选：LLM 自评是否转人工——被否，等于让被监督方决定监督强度，违反"有意义监督"。

**D5 — 披露三态建模为独立值对象，前端未知码原样显示。**
`AiParticipation = AVAILABLE | UNAVAILABLE | UNKNOWN`，另有参与态 `UNDETERMINED`；`model_version_snapshot` 单独字段、仅作版本快照不作参与证据。后端返回原始码，前端对 UNKNOWN/UNDETERMINED 显示「无法确认」。备选：布尔 `isAiInvolved`——被否，会把 UNKNOWN 压成 false，正是 AI-4 负向用例要防的退化。

**D6 — 评测与门禁复用现有骨架，不新增 CI 判据集合。**
复用 `check_procura_evalset.py` 与文档族门禁；新增负向探针为源码级单测（注入必红/还原必绿）。`testSymbol` 对齐源码符号、评测符号经源码可达性验证；`groundTruthRef` 保持指向 D3-v2 真源文档锚点（A16 约束：真源语料不可指向源码 .java，否则 resolve_ref 转红）。不触碰 `FULL_GATE_KEYS`（A10.17 判据集合），避免转红。备选：新增 CI 判据——被否，需同步三入口 + 门禁常量 + 自检探针（L3），超出本切片范围。

## Risks / Trade-offs

- [无真实模型效果证据] → walking skeleton 用受控夹具 + fail-closed 降级路径验证行为，不宣称效果达标；效果评测属 R-3 地基，另立项。
- [能力开关默认关闭，演示需显式打开] → 在测试配置与 walkthrough 显式启用；生产保持默认关闭，避免假能力暴露。
- [新模块 + 权限码 + 端点改变门禁影响面] → 精确对齐 G32 菜单路由 / G40 权限码门禁，权限种子 SQL 幂等；如触发 A10.17 集合外判据，只改文档不改判据集合。
- [评审集仍是 seededDraft，规模不足] → 本切片只做"源码可达 + 负向探针"，不把 27 例当成效果结论；扩容到 ≥200 属 R-3。
- [生产持久化端口未就绪时端点可达但落不了库] → 生产 adapter 未就绪时端点不注册（沿用 supplier-mdm 已验证范式），fail-closed。

## Migration Plan

1. 在 `bone-apps/bone-procurement/pom.xml` 聚合器新增 `bone-procurement-contract` 模块，四层包骨架；无 DDL 落库。
2. 按 TDD 顺序实现端口与领域服务（D2/D4/D5），再接适配器与 Controller（能力开关 + 权限）。
3. 接入评测集负向用例为单测探针，注入必红、还原必绿；`spotless:apply` 后 `mvn -o -f bone-apps/bone-procurement/pom.xml clean test` 全绿。
4. 前端披露/审查入口最小页面（如纳入本切片，复用 `bone-procurement-app`），tsc + eslint(json) + vitest 全绿。
5. 回滚：能力开关设 false 即下线端点；模块代码可整体回退，不影响既有 MDM/PR-PO（无共享表写入）。

## Open Questions

（无会改变 spec / 方案 / 任务拆分的问题。地基层、法务、模型选型、评测扩容属已登记 owner 决策项，不阻塞本切片。）
