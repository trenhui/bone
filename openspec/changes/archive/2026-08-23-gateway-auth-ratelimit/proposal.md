# Proposal: 网关统一鉴权与限流熔断

## 目标（Why）

`bone-gateway` 目前仅做路由 + TraceId 透传，下游各服务各自验签 JWT，网关层无限流/熔断。这是安全闸口缺失：任一服务漏校验即越权；高并发无防护。PRD 将「API 统一 `/api/v1` + 网关」列为 P0，本 change 把网关升级为统一安全闸口。

## 范围（What）

1. **JWT 全局校验**：新增 `JwtAuthGlobalFilter`（GlobalFilter，优先级高于 TraceId），从 `Authorization` 解析 JWT 并校验签名/过期，失败返 401；校验通过后解析 `tenantId`/`userId`/`roles` 注入下游 Header（`X-Tenant-Id`/`X-User-Id`/`X-Roles`）。保留 Authorization 透传（下游仍各自验签）。配置免校验白名单（`/api/v1/iam/auth/login`、`/actuator/**`、CORS OPTIONS 等）。
2. **限流**：引入 Redis 令牌桶限流（`spring-boot-starter-data-redis` + 自定义 RateLimitFilter），按 IP/用户维度限流，阈值配置化。
3. **熔断**：引入 Resilience4j `CircuitBreaker` + `TimeLimiter`，对下游路由配置熔断规则（失败率阈值/半开探测）。
4. **不破坏 TraceId 透传**。

## 非目标（Non-Goals）

- 不重写各服务自身鉴权（下游可保留各自验签或校验网关注入 Header）。
- 不改路由表结构与 CORS 策略。

## 依赖

- `iam-org-menu-baseline`（已归档）：统一角色/菜单解析。
- `bone-security`(bone-core)：复用 `JwtTokenService`/`JwtConfig`（`bone.iam.jwt` 前缀对称密钥 HS256）。

## 验收标准（Acceptance Criteria）

- [ ] 无 token / 无效 token 请求被网关 401 拒绝。
- [ ] 有效 token 通过后注入 `X-Tenant-Id`/`X-User-Id`/`X-Roles` Header 转发。
- [ ] 白名单路径免校验。
- [ ] 限流生效（超过阈值返回 429）。
- [ ] 下游故障时熔断生效（快速失败 / 半开探测）。
- [ ] TraceId 透传不被破坏。
