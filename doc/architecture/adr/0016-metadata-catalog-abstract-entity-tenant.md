# ADR-0016：catalog 聚合根继承 AbstractEntity 与自有 tenantId（ADR-MS-002）

| 项 | 内容 |
|----|------|
| **状态** | 已接受（As-Is 例外） |
| **日期** | 2026-05-27 |
| **决策者** | 元数据模块组 |
| **详设** | [元数据管理模块详细设计方案](../../design/modules/2.%20元数据管理模块详细设计方案.md) §3.3.1、§10.3 |

---

## 背景

《Bone-DDD》推荐多租户聚合使用 `TenantAggregateRoot` + `Tenantable`。`bone-metadata-server` catalog 子域在落地时选用 `com.bone.core.domain.entity.AbstractEntity<Long>`，并在 `MetaEntity` / `MetaField` / `MetaEntityRelation` 上声明自有 `tenantId` 字段；字段映射使用 **bone-metadata-sdk 自定义注解** `@Table` / `@Column`（非 JPA）。

## 决策

1. **As-Is**：catalog 三个聚合根均 `extends AbstractEntity<Long>`，租户隔离由 `CatalogTenantSupport` + 仓储查询条件保证。
2. **注解**：`com.bone.metadata.sdk.domain.annotation.Table` / `Column` 由 SDK Repository 反射读取；**领域层不依赖** Hibernate / MyBatis-Plus。
3. **命名**：领域层关系聚合根为 `MetaEntityRelation`（对齐 DDL `meta_entity_relation`）；应用/Web 层短称 `MetaRelation*`（如 `MetaRelationCatalogController`）。
4. **收敛路线**：当 `bone-architecture-test` 对 metadata-server 启用「多租户聚合必须 TenantAggregateRoot」规则时，评估迁移至 `TenantAggregateRoot` 或 `AuditableAggregateRoot`（见 [ADR-0011](./0011-aggregate-root-inheritance.md)）。

## 理由

- 与 metadata-sdk 持久化机制一致，避免 JPA 与自研 Repository 双栈。
- 与 IAM `LocalDateTime` 审计差异解耦（catalog 使用 `AbstractEntity` 的 `Date` 审计）。

## 后果

### 正面

- 详设与源码类名一致；新人可按 `com.bone.metadata.catalog.**` 导航。

### 负面 / 风险

- 文档若写「继承 AggregateRoot」会与真源不符。
- `tenantId` 重复声明于子类时，需 CR 注意与基类/接口字段遮蔽（当前为显式列映射，可接受）。

## 合规

- 收集器：`tools/metadata-compliance-collector` → `catalog-abstract-entity` 扫描项
