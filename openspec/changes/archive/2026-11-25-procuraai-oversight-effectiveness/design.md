---
comet_change: procuraai-oversight-effectiveness
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-25-procuraai-oversight-effectiveness
status: final
---

# Design

## Context

`bone-procurement-ai-governance` 已有完整的人工终决台账：`HumanFinalDecision` 记录 `reviewDurationMs`、`reason`（推翻理由）、`aiConclusion`/`finalConclusion`（判定是否 override）、`evaluationFeedbackRaised`，并通过 `HumanDecisionLedgerAdapter` 以 SDK 仓储持久化（`HumanDecisionEntity` 各列齐备）。上一轮 `ai-drift-metrics` 已建立"领域纯函数三态判词 + 服务端配置阈值 + 只读投影 + fail-closed"的成熟范式。本设计复用该范式，把监督质量的三类失真（耗时过短/无理由推翻/未回流评测）纳入同一套只读观测，不引入模型、不改 DDL、不改 CI 门禁集合。

## Goals / Non-Goals

**Goals:**
- 用领域纯函数基于人工终决真实记录计算监督有效性三态判词，识别三类监督质量失真。
- 提供只读、带权限、无副作用的治理端点。
- 阈值服务端配置、缺省 fail-closed、客户端不可注入。
- 完整测试与内部模拟 POC 证据，做到机器可验证。

**Non-Goals:**
- 不接真实模型、不调用 LLM、不做评分/推理。
- 不改 DDL/表/列、不新增端口实现（仅扩展既有 `HumanDecisionLedger` 只读查询）、不改 evalset、不改 CI 三入口判据集合、不改 ProcuraAI 文档族真源。
- 不自动改变能力状态、不放开任何自治档、不声明模型效果或监督充分性达标、不替代人工终决。

## Decisions

### D1 领域纯函数裁决，复用 DriftSignal 范式
新增 `OversightVerdict`（三态枚举 + `isActionable()`）、`OversightThresholds`（record，`reviewDurationFloorMs`/`minSampleSize`/`fastReviewRatioThreshold`，缺省 `null` 未配置 + fail-closed 校验）、`OversightSignal`（结果 record，含三类真实计数、原因码、`claimsModelHealthy()=false`）、`OversightSignalPolicy`（纯函数裁决）。
**理由**：与 `DriftSignalPolicy` 同构，便于测试、便于 owner 复用判读习惯；纯函数不依赖 Spring/IO，可被 TDD 完整覆盖。
**备选**：在应用服务内内联判断——被否，因为无法独立做负向探针与变异测试。

### D2 数据来自既有人工终决台账，只读扩展不新增端口
`HumanDecisionLedger` 新增 `List<HumanFinalDecision> findByCapability(CapabilityCode code)`；`HumanDecisionLedgerAdapter` 复用其私有 `query("capabilityCode", code.value())` 实现；`InMemoryHumanDecisionLedger`（已退役骨架）同步补齐方法签名以保持接口一致。应用服务据此用 stream 统计样本数与三类风险计数。
**理由**：数据已在库、字段齐备，只读扩展风险最低；不新增 DDL/表/仓储，符合范围边界。
**备选**：新增独立 repository/端口——被否，超出范围且引入双写风险。

### D3 阈值仅由服务端配置提供，缺省未配置
`AiGovernanceProperties` 新增 `oversightReviewDurationFloorMs`（`Long`，默认 `null`）、`oversightMinSampleSize`（int，默认 20）、`oversightFastReviewRatio`（`Double`，默认 `null`）。Controller 只读服务端属性构造 `OversightThresholds`，端点方法只接受路径参数 `code`，不接收任何阈值请求参数。
**理由**：owner 通过线不可由客户端注入（上一轮独立审查 I1 的教训）；阈值缺省未配置即 `UNDETERMINED`，fail-closed。

### D4 只读端点复用现有权限码与投影范式
`GET /api/v1/governance/capabilities/{code}/oversight` + `@PreAuthorize("hasAuthority('procurement:ai-governance:read')")`，返回 `ApiResponse<OversightMetricsView>`（含 `modelHealthClaim=NONE`、`requiresOwnerReview`）。契约测试锁定端点数量、权限码与方法签名（只 `String.class`）。
**理由**：与 `drift` 端点一致，最小权限面、无新权限码、无 DB 写。

### D5 三类风险优先于比率，未触阈不构成背书
裁决顺序：非法输入拒绝 → 阈值未配置 `UNDETERMINED` → 样本不足 `UNDETERMINED` → 无理由推翻/未回流评测任一存在即 `REVIEW_REQUIRED` → 耗时过短占比触阈 `REVIEW_REQUIRED` → 否则 `NO_ACTIONABLE_SIGNAL`。任何判词 `claimsModelHealthy()` 恒为 false。
**理由**：推翻必须留理由并回流是硬治理要求（不是统计概率），出现即需 owner 复核；比率阈值用于"橡皮图章"这种规模化失真。
**边界**：`evaluationFeedbackRaised` 由既有人工终决写入路径在 override 时自动置真（上一轮已交付的契约），本 change 是只读护栏、不改该语义；因此"未回流评测"仅覆盖台账中标记确为假的历史遗留/外部写入记录。让新数据由真实回流流程显式赋值属人工终决契约变更，留 owner 独立立项，不在本 change 范围。

## Risks / Trade-offs

- [风险] 推翻理由可能含 PII → 缓解：只统计"是否为空"，不回传 `reason`/`output` 原文；投影只含计数与原因码。
- [风险] 阈值未配置时若把全部样本算作耗时过短，`UNDETERMINED` 判词下仍会外泄"100% 橡皮图章"的误导计数 → 缓解：`reviewDurationFloorMs` 未配置时 `fastReviewCount` 直接置 0，并以负向测试锁定。
- [诚实边界] "未回流评测"对新 API 数据不可达（既有写入路径自动置真）→ 缓解：spec/design 明示仅覆盖历史遗留/外部写入记录，应用测试显式断言，Verify 报告列为留给 owner 的独立立项项，不声称护栏已验证新数据回流。
- [风险] `findByCapability` 全量 load 记录在大租户下可能偏重 → 缓解：本期为只读治理端点、低频调用；不在主业务链路；后续如需要再由 owner 立项分页聚合。
- [风险] 已退役 `InMemoryHumanDecisionLedger` 与 SDK adapter 接口不同步导致编译失败 → 缓解：同批改方法签名，编译期即可发现。
- [风险] 比率阈值边界（等于阈值）口径分歧 → 缓解：明确 `>=` 触阈，与 drift 一致，并加边界测试。

## Migration Plan

无数据迁移。新增字段默认值使配置缺省为未配置，行为 fail-closed；部署后由 owner 显式配置阈值才会产生 `REVIEW_REQUIRED`/`NO_ACTIONABLE_SIGNAL`。回滚只需移除端点与领域类，无需数据清理。

## Open Questions

无。所有会影响规格与任务拆分的问题均已在探索阶段由源码实证解决。
