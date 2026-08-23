# 架构决策记录（ADR）

破坏性变更 DDD / API 门禁时，在 `doc/architecture/adr/` 新增记录，并同步 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md) §0.3 所列下游文档。

命名建议：`NNNN-short-title.md`（递增序号）。

| ADR | 标题 |
|-----|------|
| [0011](./0011-aggregate-root-inheritance.md) | AggregateRoot 继承 AbstractEntity |
| [0012](./0012-system-exception-hierarchy.md) | SystemException 归属基础设施异常链 |
| [0013](./0013-extension-studio-repository-read-side.md) | extension-studio 仓储读侧拆 ReadPort |
| [0014](./0014-extension-rollout-traffic-canonical.md) | 扩展灰度字段收敛 — `config_json.traffic` 唯一语义源 |
| [0015](./0015-metadata-runtime-jdbc-via-engine.md) | 元数据模式 B 运行时 JDBC（engine） |
| [0016](./0016-metadata-catalog-abstract-entity-tenant.md) | 元数据 catalog AbstractEntity + tenantId |
| [0017](./0017-masterdata-catalog-sync.md) | 业务元数据实体（`meta_*`）→ 主数据实体（`mdm_*`）同步范式 |
| [0018](./0018-iam-localdatetime-audit.md) | bone-iam LocalDateTime 审计策略 |
