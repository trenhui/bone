# ADR-0004：PolicyEvaluator 进程内 → 独立 authz-service（ADR-IAM-003）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组（待评审） |
| **关联** | IAM 详设 §3.5.2、总体架构规划端口示例 8082 |

---

## 背景

RBAC 在 JWT 内携带权限码可满足 MVP；多租户、ABAC、集中审计决策后，需评估是否拆 **authz-service**。

## 决策（提议）

| 阶段 | 形态 |
|------|------|
| **阶段 A（[Target]）** | `bone-iam` 内 `PolicyEvaluator`，同步调用，读 `iam_role_permission` + `iam_policy` |
| **阶段 B（[Vision]）** | 独立 `authz-service`，gRPC/HTTP `/api/v1/authz/check`，网关或服务 Sidecar 调用 |

拆分触发条件（满足任一）：权限决策 P99 >50ms 且无法缓存；策略规则 >500 条/租户；需集中 PDP 供第三方集成。

## 理由

- 避免过早微服务化（YAGNI）。
- 保留演进路径，与 C4 边界一致。

## 后果

### 负面

- 阶段 B 增加网络跳数与可用性依赖 → 须缓存 + 熔断。

## 合规与迁移

- API 契约：`subject + resource + action + tenant_id` → `ALLOW/DENY` + 原因码。
- 须 ADR 更新总体架构 §8.3 索引。
