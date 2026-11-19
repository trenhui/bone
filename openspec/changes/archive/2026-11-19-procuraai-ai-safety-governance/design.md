---
comet_change: procuraai-ai-safety-governance
role: technical-design
canonical_spec: openspec
---

# 设计：procuraAI 安全治理（批次3）

## Context

批次1 的 `bone-procurement-ai-governance` 已提供单能力 fail-closed 与人工终决。D3-v2 §3.1 C-4/C-1/E-5 与蓝图 F3/F4/G15 要求全域停用、生成内容双标识、算法公平护栏——三者当前零实现。可复用基础：claim 侧 `AiRolloutApplicationService:292` 的「读库即时生效 kill switch」与 `FeatureTenantToggleService:47` 的「全局总开关最终裁决」范式；批次1 的端口/追溯链/权限码/能力开关。

## Goals / Non-Goals

**Goals**：交付全域 kill switch（缺省全停 + 一键停用 + 演练）、双标识出域拦截（LABEL-01）、公平确定性护栏（FAIR-01/02）的可运行代码 + 可达测试符号；全族门禁双绿。

**Non-Goals**：模型效果数字、法务定性、owner 通过线、evalset 扩容、生产 DDL、真实 MQ/trace 接线。

## Decisions

**D1 — 在现有 governance 模块扩展 `SafetyApplicationService`，不新建模块。**
三者都是 fail-closed 安全护栏，复用 registry/trace/actorPort/权限码/能力开关；独立 `SafetyController` 保持端点边界清晰，避免污染批次1 的裁决服务。

**D2 — kill switch 三态 + 缺省 STANDBY 不放行（fail-closed）。**
`STANDBY/ENGAGED/DISENGAGED`，只有显式 DISENGAGED 允许 AI 路径；无仓储记录按 STANDBY；每次裁决读最新快照无缓存（读库即时生效）。停机优先：任意态可 ENGAGED，仅 ENGAGED 可 DISENGAGE。

**D3 — 双标识缺一阻断，不因自报豁免。**
`ContentLabel` 必须有 EXPLICIT + IMPLICIT；`DeliveryDecision.decide` 优先报缺隐式（机器强校验）；`aiGenerated=false` 自报不豁免（生成合成内容按内容属性）。不碰法务定性。

**D4 — 公平护栏为确定性规则，testSymbol 对齐 evalset。**
`FairnessRule` 按受控枚举 + 建议文本 token 判定；FAIR-01 `ownershipBias_blocksModelPromotion`、FAIR-02 `regionalPreference_noSuggestionEmitted` 方法名/expected 与 evalset 精确对齐；命中阻断晋升（工程永不晋升模型版本）+ 伦理审查标记；UNKNOWN 特征 fail-closed。

**D5 — 不改 evalset caseCount / 不改 FULL_GATE_KEYS。**
6 端点走 A10.9 豁免；测试类为工程落地，`testClass` 指向新类但不扩 seed 数字；evalset status 恒 seededDraft。

## Risks / Trade-offs

- [确定性护栏非统计公平] → 明确定位为回归/调试护栏，统计公平与阈值属 backlog，不冒充。
- [双标识法务适用性未定] → 只做技术载体与阻断，不宣称合规达标。
- [内存 kill switch 非生产持久化] → L3 DDL，受控内存跑通；不伪造持久化完成。
- [Hook 枚举 stale Run 阻断写入] → 临时 allow_paths 放行，交付后还原配置。

## Migration Plan

1. governance 模块新增领域模型（kill switch/labeling/fairness）+ 端口 + 应用服务 + 端点。
2. TDD：`clean test` 82 例全绿（含 13 ArchUnit + FAIR/LABEL 对齐测试）。
3. openapi 6 端点 + A10.9 豁免 + 门禁双绿。
4. 回滚：能力开关设 false 下线端点；模块整体回退无共享表写入。

## Open Questions

法务定性、模型效果通过线、样本扩容、人月估工属已登记 owner/L3 决策项，不阻塞本批。
