# bone-blueprint As-Is 证据（CI 派生）

> **生成时间**：2026-05-27T09:42:30Z（UTC）

| ID | 能力 | 摘要 |
|----|------|------|
| `archunit` | ArchUnit 分层（BoneDddArchRules） | 1 个 Java 文件 |
| `outbox` | Transactional Outbox（bp_outbox） | schema: bp_outbox · 4 个 Java 文件 |
| `tenant` | 多租户 TenantAggregateRoot + 过滤器 | 8 个 Java 文件 |
| `money-vo` | Money 值对象 | 1 个 Java 文件 |
| `read-port` | CQRS 读侧 OrderReadPort + SQL 投影 | 4 个 Java 文件 · SQL 投影 |
| `domain-events` | 领域事件 AFTER_COMMIT 发布 | 8 个 Java 文件 |
| `cqrs-handlers` | CommandHandler / QueryHandler 分离 | 11 个 Java 文件 |
| `integration-event` | 集成事件与领域事件分离 | 7 个 Java 文件 |