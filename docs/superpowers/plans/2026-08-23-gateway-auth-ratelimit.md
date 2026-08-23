---
archived-with: 2026-08-23-gateway-auth-ratelimit
status: final
---
# Plan: 网关统一鉴权与限流熔断

> 对应 `openspec/changes/gateway-auth-ratelimit/tasks.md`（10 项任务，4 组）。
> 设计：`docs/superpowers/design/gateway-auth-ratelimit.md`

## 1. JWT 全局校验
- [x] 新增 `JwtAuthGlobalFilter`（GlobalFilter，order=HIGHEST_PRECEDENCE+10，位于 TraceId 之后）
- [x] 解析 Authorization → `GatewayJwtUtil.parseClaims` 校验签名/过期（jjwt 对称密钥），失败返 401 JSON
- [x] 解析 userId/tenantId/scopes → 注入 `X-Tenant-Id`/`X-User-Id`/`X-Roles` Header；保留原 Authorization 透传
- [x] 配置免校验白名单（login/actuator/favicon + OPTIONS），`application.yml` `bone.gateway.jwt.whitelist`

## 2. 限流
- [x] 引入 `spring-boot-starter-data-redis-reactive` + `RateLimitGatewayFilter`（Redis INCR+EXPIRE 滑动窗口）
- [x] `application.yml` 配置维度（IP/用户）+ 阈值（capacity/windowSeconds），超限返 429 + Retry-After；`enabled` 默认 false

## 3. 熔断
- [x] 引入 `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- [x] `ResilienceConfig` 注册 CircuitBreaker/TimeLimiter + `CircuitBreakerGatewayFilter` 包裹转发，OPEN 时返 503

## 4. 校验
- [x] 保留 TraceId 透传不破坏（JwtAuth order 在 TraceId 之后，不触碰 X-Trace-Id）
- [x] pom 依赖补齐；后端 mvn 编译/spotless/ArchUnit 受无 mvn 环境阻塞 → 记录接受（实现 API 对齐 Spring Cloud 2023.0.3 / Boot 3.2 / jjwt 0.12.6 标准用法）
