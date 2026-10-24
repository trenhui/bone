# BONE AI 能力中台 · 产品接入指南

> **版本**：v1.6　**日期**：2026-10-23　**配套**：《BONE-AI能力中台整体方案》《详细技术方案》
> **v1.6 变更（流式 SSE 落地，对齐 2026 业界最佳实践）**：① §1 可用能力表 gateway 行补「**流式响应（SSE）**——`streamInvoke` 复用 `invoke` 全治理链，首 token 即渲染、TTFT 可观测，零新增依赖、零改接入代码即可用（缓冲回退）」；② 新增 §2.7 流式响应——门面 `streamChat` + `LlmStreamEvent`（Token/Done/Error）用法、与 `invoke` 同脱敏口径（原文下发、脱敏只出站请求与证据）、含 PII 请求强制缓冲模式的建议；③ 验证证据：gateway 115 测试全绿（含流式 7 例）。
> **v1.5 变更（密钥管理 seam 接线，续 Go-Live 硬依赖）**：§1 可用能力表 gateway 行补「密钥按调用解析（`ApiKeyProvider`，默认配置实现 + `@Bean` 覆盖切 Virtual Key，换 key 不重启）」；§5 红线补密钥按调用解析口径 + Virtual Key 部署下空 key WARN 的预期性说明。网关侧 seam 已接通，剩余 L3 = bone-security Virtual Key 能力本体实现（详设 §6.1）。
> **v1.4 变更（RAG 诊断 P1 项落地，承接《RAG方案诊断与优化-2026-10-23》）**：§1 可用能力表 rag 行补文档级 ACL seam / MMR（默认关）/ 降级痕迹；§6 RAG 配置速查补 `bone.ai.rag.mmr-lambda`（λ 约定防误配）、降级痕迹响应结构（`{results, degradations}`）、文档级 ACL 接入方接线说明（`RagAclPort` Bean 覆盖 + 403 `AI_RAG_DOC_FORBIDDEN`，L3 前必须接入）、backlog 收敛为查询改写 / trace 接线 / ingest ACL 三项（查询改写记决策：批二启动）。
> **v1.3 变更（RAG 接线修复，承接《RAG方案诊断与优化-2026-10-23》）**：§1 可用能力表 rag 行按代码真序重写（容量护栏 / 引用强制前置 / 双路降级 / rerank 条件装配 / `doc_version` 幂等）；§2.4 建表说明补 `doc_version` 列语义；§3 REST 面补 rag 请求字段（`min_score`/`max_scan`/`doc_version`）；新增 §6 RAG 配置速查（`bone.ai.rag.*` 条件装配与降级口径）。
> **v1.2 变更（生产硬依赖代码落地，承接诊断报告 P2 项）**：§1 可用能力表补 trace 回放 REST / rules 管理 REST（`bone-ai-app` 的 `TraceController` / `RuleController`，租户隔离）+ Redis 分布式配额自动装配说明；§0 补 `bone-ai-app` 已含 Actuator 探针分组；§5 红线口径补密钥 fail-fast。
> **v1.1 变更（诊断后修订，见《方案诊断报告-2026-10-23》）**：§0 补部署双形态（`bone-ai-app` 聚合宿主）；§1 可用能力表补 rag / notification / integration / ledger 四模块 + document 行更新；§2.2 `@EnableSqlRepositories` 包清单补全（漏 `com.bone.aiorch.domain.hitl` 会导致 HITL 仓储缺失）；§2.4 建表清单补全 8 张；§3 补 `bone-ai-app` 全量拓扑宿主；§5 `Confirmed` 溯源标注目标态口径。
> **回答一个问题**：产品服务（procuraAI / ClaimSphere / Equity）如何把 bone-ai **嵌进自己的进程**。

---

## 0. 形态一句话

bone-ai 各模块是**进程内 starter（库形态，零额外跳）**，**主形态不是独立部署的 AI 服务**。产品服务以 Maven 依赖引入、自行 `@EnableSqlRepositories` 注册仓储、复用自身 DataSource；模型调用统一经 `bone-ai-gateway` 收口。两个参考宿主：**`bone-ai-server`**（blueprint 风格 demo + 端到端集成测试，演示最小接线）与 **`bone-ai-app`**（生产拓扑聚合：9 个节点库 + HITL/评测/RAG/预警/集成/规则/证据回放统一 REST 面 + Actuator liveness/readiness 探针分组，供确认台 / 跨语言消费方 / 运维面——辅形态，详见总案 §3.1 部署双形态）。

---

## 1. 当前可用能力（截至 2026-10-23，11 模块均有最小落地）

| 模块 | 提供 | 接入要点 |
|---|---|---|
| `bone-ai-gateway` | `LlmClient` 门面（同步 `chat` + 流式 `streamChat`）+ 成本红线 + OpenAI 兼容 / Mock 双实现 + `LlmCallRecorded` 事件发布 | 自动配置；默认 Mock，配 `base-url` 切真实 provider；**默认开**：PII 脱敏、租户配额、密钥 fail-fast（`api-key` 含未解析 `${}` 即启动失败）、密钥按调用解析（`ApiKeyProvider` 端口每次请求现取，`@Bean` 覆盖即切 Virtual Key / 轮换密钥，换 key 不重启）；**默认关**：精确缓存、提示注入、输出内容安全（默认开关总表见详设 §2.1）。**流式**：`streamInvoke` 复用 `invoke` 全治理链、首 token 即渲染、TTFT 可观测，零新增依赖、零改接入代码即可用（缓冲回退，详见 §2.7）。**多副本部署**：宿主引入 Redisson（`bone-metadata-sdk` 同款）即自动切 Redis 分布式配额，`bone.ai.gateway.quota-redis-enabled=false` 可关 |
| `bone-ai-rules` | 规则资产治理（CRUD / 版本 / 灰度 / 命中标记）+ QLExpress 唯一 DSL | `RuleAssetRepository`（SDK 动态代理，无需手写实现）；`bone-ai-app` REST：`/api/ai/v1/rules/...`（CRUD / 发布 / 回滚 / 灰度 / hitBlock / 废弃 / 试算求值） |
| `bone-ai-orchestration` | 编排执行器：规则预检 → 命中短路｜LLM → `DecisionGate` 确定性闸门 + HITL 五态 / 处置链 / 超时升级 | `OrchestrationApplicationService`；HITL REST 由 `bone-ai-app` 暴露 |
| `bone-ai-trace` | 证据三元组（输入哈希 + IR + 引擎版本）落库 + 回放 + 计量事件 | `EvidenceTraceListener` 订阅 `LlmCallRecorded`；回放：进程内 `EvidenceQueryApplicationService.replayByTraceId`，或 `bone-ai-app` REST `GET /api/ai/v1/trace/{traceId}`（租户隔离，越权 403） |
| `bone-ai-document` | 条款切分 + 统一 IR（PDFBox 默认；Docling sidecar 适配器就位，`bone.ai.document.docling.enabled=true` 开启即替换，默认关） | `DocumentParseApplicationService` |
| `bone-ai-rag` | 受控检索最小全链：租户过滤 → 文档级 ACL（默认仅租户硬隔离，接入方 `RagAclPort` 覆盖后 fail-closed）→ 容量护栏（`max-scan` fail-closed）→ 引用强制前置 → 向量（配 `base-url` 走 bge-m3 HTTP 客户端，否则过渡哈希兜底；故障降级 BM25 单路）+ BM25 → RRF → rerank（配 `base-url` 走 cross-encoder，否则 NoOp；故障降级 RRF 粗序）→ `min-score` 过滤 → MMR 多样性（默认关）→ 引用强制 fail-closed；降级痕迹透出响应 `degradations` | `RagApplicationService`；`ChunkRepository` 落 `ai_rag_chunk`（`doc_version` 幂等批键） |
| `bone-ai-notification` | AI 域预警：dispatch / ack / 消警守卫（Webhook + HMAC 签名） | `AlertNotificationApplicationService` |
| `bone-ai-integration` | AI 出站适配：外部系统调用 + SSRF 守卫 + HMAC 回调校验 + Shadow/Assisted/Automated 三态 | `IntegrationApplicationService` |
| `bone-ai-ledger` | 多维额度台账 + 两阶段预留（域中立内核，进程内 `InMemoryLimitLedger` 默认） | `LimitLedger` 端口，按需注入 |

---

## 2. 接入步骤

### 2.1 依赖

在产品服务的 pom 引入所需模块（版本随平台 BOM）：

```xml
<dependency>com.bone:bone-ai-gateway</dependency>
<dependency>com.bone:bone-ai-rules</dependency>
<dependency>com.bone:bone-ai-orchestration</dependency>
<dependency>com.bone:bone-ai-trace</dependency>
<!-- document 按需 -->
```

### 2.2 注册仓储（SDK 动态代理，**无需手写实现**）

各 `*Repository` 均 `extends com.bone.metadata.sdk.Repository`，由 SDK 扫描接口自动生成代理。按引入模块在启动类声明（**漏包 = 仓储缺失、启动即报**）：

```java
// 最小内核（gateway + rules + trace）
@EnableSqlRepositories(basePackages = {
    "com.bone.airules.domain.repository",
    "com.bone.aitrace.domain.repository"
})

// + orchestration（HITL 持久化）
//   "com.bone.aiorch.domain.hitl"

// 全量拓扑（对齐 bone-ai-app，五个包一次到位）
@EnableSqlRepositories(basePackages = {
    "com.bone.airules.domain.repository",
    "com.bone.aitrace.domain.repository",
    "com.bone.aiorch.domain.hitl",
    "com.bone.airag.domain.repository",
    "com.bone.aiapp.eval.domain.repository"
})
```

> 漏 `com.bone.aiorch.domain.hitl` 的后果：HITL 待裁列表 / 裁决写入无仓储代理，`HitlController` 调用 500——接入排障高频点，照抄上方全量清单最稳。

### 2.3 数据源与 SDK 配置

产品复用自身 DataSource（MySQL）；关闭统一动态数据源、开启 SDK SQL 通道。最小配置见 `bone-ai-server/src/main/resources/application.yaml`（`bone.metadata.sql.*` + `metadata.sdk.deployment-mode=EMBEDDED`）。

### 2.4 建表

AI 域 **8 张表**均已在 `bone-init.sql`（《数据库开发规范》§2.12，含 HC-008 四必备列 + 软删 + Snowflake 主键口径），按引入模块执行对应段：

| 表 | 所属 | 用途 |
|---|---|---|
| `rule_asset` / `rule_asset_version` | rules | 规则资产 + 版本链（灰度/回滚） |
| `ai_trace_snapshot` | trace | 证据三元组快照（输入 SHA-256 + IR + 引擎版本） |
| `hitl_item` / `hitl_disposition` | orchestration | 待裁项五态 + 处置链（`reason_type` / `decided_by` 留痕） |
| `ai_rag_chunk` | rag | 切片 + 向量（未配推理服务时过渡哈希兜底）+ span 引用 + `doc_version` 幂等批键 |
| `eval_case` / `eval_run` | 评测 | 黄金集/负例 + 门禁运行记录（`metrics` JSON 出报告） |

### 2.5 成本红线 + 证据链（默认自动装配，产品零额外代码）

- **成本红线**：`OrchestrationApplicationService` 强制"规则命中（`RULE_HIT_BLOCK`）⇒ 跳过 LLM、不发请求、不发事件"——不依赖调用方自觉。
- **证据链**：每次真实 LLM 调用，`bone-ai-gateway` 发布 `LlmCallRecorded` → `bone-ai-trace` 监听落证据快照（输入 SHA-256 + IR + 引擎版本）；业务代码无直接写快照的旁路。回放经 `EvidenceQueryApplicationService.replayByTraceId`。

接入编排即同时获得两者。

### 2.6 模型

默认 `MockLlmClient`（开发 / 测试 / 成本红线探针）；切真实 OpenAI 兼容 provider 时配置 `bone-ai-gateway` 的 `base-url` / `api-key`，`LlmClient` 契约与红线 / 证据装配不变。

### 2.7 流式响应（SSE，v1.6 落地）

长文逐步呈现 / 首 token 体验 / 降级兜底——SSE/流式已是 OpenAI 兼容生态与主流网关（LiteLLM / Portkey / Kong）的事实标配。BONE-AI 网关在**不引入新依赖、不复制治理链**的前提下补齐：

**门面用法**（与 `invoke` 对称的入口，`streamInvoke` 复用全治理链）：

```java
// 门面：LlmClient.streamChat(req, Consumer<LlmStreamEvent> sink)
// 事件：sealed LlmStreamEvent { Token(delta) | Done(model, finishReason, usage) | Error(message, cause) }
List<LlmStreamEvent> events = new ArrayList<>();
gateway.streamInvoke(req, events::add);
// 或边收边渲：gateway.streamInvoke(req, e -> {
//     if (e instanceof LlmStreamEvent.Token t) render(t.delta());
//     else if (e instanceof LlmStreamEvent.Done d) finish(d.usage());
//     else if (e instanceof LlmStreamEvent.Error er) fallback(er.message());
// });
```

- **治理同源**：红线短路 / 场景路由 / 租户配额 / 提示注入 / 出站脱敏 / 缓存命中全部前置；流末对全文做内容安全 / 结构化校验 / 缓存写入 / 计量 / 证据——与同步 `invoke` 完全相同，无第二套逻辑。
- **零接入成本**：`LlmClient.streamChat` 默认缓冲回退（调一次 `chat` 后发 `Token` + `Done`），**不实现 SSE 也能跑**；真正逐字流式由 `JdkLlmTransport`（JDK `HttpClient` 真 SSE，零新依赖）承载，熔断只守卫「流获取」不守卫「流消耗」。
- **脱敏口径与 `invoke` 一致（重要）**：流式面向调用方也返回**原文**，PII 脱敏只在「出站请求」与「证据落库」两处发生——**不做逐片流式脱敏**（一段 PII 恰被 SSE 切在两 chunk 间会漏脱敏；累积重脱敏又会把 `[手机号]` 这类证据标签回吐给调用方，既泄露脱敏机制又违背原文契约）。
- **含 PII 的强零泄露场景**：若业务对含 PII 响应要求严格零泄露，治理层可对该请求强制走**缓冲（非流式）模式**——`streamInvoke` 仍可用，只是内部取全量后一次性下发（代价是失去逐字流式体验，换取确定性脱敏）。
- **TTFT 可观测**：首 token 到达自动发射 `LlmMetrics.recordFirstToken(tenantId, model, latencyMs)`，后续接 OTel `gen_ai.time_to_first_token`（映射表见详设 §3.2）。

---

## 3. 参考宿主（两个，按用途选）

### 3.1 `bone-ai-server`（blueprint 风格，非生产拓扑）

- **是什么**：把库接线成一个可运行 Spring Boot 应用 + 承载端到端集成测试的"活模板"。
- **为什么不是服务**：生产形态是产品服务进程内依赖这些库；独立 AI 服务会多一跳、违背"库形态优先"。
- **集成测试** `AiPipelineIntegrationTest`：真实服务 + 进程内事件流 + mock 仓储，证明
  - 规则命中（hitBlock）⇒ 短路，不调 LLM、不留痕；
  - 规则未命中 ⇒ 调 LLM ⇒ `LlmCallRecorded` ⇒ trace 落证 ⇒ 按 traceId 回放。
- **运行**：需 MySQL（同 `bone-metadata-server`）；`POST /api/ai/v1/pipeline` 为演示端点。

### 3.2 `bone-ai-app`（生产拓扑聚合宿主，辅形态）

- **是什么**：9 个节点库（gateway/trace/rules/orchestration/document/rag/notification/integration/ledger）聚合为**可独立部署的 Spring Boot 应用**——统一 REST 面 + Actuator 健康检查，javadoc 自称"八个 AI 节点库"为笔误（pom 实依赖 9 个）。
- **与主形态的关系**：产品服务仍以进程内嵌库为主形态（零额外跳）；`bone-ai-app` 服务**确认台前端 / 跨语言消费方 / 运维观测面**——它不是第二编排真源（实现仍是那批库），部署演进见总案 §3.1 双形态与 §4.7。
- **REST 面**：HITL（`/api/ai/v1/hitl/...`）、RAG（`/rag/retrieve|ingest`）、评测（`/eval/...`）、预警（`/notify/...`）、集成（`/integration/invoke`）、演示（`/pipeline`）。
- **配置要点**：`metadata.sdk.deployment-mode=EMBEDDED` + 5 个仓储包（§2.2 全量清单）；生产部署资源配置（HPA / 探针 / 配额多副本）见详设 §6 生产就绪清单。

---

## 4. 批二 / 三预告

rag 指标达标与 pgvector 终局实测、document 完整管线（Docling sidecar 验收）、eval 门禁挂 CI、前端确认台（qiankun 子应用，`bone-frontend/apps/` 尚无）。`bone-ai-app` 后端宿主已落地（§3.2，含规则 / 证据回放 REST + Actuator 探针分组），其生产部署按详设 §6 就绪清单推进。范围与出口判据见《详细技术方案》§5。

---

## 5. 红线（不变式，ArchUnit / 结构强制）

- 资金 / 责任 / 数值 / 合规结论走 `bone-ai-rules` + 代码，**不进 LLM**。
- AI 输出落业务表前必须有 `Confirmed` 溯源（`decided_by` / `evidence_ref`）——**目标态口径**：`AiSuggestion`/`Confirmed` 类型批二补建前，由 `DecisionGate`（置信度 / 金额 / AI 可用性合取）+ HITL 处置链留痕等价保证（详设 §2.3）。
- 证据链无旁路；模型 HTTP / key 只出现在 gateway。
- 精确缓存开启时键必须带租户维度（防跨租户串味）；缓存默认关。
- **密钥不得留未解析占位符**：`bone.ai.gateway.api-key` 含 `${...}` 即启动失败（fail-fast，防模板配置流入生产）；https 远端 provider 空 key 会 WARN（接 Virtual Key 后配置态空 key 属预期，可忽略该 WARN）。
- **密钥按调用解析**：provider key 经 `ApiKeyProvider` 端口每次请求现取（默认读配置；以 `@Bean ApiKeyProvider` 覆盖即切 Virtual Key / 托管密钥——支持轮换与短时限 key，换 key 不重启，客户端零改动）；模型 HTTP / key 只出现在 gateway，业务侧不接触。
- **多副本部署配额**：宿主引入 Redisson 即自动切 Redis 分布式配额（`quota-redis-enabled` 默认 true）；Redis 故障 fail-open + WARN（配额是成本防线不是可用性防线）。
- **上线前**过《详设》§6 生产就绪清单（部署 / 观测 / 安全 / 容量 / 灰度 / 演练六域 + Go-Live 判据）。

---

## 6. RAG 配置速查（`bone.ai.rag.*`，2026-10-23 起生效）

| 配置 | 默认 | 语义 |
|---|---|---|
| `bone.ai.rag.embedding.base-url` | 空 | 共享嵌入服务（TEI / infinity 兼容，如 `http://tei:80`）；**空 ⇒ `TransitionalHashEmbedding`**（无语义，仅保链路可跑） |
| `bone.ai.rag.embedding.model` / `version` | `BAAI/bge-m3` / `bge-m3@http` | 模型标识 / 写入 `embedding_ver` 的版本串（与之不符的旧向量 fail-closed 跳过向量路） |
| `bone.ai.rag.rerank.base-url` | 空 | 共享重排序服务；**空 ⇒ `NoOpReranker`**（保持 RRF 粗序，不精排） |
| `bone.ai.rag.candidate-pool` | 30 | 进入 cross-encoder 精排的候选池（≥ topK；详设 §2.6 top-30 → top-5） |
| `bone.ai.rag.min-score` | 0 | 精排后分数阈值（0 = 不启用；量纲双轨，须按评测分布标定） |
| `bone.ai.rag.max-scan` | 10000 | 容量护栏：作用域切片数上限，超限 fail-closed 抛 `AI_RAG_SCOPE_OVER_LIMIT`（422） |
| `bone.ai.rag.mmr-lambda` | 0 | MMR 多样性权重（top-K 选取去重，防结果同质化；**0 = 关闭**，λ∈(0,1) 生效，λ=1 纯相关性等价关闭，**λ=0 约定为关闭而非数学上的纯多样性**——防误配乱序；须按评测集回流校准后启用；开启时精排候选按 3 倍扩样） |

**降级口径（读路径）**：嵌入 provider 故障 ⇒ WARN 事件 + BM25 单路；重排序 provider 故障 ⇒ WARN 事件 + RRF 粗序；引用强制语义在降级下不变；**严禁切云端辅路**（数据不出域红线）。写入路径（ingest）不降级：嵌入失败即上抛，重入按 `doc_version` 幂等重建。
**降级痕迹（响应可观测）**：`POST /rag/retrieve` 响应为 `{results: [...], degradations: [...]}`——`degradations` 非空即本次发生能力档位下降（`EMBEDDING_DEGRADED` = 回落 BM25 单路 / `RERANK_DEGRADED` = 回落 RRF 粗序），结果仍全部带出处锚点；接入方 / 评测管线可据此观测，无需翻服务端日志。服务端同步经 `RagEventSink` 下沉事件（默认结构化 WARN）。
**文档级 ACL（接入方接线）**：检索默认仅租户硬隔离（`NoDocAcl`）。接入方以 `@Bean` 提供 `RagAclPort` 实现（读文档中心 / bone-ai-document 可见集合，身份自行从上下文解析）后：显式 `doc_scope` 无 ACL 归属 ⇒ 403 `AI_RAG_DOC_FORBIDDEN`（不静默回落全租户检索），无作用域时按可见集过滤。**L3 私有化交付前必须接入**（兜底形态存在同租户内跨文档越权面）。
**ingest 幂等**：`{doc_id, doc_version, segments}`——同版本且切片数一致 ⇒ 跳过；异版本或残缺 ⇒ 软删旧切片后重建（崩溃半截场景自愈）。
**backlog（P1 剩余）**：查询改写（multi-query / HyDE——决策：批二按评测回流启动，暂不建零消费方 machinery）、降级事件 trace 落库 + 告警接线（端口已就位）、ingest 侧 ACL 校验（随文档中心 ACL 模型定，见详设 §2.6）。
