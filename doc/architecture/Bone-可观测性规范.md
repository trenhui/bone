# Bone 平台可观测性规范（Metrics / Trace）

> **文档性质**：指标、链路、SLO 与告警的**统一约定**（日志见 [Bone-日志规范.md](./Bone-日志规范.md)）。  
> **更新**：2026-05-17  
> **关联**：[BONE-总体架构设计方案](./BONE-总体架构设计方案.md) §12、[Bone-API-规范.md](./Bone-API-规范.md)（`X-Request-Id`）

---

## 1. 三大信号分工

| 信号 | 文档 | 用途 |
|------|------|------|
| **Logs** | [Bone-日志规范](./Bone-日志规范.md) | 排障、审计、已知错误 `errorCode` |
| **Metrics** | 本文 | SLO、容量、告警 |
| **Traces** | 本文 | 延迟分解、跨服务依赖 |

---

## 2. 参考标准

| 标准 | Bone 采纳 |
|------|-----------|
| [OpenTelemetry](https://opentelemetry.io/) | 语义约定、Trace/Metrics 导出 |
| [W3C Trace Context](https://www.w3.org/TR/trace-context/) | `traceparent` 透传 |
| Google SRE | SLI/SLO、错误预算 |
| RED 方法 | Rate、Errors、Duration（请求服务） |
| USE 方法 | Utilization、Saturation、Errors（资源） |

**实现**：SkyWalking 9.x + Micrometer Prometheus（见 `bone-parent`）；逐步对齐 OTel SDK。

---

## 3. 链路（Trace）

| 规则 | 说明 |
|------|------|
| 入口 | 网关/Filter 解析或生成 `traceId`，写入 MDC（与日志一致） |
| 透传 | HTTP 出站携带 `traceparent`；MQ 消息信封含 `traceId`（见 [消息规范](./Bone-消息与事件规范.md)） |
| 跨度命名 | `{http.method} {http.route}`，如 `GET /api/v1/extension/plugins/{id}` |
| 禁止 | 以原始 URI（含 UUID）作为 metric/trace 高基数标签 |

---

## 4. 指标（Metrics）

### 4.1 HTTP 服务（RED）

| 指标 | 类型 | 标签 | 说明 |
|------|------|------|------|
| `http_server_requests_seconds` | Histogram | `method`, `uri`→**`http_route`**, `status`, `application` | 延迟分布 |
| `http_server_requests_total` | Counter | 同上 | 请求量 |
| 派生 | — | — | 5xx 率、P95/P99 |

**`http_route`**：Spring `http.server.requests` 的 URI 模板，**禁止** `uri="/api/v1/extension/plugins/71103..."`。

### 4.2 业务（按需）

| 指标 | 说明 |
|------|------|
| `bone_extension_deploy_total` | 部署成功/失败 Counter |
| `bone_integration_execution_duration` | 流程执行 Histogram |

命名：`bone_{domain}_{verb}_{unit}`，小写蛇形。

### 4.3 资源（USE）

| 资源 | 指标来源 |
|------|----------|
| JVM | `jvm.memory.used`, GC |
| 连接池 | Hikari `active`, `pending` |
| Redis | 客户端延迟、命中率 |

---

## 5. SLI / SLO（平台默认建议）

| SLI | 定义 | SLO（生产建议） |
|-----|------|----------------|
| 可用性 | `1 - (5xx / 全部请求)` | ≥ 99.9% / 30 天 |
| 延迟 | `http_server` P95 | 读 < 500ms，写 < 1s（域可调） |
| 正确性 | 业务 `errorCode` 非 5xx 比例 | 按域 dashboard |

错误预算耗尽 → 冻结非紧急发布（见 [版本与发布规范](./Bone-版本与发布规范.md)）。

---

## 6. 告警

| 告警 | 条件 | 严重级 |
|------|------|--------|
| 高 5xx 率 | 5xx / total > 1% 持续 5m | P1 |
| P95 恶化 | 较 7 天基线 +50% 持续 15m | P2 |
| 慢请求 | `[API] durationMs>3000` WARN 突增 | P2 |
| 依赖不可用 | DB/Redis 连接失败 ERROR | P1 |

告警注解须含：`service`, `http_route`, `traceId`（样例）、`runbook` 链接。

---

## 7. 仪表盘（最小集）

每服务至少：

1. RED：QPS、5xx%、P95  
2. JVM + 连接池  
3. 依赖健康（DB、Redis、MQ）  

平台级：按 `domain` 聚合的 API 黄金指标。

---

## 8. 与日志关联

排障路径：**告警 → Metrics 视图 → 按 traceId 查 Logs**。

`ProblemDetail.traceId` = MDC `traceId` = Trace root span id（或映射字段）。

---

## 9. 检查清单

- [ ] 新服务暴露 `/actuator/prometheus`（生产鉴权）  
- [ ] HTTP 指标使用路由模板标签  
- [ ] Filter 注入 traceId/MDC  
- [ ] 关键业务有 Counter/Histogram  
- [ ] 告警规则已登记 runbook  

---

## 10. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版 |
