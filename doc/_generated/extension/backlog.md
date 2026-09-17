# 扩展模块 [Target] / [Vision] Backlog

> **生成时间**：2026-09-17T03:20:47Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/extension-compliance-collector/backlog.yaml)（仅写未落地项）。

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `bytecode-hotload-classloader` | 字节码热载 / 独立 ClassLoader 隔离 | 详设 §4.4 | As-Is 仅路由元数据热更；JAR 归档不加载字节码 |
| **Target** | `resilience4j-circuit` | Resilience4j 按插件熔断 | 详设 §4.4 | 已落地按插件 JDK 舱壁 + extension_bulkhead_rejected_total；熔断器/CircuitBreaker 仍待 Resilience4j |
| **Target** | `archunit-shrink-freeze` | ArchUnit freeze 基线持续收缩 | Bone-DDD G-1 | ArchitectureTest 已启用；迁移后只缩不扩 |
| **Target** | `integration-events` | ExtensionDeployedIntegrationEvent 等集成事件 | Bone-消息与事件规范 | 详设 §4.5 |
| **Vision** | `wasm-runtime` | Wasm/WASI 运行时 | 详设 §12.1 | — |
| **Vision** | `plugin-marketplace-remote` | 插件市场（OCI / 远端仓库） | 详设 §12.2 | v1 已落地静态 JSON 目录（marketplace/items.json）；远端 OCI / 凭证 / 评分仍待 |
| **Vision** | `resource-metering` | 资源计量 | 详设 §12.4 | ext_plugin_resource_usage 未入 init |