# ADR-0002：默认 RBAC + 权限码作为 JWT authorities（ADR-IAM-001）

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-05-20 |
| **决策者** | 架构组 |
| **关联** | [IAM 详设 §3.5.1](../../design/modules/6.%20IAM%E8%B4%A6%E5%8F%B7%E6%9D%83%E9%99%90%E7%AE%A1%E7%90%86%E6%A8%A1%E5%9D%97%E8%AF%A6%E7%BB%86%E8%AE%BE%E8%AE%A1%E6%96%B9%E6%A1%88.md) |

---

## 背景

BONE MVP 需统一各微服务鉴权方式；避免每域自建用户表与权限模型。`bone-iam` 已具备账号/角色/权限表与 Spring Security + JWT。

## 决策

1. **授权模型**：默认 **RBAC**（账号 → 角色 → 权限码）。
2. **JWT Claim**：登录时将 `iam_permission.code` 列表写入 JWT（claim 名 `scopes` 或 `authorities`，与消费方约定一致）。
3. **下游校验**：各服务进程内 `hasAuthority('{domain}:{resource}:{action}')` 或等价过滤器；**不**在 MVP 引入独立 authz 微服务。
4. **权限码真源**：字符串与 DB `iam_permission.code`、前端 `@bone/shared-types` `BonePermissionCodes` 一致。

## 理由

- 与 Spring Security 生态一致，实现成本低。
- 无状态 JWT 适合水平扩展。
- 权限码字符串可跨元数据/扩展/集成域统一命名（见 IAM 详设 §3.7）。

## 后果

### 正面

- 各引擎可渐进接入同一套权限码。
- 与 OpenAPI / 契约测试可对齐。

### 负面 / 风险

- JWT 体积随权限增多而增大 → [Target] 权限快照 + introspection（ADR-0004/0003）。
- 权限变更非实时生效（至 Token 过期）→ [Target] Redis 黑名单 + 短 TTL access token。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| 每请求回调 IAM 鉴权 | 延迟与单点依赖高 |
| 独立 OPA/Casbin 侧车 | MVP 过重 |

## 合规与迁移

- **As-Is（2026-05-20）**：`AccountAuthoritiesQueryHandler` 经 `iam_account_role` → `iam_role_permission` → `iam_permission.code` 解析；`LoginHandler` / `refresh` 写入 JWT `scopes`；`bone-init.sql` 预置 SUPER_ADMIN 绑定；无绑定管理员回退 `DefaultPermissionCodes`。
- 新增权限码须同步：init 数据 / 迁移、`BonePermissionCodes`、消费方 `@PreAuthorize`。
