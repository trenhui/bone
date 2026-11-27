---
change: procuraai-contract-reviewed-event-chain
design-doc: openspec/changes/procuraai-contract-reviewed-event-chain/design.md
base-ref: 5c20768482165569eaf4bb06dfef61365423213b
archived-with: 2026-11-23-procuraai-contract-reviewed-event-chain
---

<!-- comet-task-authority: openspec/changes/procuraai-contract-reviewed-event-chain/tasks.md -->

# Build Plan — procuraAI ContractReviewed 持久化事件闭环（实施口径唯一真源，2026-11-23）

> 本计划引用已批准的 `design.md` 与 `tasks.md`（Open/Design 已锁定为已接受证据，本轮不修改）。任务完成状态一律以 tasks.md 为准，本计划仅以 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 1. 执行配置

`build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard` / `subagent_dispatch=null` / `direct_override=false`（沿用用户 2026-11-23 确认、与已交付的 identity-binding change 一致；本轮用户显式选「完整闭环 + 三入口，按已批 design」）。TDD 保留真实 RED/GREEN；standard 对风险任务与最终集成做唯一独立审查。

## 2. 基线事实（2026-11-23 源码实证）

- 分支 `dev`，HEAD `5c2076848`（已含 identity-binding 交付 + 远程 30+ claim/synap-claim 提交）；工作区两个 `synapclaim-*` 未跟踪 change 属他人工作，不触碰。
- `ContractReviewedConsumer` 以 `ConcurrentHashMap<String, StoredConclusion>` + `Set<String> quarantined` 存结论/隔离态，由 `ContractReviewApplicationService.override` 进程内直连 `consume()`，无持久化、无真实 MQ。
- `ContractReviewedEvent` 严格 7 字段 record；`ReviewTracePort` 已有持久化追溯链；`t_proc_contract_review` / `t_proc_review_trace` 为 init-only 表，persistence 门禁守护 6 表 / 9 端口。
- A10.17 以 `FULL_GATE_KEYS`（`tools/check_procura_alignment.py:295`）精确比对三入口判据集合：`.github/workflows/ci.yml` / `scripts/check.sh` / `doc/ai-product/procuraAI/scripts/check-procura-ci.sh`。新增判据须同步三入口 + 门禁常量 + 注入反证。
- DDL 唯一真源 `bone-init.sql`（`t_proc_*` 从 :4498 起）；新增表须含 `tenant_id` + 四类审计列并登记 migration manifest。

## 3. 实施纪律

- TDD：先写会红的负向/正向测试，再实现转绿；三类负向探针（重复投递只消费一次、跨租户拒绝留痕、失败入 DLQ 可重放）各有独立断言，不伪造 RED。
- 分层：domain 端口 + infrastructure SDK 持久化实现（`@Column` 齐全、审计列只用 `java.util.Date`）；application 编排 Outbox producer / Inbox consumer / DLQ replayer，不改 `ContractReviewedEvent` 7 字段记录。
- 租户锚定：消费以 `TenantContext` 为准，事件 tenant 不一致即拒绝留痕，绝不用事件 tenant 覆盖上下文；唯一键 `tenant_id + review_id` 裁决幂等。
- 语义不变：7 字段锁定、PII 禁回传、UNKNOWN 隔离转人工、AI 披露三态、人工推翻快照不可变。
- L3 三入口：改前 fetch、提交前 rebase、逐文件核对判据集合一致、全程不 force；措辞避开 A10.16 黑名单精确串；配注入必红/还原必绿反证。
- 无生产库迁移、不接生产 MQ 凭证、用本地持久化 + 显式驱动模拟投递并诚实标注。
- 改 Java 后 `mvn spotless:apply`；关键产物完成即精确 `git add`（共享树并行会话保护）。

## 4. 任务映射

### 组 1 · 持久化表与领域端口

- 1.1 在 `bone-init.sql` 新增 outbox/inbox/dlq 三表（`tenant_id + review_id` 唯一约束、四类审计列、无 PII），登记 migration manifest；DDL 对账测试验证零违规。 <!-- comet-task-ref:e7cd1fc3-40d5-426c-838b-87057609b99c -->
- 1.2 contract 域新增事件存储端口（Outbox 发布/待投递扫描、Inbox 条件插入去重、DLQ 写入/重放查询）+ SDK `@EnableSqlRepositories` 持久化实现；仓储单测验证 tenant 锚定、唯一键冲突、持久化读回。 <!-- comet-task-ref:fbb6d9b9-7d52-40b3-bdf6-d790a90d8eed -->

### 组 2 · 事务性发布与幂等消费

- 2.1 审查结论落库与 Outbox 插入同一事务 + 待投递扫描驱动（不接生产 MQ）；两组测试验证不丢事件、事务一致。 <!-- comet-task-ref:9184ffe3-2213-4564-a5f2-8c0e5359069a -->
- 2.2 `ContractReviewedConsumer` 从内存 Map 迁移到持久化 Inbox，`tenant_id + review_id` 条件插入判幂等；重复投递/重启重投验证只一条结论。 <!-- comet-task-ref:f9861496-ee46-46fd-af46-ceb059ba93d7 -->
- 2.3 消费以消费方 `TenantContext` 校验事件租户，不一致拒绝并写留痕；跨租户错投负向测试验证不落库、留痕含 review_id 与原因。 <!-- comet-task-ref:f122d3d3-ecf7-4eba-93b9-7c67262809f8 -->
- 2.4 消费失败写 DLQ（review_id + 原因 + payload 引用）不留半条结论，提供 `replay(review_id)`；失败入 DLQ/重放成功/重复重放不重复三组测试。 <!-- comet-task-ref:2d4cb2a4-628e-45c5-ae2d-723b0eabee9a -->

### 组 3 · 语义保持与契约同步

- 3.1 保持 7 字段/PII 禁入/UNKNOWN 隔离/AI 披露三态/快照不可变；现有事件测试 + 两条新增语义测试验证无回归。 <!-- comet-task-ref:c34d2cf5-9431-4dc8-a1fe-40e868665d5d -->
- 3.2 同步 `asyncapi.yaml` ContractReviewed 通道说明为 Outbox 发布/Inbox 幂等消费/DLQ 失败隔离，不改 payload 7 字段；`generate_asyncapi.py --check` 零漂移。 <!-- comet-task-ref:118e3268-363e-443a-a0fb-508e8b3520a0 -->

### 组 4 · 三入口门禁接入（L3）

- 4.1 扩展 persistence 门禁守护新 3 表与必备列/唯一键，断言 6 表/9 端口更新为新值；`check_procura_persistence.py` + 删表必红/还原必绿注入。 <!-- comet-task-ref:dd1ebd3f-87d8-4404-b14c-043bf3870bb7 -->
- 4.2 新增 event-chain 独立门禁脚本守护三表 DDL + 三类负向探针源码可达；`--self-test` 注入反证。 <!-- comet-task-ref:2c3bbd51-190f-403d-afb9-bea7a5c2389b -->
- 4.3 按 A10.17 / D-11 四入口一致流程在 `GATE_KEYS` 追加 `eventChainGate` + `eventChainProbe`，同步三入口 + 门禁常量；`check_procura_alignment.py`（含 `--self-test`）验证判据集合一致、注入必红、还原必绿，避开 A10.16 黑名单。 <!-- comet-task-ref:a07d1216-74be-4bff-86ca-303bff93d5a1 -->

### 组 5 · 集成验收与独立审查

- 5.1 运行 contract 与采购全量 Maven 测试，无失败/无跳过回归，写操作有持久化读回或拒绝后状态不变断言。 <!-- comet-task-ref:d6953ea3-d15a-439e-8d19-a27a0530dadf -->
- 5.2 运行前端 tsc / vitest / ESLint JSON 门禁、procuraAI 对齐 / evalset / AsyncAPI / frontmatter / 迁移清单，验证三入口改动未触发漂移。 <!-- comet-task-ref:6a903bb9-915d-4edd-9f60-32b90bc6725d -->
- 5.3 唯一最终集成审查，修复全部 CRITICAL/IMPORTANT，生成验收报告 PASS（含三类负向探针与三入口注入证据）。 <!-- comet-task-ref:47df5deb-39a8-44f3-b3f4-6e744e68d867 -->
