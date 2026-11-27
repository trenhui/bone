---
comet_change: procuraai-drift-metrics
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-25-procuraai-drift-metrics
status: final
---

# Design

## Context

procuraAI 治理模块（`bone-procurement-ai-governance`）已有人工终决台账 `HumanDecisionLedger.countOverrides` 与追溯链 `GovernanceTracePort.listByCapability`，可给出真实推翻计数；但缺漂移判词与只读指标面。治理 bean 由 `bone.procurement.ai-governance.enabled` 条件装配（默认关闭），端点强制 `@PreAuthorize`。DDL 已有 `t_proc_human_decision` / `t_proc_governance_trace`（`bone-init.sql`），无需新增表。

## Goals / Non-Goals

**Goals:**
- 用真实计数给出 fail-closed 漂移三态判词，判词可被单测与 POC 验证。
- 提供只读治理指标端点，不产生副作用。
- 明确"判词 ≠ 模型效果结论"的合规边界。

**Non-Goals:**
- 不接真实模型、不训练、不声明模型效果达标。
- 不改 evalset 状态（保持 `seededDraft`）、不改 owner/法务结论、不冻结契约/DDL。
- 不改 A10.17 三入口判据集合、不改 CI 配置、不新增门禁 key。
- 不自动改变能力状态、不触发真实重评/再训练（属 owner/L3）。

## Decisions

### 决策 1：漂移判词为纯领域值对象 + 纯函数裁决

在 `domain/model/capability/` 新增 `DriftVerdict`（三态枚举）、`DriftThresholds`（阈值配置 record）、`DriftSignal`（判词结果 record），由 `domain/service/DriftSignalPolicy` 纯函数计算。判定顺序：阈值合法性 → 样本充足性 → 计数一致性 → 触阈判定。

- **理由**：领域零框架依赖，可被 ArchUnit `domain_independent` 守护；纯函数无需端口/mock，负向探针确定性强；符合现有 `CapabilityGovernancePolicy` / `FairnessRule` 的领域服务惯例。
- **替代方案**：放在应用服务内判定——被否，因无法被领域单测独立覆盖，且违反分层职责。

### 决策 2：计数取自现有端口，不新增持久化

应用服务 `driftSignal(code)` 用 `tracePort.listByCapability(code)` 统计 `HUMAN_FINAL_DECISION` / `HUMAN_OVERRIDE` 事件数作为样本数，用 `ledger.countOverrides(code)` 取推翻数。不新增 port、不改 DDL、不写 trace。

- **理由**：`recordHumanDecision` 每次终决都会 trace append（现有测试 `治理裁决与终决全程留痕` 已证明），计数与台账自洽；只读端点不得有副作用。
- **替代方案**：新增 `countAll` 端口方法——被否，因需要改所有实现（SDK 适配 + 内存 + 测试 stub）并触碰仓储方法白名单门禁，超出最小变更。

### 决策 3：阈值由调用方配置传入，缺省不可判定

`DriftThresholds` 缺省为未配置（null 阈值），此时判 UNDETERMINED。应用服务方法签名接收显式阈值参数；HTTP 端点使用配置属性（默认未配置）。

- **理由**：阈值属 owner 通过线（D-10 未闭合），本 change 不得替 owner 定阈值；缺省 UNDETERMINED 是唯一诚实的 fail-closed 行为。
- **替代方案**：硬编码默认阈值（如 0.3）——被否，等于伪造 owner 通过线。

### 决策 4：只读投影 + 现有权限码

新增应用 DTO `DriftMetricsView`（record）与 Controller `GET /capabilities/{code}/drift`，权限 `procurement:ai-governance:read`。不新增权限码（避免改安全配置与前端授权面）。

## Risks / Trade-offs

- **样本数来自追溯事件计数，非独立终决计数表**：若历史终决未全部留痕会低估样本数 → 后果偏保守（倾向 UNDETERMINED），不会误报触阈；现有留痕测试保证每次终决都有事件。
- **阈值缺省不可判定**：开箱即用体验为 UNDETERMINED，需 owner 配置阈值才有信号 → 这是刻意诚实，非缺陷；在 POC 报告与端点原因码中说明。
- **hook 拦截写操作**：开发期需保持 selection 指向本 change（已 `state select procuraai-drift-metrics`）；如被其他会话切走需重新 select。

## Migration Plan

无数据迁移。新端点向后兼容（新增路径，不改既有契约）。回滚：移除新增类与端点即可，无库表变更。

## Open Questions

无影响构建的问题。真实阈值由 owner 在 D-10 闭合时给定，不影响本 change 的实现与验收（缺省 UNDETERMINED）。
