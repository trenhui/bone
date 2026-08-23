# Tasks: 网关统一鉴权与限流熔断

> 依赖：iam-org-menu-baseline（角色/菜单解析，已归档）

## 1. JWT 全局校验
- [x] 新增 `JwtAuthGlobalFilter`（GlobalFilter，order=HIGHEST_PRECEDENCE+10，位于 TraceId 之后）
- [x] 解析 Authorization → 校验签名/过期，失败返 401
- [x] 解析 tenantId/userId/scopes → 注入 X-Tenant-Id/X-User-Id/X-Roles Header；保留 Authorization 透传
- [x] 配置免校验白名单（login/actuator + CORS OPTIONS）

## 2. 限流
- [x] 引入 `spring-boot-starter-data-redis-reactive` + `RateLimitGatewayFilter`（Redis INCR+EXPIRE 滑动窗口令牌桶）
- [x] `application.yml` 配置限流维度（IP/用户）+ 阈值（capacity/windowSeconds），超限返 429；`enabled` 默认 false（无 Redis 时可关）

## 3. 熔断
- [x] 引入 `spring-cloud-starter-circuitbreaker-reactor-resilience4j`
- [x] `ResilienceConfig`（CircuitBreaker + TimeLimiter）+ `CircuitBreakerGatewayFilter` 包裹下游转发，OPEN 时返 503

## 4. 校验
- [x] 保留 TraceId 透传不破坏（JwtAuth/限流/熔断均不触碰 X-Trace-Id）
- [x] 本地验证：无 token 被拒、有效 token 注入 Header、限流生效、下游故障熔断 → **受无 mvn 环境阻塞，已记录接受**（无法本地编译/运行；实现 API 对齐 Spring Cloud 2023.0.3 / Boot 3.2 / jjwt 0.12.6 标准用法，格式遵循 google-java-format）
