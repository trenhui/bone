---
comet_change: procuraai-governance-posture-board
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-26-procuraai-governance-posture-board
status: final
---

# Design：procuraAI 治理态势聚合看板

## Approaches

- **方案 A（选用）**：后端在 ai-governance 模块内新增单一只读聚合端点 `/posture` 聚合能力分项（裁决/漂移/监督），readiness 与 kill switch 由前端从既有端点补取，汇总判词在领域层确定性推导。取舍：汇总逻辑可在后端确定性单测、模块边界不破坏、复用既有 service；代价是前端消费两个数据源。
- **方案 B（否决）**：纯前端聚合，不新增后端端点。取舍：零后端改动，但判词规则散落前端、无法后端单测、前端直连多模块端点，违背单一真源，否决。
- **方案 C（否决）**：跨模块后端聚合 readiness + governance + disclosure。取舍：一次拿全，但引入 ai-governance→app 反向依赖或新聚合模块、破坏模块边界，且 disclosure 为 per-review 无全局态可聚合，否决。

## Context

procuraAI 治理信号已分散落地：`AiGovernanceApplicationService` 提供 `listCapabilities()` / `decide(code)` / `driftSignal(code, thresholds)` / `oversightSignal(code, thresholds)` 与 `countOverrides(code)`；全域 kill switch 与就绪度事实由 `bone-procurement-app` 的 `AiReadinessController` 提供；合同审查披露为 per-review 端点 `GET /{reviewId}/disclosure`。前端 `GovernanceStatus.tsx` 仅消费 readiness 一个端点。缺口不在能力本身，而在「一处聚合、确定性汇总、诚实渲染」。本轮严格只读，不触碰人工终决、模型接入、owner 阈值与 CI 判据集合。

## Goals / Non-Goals

**Goals:**

- 在 ai-governance 模块内新增单一只读聚合端点，复用既有 service 方法，不新增跨模块服务依赖。
- 领域层新增确定性汇总判词，规则单一真源：`UNDETERMINED` 优先于 `ATTENTION_REQUIRED` 优先于 `ALL_CLEAR`。
- 前端把治理页升级为态势看板，未知码显示原始码、取数失败显示「无法确认」、披露以入口呈现。
- 后端与前端都以失败/未知为安全侧，不因缺省数据渲染成「通过」。

**Non-Goals:**

- 不接真实模型、不声明模型效果达标、不修改评测集下界与 owner 冻结阈值。
- 不修改现有治理 spec（readiness/drift/oversight/decision-governance/human-review）的行为需求。
- 不新增写端点、不暴露 PII 或模型输出原文、不把披露伪造成全局状态。
- 不改 DDL、不改 CI 三入口判据集合、不改权限码。

## Decisions

1. **聚合位置 = ai-governance 应用服务**：新增 `posture()` 方法，遍历 `listCapabilities()`，对每个能力调用 `decide` / `driftSignal` / `oversightSignal`，组装 `GovernancePostureView`。理由：这些能力都在同一模块与同一 service 上，聚合无需跨模块依赖；readiness 与 kill switch 由前端从已有 `readinessApi` 单独取，避免 ai-governance 反向依赖 app 模块。
2. **阈值来源单一**：漂移/监督阈值仍由 `AiGovernanceProperties` 提供，缺省即 `unconfigured()` → 分项 `UNDETERMINED`。端点只接受路径/无参数，不暴露任何阈值查询参数。
3. **汇总规则领域化**：新增 `PostureVerdict` 枚举与 `GovernancePosturePolicy`（纯函数）。规则：任一分项为 UNDETERMINED 或能力缺失 → `UNDETERMINED`；否则任一能力 drift/oversight actionable 或全域总闸停用 → `ATTENTION_REQUIRED`；否则 `ALL_CLEAR`。kill switch 状态由前端传入视图组合，领域策略只消费已结构化的分项判词。
4. **视图不含敏感原文**：`GovernancePostureView` 与能力明细只含 code / displayName / state / verdict 文本与计数，不回传 reason 原文、model output、PII。
5. **前端诚实渲染**：新增 `governanceApi.ts` 与 `governancePostureView.ts`（含 `verdictTextOrRaw` 同型映射）；未知码回退原始串；取数失败统一「无法确认」；披露区域渲染到 ContractReviewDisclosure 页的入口。
6. **测试策略（TDD）**：先写领域策略测试、应用聚合测试、控制器契约测试、前端视图映射与页面渲染测试（RED），再实现（GREEN）。复用现有 `@ConditionalOnAiGovernanceEnabled` 开关测试与权限断言。

## Risks / Trade-offs

- **聚合 N+1 调用**：每个能力三次 service 调用，能力数量有限且治理为低频只读端点，可接受；若未来能力规模上升再考虑批量查询，本轮不做。
- **跨模块就绪度未纳入后端汇总**：readiness/kill switch 由前端组合展示，后端汇总判词不含 readiness 维度；已在 spec 明确汇总只覆盖能力分项，避免语义漂移。
- **前端未知码兼容**：旧后端或新增判词码时以原始码呈现，可能显示英文枚举值，但比压成「正常」更安全。
- **证据漂移**：审查后若改 design.md，须走 `revise-design → propose-design → decide-design → complete-design` 重走设计确认，避免 `CLASSIC_EVIDENCE_DRIFT`。
