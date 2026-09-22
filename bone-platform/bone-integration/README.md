# bone-platform-integration

平台**唯一**集成服务：连接器、流程设计、执行监控与领域事件。

| 项 | 值 |
|----|-----|
| 构件 ID | `bone-platform-integration` |
| 默认端口 | **8085**（`context-path` `/api`） |
| API | `/api/v1/integration/**` |
| DDL | 根目录 [`bone-init.sql`](../../bone-init.sql) 中 `int_*` |

## 构建

```bash
mvn -pl :bone-platform-integration -am compile -DskipTests
mvn -pl :bone-platform-integration test
```

## 执行模型

| 组件 | 说明 |
|------|------|
| `LinearSyncFlowRuntime` | 默认执行器（INT-09）：线性拓扑 + REST 节点 |
| `CamelIntegrationContext` | Camel 上下文 + `extend-http` 组件（自 legacy 抽取） |
| `CamelFlowCompiler` | INT-11：`int_flow_node` → Camel 路由（choice/multicast；`integration.camel.execution-enabled=true` 启用） |

## 领域事件 Outbox（INT-10）

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `BONE_INTEGRATION_OUTBOX_ENABLED` | `true` | 业务事务内写 `int_outbox` |
| `BONE_INTEGRATION_OUTBOX_MQ_ENABLED` | `false` | `true` 时中继至 RocketMQ；否则结构化日志 |

Topic 登记见 [Bone-消息与事件规范.md](../../doc/architecture/Bone-消息与事件规范.md) §4。

## 安全与告警

- JWT：`BONE_INTEGRATION_JWT_ENABLED`（见 `SecurityConfig`）
- 事件告警：`BONE_INTEGRATION_ALERT_ENABLED`、`BONE_ALERT_DINGTALK_*` — [config/env/README.md](../../config/env/README.md)

## DDD 约定

- 应用模块，适用 [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md)；ArchUnit：`src/test/java/com/bone/integration/architecture/ArchitectureTest.java`。

### domain 分组形态（E-10 登记）

**目标形态**（[ADR-0036](../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`，聚合根 / 实体 / 值对象在聚合包内直接平铺，`event/` `projection/` 为聚合内子包；`repository` / `gateway` 端口留在 `domain/` 根。

**本模块现状**：**已合并大部分，剩 `client` 待定性**——`domain/{connector,execution,flow}` 已迁入 `domain/model/{connector,execution,flow}/`（2026-09-22 完成，75 个测试全绿）；`domain/client` 仍在 `domain/` 根，需逐个定性（模型 vs 端口）后决定是迁入 `domain/model/client/` 还是按 ADR-0036 R3 作为端口留根。定性前属 ADR-0036 R4 禁止的并存状态。

### 已登记的租户隔离缺口（2026-09-20，G-2 登记；Owner：integration 模块）

**现象**：本模块 5 个聚合（`Connector` / `IntegrationFlow` / `FlowNode` / `FlowConnection` / `IntegrationLog`）全部
`extends AggregateRoot`（无 `tenantId`），而 `bone-init.sql` 里对应 `int_*` 表都是
`tenant_id BIGINT NOT NULL DEFAULT 0`、本模块详设要求"流程列表与执行日志**严格按 `tenant_id` 隔离**"。

**后果**：SDK 的租户表判定看**实体字段**（`TableMetadataResolver` → `TableMetadata.isTenantScoped()`），实体不声明
`tenantId` ⇒ `TenantFilterInjector` 直接返回 ⇒ **Criteria / QueryBuilder 查询不加任何租户条件**。用户可达路径示例：
`GET /api/v1/integration/executions`、`GET /executions/{id}`、`GET /statistics`（`MonitorController` →
`FlowStatisticsQueryHandler` / `FlowMonitorService`），以及 `FlowStatisticsJob` 的定时汇总——任何已认证租户用户
理论上可读到其它租户的流程与执行日志。门禁信号：`ArchitectureTest` 的
`schedule_only_calls_all_tenants_repository_methods` 冻结条目（它报出的是"定时任务调用租户内读"，只是表象）。

**为何未在门禁修复中一并改**：修法是把 5 个聚合改为 `TenantAggregateRoot`（创建路径已集中在 `IntegrationFlow.of` /
`IntegrationLog.of` 两处，属可控范围），但**存量行 `tenant_id` 全是默认 0，需要确认租户归属并做数据迁移**（属 L4，
AI 不执行）；且模块 Spring 测试需在有数据库的环境验证。

**拆除条件**：① 确认 `int_*` 存量行的租户归属并完成迁移；② 5 个聚合改 `TenantAggregateRoot`，在工厂与调用方补
`tenantId`（经 `TenantPort` 取得，不由业务代码读 `TenantContext`，E-2）；③ 定时统计任务随之改为显式全租户入口
（`*AllTenants`）或按租户执行；④ 删除本节，并清零 `archunit_store` 中的对应冻结条目。

### 命名差异（2026-08）

- **既有风格**：CommandHandler 命名为 `{Action}{Entity}Handler`（如 `CreateConnectorHandler`、`EnableConnectorHandler`、`DeleteFlowHandler`），不带 `*CommandHandler` 后缀（ArchUnit `command_handler_naming` 冻结基线内）。
- **v5.0 口径**：Handler 后缀属于 Advisory 工程风格，不用于证明 DDD 语义；新增代码优先保持模块内部一致。
- **迁移计划**：是否全量改为 `*CommandHandler` 由模块 Maintainer 决定。随功能重构逐类更名时同步 Controller 注入与冻结基线收缩；当前不做一次性批量改名。
- **拆除条件**：全部 Handler 更名完成且冻结基线收缩后，本段删除。

## 历史说明

原 `bone-engine/bone-integration`（30888、`t_flow_*`）已删除，决策记录：[ADR-integration-consolidation.md](../../doc/architecture/ADR-integration-consolidation.md)。
