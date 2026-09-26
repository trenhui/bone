# IAM 模块 [Target] / [Vision] Backlog

> **生成时间**：2026-09-25T21:18:06Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/iam-compliance-collector/backlog.yaml)（仅写未落地项）。

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `data-permission-engine` | 数据权限引擎 | 详设 §1.5 IAM-16 / §2.2 | As-Is 仅功能权限；数据权限 UI/文案需收敛 |
| **Target** | `slo-instrumentation` | SLO/SLI 仪表化（Prometheus + Grafana + 错误预算告警） | 详设 §1.5 IAM-17 / §9.1 | 仅文档 SLO，无现网采集 |
| **Target** | `tenant-isolation-three-mode` | 三模式租户隔离（行级/Schema/库） | 详设 §4.5 / ADR-IAM-005 | iam_tenant.isolation_mode 等扩展列未入 init.sql |
| **Target** | `role-inheritance-evaluation` | 角色继承求值（parent_role_id 闭包） | 详设 §1.4 / §2.2 | DDL 已预留 parent_role_id，无继承求值代码 |
| **Target** | `argon2id-password-hash` | 密码哈希 BCrypt → Argon2id | ADR-IAM-007 / §7.2.1 | 当前 SecurityConfig 使用 BCryptPasswordEncoder |
| **Target** | `rs256-jwks` | JWT HS256 → RS256/EdDSA + JWKS 热轮换 | ADR-IAM-004 / §7.2.2 | 当前 JwtTokenService 共享 secret HS256 |
| **Target** | `gateway-tenant-injection` | Gateway 注入可信 tenant（替代 JWT 透传） | 详设 §6.2 / 总体架构 §8 | 当前由 bone-iam JwtAuthenticationFilter 写入 TenantContext |
| **Target** | `audit-aop-async` | 审计日志 AOP 异步管道 + 队列削峰 | 详设 §7.1 | 当前为同步落库 |
| **Vision** | `platform-audit-bus` | 平台审计总线（全平台操作）+ WORM | ADR-IAM-006 / PRD IAM-004 商业版 | 当前 iam_audit_log 仅 IAM 域 |
| **Vision** | `abac-policy-runtime` | iam_policy 运行时求值 / 五层 ABAC·ReBAC | ADR-IAM-002 / 总体架构 §10.2 | DDL 已建表，无运行时求值 |
| **Vision** | `mfa-actual-enablement` | MFA 实际启用（TOTP / WebAuthn） | 详设 §1.4 | MfaController 注册/验证为 501 契约 |
| **Vision** | `sso-idp-federation` | SSO/LDAP/OAuth2/SAML IdP 联邦 | 详设 §3.1.3 / PRD §3.1.3 | 社区版仅 /sso/config 占位 + /sso/callback 501 |
| **Vision** | `authz-service-standalone` | 独立 authz-service（PDE） | ADR-IAM-003 / 总体架构 §10.2 | 当前进程内 Spring Security |
| **Vision** | `field-level-permission` | 字段级权限（与元数据 ACL 联动） | 详设 §3.5.3 | 须独立 ADR |
| **Vision** | `dengbao-three-bundle` | 等保三级专项交付包 | 详设 §7.3 | 商业版年度交付 |