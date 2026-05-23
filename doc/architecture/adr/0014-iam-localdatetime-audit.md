# ADR-0014：bone-iam 域内 LocalDateTime 审计字段策略

| 项 | 内容 |
|----|------|
| **状态** | 已接受（阶段 1 已落地；阶段 2 按需） |
| **日期** | 2026-05-22 |
| **决策者** | 架构组 |
| **关联** | [ADR-0011](./0011-aggregate-root-inheritance.md) |

---

## 背景

- `bone-core` 的 `AbstractEntity` / `AuditableAggregateRoot` 使用 `java.util.Date` 审计字段。
- `bone-iam` 域模型普遍使用 `java.time.LocalDateTime`（表字段、QueryBuilder、DTO 一致）。
- 若聚合根直接继承 `AbstractEntity`，会与 `Auditable` 的 `Date` 返回类型冲突（编译失败）。

## 决策

### 阶段 1（当前，已落地）

1. 多租户聚合根使用 `TenantAggregateRoot`（`AggregateRoot` + `tenantId` + 领域事件），**不**继承 `TenantAbstractEntity`。
2. 审计字段（`createdAt` / `updatedAt`）在 IAM 聚合内**显式声明为 `LocalDateTime`**，由工厂方法赋值。
3. 主键使用 `setId` / `setTenantId`，避免与基类字段重复。
4. 适用实体：`Account`、`Role`、`AuditLog`（多租户）；`Tenant`、`Permission`（`AggregateRoot` + `setId`）。

### 阶段 2（按需，未排期）

任选其一，须单独 PR + 数据迁移评估：

| 方案 | 说明 |
|------|------|
| A | `bone-core` 审计改为 `Instant`，全平台渐进迁移 |
| B | IAM 引入 `AuditTimestamps` 值对象，聚合组合而非继承 `Date` 审计 |
| C | 仅 adapter/query DTO 做 `LocalDateTime` ↔ `Instant` 映射，域内保持现状 |

**默认推荐**：维持阶段 1，直至有跨模块统一审计类型的平台级 ADR。

## 理由

- 阶段 1 以最小改动满足 ADR-0011，且不破坏 IAM 现有 API/查询类型。
- 阶段 2 成本高，收益主要在「基类统一」，对运行时行为无硬性要求。

## 后果

- 新 IAM 聚合根须遵循 [bone-iam/README.md](../../../bone-platform/bone-iam/README.md) 基类选用表。
- ArchUnit **不**拦截 `LocalDateTime` 审计（由 CR + 模块 README 约束）。
