# ADR-0011：AggregateRoot 继承 AbstractEntity

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-05-21 |
| **决策者** | 架构组 |

---

## 背景

`AggregateRoot` 与 `AbstractEntity` 为平行继承链，聚合根无法同时获得审计字段与领域事件，各业务模块自行重复 `createdAt`/`tenantId` 等字段。

## 决策

1. **阶段 1（当前）**：`AggregateRoot` 仍继承 `Entity`；新增 `AuditableAggregateRoot` 继承 `AbstractEntity`（`Date` 审计 + 领域事件），供 blueprint / masterdata 等模块选用。
2. **阶段 1**：`TenantAggregateRoot` 继承 `AggregateRoot` + `tenantId`，**不**继承 `TenantAbstractEntity`（避免与 bone-iam 等 `LocalDateTime` 审计冲突）。
3. **阶段 2（待 IAM 统一审计类型后）**：评估 `AggregateRoot` 合并进 `AuditableAggregateRoot` 或 IAM 全量迁 `Date`/`Instant`。
4. 样板：`bone-iam` `Account` 使用 `TenantAggregateRoot` + 域内 `LocalDateTime` 审计字段。

## 理由

- 对齐 DDD 文档 §16.2 方案 A，消除单继承限制。
- 子类已声明的审计字段可与基类并存，逐步删除重复字段。

## 后果

### 正面

- 聚合根默认可用 `createdAt`/`updatedAt`/`deleted`。
- 文档与 bone-core 一致。

### 负面 / 风险

- 子类若重复声明 `id`/`createdAt` 等字段，存在字段遮蔽；CR 时提醒收敛。

## 合规与迁移

- 更新 `doc/architecture/Bone-DDD-最终实践方案.md` §16.2。
- 无强制批量改码；`Account` 等可后续改为 `TenantAggregateRoot` 并删除重复字段。
