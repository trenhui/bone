# ADR-0007：平台审计总线（AOP + WORM）落地路径（ADR-IAM-006）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组 + 合规（待评审） |
| **关联** | IAM 详设 §3.6、PRD IAM-004 |

---

## 背景

`iam_audit_log` 仅覆盖 IAM 域 API。PRD 商业版要求全平台操作 WORM 180 天；社区版 DB 30 天。

## 决策（提议）

1. **As-Is**：IAM 域继续写 `iam_audit_log`。
2. **[Target]**：各服务统一 `@Audit` AOP → Kafka/HTTP **audit-bus**（含 `traceId`、`tenantId`）。
3. **[Vision]**：消费者写 S3/MinIO **Object Lock**（WORM）+ 冷归档。

IAM 服务**不**承担全平台日志存储，仅提供 schema 与查询 API 聚合层（可选）。

## 理由

- 避免 IAM 成为全平台写瓶颈。
- 满足等保「集中审计」要求。

## 合规与迁移

- 脱敏规则统一在 audit-bus 入口执行。
- 与 ADR-0009（`iam_audit_log` 扩列）职责划分见该 ADR。
