---
archived-with: 2026-08-23-gateway-auth-ratelimit
status: final
---
# Design Doc: 网关统一鉴权与限流熔断

## 1. 背景与目标

`bone-gateway`（端口 8888，无 context-path）目前仅做路由 + TraceId 透传（`TraceIdRelayGatewayFilter`，`Ordered.HIGHEST_PRECEDENCE`）。下游各服务各自验签 JWT，网关无限流/熔断 = 安全闸口缺失。

目标：把网关升级为统一安全闸口——校验 JWT、注入租户/角色 Header、Redis 令牌桶限流、Resilience4j 熔断，且不破坏 TraceId 透传与 Authorization 透传（下游仍各自验签）。

## 2. 关键决策

### Decision 1：JWT 全局校验 —— 复用 bone-security
- 网关引入 `bone-security`(bone-core) 依赖，复用 `com.bone.core.security.jwt.JwtTokenService`/`JwtConfig`（`bone.iam.jwt` 前缀，HS256 对称密钥，所有模块共享 `BONE_IAM_JWT_SECRET_KEY`/`BONE_JWT_SECRET`）。
- 新增 `JwtAuthGlobalFilter`（`GlobalFilter, Ordered`），`getOrder()` 返回低于 TraceId 的优先级（在 TraceId 之后执行：TraceId=HIGHEST_PRECEDENCE，JwtAuth 用 `HIGHEST_PRECEDENCE + 10`），保证先透传 TraceId 再做鉴权。
- 逻辑：读 `Authorization`（`jwtConfig.headerName`）→ `JwtTokenService.parseClaims` 校验签名/过期 → 失败返回 401 JSON；成功解析 `userId/tenantId/scopes` → 写入下游请求头 `X-Tenant-Id`/`X-User-Id`/`X-Roles`（scopes 逗号拼接）。**保留原 Authorization 透传**（下游各自验签）。
- 白名单：`/api/v1/iam/auth/login`、`/actuator/**`、`/api/v1/system/...`（若 system 有公开端点）、CORS 预检（`OPTIONS`）等，`application.yml` 配置 `bone.gateway.auth.whitelist`。
- 网关校验用全局密钥，与下游一致，故可直接复用 `JwtTokenService`。

### Decision 2：限流 —— Redis 令牌桶（Reactive）
- 引入 `spring-boot-starter-data-redis-reactive` + `spring-cloud-starter-circuitbreaker-reactor-resilience4j`。
- 新增 `RateLimitGatewayFilter`（`GlobalFilter, Ordered`）：基于 Redis `INCR` + `EXPIRE` 实现滑动窗口令牌桶（无需 Bucket4j，轻量自实现），按 IP + 用户维度限流。
- 阈值在 `application.yml`：`bone.gateway.ratelimit.enabled`、`capacity`、`refillPerSecond`。
- 超过阈值返回 429（带 `Retry-After`）。

### Decision 3：熔断 —— Resilience4j CircuitBreaker
- 使用 `spring-cloud-starter-circuitbreaker-reactor-resilience4j`，对下游路由配置 `CircuitBreaker` + `TimeLimiter`。
- 通过 `ReactiveResilience4JCircuitBreakerFactory` 注册命名 CircuitBreaker，在路由转发 `filter` 链中包裹。
- 规则：失败率阈值（默认 50%）、滑动窗口大小、半开探测（`waitDurationInOpenState`）。
- 熔断开启时快速失败返回 503。

### Decision 4：TraceId 兼容
- `JwtAuthGlobalFilter.getOrder()` > TraceId 的 order，确保 TraceId 先写入。限流/熔断 filter 不触碰 X-Trace-Id。

## 3. 目录结构（新增）

```
bone-gateway/src/main/java/com/bone/gateway/
  filter/JwtAuthGlobalFilter.java        # GlobalFilter 鉴权 + Header 注入
  filter/RateLimitGatewayFilter.java     # GlobalFilter 限流
  config/ResilienceConfig.java           # CircuitBreaker/TimeLimiter Bean
  security/GatewayJwtUtil.java           # 封装 JwtTokenService 校验（可选）
  dto/ErrorBody.java                     # 统一 401/429/503 JSON
```

## 4. 风险

- 网关必须与下游共享同一 JWT 密钥（`BONE_JWT_SECRET` 环境变量注入，禁止硬编码）。
- 网关校验通过后仍透传原 Authorization，避免破坏下游自身验签。
- 限流/熔断用 Redis，需网关可访问 Redis（配置 `spring.data.redis`）。

## 5. 验收对照

- 无 token/无效 token → 401。
- 有效 token → 注入 X-Tenant-Id/X-User-Id/X-Roles。
- 白名单路径免校验。
- 超阈值 → 429。
- 下游故障 → 熔断 503 / 半开探测。
- TraceId 透传不被破坏。
