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
