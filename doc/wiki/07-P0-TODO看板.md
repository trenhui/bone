# 07 — P0 未完成项看板（平台内核）

[← Wiki 首页](./README.md)

> **用途**：与 PRD MoSCoW **P0** 对齐的**工程债清单**；完成一项请改状态并链到 PR。  
> **范围**：`bone-platform` 内核 + `bone-iam` 架构测试；**不含** `bone-business/tpa-saas` 行业包实现细节。  
> **TPA 迁移**：可选目录 `bone-business/`（本 clone 可能不存在）；路线见 `bone-business/TPA-MIGRATION.md`；里程碑用 **TPA-*** 前缀登记。

---

## TPA 迁移（已废止）

> **2026-05**：`bone-business/` 行业包已移出本仓库主干；下列 **TPA-*** 项一律 **cancelled**，勿再排期。

| ID | 描述 | 状态 |
|----|------|------|
| TPA-00～06 | 原 Go/React 迁移路线 | cancelled |

---

## 状态说明

| 状态 | 含义 |
|------|------|
| `open` | 未开始或仅占位 |
| `wip` | 开发中 |
| `done` | 已合并主干 |

---

## 集成引擎（bone-platform/bone-integration）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| INT-01 | `RestClientImpl` | HTTP 未实现时 **HTTP 501** + `INT_CONNECTOR_NOT_IMPLEMENTED`（非假成功） | done | P0 |
| INT-02 | `FtpClientImpl` | FTP 未实现时 **HTTP 501** + `INT_CONNECTOR_NOT_IMPLEMENTED` | done | P1 |
| INT-03 | `JdbcClientImpl` | JDBC 未实现时 **HTTP 501** + `INT_CONNECTOR_NOT_IMPLEMENTED` | done | P1 |
| INT-04 | `MqClientImpl` | MQ 未实现时 **HTTP 501** + `INT_CONNECTOR_NOT_IMPLEMENTED` | done | P1 |
| INT-05 | `FlowStatisticsJob` | 每日汇总 `int_execution_log` 成功率（`FlowMonitorService`） | done | P2 |
| INT-06 | `*Handler` 事件类 | 连接器/流程事件后续动作（`IntegrationDomainEventPublisher` + 7 类 Handler + `bone-notification`） | done | P2 |
| INT-07 | `bone-platform/pom.xml` | `bone-platform-integration` 入 reactor（唯一集成 Maven 构件） | done | P0 |
| INT-08 | `RestClientImpl` | REST/HTTP/HTTPS 连接器真实 `HttpClient` 调用 | done | P1 |
| INT-09 | `LinearSyncFlowRuntime` | **同步 MVP**：请求线程内 START→HTTP→END（`ExecuteFlowHandler` 写回执行日志） | done | P1 |
| INT-10 | 领域事件 | `int_outbox` + 信封 + 中继（`BONE_INTEGRATION_OUTBOX_MQ_ENABLED` 切 RocketMQ） | done | P2 |
| INT-11 | `CamelFlowCompiler` | `int_flow_node` → Camel 路由（Choice/并行；HTTP 组件已迁入） | open | P2 |
| INT-12 | 模块收敛 | 删除 `bone-engine/bone-integration`，唯一服务 `bone-platform-integration` | done | P0 |
| INT-SEC-01 | `SecurityConfig` | `BONE_INTEGRATION_JWT_ENABLED=true` 时 `/integration/**` 需 IAM JWT | done | P1 |

**建议**：对外 API 在未实现前返回 **501** + 明确错误码，避免「假成功」。

---

## 主数据（bone-platform/bone-masterdata）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| MD-01 | `DataQualityController` | 质量报告查询（`mdm_qcheck_report` + `GET …/reports/{id}`） | done | P1 |
| MD-02 | `MasterDataRecordController` | 主数据记录 JSON 导出 | done | P1 |
| MD-03 | `MasterDataEntityController` | 元数据实体 ID → `md_entity` 转换 | done | P1 |

---

## IAM（bone-platform/bone-iam）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| IAM-01 | `AuthController` | Token 黑名单（Redis，无 Redis 时仅撤销 refresh） | done | P0 |
| IAM-02 | `AuthController` | Token 刷新（`iam_refresh_token`） | done | P0 |
| IAM-03 | `ArchitectureTest` | ArchUnit 分层 + 空仓储接口；`AuthService` 允许 Spring Security | done | P0 |

---

## 扩展管理（bone-engine/bone-extension-engine + bone-frontend）

> 详设：[`doc/design/modules/5. 扩展管理模块详细设计方案.md`](../design/modules/5.%20扩展管理模块详细设计方案.md) §0.4。PRD：§4.6。

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| EXT-MVP-01 | `bone-extension-app` | 扩展点/实现 CRUD、config 编辑、publish-runtime | done | P1 |
| EXT-MVP-02 | `bone-extension-app` | Vite 代理 Studio **8088** | done | P1 |
| EXT-MVP-03 | `bone-extension-studio-ui` | 已删除目录；以 `bone-extension-app` 为准 | done | P1 |
| EXT-MVP-04 | Studio + IAM | `/api/**` JWT 鉴权（`bone.iam.jwt`，CORS 含 3008） | done | P1 |
| EXT-MVP-05 | `bone-extension-studio` | `points`/`plugins` 支持 `page`/`size`（兼容全量列表） | done | P2 |
| EXT-MVP-06 | Studio | `rollback` 版本回滚（`ExtensionServiceImpl.rollbackExtension`） | done | P2 |
| EXT-MVP-07 | Studio | LRO 部署：`POST …:deploy` → **202** + `GET /operations/{id}`（`StudioLroService`） | done | P2 |
| EXT-MVP-08 | Studio | 幂等写 + `If-Match` 乐观锁（`StudioIdempotencyService`，412/409） | done | P2 |
| EXT-MVP-09 | `bone-extension-sdk` | 执行防护 `ExtensionExecutionGuard`（超时/并发可配置） | done | P2 |
| EXT-PH2-01 | Studio | JAR Multipart 上传 + `ext_plugin_version` | open | P2 |
| EXT-PH3-01 | SDK | Wasm 隔离运行时 | open | P3 |

---

## 元数据能力族（sdk / server / engine / generator）

> 定义与协作：[元数据能力-实现映射与竞品对照.md](../design/modules/元数据能力-实现映射与竞品对照.md) · 详设 [§2](../design/modules/2.%20元数据管理模块详细设计方案.md) §0

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| META-ASIS-01 | `bone-metadata-server` | 扩展字段 API `/api/v1/metadata/fields:*` | done | P0 |
| META-ASIS-02 | `bone-metadata-sdk` | 平台 `@EnableSqlRepositories` + EAV | done | P0 |
| META-VIS-01 | `bone-metadata-server` catalog | 实体/关系 REST：`/api/v1/metadata/entities`、`…/entities/{id}/fields`、`…/relationships` | done | P0 |
| META-VIS-02 | `bone-metadata-app` :3004 | 建模 UI CRUD；代理 **9001** + `VITE_API_KEY` | done | P0 |
| META-VIS-03 | `studio-generator` | 物理库 + **CATALOG_SNAPSHOT**；`GET /api/v1/generator/metadata-entity-snapshots`、`GET …/data-sources/{id}/tables` | done | P0 |
| META-VIS-04 | `bone-generator-app` | 与 generator :8085 API 对齐 | done | P1 |
| META-ENG-01 | `bone-metadata-engine` | `SdkMetadataPlatformBridge` 读 `meta_*` status=1 + starter 自动装配 | done | P1 |
| META-002B-01 | `meta_entity.delivery_mode` + catalog API | 实体交付模式 0-GENERATIVE / 1-RUNTIME；`bone-metadata-app` 可选 | done | P1 |
| META-002B-02 | `bone-metadata-engine` + `bone-metadata-server` | 模式 B：`JdbcRuntimeRecordService` + `/api/v1/runtime/entities/{code}/records` | done | P1 |
| META-002B-03 | `studio-generator` | `delivery_mode=RUNTIME` 实体跳过标准 CRUD 生成（catalog 快照过滤） | done | P2 |

## 控制台与仪表盘（bone-shell + bone-system）

> 详设：[1. 控制台与仪表盘模块详细设计方案.md](../design/modules/1.%20控制台与仪表盘模块详细设计方案.md) · PRD §4.3 DASH-001/002

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| DASH-01 | `bone-shell` | 首页对接 `GET /api/v1/console/overview`（30s 刷新） | done | P0 |
| DASH-02 | `bone-shell` | 快捷操作来自 `GET /api/v1/console/quick-actions` | done | P0 |
| DASH-03 | `bone-system` | `ConsoleController` 聚合 JVM 指标与服务状态 | done | P0 |

---

## Metadata Engine（bone-engine/bone-metadata-engine，原 bone-smartmeta）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| SM-01 | `MetadataImpactAnalyzer` | 关系/依赖/工作流/规则影响分析 | open | P2 |

---

## 维护约定

- 新增占位实现时**同步**在本表登记一行。  
- 季度评审：与 [BONE产品需求文档正式版](../prd/BONE产品需求文档正式版.md) §4 功能需求对照，关闭或降级非 P0 项。
