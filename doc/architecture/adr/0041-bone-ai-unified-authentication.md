# ADR-0041：bone-ai 接入统一鉴权层（依赖 bone-security + ai:* 权限码 + 写端点 @PreAuthorize）

| 项 | 内容 |
|----|------|
| **状态** | **提议（Proposed）** — 属 L3（新增依赖 + 权限目录数据），待架构师审批后实施 |
| **日期** | 2026-10-17 |
| **决策者** | 架构师（待） |
| **关联** | HC-003（统一响应）、E-4（入站边界）、ADR-0002（权限码作为 JWT authorities）、ADR-0034（租户隔离显式性）、[ADR-0031](./0031-sdk-optimistic-lock-and-async-tenant-context.md)（异步入口租户声明） |
| **下游同步** | `bone-ai/bone-ai-app/pom.xml`、`bone-ai/bone-ai-server/pom.xml`、`bone-platform/bone-iam` 权限码目录、`scripts/migration/00XX_ai_permission_seed.sql`、**扩展** `scripts/check-controller-authorization.py` 扫描根至 `bone-ai` |

---

## 背景

### 1. 事实：bone-ai 完全没有认证层

| 检查项 | 实测 |
|--------|------|
| `spring-boot-starter-security` / `bone-security` 依赖 | **bone-ai 全模块零声明** |
| `bone-web`（`TenantInterceptor` 写入 `TenantContext` 的唯一 MVC 链路） | **零声明** |
| `@PreAuthorize` | **全模块 0 处** |
| `SecurityFilterChain` | **无** |
| `check-controller-authorization.py` 扫描根 | **不含 `bone-ai`** |

`bone-security`（`bone-framework/bone-security/pom.xml:22`）已含 `spring-boot-starter-security`，且 `AbstractJwtAuthenticationFilter:91` 已实现「认证后把 `X-Tenant-Id` 改写为已签名 claim」。**能力齐备，缺的是接线。**

### 2. 但租户边界没有被突破 —— 缺的是认证，不是隔离

各 AI 库的基础设施适配器均已 fail-closed：

- `bone-ai-rag/.../TenantContextProviderAdapter`：`currentTenantId()` 在空上下文时抛 `AI_RAG_TENANT_CONTEXT_MISSING`
- `bone-ai-gateway`、`bone-ai-trace` 同构
- `bone-ai-app/.../AiTenantContext:23/39` 提供 `requireTenantId()` / `requireNumericTenantId()`，缺上下文抛 `AI_APP_TENANT_CONTEXT_MISSING`

因此 bone-ai-app 的 8 个端点当前**全部返回 400**（无人写入 `TenantContext`，因为未依赖 `bone-web`）。这是**误打误撞自保**，不是设计保障 —— 一旦有人在前面挂上任意 `TenantInterceptor`，鉴权仍是零。

### 3. 两类风险需要分开裁决

**风险 A：bone-ai-app 自述「生产拓扑聚合」却无认证。**
`bone-ai-app/pom.xml:20-21` 写明「生产拓扑聚合：…可部署的生产 Spring Boot 应用」，`server.port: 8080`，但不经 `bone-gateway` 路由（网关 11 条路由无 `/api/ai/**`）。即：**若直接暴露 8080 端口，则无任何认证可达**。

**风险 B：bone-ai-server 的 3 个端点直接信任入参租户。**
`ClaimController.java:38/60/75` 用 `@RequestPart("tenantId")` 取租户，**任意调用方可指定任意租户**。但该模块 pom 自述「非生产拓扑——…本模块是『如何接线』的活模板 + 集成测试床」（`pom.xml:19-20`）。

### 4. 为什么现在不加 `@PreAuthorize`

无 `spring-boot-starter-security` 时，`@PreAuthorize` **不被强制执行** —— 注解在但不生效，代码看起来有鉴权、实则全通。**这比没有注解更危险**（审计时误判为已保护）。这也是本轮刻意**不**把 `check-controller-authorization.py` 扫描根扩到 bone-ai、也**不**把 14 条违规塞进 baseline 的原因：baseline 会把债固化成「永久例外」。

---

## 决策

### D1. bone-ai-app 引入既有安全组件，不自造 JWT 解析

```xml
<dependency><groupId>com.bone</groupId><artifactId>bone-security</artifactId>
  <version>${bone-security.version}</version></dependency>
<dependency><groupId>com.bone</groupId><artifactId>bone-web</artifactId>
  <version>${bone-core.version}</version></dependency>
```

沿用 `bone-iam/pom.xml:23-27` 的既有坐标写法。`bone-web` 仅为复用 `TenantInterceptor`（`bone-web/.../TenantInterceptor.java:49`，从 `X-Tenant-Id` 建上下文）—— **这正是当前 8 个端点 400 的缺失环节**。

### D2. 权限码按 `{域}:{资源}:{动作}` 注册到 bone-iam

沿用既有命名（`metadata:model:read`、`commerce:channel:write`）：

| 权限码 | 覆盖端点 |
|--------|---------|
| `ai:pipeline:write` | `POST /pipeline`（编排，触发 LLM 计费） |
| `ai:hitl:read` / `ai:hitl:write` | `GET /hitl/items*`、`POST /hitl/items/{itemId}/act` |
| `ai:rag:read` / `ai:rag:write` | `POST /rag/retrieve`、`POST /rag/ingest` |
| `ai:integration:invoke` | `POST /integration/invoke`（自动化模式打外部系统，最高危） |
| `ai:eval:read` / `ai:eval:write` | `GET /eval/runs`、`POST /eval/{cases,runs,judge}` |
| `ai:notify:read` / `ai:notify:write` | `POST /notify/{dispatch,ack}` |

种子数据走 `scripts/migration/00XX_ai_permission_seed.sql`（参照 `0045_runtime_admin_authorization_repair.sql`）。

### D3. 14 个 POST + 5 个 GET 全部加 `@PreAuthorize`

端点清单与租户来源见下表（14 POST，无 PUT/DELETE）：

| # | 端点 | 权限码 | 备注 |
|---|------|--------|------|
| 1 | `POST /api/ai/v1/pipeline`（app:31） | `ai:pipeline:write` | 与 #2 路径冲突，见 D5 |
| 2 | `POST /api/ai/v1/pipeline`（server:28） | — | 测试床，见 D4 |
| 3-5 | `POST /claim/underwrite`、`/{itemId}/act`、`/{itemId}/history`（server） | — | 测试床，见 D4 |
| 6 | `POST /hitl/items/{itemId}/act`（app:47） | `ai:hitl:write` | `actor` 当前由 body 传入 → **改为取认证主体** |
| 7-8 | `POST /rag/retrieve`、`/rag/ingest`（app:32/39） | `ai:rag:read` / `write` | |
| 9 | `POST /integration/invoke`（app:37） | `ai:integration:invoke` | |
| 10-12 | `POST /eval/{cases,runs,judge}`（app:52/66/82） | `ai:eval:write` | `/judge` 消耗 LLM |
| 13-14 | `POST /notify/{dispatch,ack}`（app:38/53） | `ai:notify:write` | `/dispatch` 是 webhook SSRF 面 |

### D4. bone-ai-server 不接鉴权，但以架构测试圈死

该模块是接线模板与集成测试床，接鉴权会破坏其示范价值。改为：

1. 类级 javadoc 与 pom description **显式标注「非生产拓扑，不得作为生产依赖」**；
2. **新增架构测试**：任何生产模块（`bone-platform/*`、`bone-engine/*`）不得依赖 `bone-ai-server`；
3. 其 3 个入参租户端点在 javadoc 标注「信任入参租户，仅测试床可用」。

### D5. 消除 `/api/ai/v1/pipeline` 路径冲突

app 的 `PipelineController:31` 与 server 的 `AiPipelineController:28` 路径完全相同但租户处理不同（前者走 `AiTenantContext`，后者读 body）。测试床迁至 `/api/ai/v1/demo/**` 前缀，消除同路径双实现。

### D6. 门禁闭环：`check-controller-authorization.py` 纳入 bone-ai

D1~D3 落地后扩展扫描根，**baseline 必须为空**（不接受「先登记再慢慢修」）。此后新增无 `@PreAuthorize` 的 AI 写端点将被 CI 直接拦截。

---

## 影响面与风险

| 项 | 评估 |
|----|------|
| 依赖变更 | 新增 `bone-security` + `bone-web`（**L3**）。二者均为平台内既有组件，不引入三方新库 |
| 数据变更 | 新增权限码种子（**L3**，DML 按 `ADR-生产数据库增量迁移策略` 走 expand/contract） |
| 行为变更 | bone-ai-app 端点从「恒 400」变为「认证后可用」；未带合法 JWT 的调用仍为 401/403 |
| 破坏面 | 现有 `bone-ai-app` 测试用 `MockMvc` 独立装配，不经过滤器链，**不受影响**；`bone-ai-server` 测试床不动 |
| 回滚点 | 依赖与权限码均为加法；回滚 = 移除依赖 + 撤下 `@PreAuthorize` + 权限码置灰，无数据破坏 |

## 验收门禁

1. `scripts/check-controller-authorization.py` 扫描根含 `bone-ai` 且 **baseline 为空**；
2. `mvn -pl bone-ai/bone-ai-app test` 全绿，且新增「无 JWT → 401」「有 JWT 无权限 → 403」「有权限 → 2xx」三组用例；
3. `bone-ai-server` 的架构测试对「生产模块依赖测试床」为红；
4. `scripts/check.sh` 全量通过。

## 未决问题（需审批时一并裁定）

1. **`ai:*` 权限码归属**：注册进 `bone-iam` 权限目录（需种子数据 + 菜单挂载），还是先只在 `bone-ai-app` 内做 authorities 映射？
2. **`/judge` 与 `/pipeline` 的 LLM 成本护栏**：是否需要独立于权限码的配额门禁（当前只有网关侧成本红线）？
3. **测试床路径前缀**（D5）是否影响既有 e2e 脚本？
