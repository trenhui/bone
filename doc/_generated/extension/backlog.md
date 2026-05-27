# 扩展模块 [Target] / [Vision] Backlog

> **生成时间**：2026-05-27T02:13:41Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/extension-compliance-collector/backlog.yaml)（仅写未落地项）。

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `patch-partial-update` | PATCH 部分更新 | API 规范 §2.4 | 详设 §7.2/§7.3；当前仅 PUT 全量 |
| **Target** | `idempotency-redis-cluster` | Idempotency Redis 集群幂等 | API 规范 §8 | 当前 StudioIdempotencyService 进程内 24h TTL |
| **Target** | `deployment-status-ddl` | deployment_status DDL + 状态机落库 | 详设 §3.3 | DeploymentStatus 枚举已就位；bone-init.sql 未建列 |
| **Target** | `red-metrics-micrometer` | RED 指标 (Micrometer) | API 规范 §10 + 可观测性规范 §4.2.1 | extension_invoke_* 等未注册 |
| **Target** | `bytecode-hotload-classloader` | 字节码热载 / 独立 ClassLoader 隔离 | 详设 §4.4 | As-Is 仅路由元数据热更；JAR 归档不加载字节码 |
| **Target** | `artifact-download-auth-url` | 制品下载鉴权 URL | API 规范 §14.4 | file_path 仍为绝对路径；:download [Target] |
| **Target** | `mime-magic-number` | MIME magic-number + 病毒/依赖扫描 | API 规范 §14.4 | As-Is 仅 .jar 扩展名校验 |
| **Target** | `rollout-percent-traffic-merge` | rollout_percent ↔ config_json.traffic 收敛 | 详设 §4.3 / §6.2 | 须 ADR 二选一后清理冗余列 |
| **Target** | `resilience4j-circuit` | Resilience4j 按插件熔断 | 详设 §4.4 | 当前 ExtensionExecutionGuard 全局 Semaphore |
| **Target** | `archunit-shrink-freeze` | ArchUnit freeze 基线持续收缩 | Bone-DDD §21 | ArchitectureTest 已启用；迁移后只缩不扩 |
| **Target** | `integration-events` | ExtensionDeployedIntegrationEvent 等集成事件 | Bone-消息与事件规范 | 详设 §4.5 |
| **Vision** | `wasm-runtime` | Wasm/WASI 运行时 | 详设 §12.1 | — |
| **Vision** | `plugin-marketplace` | 插件市场 | 详设 §12.2 | ext_marketplace_item 未入 init |
| **Vision** | `plugin-dependency-graph` | 插件依赖图 | 详设 §12.3 | — |
| **Vision** | `resource-metering` | 资源计量 | 详设 §12.4 | ext_plugin_resource_usage 未入 init |