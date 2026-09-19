# bone-blueprint As-Is 证据（CI 派生）

> **生成时间**：2026-09-19T05:16:29Z（UTC）

| ID | 能力 | 摘要 |
|----|------|------|
| `openapi-blueprint-orders` | blueprint-orders-v1 OpenAPI | OpenAPI 2 paths |
| `adapter-rest-contract` | adapter 入参/出参 DTO + 201 Location | jakarta.validation · 4 个 Java 文件 |
| `archunit` | ArchUnit 分层（BoneDddArchRules） | 2 个 Java 文件 |
| `outbox` | Transactional Outbox（bp_outbox） | schema: bp_outbox · 5 个 Java 文件 |
| `tenant` | 多租户 TenantAggregateRoot + 过滤器 | 3 个 Java 文件 |
| `money-vo` | Money 值对象 | 1 个 Java 文件 |
| `read-port` | CQRS 读侧：域仓储读方法 + 领域投影 + SQL 投影（ADR-0030 合并） | 11 个 Java 文件 · SQL 投影 |
| `domain-events` | 领域事件 AFTER_COMMIT 发布 | 6 个 Java 文件 |
| `cqrs-handlers` | CommandHandler / QueryHandler 分离 | 2 个 Java 文件 |
| `integration-event` | 集成事件与领域事件分离 | 6 个 Java 文件 |