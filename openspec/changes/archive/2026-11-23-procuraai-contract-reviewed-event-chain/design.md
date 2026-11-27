---
comet_change: procuraai-contract-reviewed-event-chain
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-23-procuraai-contract-reviewed-event-chain
status: final
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
