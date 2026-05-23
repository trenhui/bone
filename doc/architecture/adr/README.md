# 架构决策记录（ADR）

破坏性变更 DDD / API 门禁时，在 `doc/architecture/adr/` 新增记录，并同步 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md) §0.3 所列下游文档。

命名建议：`NNNN-short-title.md`（递增序号）。

| ADR | 标题 |
|-----|------|
| [0011](./0011-aggregate-root-inheritance.md) | AggregateRoot 继承 AbstractEntity |
| [0012](./0012-system-exception-hierarchy.md) | SystemException 归属基础设施异常链 |
| [0013](./0013-extension-studio-repository-read-side.md) | extension-studio 仓储读侧拆 ReadPort |
| [0014](./0014-iam-localdatetime-audit.md) | bone-iam LocalDateTime 审计策略 |
| [0013](./0013-extension-studio-repository-read-side.md) | extension-studio 仓储读侧 gateway 拆分 |
