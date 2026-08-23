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

### 命名例外（§0.2 登记，2026-08）

- **既有风格**：CommandHandler 命名为 `{Action}{Entity}Handler`（如 `CreateConnectorHandler`、`EnableConnectorHandler`、`DeleteFlowHandler`），不带 `*CommandHandler` 后缀（ArchUnit `command_handler_naming` 冻结基线内）。
- **覆盖范围**：新增同类 CommandHandler 遵循本模块既有风格**不视为新违规**（避免冻结基线「只收缩不扩张」被持续违反，见[主文档 §0.2 命名冲突裁决路径](../../doc/architecture/Bone-DDD-最终实践方案.md#02-存量不符合规范代码的处理)）。
- **迁移计划**：目标全量对齐主文档 §23（`*CommandHandler` 后缀）。随功能重构**逐类更名**（更名时同步 Controller 注入与冻结基线收缩）；当前不做一次性批量改名，避免大 diff 与回归风险。
- **拆除条件**：全部 Handler 更名完成且冻结基线收缩后，本条例外删除。

## 历史说明

原 `bone-engine/bone-integration`（30888、`t_flow_*`）已删除，决策记录：[ADR-integration-consolidation.md](../../doc/architecture/ADR-integration-consolidation.md)。
