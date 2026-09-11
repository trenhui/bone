# 架构决策记录（ADR）

破坏性变更 DDD / API 门禁时，在 `doc/architecture/adr/` 新增记录，并同步 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md) E-0.3 所列下游文档。

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
| [0019](./0019-id-generation-contract-respect-non-null-id.md) | ID 生成契约 — SDK `insert` 尊重非空 id（**已接受 / 已落地**） |
| [0020](./0020-anti-anemia-weak-form-strong-behavior.md) | 反贫血机制 — 弱约束代码形式、强约束行为（**提议**） |
| [0021](./0021-outbox-and-consumer-idempotency-platformization.md) | Outbox 与消费端 `eventId` 幂等平台化（**提议**） |
| [0022](./0022-external-callback-signature-verification-port.md) | 外部回调验签端口化 — 由支付样板推广为全局硬规则（**提议**） |
| [0023](./0023-core-domain-smart-metadata.md) | Bone 当前核心域定为 Smart Metadata（**已接受**） |
| [0024](./0024-ddd-v5-rule-semantics-and-document-split.md) | DDD v5.0 规则语义校准与文档分册（**已接受**） |
| [0025](./0025-ddd-v5-0-2-implementation-alignment.md) | DDD v5.0.2 规则标识与实现状态对齐（**已接受**） |
| [0026](./0026-ddd-single-document-consolidation.md) | DDD v5.1.0 单文档整合（**已接受，取代 ADR-0024 的分册决定**） |
