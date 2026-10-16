# BONE AI 能力中台 · 产品接入指南

> **版本**：v1.0　**日期**：2026-10-16　**配套**：《BONE-AI能力中台整体方案》《详细技术方案》
> **回答一个问题**：产品服务（procuraAI / ClaimSphere / Equity）如何把 bone-ai **嵌进自己的进程**。

---

## 0. 形态一句话

bone-ai 各模块是**进程内 starter（库形态，零额外跳）**，**不是独立部署的 AI 服务**。产品服务以 Maven 依赖引入、自行 `@EnableSqlRepositories` 注册仓储、复用自身 DataSource；模型调用统一经 `bone-ai-gateway` 收口。**参考宿主 `bone-ai/bone-ai-server`** 演示了完整接线 + 端到端集成测试，可照抄。

---

## 1. 当前可用能力（批一）

| 模块 | 提供 | 接入要点 |
|---|---|---|
| `bone-ai-gateway` | `LlmClient` 门面 + 成本红线 + OpenAI 兼容 / Mock 双实现 + `LlmCallRecorded` 事件发布 | 自动配置；默认 Mock，配 `base-url` 切真实 provider |
| `bone-ai-rules` | 规则资产治理（CRUD / 版本 / 灰度 / 命中标记）+ QLExpress 唯一 DSL | `RuleAssetRepository`（SDK 动态代理，无需手写实现） |
| `bone-ai-orchestration` | 编排执行器：规则预检 → 命中短路｜LLM | `OrchestrationService` |
| `bone-ai-trace` | 证据三元组（输入哈希 + IR + 引擎版本）落库 + 回放 | `EvidenceTraceListener` 订阅 `LlmCallRecorded` |
| `bone-ai-document` | 条款切分 + 统一 IR（PDFBox 起步，批二接 sidecar） | `DocumentParseService` |

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

`RuleAssetRepository` / `EvidenceSnapshotRepository` 均 `extends com.bone.metadata.sdk.Repository`，由 SDK 扫描 `*Repository` 接口自动生成代理。在产品应用的启动类加：

```java
@EnableSqlRepositories(basePackages = {
    "com.bone.airules.domain.repository",
    "com.bone.aitrace.domain.repository"
})
```

### 2.3 数据源与 SDK 配置

产品复用自身 DataSource（MySQL）；关闭统一动态数据源、开启 SDK SQL 通道。最小配置见 `bone-ai-server/src/main/resources/application.yaml`（`bone.metadata.sql.*` + `metadata.sdk.deployment-mode=EMBEDDED`）。

### 2.4 建表

`rule_asset`、`ai_trace_snapshot` 已在 `bone-init.sql`（《数据库开发规范》§2.12），含 HC-008 四必备列 + 软删。

### 2.5 成本红线 + 证据链（默认自动装配，产品零额外代码）

- **成本红线**：`OrchestrationService` 强制"规则命中（`RULE_HIT_BLOCK`）⇒ 跳过 LLM、不发请求、不发事件"——不依赖调用方自觉。
- **证据链**：每次真实 LLM 调用，`bone-ai-gateway` 发布 `LlmCallRecorded` → `bone-ai-trace` 监听落证据快照（输入 SHA-256 + IR + 引擎版本）；业务代码无直接写快照的旁路。回放经 `EvidenceQueryService.replayByTraceId`。

接入编排即同时获得两者。

### 2.6 模型

默认 `MockLlmClient`（开发 / 测试 / 成本红线探针）；切真实 OpenAI 兼容 provider 时配置 `bone-ai-gateway` 的 `base-url` / `api-key`，`LlmClient` 契约与红线 / 证据装配不变。

---

## 3. 参考宿主 `bone-ai-server`（blueprint 风格，非生产拓扑）

- **是什么**：把五个库接线成一个可运行 Spring Boot 应用 + 承载端到端集成测试的"活模板"。
- **为什么不是服务**：生产形态是产品服务进程内依赖这些库；独立 AI 服务会多一跳、违背"库形态优先"。
- **集成测试** `AiPipelineIntegrationTest`：真实服务 + 进程内事件流 + mock 仓储，证明
  - 规则命中（hitBlock）⇒ 短路，不调 LLM、不留痕；
  - 规则未命中 ⇒ 调 LLM ⇒ `LlmCallRecorded` ⇒ trace 落证 ⇒ 按 traceId 回放。
- **运行**：需 MySQL（同 `bone-metadata-server`）；`POST /api/ai/v1/pipeline` 为演示端点。

---

## 4. 批二 / 三预告

`bone-ai-rag`（混合检索 + 引用强制）、document 完整管线（Docling sidecar）、eval 门禁扩指标、notification / integration AI 节点、bone-ai-app。范围与出口判据见《详细技术方案》§5。

---

## 5. 红线（不变式，ArchUnit / 结构强制）

- 资金 / 责任 / 数值 / 合规结论走 `bone-ai-rules` + 代码，**不进 LLM**。
- AI 输出落业务表前必须有 `Confirmed` 溯源（`decided_by` / `evidence_ref`）。
- 证据链无旁路；模型 HTTP / key 只出现在 gateway。
