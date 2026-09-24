# ADR-0037：集成引擎单模块收敛

> **状态**：已实现（2026-05）
> **决策**：仅保留 `bone-platform/bone-integration`（构件 `bone-platform-integration`）；`bone-engine/bone-integration` 已从仓库删除。
> **说明**：本 ADR 于 2026-09-23 从 Git 历史（`f28cb5965^`）恢复。原 `doc/architecture/ADR-integration-consolidation.md` 曾被删除但仍被多处引用，统一以本编号 ADR 为权威入口。

## 背景

历史上存在两个同名包 `com.bone.integration` 的 Spring Boot 服务：

| 模块 | 端口 | 数据 | 与平台关系 |
|------|------|------|------------|
| `bone-engine/bone-integration` | 30888 | `t_flow_*`、TPA 样例 | 未接网关/前端/`bone-init` |
| `bone-platform/bone-integration` | 8085 | `int_*` | 网关、`bone-integration-app`、INT-* 工程债 |

双模块导致 README 误读、DDL 双轨、Camel 能力与控制台脱节。

## 决策

1. **废止并删除** `bone-engine/bone-integration`（自 `bone-engine/pom.xml` 移除模块）。
2. **唯一集成服务**：`bone-platform/bone-integration`（`:8085`，`/api/v1/integration/**`）。
3. **Camel 资产**：将无 TPA 依赖的 HTTP 组件与 `CamelIntegrationContext` 迁入平台 `infrastructure/camel/`；完整图编排（Choice/并行等）登记 **INT-11**，按 `int_flow_node` 逐步实现。
4. **执行路径**：默认 **INT-09** 线性同步（`LinearSyncFlowRuntime`）；**INT-11** Camel 编译执行由 `integration.camel.execution-enabled=true` 启用（`CamelFlowCompiler` + `CamelFlowRuntime`）。

## 后果

- 正面：单 DDL、单 API、单进程，符合 API-First 与工程诚实原则。
- 负面：依赖 30888 + `t_flow_definition` 的旧部署需自行迁移或从历史 Git 标签恢复引擎模块。
- 需从 Git 历史 cherry-pick 时：检索删除前的 `bone-engine/bone-integration/flow/visitor/camel/`。

## 参考

- [07-P0-TODO看板](../../wiki/07-P0-TODO看板.md) INT-08/09/11
- [集成管理模块详设](../../design/modules/4.%20集成管理模块详细设计方案.md)
