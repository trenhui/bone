# ADR-0006：多租户隔离三模式切换策略（ADR-IAM-005）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组（待评审） |
| **关联** | IAM 详设 §4.5、PRD IAM-005 |

---

## 背景

PRD 定义三种隔离：行级 `tenant_id`（默认）、独立 Schema、独立库。`iam_tenant` As-Is 无 `isolation_mode` 列。

## 决策（提议）

| 模式 | 适用 | 默认 |
|------|------|------|
| **ROW_LEVEL** | 社区版 / MVP | **是** |
| **SCHEMA_LEVEL** | 商业版中等隔离 | 否 |
| **DATABASE_LEVEL** | 商业版强隔离 / 大客户 | 否 |

切换须：迁移脚本 + 租户级开关 + **禁止**运行时无 ADR 切换生产租户模式。

## 理由

- As-Is 代码与 `bone-init.sql` 已按行级设计。
- Schema/库隔离影响连接池与 Flyway 策略，须显式决策。

## 合规与迁移

- [Target] 扩展 `iam_tenant` 列 + `TenantController`。
- MyBatis 拦截器 / 数据源路由按 `isolation_mode` 分支。
