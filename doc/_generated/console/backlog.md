# bone-system 控制台 [Target] / [Vision] Backlog

> **生成时间**：2026-09-21T06:26:29Z（UTC）
> **维护源**：[`backlog.yaml`](../../../tools/console-compliance-collector/backlog.yaml)

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `remote-service-probe` | 远端服务真探活（替换 LocalActuatorServiceHealthGatewayAdapter 的 UNKNOWN） | 详设 §1.4 / §3.3.4 | P0 · 接服务注册中心或网关主动探活 |
| **Target** | `cross-db-metrics` | prod 分库部署时跨库 keyMetrics 聚合（通过 Gateway/Feign 调各服务只读 API） | 详设 §3.3.3 / §11.2 | P0 · 当前默认连共享库 bone；分库时退化为 0（graceful degradation） |
| **Target** | `cpu-disk-metrics` | 节点 CPU / 磁盘使用率 | 详设 §1.4 | P1 · OperatingSystemMXBean 或 node_exporter |
| **Target** | `alerts-feed` | alerts[] 接入 sys_alert_event | 详设 §1.4 / §2.1 | P1 · 与 AlertController 串联 |
| **Target** | `recent-access` | cnsl_recent_access 表通过 Web 拦截器写入 | 详设 §2.3 / §4 | P1 · 新增 RecentAccessRecorder |
| **Target** | `quick-action-table` | quick-actions 改为 cnsl_quick_action 表驱动（多租户/角色过滤） | 详设 §2.3 | P2 · 新增 cnsl_quick_action 表 + Handler |
| **Vision** | `prometheus-grafana` | Prometheus 抓取 + Grafana 面板 | 详设 §3.2 / §9 | — |
| **Vision** | `configurable-dashboard` | 可配置仪表盘（Widget CRUD / 拖拽布局 / 时间范围） | 详设 §2.2 / §5.2-§5.4 | — |
| **Vision** | `ai-layout` | AI 智能布局与 Widget 异常检测 | 详设 §10.4.1 | — |
| **Vision** | `standalone-console-service` | 拆出独立 bone-console 微服务 | 详设 §10.1 / §10.6 | — |
