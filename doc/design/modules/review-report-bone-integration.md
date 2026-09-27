# 复核报告 · bone-integration（夜间 A 阶段）

> 派生自 `doc/design/_global-contracts.yaml`（锚定，2026-09-26T23:40 生成）。
> 复核方法：通读 `doc/design/modules/4. 集成管理模块详细设计方案.md`（497 行）+ 比对 `bone-platform/bone-integration` 源码（CI 派生锚点）。
> 阶段：A（设计复核）+ A'（方案自审）。本模块此前 `status: pending`，本轮首次进入夜间轮换。

## 1. 模块定位与范围

| 项 | 值 |
|----|----|
| 唯一服务 | `bone-platform/bone-integration`（:8085，`context-path=/api`） |
| 聚合 | IntegrationFlow / FlowNode / FlowConnection / Connector / IntegrationLog（租户聚合）；IntegrationOutboxRecord（技术聚合） |
| 暴露 API | `/api/v1/integration/connectors`、`/flows`、`/executions`、`/statistics` |
| 已落 Outbox | Writer(Impl) + Relay + Repository + RocketMqIntegrationMessageSender + LoggingIntegrationMessageSender |
| 依赖 | bone-core、bone-metadata-sdk、bone-notification（pom 声明未 import） |

## 2. 硬约束（HC）合规复核

| HC | 锚定 | 复核结论 | 证据 |
|----|------|---------|------|
| HC-001 分层 | implemented | ✅ 合规 | command/handler、query/handler、domain、infrastructure 分层清晰；Controller 未直接注入 domain/service |
| HC-002 入站边界 | implemented | ✅ 合规 | 语义化 `*ApplicationService` / `*CommandHandler`；未见 Handler 与 ApplicationService 套娃 |
| HC-003 统一响应 | implemented | ✅ 合规（**无假阳性**） | 3 个 Controller（Connector/Flow/Monitor）**全部返回 `ApiResponse<T>`，0 个 `ResponseEntity`**（grep 命中 0） |
| HC-004 安全 | implemented | ✅ 合规 | 未见自建异常绕过；`BizException`/`NotFoundException` 来自 bone-core |
| HC-005 租户审计 | implemented | ✅ 合规 | 实体继承租户基类；`int_connector`/`int_flow` 含 `tenant_id`+审计列（doc4 §4.1/§4.2） |
| HC-006 持久化唯一 | implemented | ✅ 合规 | 全部走 bone-metadata-sdk；`DeadLetterMetricsRefresher` 已在 baseline 登记 |
| HC-007 OpenAPI | implemented | ✅ 合规 | `doc/architecture/openapi/integration-v1.yaml` 存在（22 paths） |
| HC-008 审计列 | partial | ⚠️ by-design（**非缺口**） | `int_execution_log` 仅追加、无 `updated_at`/`deleted`（doc4 §4.3）；属 append-only 日志基线，无 gap-candidate |

> **本轮无假阳性**：HC-003/HC-008 均为真实合规/by-design，与 metadata-server 轮（HC-003/008 误报）不同。

## 3. 设计-代码 GAP（锚定 5 项逐一核验）

| # | 锚定声称 | 代码侧核验 | 结论 |
|---|---------|-----------|------|
| G1 | Outbox 标 `[Target]`，代码已落地 | `IntegrationOutboxWriterImpl`/`Relay`/`Repository`/`RocketMqIntegrationMessageSender` 全部存在 | **文档滞后**（代码领先） |
| G2 | `/flows/{id}/test`、`/{id}/versions` 标 `[Vision]`，代码已实现 | `FlowController.test()`（L91）、`FlowController.versions()`（L106）均返回 `ApiResponse` | **文档滞后**（代码领先） |
| G3 | `Idempotency-Key`（Redis 24h）零实现 | 全模块 `grep Idempotency` 命中 0 | **真实缺口**（路线图 INT-10） |
| G4 | INT-CON-01 禁止 FTP/JDBC/MQ/Kafka/SOAP，代码有 9 种 ClientImpl | external 下 9 类：`Rest/Jdbc/Mq/Soap/Ftp/Mongo/Es/S3/Redis` ClientImpl，多为 501 占位 | **真实偏离**，但符合"未实现协议显式 501"（INT-CON-01 允许），需架构师裁定命名/存在性 |
| G5 | 无共享 `IntegrationEnvelope` 契约 | 仅模块私有 `record IntegrationEventEnvelope(...)`（8 字段，schemaVersion=1.0），无跨模块接口；`IntegrationEventEnvelopeFactory` 构造 | **真实缺口**（跨模块事件无 breaking-change 检查） |

## 4. 错误码专项（重点）

- 设计稿 §5.0 宣称失败用 `ProblemDetail` + `INT_*` 错误码，但 **代码无 `INT_` 常量类**。
- `IntegrationExceptionAdvice`（adapter/web/advice）注释自陈：**"前端拿不到 errorCode；『未实现』这类语义还是靠 `INT_CONNECTOR_NOT_IMPLEMENTED:` 前缀拼在 message"** —— 即 errorCode 未真正透传前端，i18n 取不到。
- 散落裸串：`CreateConnectorCommandHandler`→`INT_CONNECTOR_NAME_DUPLICATED`、`CreateFlowCommandHandler`→`INT_FLOW_NAME_DUPLICATED`/`INT_FLOW_NOT_FOUND`/`INT_FLOW_NOT_ACTIVE`、`ConnectorClientSupport`→`INT_CONNECTOR_NOT_IMPLEMENTED:`。
- 与 bone-metadata-server F1 同源问题（裸串 + 透传缺失），但该处 advice 注释已显式暴露缺陷，**修复价值更高**。

## 5. 建议项（B 阶段候选）

| 项 | 级别 | 范围 | 说明 |
|----|------|------|------|
| **F1** | 高 | 代码+登记+i18n | 新建 `IntegrationErrorCodes` 常量类（聚合 INT_* 裸串，同包或紧邻 advice 免 import）；**真正把 errorCode 经 `IntegrationExceptionAdvice` 透传前端**（修掉注释自陈的缺陷）；错误码登记 §6 补 `INT_` 表；en-US/zh-CN `errors` 同步 |
| **F2** | 高 | 跨模块契约 | 在**本模块先定 `IntegrationEnvelope` 契约基线**：抽 `interface IntegrationEnvelope`（eventId/eventType/topic/tenantId/traceId/schemaVersion/payload），私有 record 实现；含 schemaVersion 演进与 breaking-change 检查；对齐 bone-blueprint 约定，消除"跨模块复制" |
| **F3** | 中 | 路线图 | `Idempotency-Key`（Redis 24h 幂等，doc §5.0/§7 宣称）零实现 → 归 INT-10 专项，非单夜 |
| **F4** | 低 | 文档 | doc4 §6.2 把 Outbox 从 `[Target]` 改为已落地；§5.2 把 `/test`、`/versions` 从 `[Vision]` 改为已实现；附录 B backlog 移除已实现项 |
| **F5** | 决策 | 架构师裁定 | INT-CON-01 红线 vs 9 种 ClientImpl（含 FTP/JDBC/MQ/SOAP）。建议二选一：① 保留为显式 501 占位并在 doc 显式登记 by-design；② 移除被禁协议类。需架构师裁决 |

> F2/F3/F5 均为跨模块/路线图/待裁决，**本夜 B 阶段只落地 F1（+F4 文档）**；F2/F3/F5 留专项或后续轮次。

## 6. 跨模块依赖

- `bone-notification`：pom 声明依赖但 `src/main` 无 import（声明未使用）→ 建议从 pom 移除或补 `@NoDomainEvent`/订阅侧接线（当前无订阅事件，published_events 全为领域事件但未实现 `IntegrationEnvelope`）。
- 事件信封：本模块 `IntegrationEventEnvelope` 私有，blueprint/其他模块各自复制约定 → F2 解决。
- 与 `bone-masterdata` 协同：主数据同步走本模块连接器，禁止 masterdata 直连外部（doc4 §6.2）——代码侧无越界证据。

## 7. 判定与状态迁移

**判定：PASS（无阻断级缺陷，2 项真实缺口均属路线图/待裁决，非硬违反）**。

- HC-003/HC-008 合规（无假阳性）。
- G1/G2 为**文档滞后**（代码领先），不构成缺陷，F4 校正。
- G3（幂等键）、G5（信封契约）为路线图/跨模块专项。
- G4（连接器红线）需架构师裁定（F5）。
- 错误码散落（§4）为 F1 高价值修复点。

**状态迁移**：`pending` → `design_ready`（stages_done A/A'）。下一夜默认放行进 B 实现 F1（+F4），F2/F3/F5 待专项/裁决；仅当 `doc/design/approvals/bone-integration.yaml` 写 `decision: hold/block` 才停。
