# Bone 平台日志规范

> **文档性质**：后端可观测性中的**应用日志、Access Log、审计日志**统一约定（与 HTTP 契约配合，但不限于 REST）。  
> **更新**：2026-05-17  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md)（`X-Request-Id`、`ProblemDetail.traceId`）、[Bone-可观测性规范.md](./Bone-可观测性规范.md)（Metrics/Trace/SLO）、[数据库开发规范.md](./数据库开发规范.md)（表审计列）

---

## 1. 目标与适用范围

| 项 | 说明 |
|----|------|
| **目标** | 统一日志分类、MDC、级别、脱敏、审计落库与告警标签；支持排障、合规与 SRE 指标。 |
| **适用** | `bone-platform/*`、`bone-engine/*` 全部 Java 服务；网关与前端仅约定关联头（见 §8）。 |
| **真源** | 本文 > 模块 README 中的零散说明；与 [Bone-错误码登记](./Bone-错误码登记.md) 配合使用（WARN 须带 `errorCode`）。 |

### 1.1 参考标准（业界）

| 标准 / 实践 | Bone 采纳 |
|-------------|-----------|
| [OpenTelemetry Logs](https://opentelemetry.io/docs/specs/otel/logs/) | 结构化字段、`traceId` 与 Trace 关联 |
| [W3C traceparent](https://www.w3.org/TR/trace-context/) | 与 SkyWalking / 网关透传对齐 |
| [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) | 脱敏、禁止敏感数据入日志 |
| Google SRE（日志作为事件流） | Access 一条摘要 / 请求；业务用 `[Biz]` |
| 12-Factor（XI. Logs） | stdout 聚合；生产 JSON |

---

## 2. 日志分类与保留

| 类型 | 前缀 | 用途 | 保留建议 |
|------|------|------|----------|
| Access | `[API]` | 每次 HTTP 一条摘要 | 30–90 天 |
| Application | `[Biz]` | 用例、状态变更、排障 | 7–30 天 |
| Audit | `[Audit]` + DB | 合规、谁对谁做了什么 | ≥1 年（按政策） |
| Integration | `[Integration]` | 出站 HTTP/MQ/DB 调用 | 30 天 |
| Job | `[Job]` | 定时/批处理 | 30 天 |
| Security | `[Security]` | 鉴权失败、越权尝试（可选） | 90 天 |

**禁止**用 DEBUG 在生产排障长期开启；临时开启须带 TTL 与审批记录。

---

## 3. MDC（强制）

入口 `Filter` / 网关设置，`finally` 中 **`MDC.clear()`**，防止线程池污染。

| Key | 来源 | 说明 |
|-----|------|------|
| `traceId` | `X-Request-Id` / SkyWalking | 与响应头、`ProblemDetail.traceId` **一致** |
| `tenantId` | JWT / `X-Tenant-Id` | 多租户隔离排障 |
| `userId` | JWT | 禁止记录完整 Token |
| `bizIdentityCode` | 头 / `TenantContext` | 业务身份 |
| `domain` | 服务配置 | 如 `extension`、`iam` |
| `httpMethod` | 请求 | GET/POST… |
| `httpRoute` | **URI 模板** | 如 `/api/v1/extension/plugins/{id}`，**禁止**原始含 ID 路径做指标标签 |

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BoneRequestContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String traceId = Optional.ofNullable(req.getHeader("X-Request-Id"))
                .filter(StringUtils::hasText)
                .orElse(UUID.randomUUID().toString().replace("-", ""));
        MDC.put("traceId", traceId);
        res.setHeader("X-Request-Id", traceId);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            log.info("[API] traceId={} method={} path={} status={} durationMs={} tenantId={} userId={}",
                    traceId, req.getMethod(), req.getRequestURI(), res.getStatus(),
                    System.currentTimeMillis() - start, MDC.get("tenantId"), MDC.get("userId"));
            MDC.clear();
        }
    }
}
```

> **实现注意**：生产 Access Log 的 `path` 应使用路由模板（Spring `RequestMapping` 模式），避免高基数路径打爆指标。

---

## 4. 级别与前缀

| 级别 | 场景 | 生产默认 |
|------|------|----------|
| ERROR | 未捕获异常、依赖不可用 | 开 |
| WARN | 已知业务失败（**含 errorCode**）、慢请求、重试 | 开 |
| INFO | Access、关键状态变更、审计摘要 | 开 |
| DEBUG | 诊断、SQL（仅 local） | **关** |
| TRACE | 禁止生产 | 关 |

| 类型 | 级别 | 内容 |
|------|------|------|
| `BizException` / 已知码 | WARN | `errorCode` + 简短 message；默认**不打满栈** |
| 未知 `Exception` | ERROR | `traceId` + 完整堆栈 |

---

## 5. Access Log

每条对外 HTTP 至少一条 **INFO** `[API]`：

| 条件 | 要求 |
|------|------|
| `status >= 400` | 必须带 `errorCode`（若已翻译） |
| `durationMs > 3000` | 额外 **WARN** 慢请求 |
| 所有请求 | 记录 `method`、`httpRoute`/`path`、`status`、`durationMs`、`traceId` |

**禁止**写入：完整 `Authorization`、密码、Cookie、请求/响应大 body、证件号明文。

---

## 6. 审计日志

### 6.1 必须审计的操作

登录/登出、IAM 用户/角色/权限变更、插件部署/回滚、主数据发布/删除、系统配置变更、集成流程激活/删除。

### 6.2 落库字段（与 DDL 对齐）

`traceId`, `principal`（用户标识）, `tenantId`, `action`, `resourceType`, `resourceId`, `result`（SUCCESS/FAIL）, `timestamp`；扩展字段见 `iam_audit_log` 等表。

### 6.3 与 Application Log 分工

| 审计 | Application |
|------|-------------|
| 合规、不可篡改需求 | 排障、指标 |
| DB + 可选 `[Audit]` INFO | `[Biz]` |
| 禁止仅存日志不落库（关键操作） | 禁止用 DEBUG 替代审计 |

---

## 7. 脱敏

| 数据 | 规则 |
|------|------|
| 密码、Token、API Key、私钥 | **禁止**出现在任何级别 |
| 手机号、证件号 | 掩码（如 `138****8000`） |
| 银行卡 | 仅后四位 |
| SQL | 仅 `local` profile 的 DEBUG |
| 请求体 | 默认不打印；必要时报 `contentLength` + 哈希 |

---

## 8. Logback 配置（推荐）

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} [traceId=%X{traceId}] [tenantId=%X{tenantId}] - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="com.bone" level="INFO"/>
    <logger name="org.springframework" level="WARN"/>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
</configuration>
```

**生产**：使用 `logstash-logback-encoder` 输出 JSON，字段至少包含：`timestamp`、`level`、`logger`、`message`、`traceId`、`tenantId`、`errorCode`（若有）。

---

## 9. 链路、前端与告警

| 项 | 约定 |
|----|------|
| 响应头 | 回显 `X-Request-Id` |
| 错误体 | `ProblemDetail.traceId` 与 MDC `traceId` 一致 |
| 前端 | axios 注入 `X-Request-Id`；错误 UI 展示 `traceId` + `errorCode` |
| 指标 | Prometheus 使用 `http_route`（模板路径），**禁止**高基数 `uri` |
| 告警建议 | 5xx 率、P95 延迟、`[API]` 慢请求 WARN 计数、ERROR 突增 |

与 SkyWalking：保证 TraceId 注入 MDC（`SW_TRACE_ID` 或桥接 Filter）。

---

## 10. 分层职责（DDD）

| 层 | 可打 | 禁止 |
|----|------|------|
| Controller / Filter | `[API]`、`[Security]` | 业务对象刷屏、PII 全量 DTO |
| Application | `[Biz]` 状态变更 | 完整领域对象 dump |
| Domain | 不变式违反 WARN | 依赖 SLF4J 以外的基础设施 |
| Infrastructure | `[Integration]` 失败/重试 | 吞异常不打日志 |

---

## 11. 实施检查清单

- [ ] 服务入口 Filter 设置 MDC 且 `finally` 清理  
- [ ] 每条 HTTP 有 `[API]` INFO  
- [ ] 4xx/5xx 带 `errorCode`（已知业务异常）  
- [ ] 审计操作落库 + 可选 `[Audit]`  
- [ ] 生产无 Token/密码日志  
- [ ] JSON 日志含 `traceId`（生产）  
- [ ] 指标标签使用路由模板  

---

## 12. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 从 Bone-API-规范 §10 独立为本文 |
| 2026-05-17 | 关联可观测性规范 |
