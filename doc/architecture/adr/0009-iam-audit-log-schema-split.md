# ADR-0009：`iam_audit_log` 扩列 vs 平台审计总线职责（ADR-IAM-008）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组（待评审） |
| **关联** | IAM 详设 §4.4、ADR-0007 |

---

## 背景

IAM 详设曾列 `request_method`、`request_url`、`response_status` 等字段，与 As-Is `bone-init.sql`（`operation`、`ip`、`parameters`…）不一致。

## 决策（提议）

| 数据 | 存储 |
|------|------|
| IAM 管理面操作（账号/角色/权限 CRUD、登录） | **`iam_audit_log`（保持精简列集）** |
| 全 HTTP 访问轨迹、响应码、完整 URL | **平台 audit-bus → 冷存储**（ADR-0007） |

**不**为对齐 PRD 全量 HTTP 字段而扩 `iam_audit_log`，除非商业版客户明确要求 IAM 库内查询。

## 理由

- 避免 IAM 表宽化与双写。
- init SQL 为 DDL 真源（ADR-0001）。

## 合规与迁移

- 查询 UI：IAM 页查 `iam_audit_log`；合规导出走 audit-bus 索引 **[Target]**。
