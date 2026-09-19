# Bone Metadata SDK 文档索引

| 文档 | 说明 |
|------|------|
| [AGENT-持久化速查手册.md](./AGENT-持久化速查手册.md) | **改造前先看**：决策树、可复制骨架、14 条坑位清单 |
| [使用指南.md](./使用指南.md) | 快速接入、`@EnableSqlRepositories`、Criteria/DSL/`@Sql`、读写分离与常见配置 |
| [Bone-Metadata-SDK-最佳实践方案.md](./Bone-Metadata-SDK-最佳实践方案.md) | 架构原则、仓储模式、`@Sql` 执行链、租户注入、性能建议 |

**事实来源**：API 与注解以 **`../src/main/java`** 及模块测试为准；与 [doc/architecture/Bone-DDD-最终实践方案.md](../../../doc/architecture/Bone-DDD-最终实践方案.md) §5.1.1 持久化规约一致。

**相关模块**：可部署服务为 **`bone-metadata-server`**（原 `bone-metadata`）；智能引擎为 **`bone-metadata-engine`**（原 `bone-smartmeta-*`）。
