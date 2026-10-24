# AI 文档区（`doc/ai-product`）

> 本目录收 **AI 产品与 AI 能力的方案与交付物**。2026-10-14 自 `doc/design/` 迁入归位。

## 归类原则（三条，按序过滤）

1. **主题判定**：文档主体是 AI（LLM / 智能体 / AI 能力 / AI 产品），AI 是主题而非点缀；
2. **归属判定**：只收"AI 产品/能力的**方案与交付物**"；教 AI 怎么干活、或被工具链引用的**工程规范/流程**留在工程域（见下"不入本目录"）；
3. **可迁性判定**：路径被工具链钉死、或拆散会破坏成套系列的，不迁（路径纪律优先）。

## 目录索引（单入口 + 双分区）

| 路径 | 说明 |
|------|------|
| [platform/](./platform/) | **AI 能力中台分区**（平台能力资产的方案与设计，随批次生长） |
| ├ [BONE-AI能力中台整体方案.md](./platform/BONE-AI能力中台整体方案.md) | **AI 能力中台唯一方案真源**（v1.15：能力同构论证 C1–C11 + 模块架构（11 模块 + 评测挂流水线）+ 框架/许可定案 + 落地批次；v1.15：类型护栏机械强制 + RAG ACL 默认 fail-closed（接入方适配升 Go-Live 硬依赖）+ 写入侧 PII 闭环 + max-scan 下调 3000 + 网关指标适配器） |
| ├ [BONE-AI能力中台详细技术方案.md](./platform/BONE-AI能力中台详细技术方案.md) | **详设**（v1.9）：模块详设 / 契约与 DDL（8 张已入 `bone-init.sql`）/ 非功能阈值初值 / WBS / 平台 API 面 / **生产就绪清单**；v1.8 补流式 SSE（§2.1）+ TTFT（§3.2/§6.2）+ ArchUnit 口径订正（§3.3） |
| ├ [BONE-AI接入指南.md](./platform/BONE-AI接入指南.md) | **产品接入指南**（v1.8）：进程内嵌库主形态 + `bone-ai-app` 聚合宿主辅形态；可用能力表 / 接线步骤 / 双参考宿主 / RAG 配置速查；v1.6 补流式响应 §2.7；v1.8 补 RAG 回答链（`/rag/answer` + `rag-answer-model`） |
| ├ [BONE-AI-RAG能力方案.md](./platform/BONE-AI-RAG能力方案.md) | **RAG 专项独立方案**（v1.8）：以 `bone-ai-rag` 源码真源为基线，覆盖架构/数据模型/写入链/读取全链/**回答链（围栏提示词 + 生成端口 + 接地守卫 + 生产接线）**/引用强制/隔离/降级/可观测/配置/API；对标 2026 最佳实践的对齐矩阵（B1–B21）+ 路线图（R1 主体落地 / R4 pgvector 第二批前启动 / R5 评测前置 / R9 语义缓存 / R10 嵌入轮换）。**v1.3：规模止血可观测 + 批量嵌入 + 评测 sidecar；v1.4：中文双路口径统一 + golden-set 门禁进 CI；v1.5：接地守卫 `GroundingGuard`；v1.6：`RagAnswerService` 回答链闭环（`GroundedPromptBuilder` 数据围栏 + 编号引用 + `context-token-budget`，`AnswerGenerationPort` 生成端口）；v1.7：ACL 默认 deny（`DenyDocAcl`，接入方适配升 Go-Live 硬依赖）+ 写入侧 PII 等长掩码（`SensitiveDataMasker`，`INGEST_PII_MASKED`）+ 检索双路并发（R7，`CompletableFuture`）；v1.8：R1「接 G」主体落地——组合根 `RagAnswerGenerationAdapter` 转调网关 `invoke`（全治理链继承，含间接注入纵深防护）+ `POST /rag/answer` + 生成故障收敛为可控拒答（`AnswerGenerationException`，errorCode 保真）**；取代总案 §4.5 与详设 §2.6 的散落描述 |
| ├ [BONE-AI依赖与许可SBOM清单.md](./platform/BONE-AI依赖与许可SBOM清单.md) | 法务送审版 SBOM（v1.2）：依赖许可总表 + 零新依赖实证 + 禁用负表 + 5 项待确认点（POC 签前冻结选型） |
| └ [方案诊断复核-2026-10-24.md](./platform/方案诊断复核-2026-10-24.md) | RAG 方案 v1.2 的诊断复核（minScore 量纲漂移等 → v1.2 修复依据） |
| [方案诊断报告-2026-10-23.md](./方案诊断报告-2026-10-23.md) | 四件套逻辑自洽 / 名实相符 / 务实 / 可落地诊断（源码走查实证）+ 优化执行记录 |
| [RAG方案诊断与优化-2026-10-23.md](./RAG方案诊断与优化-2026-10-23.md) | **（变更史，已被《BONE-AI-RAG能力方案》取代）** bone-ai-rag 专项诊断：rerank/embedding 零接线、docVersion 幂等缺失、容量/分数护栏缺失 → P0 修复；P1 落地：文档级 ACL seam（403 fail-closed）/ MMR 多样性（默认关）/ 降级痕迹可观测；查询改写记决策（批二启动） |
| [RAG方案诊断-企业复杂场景胜任度-2026-10-24.md](./RAG方案诊断-企业复杂场景胜任度-2026-10-24.md) | **企业复杂场景胜任度诊断**（源码实证）：4 个 P0 硬阻塞（全量内存扫描规模 / 默认 NoDocAcl 跨文档越权 / 生成侧 grounding 缺失 / 评测门禁缺失）+ P1/P2 优化，驱动 v1.3 的规模止血、批嵌入、评测脚手架落地 |
| [procuraAI/](./procuraAI/) | 采购 AI 专题包（PRD / 技术方案 / POC / 接口契约 / DEMO + assets） |
| [synapClaim/](./synapClaim/) | ClaimSphere 智能理赔专题包（PRD / 设计 / MVP / delivery 交付包 / DEMO） |
| [synapEquity/](./synapEquity/) | Synap Equity 估值专题包（产品方案 v0.18 / 方法学 v0.2 / 合规 v0.2 / 数据源 v0.2 / 技术 v0.2 / 评测 v0.2 / 开发 Backlog v0.2 / DEMO） |

**分区语义**：`platform/` 收 AI 能力的**方案与设计**（含将来的 bone-ai 模块设计、治理面/评测/OTel 对齐等，**替代**"详设散进 `doc/design/modules/`"的做法，modules/ 继续是元数据平台模块域，两不相扰）；三个产品目录是**成品快照**（PRD/DEMO/交付包，趋稳）。产品包是 platform 中台论证的输入旁证，中台是产品的能力上游。

## 不入本目录（含理由）

| 文档 | 不入理由 |
|------|----------|
| `doc/design/AI-夜间设计复核工作流方案.md` | 路径被 `scripts/prompts/01-global-anchor.md` 与 `_global-contracts.yaml` 夜间锚定契约钉死；性质是工作流程，非 AI 产品 |
| `doc/agenticx/` | Bone Agentic Engineering 工程宪法（与 CLAUDE.md/AGENTS.md/doc/agents 成套），工程规范 ≠ AI 产品 |
| `doc/agents/06-AI协作与编码准则.md` | 01–06 编号系列成套，属工程规范域 |
| `design/modules/`、`lowcode/`、`architecture/` 等 | BONE 平台本体设计，AI 只是章节点缀 |
