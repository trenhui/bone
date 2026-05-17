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
| `CamelFlowCompiler` | INT-11 占位：将 `int_flow_node` 编译为 Camel 路由 |

## 领域事件 Outbox（INT-10）

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `BONE_INTEGRATION_OUTBOX_ENABLED` | `true` | 业务事务内写 `int_outbox` |
| `BONE_INTEGRATION_OUTBOX_MQ_ENABLED` | `false` | `true` 时中继至 RocketMQ；否则结构化日志 |

Topic 登记见 [Bone-消息与事件规范.md](../../doc/architecture/Bone-消息与事件规范.md) §4。

## 安全与告警

- JWT：`BONE_INTEGRATION_JWT_ENABLED`（见 `SecurityConfig`）
- 事件告警：`BONE_INTEGRATION_ALERT_ENABLED`、`BONE_ALERT_DINGTALK_*` — [config/env/README.md](../../config/env/README.md)

## 历史说明

原 `bone-engine/bone-integration`（30888、`t_flow_*`）已删除，决策记录：[ADR-integration-consolidation.md](../../doc/architecture/ADR-integration-consolidation.md)。
