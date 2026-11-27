# Comet Design Handoff

- Change: procuraai-contract-reviewed-event-chain
- Phase: design
- Mode: compact
- Context hash: b9e00445f410b3573f7e4be7ed3874633f5630bc3b9e698c01e2f83aa2920dfa

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-contract-reviewed-event-chain/proposal.md

- Source: openspec/changes/procuraai-contract-reviewed-event-chain/proposal.md
- Lines: 1-31
- SHA256: 84e21bc414b692c4be49f35e46cb95d0fe6409f3d3a45bfe064e7357c7c3238a

```md
# Proposal

## Why

`ContractReviewedConsumer` 目前用进程内 `ConcurrentHashMap` 存结论，由 `ContractReviewApplicationService.override` 直连调用 `consume()` 代替真实 MQ 的发布-消费闭环。这带来三个不可接受的生产缺陷：结论重启即丢失、发布失败即永久丢事件、消费失败无重试与隔离，无法证明"重复投递只消费一次 / 跨租户拒绝留痕 / 失败可重放"。业界标准的 transactional outbox + inbox 模式（幂等键 + 租户锚定 + DLQ）是把 walking skeleton 升级为可验证生产闭环的最小路径。

## What Changes

- **新增持久化事件闭环**：在 contract 域新增 Outbox（发布）、Inbox（消费去重）、DLQ（失败隔离）三张本地表及迁移脚本，以 `review_id` 幂等键 + `tenant_id` 锚定实现事务性发布与幂等消费；不改生产 MQ 凭证、不执行生产库操作。
- **升级消费语义**：`ContractReviewedConsumer` 从内存 Map 迁移到持久化 Inbox；消费失败进入 DLQ 并可按 `review_id` 重放；`risk_level=UNKNOWN` 仍隔离转人工、AI 披露三态与快照不可变语义保持不变。
- **三类负向探针**：重复投递只消费一次、跨租户事件拒绝且留痕、投递失败入 DLQ 且可重放，均为可重复执行的机器可验证测试。
- **三入口门禁接入（L3）**：按 A10.17 以 D-11 四入口一致流程同步 `ci.yml` / `scripts/check.sh` / `doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 判据集合 + `FULL_GATE_KEYS` + 门禁常量 + 注入必红反证；新增独立 persistence/event-chain 门禁守护新增表与负向探针，删除判据后主门禁必红。
- **契约文案同步**：`asyncapi.yaml` 中 `ContractReviewed` 通道说明同步为"Outbox 发布 / Inbox 幂等消费 / DLQ 失败隔离"，不改事件 payload 的 7 字段锁定。

## Capabilities

### New Capabilities

（无新增 capability，复用既有边界。）

### Modified Capabilities

- `procurement/contract-ai-review`: 把"审查留痕、幂等与追溯链"从进程内内存幂等升级为持久化 Outbox→Inbox→DLQ 闭环，新增三项可观察行为——跨租户事件拒绝且留痕、消费失败入 DLQ 可按 review_id 重放、重启后结论与去重状态可恢复；事件 payload 7 字段与 PII 禁回传不变。

## Impact

- **代码**：`bone-procurement-contract` 新增 domain 端口 + infrastructure 持久化实现 + application 的 Outbox producer / Inbox listener / DLQ replayer；`ContractReviewedConsumer` 改为 Inbox 支撑；迁移脚本登记至 migration manifest。
- **DDL**：`bone-init.sql` 新增 3 张 `t_proc_contract_*` 表（含必备审计列与 tenant_id）；persistence 门禁表数断言从 6 → 9、端口数相应更新。
- **门禁**：`tools/check_procura_alignment.py` 的 `GATE_KEYS` 新增 event-chain 两 key（gate + probe），三入口判据集合同步；A10.16 黑名单措辞规避。
- **文档**：`contract-ai-review` 主 spec 追加 delta 场景；`asyncapi.yaml` 通道说明同步。
- **范围外**：不接生产 MQ 凭证、不执行生产数据库操作、不扩展其它事件类型、不改 evalset 状态、不冻结契约。

```

## openspec/changes/procuraai-contract-reviewed-event-chain/design.md

- Source: openspec/changes/procuraai-contract-reviewed-event-chain/design.md
- Lines: 1-68
- SHA256: 806d7b97c1b6496b6a351d0a3cbb16998f851938ad1b4cb2f7283f1da17aa4a2

```md
---
comet_change: procuraai-contract-reviewed-event-chain
role: technical-design
canonical_spec: openspec
---

# Design

## Context

见 proposal.md 的 Why。源码实证（2026-11-23）：
- `ContractReviewedConsumer` 以 `ConcurrentHashMap<String, StoredConclusion>` + `Set<String> quarantined` 存结论与隔离态，由 `ContractReviewApplicationService.override` 在进程内直连 `consume()`，无持久化、无真实 MQ。
- `ContractReviewedEvent` 是严格 7 字段 record；`ReviewTracePort` 已有持久化追溯链，`t_proc_contract_review` / `t_proc_review_trace` 已是 init-only 表（persistence 门禁守护 6 表 / 9 端口）。
- A10.17 以 `FULL_GATE_KEYS`（`tools/check_procura_alignment.py:295`）精确比对三入口判据集合；三入口为 `.github/workflows/ci.yml` / `scripts/check.sh` / `doc/ai-product/procuraAI/scripts/check-procura-ci.sh`。新增判据须同步三入口 + 门禁常量 + 注入反证，属 L3。
- DDL 唯一真源 `bone-init.sql`（`t_proc_*` 从 :4498 起）；新增表须含 `tenant_id` 与四类审计列，并登记 migration manifest。

## Goals / Non-Goals

**Goals:**
- 把 ContractReviewed 从进程内直连升级为持久化 Outbox→Inbox→DLQ 闭环，重启后结论与去重态可恢复。
- 三类可重复执行的机器验证：重复投递只消费一次、跨租户事件拒绝且留痕、失败入 DLQ 可按 review_id 重放。
- 按 A10.17 / D-11 四入口一致流程把新判据接入三入口，配注入必红反证。
- 保持事件 7 字段、PII 禁回传、UNKNOWN 隔离、AI 披露三态、人工推翻快照不可变语义不变。

**Non-Goals:**
- 不接生产 MQ 凭证、不执行任何生产数据库操作；Inbox/Outbox 用本地持久化 + 显式驱动（测试/定时扫描）模拟投递，不伪装成生产 MQ。
- 不扩展其它事件类型、不改 evalset 状态、不冻结契约、不改遥测/SLO 承诺。
- 不改 `FULL_GATE_KEYS` 既有 9 个 key 的语义，只在末尾追加 event-chain 两 key。

## Decisions

### 1. 三表 schema：Outbox / Inbox / DLQ
新增 `t_proc_contract_outbox`、`t_proc_contract_inbox`、`t_proc_contract_dlq`，均以 `review_id` 为业务唯一键、`tenant_id` 参与唯一约束（`tenant_id + review_id`），含必备审计列。Outbox 存 payload 快照（7 字段 JSON，禁 PII）；Inbox 存结论去重态；DLQ 存失败原因与重放状态。
- 备选：复用 `t_proc_review_trace` 承载幂等。否决——追溯链是 append-only 审计语义，去重态需要唯一约束与状态更新，混用会污染审计且无法原子判定"只消费一次"。

### 2. 事务性发布与幂等键判定
审查结论落库与 Outbox 插入同一事务（`@Transactional`）；Inbox 消费以 `tenant_id + review_id` 唯一约束 + 条件插入判定幂等，冲突即视为重复投递返回"已跳过"，不产生第二条结论。
- 备选：分布式锁 / SELECT FOR UPDATE。否决——AGENTS.md 禁止业务层悲观锁；唯一约束 + 条件插入是单写者幂等的正当路径，多副本下由 DB 唯一键裁决。

### 3. 租户锚定与 fail-closed
消费时以消费方 `TenantContext` 为准，事件的 tenant 不一致即拒绝并写拒绝留痕，绝不用事件 tenant 覆盖上下文或从默认租户继续。与上个 change 的 `AwardIdentityPort` / `ReviewIdentityPort` 登录态权威语义一致。
- 备选：信任事件 payload 的 tenant。否决——跨租户错投会被当作合法租户写入，违反多租户隔离红线。

### 4. DLQ 失败隔离与按 review_id 重放
消费抛出可重试异常时写入 DLQ（记录 review_id + 失败原因 + 原始 payload 引用），不落半条结论；提供 `replay(review_id)` 显式重放入口，成功后标记 DLQ 已解决，幂等键保证不重复。
- 备选：无限自动重试。否决——无死信终点的重试会掩盖系统性故障，且无法人工介入；DLQ 是可观测、可重放的业界标准。

### 5. 不改事件 payload 与追溯语义
7 字段锁定、PII 禁入、UNKNOWN 隔离转人工、AI 披露三态、人工推翻快照不可变全部保持；Inbox 只新增"去重/隔离/DLQ"状态，不改写 `ContractReviewedEvent` 记录。

### 6. 三入口门禁接入（L3，D-11 四入口一致）
在 `GATE_KEYS` 追加 `eventChainGate` + `eventChainProbe` 两 key，新增独立检查脚本守护：3 表 DDL + 必备列 + 三类负向探针的源码可达性 + 三入口判据集合一致。同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + `FULL_GATE_KEYS` + 门禁常量 + 自检探针；注入"删表/删负向探针/删判据"必红、还原必绿。措辞避开 A10.16 黑名单精确串。
- 备选：只做本地脚本、不接三入口。否决——用户本轮明确选择"完整闭环 + 三入口接入"，且 A10.17 要求判据集合一致，半接入会导致三入口转红或守护失效。

## Risks / Trade-offs

- [风险] 三入口是并行会话高频冲突区（`ci.yml` 历史上被并行修复）→ 缓解：改动前 fetch，提交前 rebase，逐文件确认判据集合一致；全程不 force。
- [风险] persistence 门禁当前硬编码 6 表 / 9 端口，新增 3 表会触发断言 → 缓解：同步扩展门禁守护清单（10 表 / 端口相应增加），配注入必红反证。
- [风险] 事件 payload 存 Outbox 可能夹带 PII → 缓解：只存既有 7 字段（已由 PII 门禁守护），入 Outbox 前复用现有 PII 入口扫描。
- [权衡] 用显式驱动（定时扫描 Outbox）而非真实 MQ → 不伪装生产闭环，但需诚实标注"本地模拟投递，生产 MQ listener 仍属 L3"。

## Migration Plan

无生产库迁移。本地顺序：① 新增 3 表 DDL 与迁移脚本并登记 manifest → ② domain 端口 + infrastructure 持久化实现 → ③ Outbox producer / Inbox consumer / DLQ replayer 改造 → ④ 三类负向探针 → ⑤ 三入口门禁与注入反证 → ⑥ 全量验证。回滚：撤销表与实现即可，既有 `t_proc_contract_review` / trace 数据不受影响。

## Open Questions

（无——所有影响 specs、方案或任务拆分的问题均已在 Decisions 中定案。）

```

## openspec/changes/procuraai-contract-reviewed-event-chain/tasks.md

- Source: openspec/changes/procuraai-contract-reviewed-event-chain/tasks.md
- Lines: 1-35
- SHA256: 7693df60422c45eca767d76967424fe56257bf68749427a3de8ccd879d6c2667

```md
# Tasks

## 1. 持久化表与领域端口

- [ ] 1.1 在 `bone-init.sql` 新增 `t_proc_contract_outbox` / `t_proc_contract_inbox` / `t_proc_contract_dlq` 三表（`tenant_id + review_id` 唯一约束、四类审计列、无 PII 列），登记 migration manifest；用 `check-ddl-required-columns.py --check` 与 DDL 对账测试验证零违规。 <!-- comet-task:e7cd1fc3-40d5-426c-838b-87057609b99c -->
- [ ] 1.2 在 contract 域新增事件存储端口（Outbox 发布/待投递扫描、Inbox 条件插入去重、DLQ 写入/重放查询）并在 infrastructure 用 SDK `@EnableSqlRepositories` 实现（`@Column` 齐全、只用 `AbstractEntity` 的 `java.util.Date` 审计列）；用仓储单测验证 tenant 锚定定位、唯一键冲突与持久化读回。 <!-- comet-task:fbb6d9b9-7d52-40b3-bdf6-d790a90d8eed -->

## 2. 事务性发布与幂等消费

- [ ] 2.1 把审查结论落库与 Outbox 插入纳入同一事务，新增待投递扫描驱动（显式/定时，不接生产 MQ）；用"发布前崩溃重启仍可投递"与"结论失败无孤立 Outbox"两组测试验证不丢事件、事务一致。 <!-- comet-task:9184ffe3-2213-4564-a5f2-8c0e5359069a -->
- [ ] 2.2 把 `ContractReviewedConsumer` 从内存 Map 迁移到持久化 Inbox，以 `tenant_id + review_id` 条件插入判定幂等；用重复投递、重启后重投两组测试验证只产生一条结论、无第二条追溯记录。 <!-- comet-task:f9861496-ee46-46fd-af46-ceb059ba93d7 -->
- [ ] 2.3 消费时以消费方 `TenantContext` 为准校验事件租户，不一致即拒绝并写拒绝留痕；用跨租户错投负向测试验证不落库、留痕含 review_id 与原因。 <!-- comet-task:f122d3d3-ecf7-4eba-93b9-7c67262809f8 -->
- [ ] 2.4 消费失败写 DLQ（review_id + 失败原因 + payload 引用）且不留半条结论，提供按 `review_id` 重放入口；用失败入 DLQ、重放成功、重复重放不重复三组测试验证。 <!-- comet-task:2d4cb2a4-628e-45c5-ae2d-723b0eabee9a -->

## 3. 语义保持与契约同步

- [ ] 3.1 保持 ContractReviewed 7 字段锁定、PII 禁回传、UNKNOWN 隔离转人工、AI 披露三态、人工推翻快照不可变；用现有 ContractReviewedEvent 测试 + 两条新增语义测试（UNKNOWN 持久化链路仍隔离、人工推翻快照不可变）验证无回归。 <!-- comet-task:c34d2cf5-9431-4dc8-a1fe-40e868665d5d -->
- [ ] 3.2 同步 `asyncapi.yaml` 中 ContractReviewed 通道说明为 Outbox 发布 / Inbox 幂等消费 / DLQ 失败隔离，不改 payload 7 字段；用 `generate_asyncapi.py --check` 验证零漂移。 <!-- comet-task:118e3268-363e-443a-a0fb-508e8b3520a0 -->

## 4. 三入口门禁接入（L3）

- [ ] 4.1 扩展 persistence 门禁守护新 3 表与必备列/唯一键，断言从 6 表/9 端口更新为新值；用 `check_procura_persistence.py` 与"删表必红/还原必绿"注入验证。 <!-- comet-task:dd1ebd3f-87d8-4404-b14c-043bf3870bb7 -->
- [ ] 4.2 新增 event-chain 独立门禁脚本，守护三表 DDL + 幂等/跨租户/DLQ 三类负向探针源码可达；配 `--self-test` 注入反证（删探针必红、正例必绿）。 <!-- comet-task:2c3bbd51-190f-403d-afb9-bea7a5c2389b -->
- [ ] 4.3 按 A10.17 / D-11 四入口一致流程在 `GATE_KEYS` 追加 `eventChainGate` + `eventChainProbe`，同步 `ci.yml` / `scripts/check.sh` / `check-procura-ci.sh` + 门禁常量；用 `check_procura_alignment.py`（含 `--self-test`）验证三入口判据集合一致、注入删判据必红、还原必绿，措辞避开 A10.16 黑名单。 <!-- comet-task:a07d1216-74be-4bff-86ca-303bff93d5a1 -->

## 5. 集成验收

- [ ] 5.1 运行 contract 模块与采购全量 Maven 测试，验证无失败/无跳过回归，所有写操作有持久化读回或拒绝后状态不变断言。 <!-- comet-task:d6953ea3-d15a-439e-8d19-a27a0530dadf -->
- [ ] 5.2 运行前端 tsc / vitest / ESLint JSON 门禁、procuraAI 对齐 / evalset / AsyncAPI / frontmatter / 迁移清单，验证三入口改动未触发契约漂移。 <!-- comet-task:6a903bb9-915d-4edd-9f60-32b90bc6725d -->
- [ ] 5.3 执行唯一最终集成审查，修复全部 CRITICAL/IMPORTANT，生成验收报告 PASS（含三类负向探针与三入口注入证据）。 <!-- comet-task:47df5deb-39a8-44f3-b3f4-6e744e68d867 -->

## Workflow follow-up

- 完成 Comet Verify 的唯一最终集成审查后再归档。
- 归档交付方式（local/push/pr）由用户在 Archive 阶段明确授权；未授权前不提交、不推送。

```

## openspec/changes/procuraai-contract-reviewed-event-chain/.openspec.yaml

- Source: openspec/changes/procuraai-contract-reviewed-event-chain/.openspec.yaml
- Lines: 1-2
- SHA256: 01c3365f21ad2129d7e1db5577bc59c382883cdcbfece60900a8ac86293e3398

```md
schema: spec-driven
created: 2026-11-23

```

## openspec/changes/procuraai-contract-reviewed-event-chain/specs/procurement/contract-ai-review/spec.md

- Source: openspec/changes/procuraai-contract-reviewed-event-chain/specs/procurement/contract-ai-review/spec.md
- Lines: 1-55
- SHA256: 4f6699112ab78eceec6defdc8b44a9d99e699638117082cfd3496e4721a48327

```md
# Spec Delta：procurement/contract-ai-review

## ADDED Requirements

### Requirement: ContractReviewed 采用持久化事务性发布

系统 SHALL 通过持久化 Outbox 发布 ContractReviewed：审查结论与 Outbox 记录 MUST 在同一事务内落库，发布成功前事件不得丢失；进程重启后未发布事件 MUST 可被继续投递。事件 payload MUST 保持既有 7 字段锁定，MUST NOT 回传 output 原文或任何 PII。

#### Scenario: 事务性发布不丢事件
- **WHEN** 审查结论提交后、事件投递前进程崩溃并重启
- **THEN** Outbox 中未投递事件仍可被发现并继续投递，事件不丢失且 payload 仍为 7 字段

#### Scenario: 发布与结论同事务
- **WHEN** 审查结论写入成功
- **THEN** 对应 Outbox 记录在同一事务内可见；结论写入失败时不得留下孤立 Outbox 记录

### Requirement: 幂等消费与跨租户隔离

系统 SHALL 以持久化 Inbox 消费 ContractReviewed，以 `review_id` 为幂等键：同一 review_id 的重复投递 MUST 只产生一条结论；事件 MUST 携带 tenant 上下文，跨租户错投 MUST 被拒绝并留下拒绝留痕，MUST NOT 用事件的 tenant 覆盖接收方租户或从默认租户继续处理。

#### Scenario: 重复投递只消费一次
- **WHEN** 同一 review_id 的 ContractReviewed 被重复投递多次
- **THEN** Inbox 仅记录一条结论，重复投递返回"已跳过"且不产生第二条追溯记录

#### Scenario: 跨租户事件拒绝且留痕
- **WHEN** 事件的 tenant 与消费方租户上下文不一致
- **THEN** 事件被拒绝、不产生结论，系统留下跨租户拒绝留痕（含 review_id 与拒绝原因）

#### Scenario: 重启后去重状态保持
- **WHEN** 已消费的 review_id 在进程重启后再次投递
- **THEN** 依据持久化 Inbox 判定为重复并跳过，不因内存清空而重复消费

### Requirement: 消费失败进入 DLQ 且可重放

系统 SHALL 在消费处理失败（含解析异常、下游不可用）时把事件投入持久化 DLQ 并记录失败原因；运维可依据 `review_id` 触发重放，重放成功后的结论 MUST 与首次成功消费一致，同一 review_id 绝不产生重复结论。

#### Scenario: 失败入 DLQ
- **WHEN** ContractReviewed 消费过程中抛出可重试异常
- **THEN** 事件进入 DLQ 并记录 review_id 与失败原因，不产生半条结论

#### Scenario: DLQ 按 review_id 重放
- **WHEN** 对 DLQ 中的事件按 review_id 触发重放且依赖恢复
- **THEN** 事件被重新消费并产出唯一结论，DLQ 项标记为已解决，重复重放不产生第二条结论

### Requirement: 事件闭环不改变人工终决与披露语义

升级为持久化闭环 MUST NOT 稀释既有语义：`risk_level=UNKNOWN` 仍隔离转人工、AI 参与披露三态不变、人工推翻的审查快照不可变、降级/停用/人工推翻仍入追溯链。

#### Scenario: UNKNOWN 在持久化链路仍隔离
- **WHEN** 经 Outbox/Inbox 消费的事件 risk_level = UNKNOWN
- **THEN** 该结论仍隔离转人工，不被当作低风险自动通过

#### Scenario: 人工推翻快照不可变
- **WHEN** 人工推翻一条经持久化闭环产生的结论
- **THEN** 原 review_snapshot_ref 指向的快照保持不变，推翻动作入追溯链

```
