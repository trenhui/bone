---
change: claim-rag-clause-retrieval
design-doc: openspec/changes/claim-rag-clause-retrieval/design.md
base-ref: cc47d0ff52fd48fb889d14c85e0ab87a
archived-with: 2026-11-27-claim-rag-clause-retrieval
---

<!-- comet-task-authority: openspec/changes/claim-rag-clause-retrieval/tasks.md -->

# Build Plan — claim-rag-clause-retrieval（2026-11-27）

> 本计划为**验证补强型**：主体实现已在 dev，本轮不改生产逻辑、不改 spec、不改默认门控，只补验证证据与变异双向钉子。完成状态以 `tasks.md` 为准，本计划仅用 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 执行配置

- `build_mode=executing-plans` / `tdd_mode=tdd` / `review_mode=thorough` / `subagent_dispatch=null` / `direct_override=false`。

## 基线事实（2026-11-27 实证）

- 门控 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 默认 false，唯一开关位于 `application.yml:84`。
- 归档 spec `claim/ai-knowledge-rag` 四条 REQUIREMENT：受控语义检索 / 无语料或门控关闭 fail-closed / 可观测 / 默认关闭（spec.md L9-31）。
- `RagKnowledgeRetrievalPortAdapter`（`.../infrastructure/knowledge/`，L38-106）：目录准入优先 + 四态 fail-closed（STUB_NOT_INTEGRATED / RAG_RETRIEVAL_UNAVAILABLE / RAG_CORPUS_MISSING / CATALOG_UNKNOWN）。
- 现有测试 `RagKnowledgeRetrievalPortAdapterTest`（165 行，6 例）：catalogPassThrough / hitWithCitation / corpusMissing / retrievalUnavailable / nonRetrievableCausesStayPut / catalogAbsentIsHonest。
- 缺口：门控双态显式钉子、无引用 fail-closed、变异双向。

## 全局约束

- 不改 `application.yml` 门控默认值（保持 false）。
- 不改 `RagKnowledgeRetrievalPortAdapter` 生产逻辑（只补测试）。
- 所有内部模拟桩标注 `internal-simulated`，不接真实凭证/token/base-url。
- 不改 12 项 AI 原生就绪判据本体、不改资金闸/赔付语义、不新增 DDL。
- 测试数从 mvn 收据取真实值，不手写。

## 任务映射与执行顺序

### 阶段 1：现状与规格确认（证据固化）

- 1.1 核对 spec 四条 REQUIREMENT 与 adapter 实现一致，引用 `application.yml:84`、`RagKnowledgeRetrievalPortAdapter.java:56-106`、归档 spec L9-31。 <!-- comet-task-ref:fa14433d-7c48-4b0e-8766-5f5b13fbd3a2 -->
- 1.2 确认门控默认 false 且为唯一开关（grep 只命中 `application.yml:84`）。 <!-- comet-task-ref:873db42c-3ebf-4ca1-acd2-1ac3d0a2863d -->

### 阶段 2：门控双态链路验证

- 2.1 关闭态：门控关闭时走目录准入基线、`available` 与现网一致、无 RAG 检索调用（Mockito verifyNoInteractions）。 <!-- comet-task-ref:e92f29b3-7a22-4535-95ca-a3fef30b4efe -->
- 2.2 打开态：内部模拟 embedding/rerank 桩跑通 检索→引用→ACL 链路，返回带可追溯引用（source/entryId）。 <!-- comet-task-ref:b8adb0e9-425d-4dcc-9770-7cebcad3eafd -->
- 2.3 引用强制 fail-closed：注入无引用命中，断言拒答、`available=false` 带受控成因码、不进入知识依据。 <!-- comet-task-ref:559f6a98-30fc-47f5-aeaa-0daa18c8af7f -->
- 2.4 ACL deny：默认拒绝策略不被绕过（无准入目录时 `available=false`）。 <!-- comet-task-ref:a95e682b-c458-4942-95fb-a5233d67aaf3 -->
- 2.5 语料缺失/服务不可用：两场景返回 `available=false` + 受控成因码（沿用现有测试并补门控维度）。 <!-- comet-task-ref:50427780-f40c-4736-aba8-f6896c413c2c -->

### 阶段 3：工程证据与门禁

- 3.1 内部模拟桩标注 `internal-simulated`，grep 确认无真实凭证/token/base-url。 <!-- comet-task-ref:157fe0f7-3027-4bf2-83b1-09cb35829da7 -->
- 3.2 复核 12 项 AI 原生就绪判据本体未改（git diff 为空）。 <!-- comet-task-ref:19bc27e4-20f2-442b-9f10-5ee51614b6ef -->
- 3.3 变异双向钉子：门控双态、引用强制、ACL 三处，改断言必红/还原必绿。 <!-- comet-task-ref:040b4e34-e50c-4d91-970b-b1f7b61093da -->
- 3.4 跑 `mvn -pl bone-apps/synap-claim/synap-claim-app -Dtest=RagKnowledgeRetrievalPortAdapterTest test` 全绿，测试数从收据取。 <!-- comet-task-ref:8594a407-2df0-428d-bb2f-4c61b0d9508c -->

## Verification

门控双态/引用强制/ACL/语料缺失四态各至少 1 个通过测试；变异三处改必红还原必绿；无真实凭证；门控默认值未改；测试全绿。
