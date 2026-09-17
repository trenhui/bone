# bone-blueprint [Target] / [Vision] Backlog

> **生成时间**：2026-09-17T02:23:26Z（UTC）
> **维护源**：[`backlog.yaml`](../../../tools/blueprint-compliance-collector/backlog.yaml)

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `outbox-cluster-dedup` | Outbox Relay 多实例去重/Leader 选举 | 详设级 Outbox 最佳实践 | OrderOutboxRelayJob 默认可多副本重复扫表 |
| **Target** | `inventory-saga-compensation` | 库存预留失败补偿 Saga | DDD 跨上下文一致性 | 创建订单 check+reserve 失败时无自动补偿编排 |
| **Target** | `pay-idempotency-key` | 支付命令幂等键（API / 应用层） | Bone-API-规范 §8 | PayOrderCommandHandler 无 Idempotency-Key |
| **Vision** | `read-model-projection-db` | 订单读模型独立库/物化视图 | CQRS 进阶 | 当前 SQL 投影同库 Join |