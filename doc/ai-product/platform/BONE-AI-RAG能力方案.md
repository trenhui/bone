# BONE-AI RAG 能力方案（独立版）

> **版本**：v1.0　**日期**：2026-10-24　**归口**：平台架构组
> **配套**：《BONE-AI 能力中台整体方案》（v1.13）/《详细技术方案》（v1.8）/《接入指南》（v1.6）
> **方法**：以 `bone-ai/bone-ai-rag` 模块源码 + `bone-ai-app` REST 面真源为基线，对标 2026 业界 RAG 最佳实践（Modular RAG / 混合检索 + RRF + cross-encoder 精排 / 强制引用接地 / 元数据隔离 / 可观测降级），沉淀为独立可评审方案。
> **定位说明**：本文是 RAG 专项的独立方案文档（此前 RAG 散见于总案 §4.5 与详设 §2.6）。本文取代上述散落描述，作为 RAG 能力的权威基线；总案/详设相关段落保留交叉引用即可。

---

## 0. 一段话讲清 RAG 在 BONE-AI 里的角色

RAG（Retrieval-Augmented Generation）在 BONE-AI 中是一座**受控检索能力库**：它只负责「把用户问题，在受权限约束的私有语料里，按可审计的检索链召回带出处的切片」，不替业务做判断、不自带生成。生成由 `bone-ai-gateway` 的 LLM 门面 + 编排层负责，检索结果以 `RagResult`（带 `clauseRef` / `spanStart` / `spanEnd` 出处锚点）喂给生成侧做**强制引用接地**。

设计铁律：**「无出处不得生成」是 fail-closed 红线**——任何缺失条款锚点或 span 非法的切片，在检索链里直接丢弃，绝不以无来源内容喂给生成。这条红线在代码里是真的（不是文档话术）。

---

## 1. 范围与定位

| 维度 | 归属 | 说明 |
|---|---|---|
| **能力（平台）** | `bone-ai-rag` | 切分策略、嵌入、混合检索、RRF 融合、rerank、引用强制、权限隔离、降级、幂等写入 |
| **资产（产品）** | 各产品线 | 实际文档与条款语料、文档级 ACL 归属文档中心、生成侧 prompt 与判定 |
| **不建** | — | RAG 不自带 LLM 生成、不重复建设文档权限存储、不引 ES 作第三套检索栈、不建全量标注评测平台 |
| **红线** | 数据不出域 | embedding / rerank 一律走**自托管推理服务**（TEI / infinity 兼容），严禁切云端辅路；降级仅限本域内已实现路径 |

**当前接线边界（务必看清）**：截至 2026-10-24，`bone-ai-rag` 仅被 `bone-ai-app`（REST 聚合宿主）直接消费，经 `/api/ai/v1/rag/retrieve` 与 `/ingest` 暴露。**尚未**接入 `bone-ai-gateway`（LLM 门面）与 `bone-ai-orchestration`（生成编排）——即「R」已成型，「以检索结果约束生成（G）」的编排接线是待办（见 §11 路线图 R1）。

---

## 2. 架构总览

### 2.1 模块与分层

`bone-ai-rag` 采用与平台一致的 DDD 分层（domain 无外层依赖，ArchUnit **12 条**门禁全过）：

```
bone-ai-rag/
├── domain/                      # 确定性内核：无外层依赖
│   ├── model/                   # RagQuery / RagChunk / RagResult / RagEvent / RerankCandidate / RerankScored
│   ├── gateway/                 # 端口：EmbeddingPort / RerankPort / RagAclPort / RagEventSink / TenantProvider
│   ├── repository/              # ChunkRepository（SDK 仓储代理）
│   └── service/                 # RrfFuser / Bm25Scorer / CosineSimilarity / MmrSelector / NoOpReranker / NoDocAcl
├── application/                 # RagApplicationService（检索链 + 写入链编排）+ 记录类型
├── infrastructure/              # HttpBgeEmbeddingClient / HttpBgeRerankClient / TransitionalHashEmbedding
│                               #   / LoggingRagEventSink / TenantContextProviderAdapter
└── config/                     # RagAutoConfiguration + RagInferenceProperties
```

**端口即扩展点（消费方可 `@Bean` 覆盖）**：

| 端口 | 默认实现 | 覆盖为 |
|---|---|---|
| `EmbeddingPort` | `TransitionalHashEmbedding`（无推理服务兜底） | `HttpBgeEmbeddingClient`（bge-m3，TEI） / 进程内 ONNX |
| `RerankPort` | `NoOpReranker`（保持 RRF 粗序） | `HttpBgeRerankClient`（bge-reranker-v2-m3，TEI） |
| `RagAclPort` | `NoDocAcl`（仅租户硬隔离） | 读文档中心 ACL 实现（L3 硬依赖） |
| `RagEventSink` | `LoggingRagEventSink`（结构化 WARN） | bone-ai-trace 落库 + 告警适配（L3） |
| `TenantProvider` | `TenantContextProviderAdapter` | 接入方租户解析 |

> 装配约定：推理客户端**按 `base-url` 条件装配**——配了 `bone.ai.rag.embedding.base-url` 才用 HTTP 客户端，否则回落兜底；`RagInferenceProperties` 经 `@EnableConfigurationProperties` 注册（缺此则整个 `bone.ai.rag.*` 配置面无 bean 承载，运维配了也不生效）。`RagApplicationService` 以 `@ConditionalOnBean(chunkRepository)` 为前提，未接入持久化的上下文不装配。

---

## 3. 数据模型与存储

### 3.1 实体 `RagChunk`（受控检索最小存储单元）

| 字段 | 类型 | 作用 |
|---|---|---|
| `id` | BIGINT（分布式 ID） | 主键 |
| `tenant_id` | BIGINT | 租户硬隔离键 |
| `doc_id` | VARCHAR | 文档标识 |
| `doc_version` | VARCHAR | **增量幂等批键**（同版本同切片数 ⇒ 跳过；异版本/残缺 ⇒ 软删重建） |
| `section_path` | VARCHAR | 章节面包屑（条款/章节层级） |
| `chunk_text` | TEXT | 切片原文 |
| `embedding` | 过渡 CSV float（终局 MySQL JSON 列） | 向量 |
| `embedding_ver` | VARCHAR | 嵌入模型版本（换模型维度不符 ⇒ fail-closed 跳过向量路） |
| `clause_ref` | VARCHAR | **引用强制锚点**（条款 id） |
| `span_start` / `span_end` | INT | 条款内 span（引用定位原文） |
| `deleted` | BOOLEAN | 软删（幂等重建用） |

> DDL 已入 `bone-init.sql`（`ai_rag_chunk`，§12.1）。嵌入列过渡形态为 CSV float 字符串，终局迁移为 MySQL JSON 列 + 向量检索（§11 R2）。

### 3.2 检索请求与结果契约

```java
// 检索请求（护栏内嵌）
RagQuery(String query, String docScope, int topK, double minScore, int maxScan)
//   topK 上限 50；maxScan 默认 10000（fail-closed）；minScore 默认 0 = 不启用

// 检索结果（带出处锚点，供生成侧引用定位原文）
RagResult(String chunkRef, String clauseRef, int spanStart, int spanEnd,
          double score, String path, String text)
//   score 量纲双轨：接 cross-encoder 时为 rerank 分；未接/降级时为 RRF 融合分（~0.01 量级）
//   只可用于同次结果内比较，禁止跨模式比绝对值

// 检索信封（P1 可观测）
RagRetrieveOutcome(List<RagResult> results, List<String> degradations)
//   degradations ∈ {EMBEDDING_DEGRADED, RERANK_DEGRADED}；空 = 全功能档位
```

---

## 4. 写入链路（Ingestion）：切分 → 嵌入 → 幂等持久化

```
ingest(tenantId, docId, docVersion, segments)
   │
   ├─ 取该文档已有切片 findByTenantAndDoc（仅单文档，内存有界）
   ├─ 幂等判定：同版本 且 切片数一致  ⇒ 跳过（内容不变，不重写）
   ├─ 异版本 / 残缺（崩溃半截，切片数不符）⇒ 软删旧切片后重建
   ├─ 逐切片：embeddingPort.embed(text) → RagChunk.of(...) → save
   └─ 失败语义：嵌入失败直接上抛，不留半截新版本（重入时切片数不符触发重建自愈）
```

**切分策略（写入侧契约）**：当前约定「条款级切片优先 + 超长条款按语义段二次切分」，切片须携带 `clauseRef` + `spanStart/spanEnd` 锚点（否则检索侧 fail-closed 丢弃）。终局建议引入**层级切分（parent-child / sentence-window）**以提升小块召回与大块上下文的兼顾（见 §11 R3）。

**幂等批键为什么重要**：DDL 注释已承诺「同版本不重复入库」。无此机制时，重复 ingest 会无限追加重复切片，直接污染 BM25 语料统计（同文本多副本抬升 IDF）与向量路（重复命中），且崩溃中途失败会留半截切片。

---

## 5. 读取链路（Retrieval）：受控检索全链

一次查询的检索链（代码真序，非文档设想）：

```
query
  │
  ① 租户过滤（TenantProvider.currentTenantId 硬隔离）
  │
  ② 文档级 ACL（RagAclPort.visibleDocIds）
  │     · 显式 docScope 不在可见集 ⇒ 403 AI_RAG_DOC_FORBIDDEN（绝不静默回落全租户）
  │     · 无显式作用域 ⇒ 按可见集过滤候选
  │
  ③ 容量护栏（maxScan fail-closed）：作用域切片数超限 ⇒ 422 AI_RAG_SCOPE_OVER_LIMIT
  │     （绝不静默截断——「以为检了全集」不成立时引用完整性失真）
  │
  ④ 引用强制前置：无 clauseRef / span 非法 ⇒ 剔除（不占 rerank 名额、不污染 BM25 语料），聚合 WARN
  │
  ⑤ 向量 + BM25 双路
  │     · 嵌入 provider 故障 ⇒ WARN + 降级 BM25 单路（读路径可用性优先）
  │     · 切片 embedding_ver 与当前不符 ⇒ 不入向量路（RRF 末位钳制，BM25 序主导）
  │     · BM25 语料统计每查询只算一次（O(N²)→O(N)）
  │
  ⑥ RRF 融合（k=60，权重 0.5/0.5；末位钳制防整数溢出）
  │
  ⑦ 候选池：RRF 粗序取前 candidatePool（默认 30，≥topK）送精排
  │
  ⑧ rerank（cross-encoder 精排，top-30→top-5）
  │     · provider 故障 ⇒ WARN + 回落 NoOp（RRF 粗序），引用强制语义不变
  │
  ⑨ minScore 过滤（精排后按最终分；宁缺毋滥，返回数可 < topK）
  │
  ⑩ MMR 多样性选取（λ≤0 关闭；开启时精排 3 倍扩样，按版本相符向量做多样性判断）
  │
  → RagRetrieveOutcome(results, degradations)
```

### 5.1 各阶段为何如此设计

- **混合检索（向量 + BM25 + RRF）**：稠密向量补语义召回、稀疏 BM25 补关键词/专有名词召回，RRF 免归一化融合两路名次——工业界 RAG 标配，避免单路盲区。
- **cross-encoder 精排**：RRF 是「粗融合」，cross-encoder 在 query-document 对上直接打分，精度显著更高，是「引用正确率」达标的关键一环。候选池只送前 30，控制推理成本。
- **引用强制前置（非后置）**：失效切片在打分前剔除，避免污染 BM25 语料统计、不挤占 rerank 名额（否则返回有效数 < topK 且无回填）。
- **降级只走本域路径**：嵌入/重排序 provider 故障分别回落 BM25 单路 / RRF 粗序，**严禁切云端辅路**（数据不出域红线）。降级经 `RagEventSink` 事件 + 响应 `degradations` 痕迹透出，是「可观测降级」而非「假装已精排」。

---

## 6. 强制引用与「无依据不编造」

这是 RAG 的立身之本，也是 BONE-AI 与「裸 LLM 瞎编」的根本区别：

- **锚点强制**：每条 `RagChunk` 必须带 `clauseRef` + 合法 `span`，否则在检索链 ④ 前置剔除。
- **生成侧契约**：`RagResult` 把出处锚点原样交给生成侧；生成响应须可回溯到具体条款 span。业务「建议 + 人工裁决」形态下，模型只给建议 + 证据引用，资金/责任/数值判定入口只收 `Confirmed`（HITL 裁决或规则产出）。
- **无依据路径**：当检索为空或 `minScore` 过滤后无命中，生成侧应走「无依据」显式路径（声明无法基于语料作答），而非硬凑答案——这正是 `minScore` 护栏存在的意义。

---

## 7. 安全与隔离

| 层 | 机制 | 形态 |
|---|---|---|
| 租户 | `tenant_id` 硬隔离（检索链第一环） | 已落地 |
| 文档级 | `RagAclPort` 端口 + 默认 `NoDocAcl` 兜底 | seam 已落地；**接入方 ACL 适配（文档中心）为 L3 硬依赖** |
| 越权 | 显式 docScope 不在可见集 ⇒ 403 fail-closed | 已落地（负向探针测试） |
| 身份边界 | 端口不接收 user 入参，身份由实现从上下文解析 | 防「伪造身份参数绕权」，与 `TenantProvider` 同构 |

> **设计取舍**：文档权限模型归属文档中心，rag 只消费「可见 doc_id 集合」契约、不重复建设权限存储。首批内部试点未配 ACL 实现时，存在同租户内跨文档越权面——这是已知待闭环项，接入方以 `@Bean` 覆盖后 fail-closed 立即生效。

---

## 8. 韧性设计与降级口径

| 故障点 | 降级行为 | 语义保证 |
|---|---|---|
| 嵌入 provider 挂 | WARN + 回落 BM25 单路 | 引用强制不变；响应 `degradations=EMBEDDING_DEGRADED` |
| 重排序 provider 挂 | WARN + 回落 RRF 粗序 | 引用强制不变；响应 `degradations=RERANK_DEGRADED` |
| 作用域超容量 | 422 fail-closed | 显式失败，绝不静默截断 |
| 切片版本不符（换模型） | 该切片不入向量路 | 防静默 0 分，BM25 序主导 |

**降级哲学**：读路径可用性优先，但降级必须**可观测**（事件 + 响应痕迹码），绝不允许「假装全功能」。这与「护栏取值哲学」一致——容量问题显式失败（调用方知道发生了什么），阈值问题按实测分布校准（默认不误杀）。

---

## 9. 可观测性

- **事件下沉**：`RagEventSink` 端口 + `RagEvent(type, detail)`；默认 `LoggingRagEventSink` 结构化 WARN（保持既有可观测性不降级）。
- **降级 / 数据卫生痕迹**：`EMBEDDING_DEGRADED` / `RERANK_DEGRADED` / `DROP_INVALID_CHUNKS`，既进事件通道，也透出到响应 `degradations`——接入方/评测管线可直接消费，不必翻服务端日志。
- **目标态**：接入方以 `@Bean` 覆盖 `RagEventSink` 为 bone-ai-trace 落库 + 告警适配，检索链零改动（L3）。
- **OTel `gen_ai.*` 打点**：仍为 L3 依赖项（需 Micrometer，走 CVE/SBOM 门禁）。

---

## 10. 配置参考（`bone.ai.rag.*`）

| 配置项 | 默认 | 说明 |
|---|---|---|
| `embedding.base-url` | 空 | 配则 `HttpBgeEmbeddingClient`（bge-m3），空则回落过渡哈希 |
| `embedding.model` | `BAAI/bge-m3` | 嵌入模型 |
| `embedding.version` | `bge-m3@http` | 写入 `chunk.embedding_ver`，不符旧向量 fail-closed 跳过 |
| `embedding.timeout` | 10s | |
| `rerank.base-url` | 空 | 配则 `HttpBgeRerankClient`（bge-reranker-v2-m3），空则 `NoOpReranker` |
| `rerank.model` | `BAAI/bge-reranker-v2-m3` | |
| `rerank.timeout` | 10s | |
| `candidate-pool` | 30 | RRF 后送精排候选数（≥topK） |
| `min-score` | 0.0 | 精排后分数阈值（默认不启用；量纲双轨，按评测集校准） |
| `max-scan` | 10000 | 容量护栏（fail-closed） |
| `mmr-lambda` | 0.0 | 多样性权重（λ≤0 关闭；λ∈(0,1) 生效；开启时精排 3 倍扩样） |

> `bone-ai-app` 侧另有 `rag-top-k` / `rag-min-score` / `rag-max-scan` 应用级默认与请求级覆盖（`RagRetrieveRequest` 支持请求级 `minScore`/`maxScan`/`topK` 透传）。

---

## 11. 业界最佳实践对齐矩阵与路线图

下表将 BONE-AI RAG 现状对标 2026 业界最佳实践，并标注差距与路线。

| # | 业界最佳实践 | BONE-AI 现状 | 判定 | 路线 |
|---|---|---|---|---|
| B1 | 混合检索 + RRF | 向量 + BM25 + RRF(k=60) 已落地 | ✅ 达成 | — |
| B2 | cross-encoder 精排（top-30→top-5） | `RerankPort` + `HttpBgeRerankClient` 条件装配 + 降级 | ✅ 达成 | — |
| B3 | 强制引用接地 / 无依据不编造 | 引用前置 fail-closed + 锚点契约 | ✅ 达成 | — |
| B4 | 元数据隔离（租户 + 文档级 ACL） | 租户硬隔离 + `RagAclPort` seam | ⚠️ ACL 适配待 L3 | R1：文档中心 ACL 接入 |
| B5 | 切分策略（层级 / 重叠 / 调优） | 条款级 + 语义段二次切分 | ⚠️ 未层级化 | R3：parent-child / sentence-window |
| B6 | 查询改写 / 分解（multi-query / HyDE / step-back） | **未落地**（决策：批二按评测回流启动，不建零消费方 machinery） | ❌ 缺口 | R2：编排侧多查询合并预留接线 |
| B7 | 专用向量库 + ANN（HNSW） | 过渡 MySQL CSV + 应用层余弦；终局 pgvector 推迟至实测 | ⚠️ 过渡态 | R4：pgvector 终局 + ANN 索引 |
| B8 | 评测体系（RAGAS：faithfulness / context precision-recall / answer relevancy） | Python sidecar 预留，未建 CI 门禁 | ❌ 缺口 | R5：评测集 + 指标字典 + CI 负向探针 |
| B9 | RAG 接入生成编排（检索结果约束 LLM） | R 已成型，G 接线未接 gateway/orchestration | ❌ 缺口 | R1（见下）：编排检索接地闭环 |
| B10 | 语义缓存 / 查询去重 | 未落地 | ❌ 缺口（低优先级） | 批三评估 |
| B11 | 多样性控制（MMR） | `MmrSelector` 选取式，默认关，按评测校准 | ✅ 达成 | — |
| B12 | 可观测（降级痕迹 + trace 落库） | `RagEventSink` + 响应 `degradations`；trace 适配留 L3 | ⚠️ trace 待接 | R6：trace 落库适配 |
| B13 | 容量护栏（扫描上限 / 索引下推） | `maxScan` fail-closed | ✅ 过渡态达成 | 随 R4 索引化迁移触发信号 |

### 路线图（按依赖性排序）

- **R1（接 G，最高优先）**：把 RAG 接入 `bone-ai-orchestration` 生成编排 + `bone-ai-gateway` 门面，形成「检索 → 注入 → LLM 强制引用接地」闭环；同步完成文档中心 ACL（`RagAclPort`）接入，消除同租户跨文档越权面。这是「RAG 真正可用」的标志。
- **R2（召回提升）**：查询改写/分解（multi-query / HyDE / step-back），按评测集回流标定收益后启动；接线点预留于编排侧。
- **R3（切分质量）**：层级切分（parent-child / sentence-window），兼顾小块召回与大块上下文。
- **R4（规模与性能）**：pgvector 独立实例 + ANN（HNSW）索引；过渡态 `maxScan` 护栏阈值即迁移触发信号。不为想象规模预付 ES 第三套检索栈的运维税。
- **R5（质量闭环）**：RAGAS 指标（faithfulness / context precision-recall / answer relevancy）+ 评测集 + CI 门禁 + 对抗负向探针（rerank 越权、无依据路径）。
- **R6（可观测闭环）**：`RagEventSink` 覆盖为 bone-ai-trace 落库 + 告警；OTel `gen_ai.*` 打点接入。

---

## 12. 生产就绪清单（RAG 专项）

| 项 | 状态 |
|---|---|
| 混合检索 + RRF + rerank + 引用强制 fail-closed | ✅ 代码真序 |
| 条件装配（embedding/rerank base-url）+ 配置面注册 | ✅ |
| doc_version 幂等 ingest + 残缺重建 | ✅ |
| 容量护栏（maxScan）+ minScore 过滤 + MMR | ✅ |
| 文档级 ACL seam + 403 fail-closed | ✅ seam / ⚠️ 接入方适配待 L3 |
| 降级可观测（事件 + 响应痕迹） | ✅ |
| ArchUnit 门禁（12 条） + 单测（43 绿） | ✅ |
| RAG 接入生成编排（G 接线） | ❌ R1 |
| 文档中心 ACL 适配 | ❌ L3 |
| pgvector 终局 + ANN | ❌ R4 |
| 评测体系（RAGAS + CI） | ❌ R5 |
| 查询改写 | ❌ 批二决策 |
| trace 落库适配 | ❌ L3 |

---

## 13. 关键设计决策回顾（防回归）

1. **检索与生成解耦**：rag 只做受控检索，不自带 LLM；生成与判定归 gateway/orchestration。保证「无出处不得生成」是跨模块的契约而非 rag 内自嗨。
2. **降级只走本域路径、且可观测**：双路架构的意义是单路可用；但严禁切云端（数据不出域）。降级显式透出，不假装全功能。
3. **护栏取值哲学**：容量问题显式失败（调用方知情），阈值问题按实测分布校准（默认不误杀）。
4. **幂等批键先于一切**：写入侧幂等 + 残缺重建，避免重复切片污染语料与向量、避免崩溃半截。
5. **引用强制前置而非后置**：失效切片不进打分，保护 BM25 语料与 rerank 名额。
6. **不做零消费方 machinery**：查询改写未落地即源于此纪律（此前 `HttpBge*Client` / `RerankPort` 零接线的前车之鉴）——未接 producer 的能力不先建合并机制。
