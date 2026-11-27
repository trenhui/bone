# Comet Design Handoff

- Change: claim-rag-clause-retrieval
- Phase: design
- Mode: compact
- Context hash: 781107bb848fd1d2ca0070770e51babb34c1716db7f4d9c00af485c7effd84a7

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/claim-rag-clause-retrieval/proposal.md

- Source: openspec/changes/claim-rag-clause-retrieval/proposal.md
- Lines: 1-27
- SHA256: bef5996047e706576fa7b46320fabd645f47b31d5ae6741e2ed59e28c6a15f66

```md
## Why

理赔侧 `RagKnowledgeRetrievalPortAdapter` 已写好，平台 `bone-ai-rag` 模块完整，但默认门控 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED=false`，且底层为 `TransitionalHashEmbedding`（非语义哈希）+ `NoOpReranker` + `DenyDocAcl`（默认拒绝）。PRD §14 V2.0 要求「RAG 条款检索」。本 change 在**不接真实商业 provider 凭证、不改默认关闭门控、引用不强制则 fail-closed** 的前提下，用内部模拟 embedding/rerank 验证「门控打开即可用、关闭行为不变」的链路，为后续真实 provider 接入提供工程前置。

## What Changes

- **门控契约**：明确 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 关闭时行为与现网完全一致；打开时走 RAG 检索链路。
- **内部模拟链路验证**：用内部模拟 embedding/rerank 端点验证检索→引用→ACL 全链路可达，不接真实商业 provider 凭证。
- **引用强制与 ACL**：无可追溯引用则拒答（fail-closed）；ACL deny 默认拒绝生效。
- **规格与门禁**：新增/对齐 capability spec 定义门控语义、引用强制、ACL、fail-closed；补变异测试。

**非目标（明确不做）**：不接真实商业 provider 凭证与模型；不改默认关闭门控；不宣称语义检索准确率达标；不改资金闸或赔付语义；不新增 DDL。

## Capabilities

### New Capabilities
- 无。

### Modified Capabilities
- 无。现有 `claim/ai-knowledge-rag` spec 已完整规定门控默认关闭、fail-closed、引用强制与可观测等 REQUIREMENT；本 change 是**在既有规格内用内部模拟 provider 验证链路可达 + 补工程证据**，不改变任何 spec 级行为，不新增/放宽判据。因此本 change 设置 `skip_specs: true`（纯工程验证与证据，无行为规格变化）。

## Impact

- **代码**：`bone-apps/synap-claim/synap-claim-app`（RagKnowledgeRetrievalPortAdapter 接线验证）→ 主要是配置契约、测试与内部模拟 provider 验证，不改平台骨架构型。
- **配置**：`BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 门控语义；真实 embedding/rerank base-url 留待后续接入。
- **测试**：门控关闭/打开双态、引用强制、ACL deny、fail-closed 场景测试。
- **不受影响**：默认门控状态、资金路径、Go-Live 合规分册、判据本体。

```

## openspec/changes/claim-rag-clause-retrieval/design.md

- Source: openspec/changes/claim-rag-clause-retrieval/design.md
- Lines: 1-38
- SHA256: 86d90f1c7803668f3b608ef1669b80b47f1d8ed0ebaa496f2d380599d09dfb82

```md
## Context

理赔侧 `RagKnowledgeRetrievalPortAdapter` 已接线，平台 `bone-ai-rag` 模块完整（RagApplicationService/GroundingGuard/RrfFuser）。门控 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 默认 false，底层为 `TransitionalHashEmbedding`+`NoOpReranker`+`DenyDocAcl`。现有 `claim/ai-knowledge-rag` spec 已完整规定门控/fail-closed/引用强制/可观测。详见 proposal.md。

## Goals / Non-Goals

**Goals:**
- 用内部模拟 embedding/rerank 端点验证「门控打开即可用、关闭行为不变」链路
- 产出可审计的工程证据（门控双态、引用强制、ACL deny、fail-closed）
- 不改任何 spec 级行为

**Non-Goals:**
- 不接真实商业 provider 凭证、不宣称语义准确率、不改默认门控、不碰资金闸、不新增 DDL

## Decisions

**决策 1：复用内部模拟 provider（同 POC 的 `poc-internal-v1` 思路）验证链路。**
用契约合规的内部模拟 embedding/rerank（带审计标记、非真实凭证）证明 HTTP 出站→解析→引用可追溯链路可达。备选是接真实 provider——否决，本轮范围明确排除真实凭证，且不涉及准确率验收。

**决策 2：默认门控不动，双态验证。**
测试覆盖门控关闭（行为=现网）与门控打开（经模拟 provider 检索）两态，默认配置保持 false。备选是默认打开——否决，违反范围与 fail-closed 原则。

**决策 3：引用强制与 ACL 沿用现有机制，只验证不改写。**
`DenyDocAcl` 默认拒绝、无引用拒答均为现有规格，本 change 只做验证证据，不改实现。

## Risks / Trade-offs

- [内部模拟被误读为真实语义能力] → 所有证据标注 internal-simulated，明确不证明准确率
- [门控打开影响现网] → 默认 false 不变，双态测试钉死关闭行为一致
- [无引用作答] → 沿用 fail-closed，测试验证拒答

## Migration Plan

无数据迁移、无默认配置变更。内部模拟端点仅用于测试环境验证，不进入生产配置。后续真实 provider 接入为独立变更。

## Open Questions

无。

```

## openspec/changes/claim-rag-clause-retrieval/tasks.md

- Source: openspec/changes/claim-rag-clause-retrieval/tasks.md
- Lines: 1-19
- SHA256: f048117ffe5567230c6487306674bb2599184a7e317c01b307bfea68f6a03bd1

```md
## 1. 现状与规格确认

- [ ] 1.1 核对 `claim/ai-knowledge-rag` 现有 spec 四条 REQUIREMENT 与适配器实现一致；验收：逐条确认并引用文件+行号 <!-- comet-task:fa14433d-7c48-4b0e-8766-5f5b13fbd3a2 -->
- [ ] 1.2 确认门控 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 默认 false 且为唯一开关；验收：grep 确认 <!-- comet-task:873db42c-3ebf-4ca1-acd2-1ac3d0a2863d -->

## 2. 门控双态链路验证（内部模拟 provider）

- [ ] 2.1 门控关闭态：验证知识层行为与现网完全一致、无检索调用；验收：关闭态测试通过 <!-- comet-task:e92f29b3-7a22-4535-95ca-a3fef30b4efe -->
- [ ] 2.2 门控打开态：用内部模拟 embedding/rerank 端点跑通检索→引用→ACL 链路；验收：返回带可追溯引用，测试通过 <!-- comet-task:b8adb0e9-425d-4dcc-9770-7cebcad3eafd -->
- [ ] 2.3 引用强制 fail-closed：无引用检索结果被拒、不进入知识依据；验收：注入无引用场景测试通过 <!-- comet-task:559f6a98-30fc-47f5-aeaa-0daa18c8af7f -->
- [ ] 2.4 ACL deny 生效：默认拒绝策略不被绕过；验收：ACL 场景测试通过 <!-- comet-task:a95e682b-c458-4942-95fb-a5233d67aaf3 -->
- [ ] 2.5 语料缺失/服务不可用返回 available=false 带成因码；验收：缺语料场景测试通过 <!-- comet-task:50427780-f40c-4736-aba8-f6896c413c2c -->

## 3. 工程证据与门禁

- [ ] 3.1 所有内部模拟证据标注 internal-simulated、不接真实凭证；验收：grep 确认无真实凭证、证据含标注 <!-- comet-task:157fe0f7-3027-4bf2-83b1-09cb35829da7 -->
- [ ] 3.2 确认不放宽 12 项 AI 原生就绪判据；验收：复核器判据本体未改 <!-- comet-task:19bc27e4-20f2-442b-9f10-5ee51614b6ef -->
- [ ] 3.3 补变异双向钉子（门控双态/引用强制/ACL）；验收：改一处必红还原必绿 <!-- comet-task:040b4e34-e50c-4d91-970b-b1f7b61093da -->
- [ ] 3.4 跑相关测试全绿；验收：mvn 测试通过，测试数从收据取真实值 <!-- comet-task:8594a407-2df0-428d-bb2f-4c61b0d9508c -->

```

## openspec/changes/claim-rag-clause-retrieval/.openspec.yaml

- Source: openspec/changes/claim-rag-clause-retrieval/.openspec.yaml
- Lines: 1-3
- SHA256: 278dc26b0b2af5c466b0d1a5a519d3cd8310cc0d213a8190f0f81d02cc742699

```md
schema: spec-driven
created: 2026-11-23
skip_specs: true

```
