# API 网关（bone-gateway）详细设计方案 v1

> **文档状态**：v1 从零产出（2026-09-29 夜间 A 阶段）。本模块在 `doc/design/modules/` 下**此前无任何设计稿**（锚定 `design_doc: null`），本稿为第一版。
> **对齐**：工程实现与门禁以 `doc/architecture/Bone-DDD-最终实践方案.md` 为准（其 §12 / §G-1.7 为 L4，本文档不复制门禁状态）；HC 编号语义一律引用 `doc/design/_global-contracts.yaml` 的 `hc_semantics`。
> **仓库对照（As-Is）**：唯一网关 `bone-platform/bone-gateway`，端口 **8888**，Spring Cloud Gateway（**WebFlux / Reactive 栈**）。
> **HTTP 真源（当前）**：`src/main/resources/application.yml` 的 9 条路由 + 5 个 `GlobalFilter`；**尚无** OpenAPI spec（`doc/architecture/openapi/` 下无 `gateway-v1.yaml`，对应锚定 HC-007 `violated`）。
> **DDL 真源**：`bone-init.sql` —— 网关**无状态、零表**（by-design，见 §4.0）。
> **错误码前缀**：`GW_`（**尚未登记**，见 §5.3）。
> **配套报告**：`doc/design/modules/review-report-bone-gateway.md`。

---

## 0. 可观测性契约（SLI/SLO）

| SLI | 目标（**[Target]**） | As-Is（2026-09-29 实测） |
|-----|---------------------|--------------------------|
| 网关转发成功率 | ≥ 99.95% / 日 | 无埋点（仅 Actuator `metrics/prometheus`，无自定义指标） |
| 转发 P99 附加延迟（网关自身开销） | < 20ms | 无埋点 |
| 401 率（鉴权失败占比） | 可观测且可告警 | JWT 失败仅 `Optional.empty()`，无计数器 |
| 429 率（限流触发） | 可观测 | 限流默认**关闭**（`enabled: false`），无数据 |
| 熔断打开次数 / 路由 | 可观测并聚合适配健康检查 | `GatewayModuleReactiveHealthIndicator` 为 **STUB**（静态 UP，自述未做） |
| 上游路由存活 | 逐路由心跳并聚合 | **未实现**（STUB javadoc 明确列出待办） |

> **As-Is 判定依据**：`grep -rn "MeterRegistry\|Timer\|Counter\|@Observed" src/main/java` → **0 命中**；`application.yml:91-101` 暴露 `health,info,metrics,prometheus`（本轮复核模块中**暴露面最大**的一个，`prometheus` 端点依赖 JWT 白名单未放通而受保护，口径自洽）。

---

## 1. 模块概述

### 1.0 As-Is（实测，见报告附录 A）

| 维度 | 当前仓库 |
|------|----------|
| 服务 | `bone-platform/bone-gateway`（`:8888`，`${BONE_GATEWAY_PORT}` 可覆盖） |
| 技术栈 | Spring Cloud Gateway + **WebFlux（Reactive）**，非 Servlet |
| 代码规模 | `src/main/java` **14 个类**；`src/test/java` **5 个类**（4 个 RouteIT + 1 个 ArchitectureTest） |
| 分层 | 无 `domain` / `application` 包（网关为基础设施组件，by-design）：`config` / `filter` / `security` / `observability` / `dto` |
| 持久化 | **零表、零实体**（无 `@EnableSqlRepositories`，符合网关无状态定位） |
| 路由 | **9 条**（`application.yml:20-64`），测试环境仅挂载其中 **4 条** |
| 全局过滤器 | **5 个**（`TraceIdRelay` / `JwtAuth` / `RateLimit` / `CircuitBreaker` + `DedupeResponseHeader`） |
| 鉴权 | 网关侧 JWT 验签（HS256，可回退 RS256）+ 白名单；下游**各自再次验签**（纵深防御） |
| 限流 | Redis 令牌桶（Lua 原子脚本），**默认关闭** |
| 熔断 | Resilience4j，**按 routeId 隔离** |
| 前端 | 8 个微应用（3000 / 3003-3009），网关 CORS 仅放通 **3000、3008** |

### 1.1 定位与边界

bone-gateway 是**唯一的南北向入口**，承担且仅承担**横切关注点**；业务语义一律不属于网关：

| 关注点 | 归属网关 | 归属下游 | 说明 |
|--------|----------|----------|------|
| 路由转发 | ✅ | — | 9 条 `Path` 谓词路由 |
| 身份**认证**（验签 / 过期） | ✅ | ✅（二次验签） | 网关先挡，下游 `JwtAuthenticationFilter` 再验，纵深防御 |
| 身份**授权**（RBAC / 数据权限） | ❌ | ✅ | 网关只注入 `X-Roles`，不判定；判定在各模块 `@PreAuthorize` |
| 租户**注入** | ✅ | ✅（绑定上下文） | 见 §2.3，网关防伪造、下游 fail-closed |
| 限流 / 熔断 | ✅ | — | 网关统一，下游不重复 |
| 链路 ID | ✅ | — | `X-Trace-Id` / `X-Request-Id` |
| 错误码（业务语义） | ❌ | ✅ | 网关只产**传输层**错误（401/429/503），业务错误码由下游给 |

> **裁决（写入本稿）**：网关**不产生业务错误码**，只产生传输层状态码；但因它是前端唯一入口，**其错误响应信封必须与平台 `ApiResponse` 一致**（当前不一致，见 §5.2 / 报告 G-3）——这是本模块最需要优先纠正的契约问题。

### 1.2 核心目标

- 让**横切能力可配置且真生效**（当前限流默认关、熔断配置三源冲突，运维调参无效，见 G-1/G-2）；
- 让**错误响应与平台信封一致**（当前手写 JSON，前端无法按 `ApiResponse` 解析，见 G-3）；
- 让**可观测性从 STUB 走向真实**（上游心跳、熔断状态、限流计数，见 G-6）。

---

## 2. 功能设计

### 2.1 路由（P0，已落地）

| # | routeId | Path 谓词 | 上游 | 环境变量 |
|---|---------|-----------|------|----------|
| 1 | `extension-studio` | `/api/v1/extension/**` | 8088 | `BONE_EXTENSION_STUDIO_URI` |
| 2 | `bone-iam` | `/api/v1/iam/**`, `/api/v1/apps/**` | 8081 | `BONE_IAM_URI` |
| 3 | `bone-masterdata` | `/api/v1/masterdata/**` | 8084 | `BONE_MASTERDATA_URI` |
| 4 | `bone-system` | `/api/v1/system/**` | 8083 | `BONE_SYSTEM_URI` |
| 5 | `bone-console` | `/api/v1/console/**` | 8083 | `BONE_SYSTEM_URI`（与 system 同上游） |
| 6 | `bone-integration` | `/api/v1/integration/**` | 8085 | `BONE_INTEGRATION_URI` |
| 7 | `bone-file` | `/api/v1/file/**` | 8107 | `BONE_FILE_URI` |
| 8 | `bone-notification` | `/api/v1/notification/**` | 8083 | `BONE_SYSTEM_URI`（SDK，宿主为 system） |
| 9 | `studio-generator` | `/api/v1/generator/**` | 8086 | `BONE_GENERATOR_URI` |
| 10 | `bone-metadata-server` | `/api/v1/metadata/**`, `/api/v1/runtime/**` | 9001 | `BONE_METADATA_SERVER_URI` |

> 上表按 `application.yml:20-64` 逐条抄录（实测为 **10 条**，其中 #5/#8 与 #4 共用 `BONE_SYSTEM_URI`）。routeId 即**熔断隔离单元**（§2.5）与**限流/观测维度**。

### 2.2 鉴权（P0，已落地）

过滤器 `JwtAuthGlobalFilter`（order = `HIGHEST_PRECEDENCE + 10`）：

1. `OPTIONS` 请求与白名单前缀直接放行；
2. 取 `Authorization` 头 → `GatewayJwtUtil.parse()`：HS256 验签，**失败回退 RS256**（ADR-0005 双轨过渡）；
3. 失败 → `GatewayErrorWriter.write(401, "未授权：无效或缺失 token")`；
4. 成功 → 构造 `GatewayPrincipal{userId, username, tenantId, scopes}`；

**关键安全设计（本模块亮点，须保留）**：注入可信头前**先移除客户端同名头**——

```java
builder.headers(h -> {
  h.remove("X-Tenant-Id");
  h.remove("X-User-Id");
  h.remove("X-Roles");
});
```

这消除了「客户端伪造 `X-Tenant-Id` 直通下游」的越权向量。原 `Authorization` 保留透传，下游各自再验签。

**白名单（当前双真源，须收敛，见报告 G-4）**：

| 来源 | 值 |
|------|-----|
| `GatewayJwtProperties` Java 默认 | `/api/v1/iam/**auth**/login`、`/actuator/health`、`/favicon.ico` |
| `application.yml:71-75` 实际生效 | `/api/v1/iam/login`、`/actuator/health`、`/favicon.ico` |
| IAM 真实端点（`AuthController:37,46`） | **`/api/v1/iam` + `/login`** |

> 结论：**yml 正确、Java 默认值漂移**。当前因 yml 显式绑定而覆盖默认值，不发作；一旦 yml 不配（其他部署形态），登录将被 401 拦截导致全站不可用。

### 2.3 租户注入（P0，已落地）

`X-Tenant-Id` 由网关注入 → 下游 `WebTenantConfiguration`（`bone-integration` / `bone-masterdata` / `studio-generator` 各有实现）读取并 `TenantContext.setTenantId()`，**头缺失时不回落 0，保持空由 SDK `MissingTenantContextException` 失败关闭**。经交叉验证，上下游 header 名与 fail-closed 语义**完全一致**（亮点）。

> **覆盖范围缺口**：10 条路由对应 10 个上游，但 `WebTenantConfiguration` 实测仅 3 个模块持有（integration / masterdata / studio-generator）。其余上游（iam / system / extension-studio / file / metadata-server）是否有等价绑定需逐模块核实——若缺失，则网关注入的 `X-Tenant-Id` 在这些模块**被静默丢弃**。列为 B 项（报告 G-11）。

### 2.4 限流（P0 能力 / 默认关闭）

`RateLimitGatewayFilter`（order = `JwtAuth.ORDER + 10`）：Redis 令牌桶，Lua 脚本原子 `INCR` + 首条 `EXPIRE`，消除「计数永不过期导致永久 429」的竞态（设计正确，亮点）。超限返 **429** + `Retry-After`。

维度开关：`byIp`（默认 true）/ `byUser`（true）/ `byTenant`（**默认 false**）。

| 问题 | 现状 | 处置 |
|------|------|------|
| 默认关闭 | `enabled: false` | 生产需显式开启（B 项，依赖 Redis 就绪） |
| IP 可伪造 | `resolveIp` 优先取 `X-Forwarded-For` | 未认证端点可被改 XFF 绕过限流（报告 G-2） |
| 下游重复限流 | 网关已限流 | 下游不应再实现同维度限流（避免叠加放大） |

### 2.5 熔断（P0，已落地但配置冲突）

`CircuitBreakerGatewayFilter`（order = `JwtAuth.ORDER + 20`）：**按 routeId 隔离** breaker（`ConcurrentHashMap` 缓存）。javadoc 记录了演进动因——早期单一全局 breaker（`id="gateway-routing"`）在任一上游 503 时会打开并放纵到其它健康路由，导致全栈联调被误伤；按 routeId 隔离后故障域受限（**设计正确，亮点**）。

> **本模块最严重的配置治理问题（报告 G-1）**：熔断参数存在**三处真源且互不生效**，详见 §6.2。运维按 `application.yml` 调参**实际无效**。

### 2.6 链路透传（P0，已落地）

`TraceIdRelayGatewayFilter`（order = `HIGHEST_PRECEDENCE`，**早于鉴权**）：客户端有 `X-Trace-Id` / `X-Request-Id` 则沿用，否则生成 32 位 UUID；同时写入**请求头（转发下游）与响应头（回前端）**。对齐 `Bone-API-规范` §10。

> 注：信任客户端传入的 `X-Trace-Id` 便于跨系统串链，但存在日志污染面（攻击者可控字符串进入日志）。建议加长度/字符集白名单校验（报告 G-9）。

### 2.7 过滤器编排（当前 order 表）

| 序 | 过滤器 | order 值 | 职责 |
|----|--------|----------|------|
| 1 | `TraceIdRelayGatewayFilter` | `HIGHEST_PRECEDENCE` | 链路 ID（必须最早，使 401/429/503 也带 traceId） |
| 2 | `JwtAuthGlobalFilter` | `HIGHEST_PRECEDENCE + 10` | 认证 + 注入可信头 |
| 3 | `RateLimitGatewayFilter` | `JwtAuth.ORDER + 10` | 限流（须在鉴权后，才能取到 `X-User-Id`） |
| 4 | `CircuitBreakerGatewayFilter` | `JwtAuth.ORDER + 20` | 熔断（包裹下游转发） |
| — | `DedupeResponseHeader` | SCG 内置 | 去重 CORS 响应头 |

编排自洽：**鉴权限流熔断** 顺序正确，且链路 ID 早于鉴权保证错误响应可追踪。

---

## 3. 数据模型

### 3.0 现状：零表（**by-design**，非缺口）

网关为无状态转发组件，**不应持有业务表**。`bone-init.sql` 无 `gw_*` / `gateway_*` 表 —— 本轮复核**判定为合规**，不计缺口。

唯一状态是 **Redis 限流计数**（key 前缀 `bone:gateway:ratelimit:`），为可重建的临时态，符合无状态定位。

> **待裁决（L3）**：若未来要做「配额持久化 / 熔断事件审计 / 网关访问日志落库」，则需新增表并重新评估无状态定位。当前**不需要**。

---

## 4. API 设计

### 4.0 横切约定

网关**不定义业务 API**，仅透传。其自身产生的响应只有 4 类错误：

| 状态码 | 触发点 | 当前文案 |
|--------|--------|----------|
| 401 | `JwtAuthGlobalFilter` 验签失败 | `未授权：无效或缺失 token` |
| 429 | `RateLimitGatewayFilter` 超限 | `请求过于频繁，请稍后重试` |
| 503 | `CircuitBreakerGatewayFilter` 熔断/下游不可用 | `下游服务暂不可用（熔断）` |
| 404 | SCG 无匹配路由 | SCG 默认（非本模块产出） |

### 4.1 错误响应契约（**本稿核心纠正项**）

**当前实现**（`GatewayErrorWriter:22-29` 手写拼串）：

```json
{"code":401,"message":"未授权：无效或缺失 token","traceId":"a1b2..."}
```

**平台标准信封**（`com.bone.core.model.ApiResponse` 实测字段）：

```json
{"success":false,"code":401,"message":"...","data":null,"timestamp":"2026-09-29T06:20:00"}
```

**差异**：

| 字段 | 网关当前 | ApiResponse | 影响 |
|------|----------|-------------|------|
| `success` | ❌ 缺失 | `Boolean` | 前端按 `success` 判成败 → 得 `undefined`（falsy，侥幸不误判） |
| `timestamp` | ❌ 缺失 | `String` | 排障缺服务端时间 |
| `traceId` | ✅ 有 | ❌ 无此字段 | 网关**多出**字段，前端需特殊分支 |
| `errorCode` | ❌ 缺失 | 平台规范要求字符串业务码 | 违反 `Bone-错误码登记.md` §3.1「新接口强制字符串业务码」 |

**目标态**：网关错误响应改为 `ApiResponse` 信封（序列化 core 类，禁止手写拼串），`traceId` 保留在 **HTTP 响应头 `X-Trace-Id`**（已由 `TraceIdRelayGatewayFilter` 写入），不进 body。

### 4.2 错误码表（`GW_`，需在 `Bone-错误码登记.md` §6 新建 `GW_` 段）

| 码 | HTTP | 语义 |
|----|------|------|
| `GW_UNAUTHORIZED` | 401 | 无效或缺失 token |
| `GW_RATE_LIMITED` | 429 | 触发限流 |
| `GW_UPSTREAM_UNAVAILABLE` | 503 | 下游不可用 / 熔断打开 |
| `GW_ROUTE_NOT_FOUND` | 404 | 无匹配路由 |

> 现状：**零 `GW_` 码登记**，且 `grep -rn "ErrorCode" src/` → **0 命中**（无常量类）。

---

## 5. 技术实现

### 5.1 分层结构（当前，符合网关定位）

```
com.bone.gateway
├── BoneGatewayApplication.java
├── config/      GatewayJwtProperties / GatewayRateLimitProperties / GatewayResilienceProperties / ResilienceConfig
├── filter/      TraceIdRelay / JwtAuth / RateLimit / CircuitBreaker / GatewayErrorWriter
├── security/    GatewayJwtUtil / RsaKeyParser
├── observability/ GatewayModuleReactiveHealthIndicator
└── dto/         ErrorBody   ← 零引用（死代码，见报告 G-7）
```

`ArchitectureTest` 对分层规则统一 `allowEmptyShould(true)`（网关无 domain/application），符合既有约定。

### 5.2 熔断配置三源冲突（**必改**，G-1）

| 真源 | 位置 | 关键值 | 是否生效 |
|------|------|--------|----------|
| A. Java `configureDefault` | `ResilienceConfig.java:20-44` | failureRate 50、**wait 10s**、**window 10**、halfOpen 3、TimeLimiter **5s** | ✅ **实际生效** |
| B. yml `resilience4j.instances.gatewayRoutes` | `application.yml:106-122` | failureRate 50、**wait 30s**、**window 50**、halfOpen 5、TimeLimiter **30s** | ❌ **不生效** |
| C. breakerId | `CircuitBreakerGatewayFilter:49` | = **routeId**（如 `bone-iam`） | —— |

**不生效的双重原因**：
1. `ResilienceConfig` 调用了 `factory.configureDefault(...)`，该方法为**所有** `create(id)` 提供默认配置，Spring Cloud CircuitBreaker 不再回落到 Resilience4j 原生 `instances` 配置；
2. 即便回落，breakerId 是 `routeId`（`bone-iam` 等），与 yml 定义的实例名 `gatewayRoutes` **字符串不匹配**。

> `application.yml:103-105` 的注释**已自认**该限制（"如需严格对齐下述实例，需在 filter 中显式指定 id=\"gatewayRoutes\"…"），但**未修复** → 运维按 yml 调 `wait-duration-in-open-state: 30s` 实际得到 10s。
> **本稿裁决**：**以 Java `ResilienceConfig` 为唯一真源**，删除 yml 中 `resilience4j.*` 整段（或反向：删 Java、yml 改名为 routeId 粒度）。不得两处并存。

### 5.3 配置项

| 配置 | 默认 | 说明 |
|------|------|------|
| `bone.gateway.jwt.secret-key` | `BONE_IAM_JWT_SECRET_KEY` → `BONE_JWT_SECRET` → `dev-only-...` | 启动期 fail-fast：空 / <32 字节 / prod 用默认 → **拒绝启动** ✅ |
| `bone.gateway.jwt.whitelist` | yml 3 项 | 与 Java 默认漂移（§2.2） |
| `bone.gateway.ratelimit.enabled` | **false** | 生产需开启 |
| `bone.gateway.ratelimit.{capacity,windowSeconds,byIp,byUser,byTenant}` | 100 / 60 / t / t / f | |
| `bone.gateway.resilience.enabled` | **true** | |
| `bone.data.redis.{host,port,timeout}` | localhost:6379, 3s | 限流依赖 |

### 5.4 CORS（**须修**，G-10）

当前 `application.yml:11-19`：`allowedOrigins` 仅 `localhost:3000`、`localhost:3008`，`allowedMethods: "*"`，`allowCredentials: true`。

实测前端 8 个微应用端口：`bone-shell` 3000、`bone-iam-app` 3003、`bone-metadata-app` 3004、`bone-masterdata-app` 3005、`bone-integration-app` 3006、`bone-extension-app` 3008、`bone-generator-app` **3009**。

> **影响边界（避免过度断言）**：qiankun 架构下子应用由 shell(3000) 加载，页面 origin 为 3000 → **主路径不触发**；但**子应用独立 dev 直连网关**（如直接访问 `localhost:3004` 联调 metadata）会被 CORS 拒绝。故定级为**建议级**而非阻断级。
> 对比：同仓 `bone-file/application.yml` 放通 `3000-3008` 共 8 个 origin —— 口径**不一致**，且两者都**漏了 3009（generator-app）**。

---

## 6. 性能与安全

| 项 | 现状 | 评价 |
|----|------|------|
| Reactor 非阻塞 | 全链路 `Mono`，限流用 `ReactiveStringRedisTemplate` | ✅ 正确（`ReactiveHealthIndicator` 亦按 Reactive 栈实现） |
| 限流原子性 | Lua 脚本 `INCR` + 首条 `EXPIRE` | ✅ 消除竞态窗口 |
| 密钥管理 | 环境变量注入 + 启动 fail-fast | ✅ 优于同仓多数模块 |
| 纵深防御 | 网关注入可信头前先 `remove` 伪造头 | ✅ 关键，见 §2.2 |
| 下游二次验签 | 保留 `Authorization` 透传 | ✅ |
| 运维端点保护 | `metrics`/`prometheus` 已暴露但**不在 JWT 白名单** → 需鉴权 | ✅ 与 yml:73 注释口径一致 |
| CORS 收窄 | `allowedMethods: "*"` + `allowCredentials: true` | ⚠ 建议收窄到实际方法 |
| traceId 可控 | 信任客户端 `X-Trace-Id` | ⚠ 建议加字符集/长度校验 |
| JWT 吊销 | **无黑名单 / 无登出失效** | ⚠ 通用取舍，建议登记为已知限制 |
| JSON 转义 | `escape()` 仅处理 `\` 与 `"` | ⚠ 见下 |

> **JSON 转义（诚实定级）**：`GatewayErrorWriter.escape()` 未转义 `\n` / `\r` / `\t` 及控制字符，若 message 含换行将产出**非法 JSON**。但当前三处 message **均为固定中文常量、不含换行** → **当前不发作**，属潜在缺陷（报告 G-8，建议级）。一旦改为透传异常 message 即会触发，故应在改信封（§4.1）时一并消除。

---

## 7. 测试计划

| 层 | 现状 | 目标 |
|----|------|------|
| 路由转发 IT | 4 个（`iam` / `extension` / `generator` / `integration`），用**真实 Mock HTTP Server** + `WebTestClient` 全链路 | 补齐剩余 6 条路由（masterdata / system / console / file / notification / metadata-server） |
| 测试环境路由 | `application-test.yml` 仅挂载 4 条 | 与生产 10 条对齐 |
| 鉴权 | **0** | 补：无 token→401、白名单放行、伪造 `X-Tenant-Id` 被清除、HS/RS 双模 |
| 限流 | **0** | 补：超限→429 + `Retry-After`、维度 key 构成 |
| 熔断 | **0** | 补：下游 503 连续触发→打开→降级 503；**校验生效参数**（G-1 回归） |
| 架构守护 | `ArchitectureTest`（7 条规则，`allowEmptyShould(true)`） | 保持 |

> 实测：`grep -rln "ratelimit\|CircuitBreaker\|Jwt\|whitelist" src/test/` → **0 命中**，鉴权/限流/熔断三大横切能力**零测试**。

---

## 8. 监控与告警

- **Actuator**：`health,info,metrics,prometheus`（`show-details: when-authorized`）。
- **健康检查**：`GatewayModuleReactiveHealthIndicator` 当前返回静态 `UP` + `status=STUB`，javadoc 已列明待办：逐上游 HTTP 心跳、Resilience4j 熔断状态、Redis 桶容量、白名单命中与签名失败率。**建议保留 STUB 明示**（与 `bone-system` S-9 同口径：占位需自述限制，不得伪装真实）。
- **告警（[Target]）**：401 率突增（撞库/密钥轮换失误）、429 率突增、任一 routeId 熔断打开、上游心跳连续失败。

---

## 9. 总结

bone-gateway 是**本轮复核中横切设计质量最高的模块**：Reactive 栈使用正确、租户头防伪造、限流原子性、熔断按路由隔离、密钥 fail-fast、过滤器 order 显式编排——这些都对。

但存在**三类必须纠正的治理问题**：

1. **熔断配置三源冲突**（G-1）：yml 参数实际不生效，运维按文档调参无效 —— 最容易踩的坑；
2. **错误信封与平台 `ApiResponse` 不一致**（G-3）：网关是前端唯一入口，手写 JSON 导致前端无法统一解析；
3. **CORS 与白名单的双真源漂移**（G-10 / G-4）：当前不发作，但一旦部署形态变化即成故障。

均无 L3/L4 阻断项，B 段可全量落地。

---

## 附录 A. As-Is 证据（2026-09-29 实测）

| 结论 | 证据 |
|------|------|
| 10 条路由 | `application.yml:20-64` |
| 过滤器 order 编排 | `TraceIdRelayGatewayFilter:45` / `JwtAuthGlobalFilter:25` / `RateLimitGatewayFilter:22` / `CircuitBreakerGatewayFilter:30` |
| 防伪造可信头 | `JwtAuthGlobalFilter:68-74`（先 `remove` 再注入） |
| 限流 Lua 原子脚本 | `RateLimitGatewayFilter:48-58` |
| 熔断按 routeId 隔离 | `CircuitBreakerGatewayFilter:48-50` + 演进 javadoc `:19-26` |
| 熔断配置两处冲突 | `ResilienceConfig.java:20-44`（10s/10/3/5s） vs `application.yml:106-122`（30s/50/5/30s）；自认注释 `application.yml:103-105` |
| 错误信封手写拼串 | `GatewayErrorWriter:22-29`；`ApiResponse` 字段 `ApiResponse.java:22-26` |
| `ErrorBody` 死代码 | `grep -rn "ErrorBody" src/ \| grep -v dto/ErrorBody.java` → 0 命中 |
| 白名单双真源 | `GatewayJwtProperties:57-59`（`iam/auth/login`） vs `application.yml:72`（`iam/login`）；IAM 真值 `AuthController:37,46` |
| CORS 仅 3000/3008 | `application.yml:14-16`；前端端口 `apps/*/vite.config.ts`（3000/3003/3004/3005/3006/3008/3009） |
| 限流默认关闭 | `application.yml:77` `enabled: false` |
| 限流/熔断/JWT 零测试 | `grep -rln "ratelimit\|CircuitBreaker\|Jwt\|whitelist" src/test/` → 0 命中 |
| 测试仅挂载 4 条路由 | `src/test/resources/application-test.yml` |
| HealthIndicator STUB | `GatewayModuleReactiveHealthIndicator:27-36`（`status=STUB` + limitation 文案） |
| 上下游 `X-Tenant-Id` 一致 | `bone-integration/.../WebTenantConfiguration.java:22,32`（fail-closed，javadoc 交叉引用 bone-gateway） |
| 密钥 fail-fast | `GatewayJwtProperties:31-52`（空 / <32 / prod 默认 → 拒启） |
| 无 `GW_` 错误码 | `grep -n "GW_" doc/architecture/Bone-错误码登记.md` → 0 命中；`grep -rn "ErrorCode" src/` → 0 命中 |
