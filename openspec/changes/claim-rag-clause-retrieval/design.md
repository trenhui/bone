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
