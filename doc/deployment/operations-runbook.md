# BONE 运维手册（Runbook）— DRAFT

> 面向 SRE / 运维。设计类文档在 `doc/architecture`、`doc/design`，本文件聚焦「怎么跑、怎么排障、怎么回滚」。

## 1. 服务总览（见 `deploy/README.md` 端口表）
网关 8888 → iam 8080 / system 8083 / masterdata 8084 / integration 8085 / file 8107 / notification 8100 / blueprint 8082 / studio-generator 8086 / extension-studio 8088 / metadata-server 8081。

## 2. 健康检查
- 端点：`/actuator/health`（各服务已暴露）、`/actuator/info`、`/actuator/metrics`。
- 注意不一致：当前 `bone-iam` 的 prod **不暴露 prometheus**，而 `bone-system` 暴露 —— 监控抓取策略需统一（建议全部暴露 `/actuator/prometheus`，由采集侧按需拉取，鉴权走内网）。
- K8s 探针建议指向 `/actuator/health/readiness` / `/liveness`（见 `deploy/README.md` 示例）。

## 3. 配置与密钥
- 所有敏感项走环境变量：`BONE_DB_PASSWORD`、`BONE_DB_URL`、`BONE_REDIS_PASSWORD`、`BONE_CONFIG_ENCRYPT_KEY`（prod fail-fast，缺失即启动失败）。
- 业务配置走 **Nacos**（见 `doc/deployment/nacos-config-guide.md`）。
- **禁止**在 `application*.yml` 写死密码（当前 prod 配置已合规，勿回退）。

## 4. 日志
- 已接入 `logback` + `RequestLoggingMdcFilter`（请求级 MDC 追踪）。
- **缺口**：未见集中日志采集（ELK / OpenTelemetry）配置。建议接入 Otel agent，将 traceId 透传到日志与链路。
- 排障首选：按 `traceId` / `tenantId` 在聚合平台检索。

## 5. 数据库
- **变更版本化进行中**：详见 `db/migration/README.md`（Flyway 接入草稿，当前仍为 `bone-init.sql` 全量重建）。
- **备份（草案，待定）**：每日 `mysqldump` + binlog 增量；保留 7/30/90 天三档。恢复演练见 `doc/deployment/integration_tenant_backfill.runbook.md` 风格。
- 多租户同库（`bone`）现状下，跨表 COUNT 等聚合依赖同库（详见 `bone-system` 的 graceful degradation 设计）。

## 6. 回滚
- **应用层**：镜像按 `git sha` 打 tag，K8s 回滚 `kubectl rollout undo`。
- **数据层**：补偿式 Flyway 迁移（见迁移 README）；生产数据变更先备份后演练。

## 7. 常见故障
| 现象 | 可能原因 | 处置 |
|---|---|---|
| 服务起不来，报 `encrypt-key` 缺失 | prod 未注入 `BONE_CONFIG_ENCRYPT_KEY` | 注入密钥后重启（fail-fast 设计，符合预期） |
| 健康检查一直 not ready | 依赖（MySQL/Redis/Nacos）未就绪或网络隔离 | 查 probe 日志 + 依赖连通性 |
| 网关 429 | 触发 `RateLimitGatewayFilter` 限流 | 评估配额 / 检查是否有异常流量 |
| 网关 503 | 下游熔断 `CircuitBreakerGatewayFilter` 打开 | 查下游依赖健康，恢复后自动半开 |

## 8. 待补
- [ ] 告警规则（Prometheus → 钉钉/飞书/邮件）。
- [ ] 链路追踪（Otel / Jaeger）端到端打通。
- [ ] 容量规划与压测基线。
- [ ] 灾备（DR）与定期恢复演练。
