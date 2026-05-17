# 07 — P0 未完成项看板（平台内核）

[← Wiki 首页](./README.md)

> **用途**：与 PRD MoSCoW **P0** 对齐的**工程债清单**；完成一项请改状态并链到 PR。  
> **范围**：`bone-platform` 内核 + `bone-iam` 架构测试；**不含** `bone-business/tpa-saas` 行业包实现细节。  
> **TPA 迁移**：路线 [bone-business/TPA-MIGRATION.md](../../bone-business/TPA-MIGRATION.md)；里程碑用 **TPA-*** 前缀登记。

---

## TPA 迁移（bone-business）

| ID | 描述 | 状态 | 备注 |
|----|------|------|------|
| TPA-00 | Java API 基线清单 | done | [API-INVENTORY.md](../../bone-business/tpa-go/contracts/API-INVENTORY.md) |
| TPA-01 | `tpa-go` 骨架（health + migration/status） | done | [tpa-go/README.md](../../bone-business/tpa-go/README.md) |
| TPA-02 | OpenAPI 契约 CI 校验 | open | `contracts/openapi.yaml` |
| TPA-03 | 首条业务只读 API Go 实现或代理 | open | 建议从 `/tpa/query` 或 `/health` 邻域开始 |
| TPA-04 | React `VITE_API_BASE` 灰度切换方案 | open | 见 `tpa-sass-react/.env.example` |
| TPA-05 | Vue3 功能 parity 清单 | open | 对照 React |
| TPA-06 | Java `tpa-saas` 域级下线 | open | 阶段 3 |

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
| INT-01 | `RestClientImpl` | HTTP 连接测试与请求发送 | open | P0 |
| INT-02 | `FtpClientImpl` | FTP 连接与文件操作 | open | P1 |
| INT-03 | `JdbcClientImpl` | JDBC 连接与 SQL 执行 | open | P1 |
| INT-04 | `MqClientImpl` | MQ 连接与消息发送 | open | P1 |
| INT-05 | `FlowStatisticsJob` | 流程执行统计定时任务 | open | P2 |
| INT-06 | `*Handler` 事件类 | 连接器/流程事件后续动作（通知等） | open | P2 |
| INT-07 | `bone-platform/pom.xml` | `bone-platform-integration` 入 reactor（与 engine 侧 `bone-integration` 构件区分） | done | P0 |
| INT-SEC-01 | `SecurityConfig` | `BONE_INTEGRATION_JWT_ENABLED=true` 时 `/integration/**` 需 IAM JWT | done | P1 |

**建议**：对外 API 在未实现前返回 **501** + 明确错误码，避免「假成功」。

---

## 主数据（bone-platform/bone-masterdata）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| MD-01 | `DataQualityController` | 质量报告查询 | open | P1 |
| MD-02 | `MasterDataRecordController` | 主数据记录导出 | open | P1 |
| MD-03 | `MasterDataEntityController` | 从业务实体转换为主数据实体 | open | P1 |

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
| EXT-MVP-06 | Studio | `rollback` 真实版本回滚（Phase 2） | open | P2 |
| EXT-PH2-01 | Studio | JAR Multipart 上传 + `ext_plugin_version` | open | P2 |
| EXT-PH3-01 | SDK | Wasm 隔离运行时 | open | P3 |

---

## 元数据能力族（sdk / server / engine / generator）

> 定义与协作：[元数据能力-实现映射与竞品对照.md](../design/modules/元数据能力-实现映射与竞品对照.md) · 详设 [§2](../design/modules/2.%20元数据管理模块详细设计方案.md) §0

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| META-ASIS-01 | `bone-metadata-server` | 扩展字段 API `/v1/metadata/fields:*` | done | P0 |
| META-ASIS-02 | `bone-metadata-sdk` | 平台 `@EnableSqlRepositories` + EAV | done | P0 |
| META-VIS-01 | `bone-metadata-server` catalog | 实体/关系 REST：`/api/v1/metadata/entities`、`…/entities/{id}/fields`、`…/relationships` | open | P0 |
| META-VIS-02 | `bone-metadata-app` :3004 | 建模 UI；开发代理指向 **9001**（非 IAM 8081） | open | P0 |
| META-VIS-03 | `studio-generator` | As-Is：物理库表反向解析 + 模板；Vision：读 `meta_*` 发布快照 | open | P0 |
| META-VIS-04 | `bone-generator-app` | 与 generator :8085 API 对齐 | open | P1 |
| META-ENG-01 | `bone-metadata-engine` | 接入平台 + SPI 桥接 SDK 元模型 | open | P1 |

## Metadata Engine（bone-engine/bone-metadata-engine，原 bone-smartmeta）

| ID | 位置 | 描述 | 状态 | 优先级 |
|----|------|------|------|--------|
| SM-01 | `MetadataImpactAnalyzer` | 关系/依赖/工作流/规则影响分析 | open | P2 |

---

## 维护约定

- 新增占位实现时**同步**在本表登记一行。  
- 季度评审：与 [BONE产品需求文档正式版](../prd/BONE产品需求文档正式版.md) §4 功能需求对照，关闭或降级非 P0 项。
