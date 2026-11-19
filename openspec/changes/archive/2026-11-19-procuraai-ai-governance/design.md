---
comet_change: procuraai-ai-governance
role: technical-design
canonical_spec: openspec
---

# 设计：procuraAI AI 决策治理（批次1）

## Context

合同审查 walking skeleton（2026-11-19 归档）证明了「AI 能力 + fail-closed + 人工终决 + 披露」的单点链路可行，但治理仍是各能力内嵌、无统一入口。`deferred-backlog.md` 组 5 列出三项工程任务：5.1 能力三态 fail-closed + 具名责任人 + 一键停用/降级开关与演练；5.2 人工最终决策权 + 推翻留痕 + override 回流台账；5.3 分任务评测门禁 + drift 告警。本批次落地 5.1 + 5.2 的工程骨架，5.3 的评测门禁数值与脚本判据属 D-10/R-3 owner 决策，不在本批。

业界对标（2026-11-19）：NIST AI RMF 的 GOVERN/MANAGE 强调具名 owner、risk tolerance、kill switch 与审计留痕；ISO 42001 Annex A.6.2.4 与 EU AI Act Art.14 要求 human oversight by design（intervene/override/stop）；「有意义的监督」三要素 = capability/capacity/authority，且 override 必须记录理由并回流评测。这些直接映射为下面的 D1–D6。

## Goals / Non-Goals

**Goals:**
- 交付独立可运行的治理横切模块：能力登记 → fail-closed 准入裁决 → 一键停用/降级 → 人工终决 → override 回流，全链路可测、可审计。
- 以源码与单测证明 spec 的可观察行为，注入必红、还原必绿。
- 不新建 AI 底座、不改评测脚本判据、不新增 CI 判据集合。

**Non-Goals:**
- 不实现 F1–F4 地基全量、评测集扩容、分任务效果门禁数值（D-10 owner）、全域 kill switch 联动、生产 DDL 落库（L3）、真实 bone-ai-trace 接线（L3）、自动放款/授标/结案（红线）。
- 不修改合同审查骨架 spec / 代码；治理模块只提供裁决入口供后续接入。

## Decisions

**D1 — 独立限界上下文 `bone-procurement-ai-governance`，不扩展 contract 模块。**
理由：治理是横切关注点，需服务未来 M1/M5/M7/G15 等多个能力；塞进 contract 会耦合审查语义。备选被否：扩展 contract——会造成治理能力无法复用、且改动已归档骨架引入回归风险。ArchUnit `noCrossContextDomainDependency` 守护不越界。

**D2 — 能力状态机五态，登记缺省 `UNKNOWN` fail-closed。**
`AVAILABLE/DISABLED/DEGRADED/UNKNOWN/UNDETERMINED`；登记≠启用，需显式 enable；`UNKNOWN/UNDETERMINED` 不可判定态不得直接 enable、不得压成可用；停机/降级优先于可用性。理由：对齐「能力清单 + 一键停用」与 fail-closed，缺省信任即风险。

**D3 — 准入裁决为纯领域策略 `CapabilityGovernancePolicy`，无 IO。**
未登记 → `UNKNOWN_CAPABILITY`；不可判定 → `CAPABILITY_INDETERMINATE`；停用/降级 → 对应 reason；仅 AVAILABLE 放行，reasonCode 恒为 `ALLOWED_ADVISORY_ONLY`（放行也不给终决权）。理由：裁决逻辑可单测穷举、易审计；IO 与审计留痕留在应用服务。

**D4 — 操作人/复核人一律从登录态 `GovernanceActorPort` 取，不接受命令自报。**
缺登录态直接 fail-closed（`requireActor` 抛异常）。理由：审计要求具名责任人；自报操作人等于无审计。这比合同审查骨架的租户收敛更严格（治理动作必须具名）。

**D5 — 人工终决与 override 回流台账分字段存储，不存 AI output 原文。**
`HumanFinalDecision` 记录 AI 结论/置信度与人工终决/理由/耗时分列；不一致即 `isOverride()` 并触发 `evaluationFeedbackRaised=true`；`countOverrides` 供 drift 告警。理由：PII 禁入 + override 必须可回流评测（有意义监督的 authority 要素）。

**D6 — 全部状态变更与终决写入中央追溯链；openapi 端点走 A10.9 豁免，不改判据集合。**
聚合内部有不可变留痕，应用服务另写 `GovernanceTracePort` 中央链（互补）。8 个端点登记进 openapi + `EXTRA_OPENAPI_ENDPOINTS` 豁免（与合同骨架同范式），`FULL_GATE_KEYS` 不变。理由：治理审计需要统一可查的留痕；A10.17 锁死判据集合，擅自接入会转红。

## Risks / Trade-offs

- [内存实现非生产持久化] → 受控内存跑通链路，`t_proc_ai_capability` DDL 落库属 L3；不伪造持久化完成态。
- [治理模块尚无真实 AI 能力接入] → 本批提供裁决入口与 spec，M2 等能力接入属后续批次；`decide` 可独立验证，不宣称已保护生产推理路径。
- [新增 8 端点扩大门禁影响面] → 走 A10.9 豁免 + 权限种子幂等登记；self-test 探针用自制 Ctx，不受豁免集合大小影响（已验证）。
- [Hook 枚举历史 stale Run 阻断写入] → 临时用 `hook.allow_paths` 放行写入路径，交付后整份还原配置；不碰交付物、不伪造证据。

## Migration Plan

1. 父 pom 新增模块；DDD 四层骨架 + ArchUnit（已建）。
2. TDD：领域状态机 / 裁决策略 / 人工终决（53 例全绿，含 13 条 ArchUnit）。
3. openapi 8 端点 + 门禁豁免 + 权限种子 0129；跑本族门禁双绿。
4. 回滚：能力开关设 false 下线端点；模块可整体回退，无共享表写入。

## Open Questions

（本批无改变 spec / 拆分的开放问题。评测门禁数值、DDL 落库、法务结论、全域 kill switch 属已登记 owner/L3 决策项，不阻塞本批。）
