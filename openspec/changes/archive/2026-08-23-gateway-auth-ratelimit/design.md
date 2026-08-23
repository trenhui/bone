# Design: 网关统一鉴权与限流熔断

## Context
`bone-gateway` 仅做路由 + TraceId 透传，下游各服务各自验签 JWT，无限流/熔断。这是安全闸口缺失：任一服务漏校验即越权；高并发无防护。PRD 将「API 统一 `/api/v1` + 网关」列为 P0。

## Decision 1：JWT 全局校验
- 新增 `JwtAuthGlobalFilter`（GlobalFilter 优先度高）：从 `Authorization` 解析 JWT，校验签名/过期；失败返回 401。
- 校验通过后解析 `tenantId`/`roles`/`userId`，注入下游 Header（`X-Tenant-Id`/`X-User-Id`/`X-Roles`）。
- 白名单：`/api/v1/iam/auth/login`、`/actuator/**` 等免校验。

## Decision 2：限流
- Redis 令牌桶（`spring-boot-starter-data-redis` + 自定义 RateLimitFilter 或 `Bucket4j`）。按 IP/用户维度限流，配置在 `application.yml`。

## Decision 3：熔断
- Resilience4j `CircuitBreaker` + `TimeLimiter`，对下游路由配置熔断（失败率阈值、半开探测）。

## Risks
- 网关需与下游共享 JWT 密钥（环境变量/配置中心注入，禁止硬编码）。
- 注入 Header 需下游信任网关（下游可保留校验或仅校验网关签名）。
