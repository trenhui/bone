# ADR-0010：账号直连权限（`iam_account_permission`）是否纳入 MVP（ADR-IAM-009）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-05-20 |
| **决策者** | 产品 + 架构（待评审） |
| **关联** | PRD IAM-003、[IAM 详设 §1.4](../../design/modules/6.%20IAM%E8%B4%A6%E5%8F%B7%E6%9D%83%E9%99%90%E7%AE%A1%E7%90%86%E6%A8%A1%E5%9D%97%E8%AF%A6%E7%BB%86%E8%AE%BE%E8%AE%A1%E6%96%B9%E6%A1%88.md) |

---

## 背景

PRD 原验收「将权限分配给角色**或用户**」。As-Is 仅有 `iam_role_permission`，无 `iam_account_permission`。

## 决策（待选 · 提议倾向 B）

| 选项 | 内容 |
|------|------|
| **A — [Target] 纳入** | 新增 `iam_account_permission` + API；JWT 合并角色权限 ∪ 直连权限 |
| **B — 收敛 PRD（已部分实现）** | MVP **仅 RBAC**；直连权限推迟至阶段 1；PRD BDD 已拆分 As-Is / [Target] |

**提议**：采用 **B**，除非已有客户合同明确要求用户级授权。

## 理由

- RBAC 满足当前控制台与引擎接入。
- 直连权限增加 UI、审计与撤销复杂度。

## 后果

- 若选 A：须更新 init SQL、IAM API、前端权限分配页、ArchUnit 测试。

## 合规与迁移

- 评审截止后更新 PRD / IAM 详设 §1.4 矩阵为 **Accepted** 或 **Rejected**。
