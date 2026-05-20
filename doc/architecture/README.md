# 架构设计文档（`doc/architecture`）

本目录集中存放 **与仓库实现一致** 的平台架构、工程规范与 UI 约定。

## 阅读顺序（新人）

| 顺序 | 文档 | 说明 |
|------|------|------|
| 0 | [README.md](../../README.md) | 产品理念、四大引擎、**元数据双模式**、演进路线 |
| 1 | [BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md) | 平台总体、模块、NFR（§2.1.1 对齐 README 原则） |
| 2 | [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) | 分层、CQRS、ArchUnit |
| 3 | [Bone-API-规范.md](./Bone-API-规范.md) | REST 契约、OpenAPI、契约测试 |
| 4 | [数据库开发规范.md](./数据库开发规范.md) | DDL 真源 `bone-init.sql` |

## 工程规范索引

### HTTP 与契约

| 文档 | 说明 |
|------|------|
| [Bone-API-规范.md](./Bone-API-规范.md) | URL、信封、分页、头、LRO、幂等 |
| [Bone-错误码登记.md](./Bone-错误码登记.md) | `errorCode` 台账（真源） |
| [openapi/](./openapi/) | 域契约：`iam-v1` · `masterdata-v1` · `integration-v1` · `metadata-runtime-v1` · `generator-v1` · `extension-v1` |

### 可观测性与运维

| 文档 | 说明 |
|------|------|
| [Bone-日志规范.md](./Bone-日志规范.md) | 日志、MDC、审计、脱敏 |
| [Bone-可观测性规范.md](./Bone-可观测性规范.md) | Metrics、Trace、SLO、告警 |
| [Bone-配置与环境规范.md](./Bone-配置与环境规范.md) | 环境变量、Profile、密钥 |
| [config/env/README.md](../../config/env/README.md) | 本地 `.env` 操作入口 |

### 安全、数据与集成

| 文档 | 说明 |
|------|------|
| [Bone-安全开发规范.md](./Bone-安全开发规范.md) | OWASP、认证、密钥、插件沙箱 |
| [Bone-多租户规范.md](./Bone-多租户规范.md) | `tenant_id` 传递链 |
| [Bone-缓存规范.md](./Bone-缓存规范.md) | Redis/Caffeine Key 与 TTL |
| [Bone-消息与事件规范.md](./Bone-消息与事件规范.md) | Topic、信封、DLQ、Webhook |
| [ADR-integration-consolidation.md](./ADR-integration-consolidation.md) | 集成单模块收敛（移除 engine 侧 `bone-integration`） |

### 质量与交付

| 文档 | 说明 |
|------|------|
| [Bone-测试策略.md](./Bone-测试策略.md) | 单测、契约、ArchUnit、CI |
| [Bone-版本与发布规范.md](./Bone-版本与发布规范.md) | API 版本、发布顺序、回滚 |
| [Bone-国际化规范.md](./Bone-国际化规范.md) | i18n、时区、errorCode 文案 |
| [adr/](./adr/) | 架构决策记录（含 [0002 meta→mdm](./adr/0002-masterdata-catalog-sync.md)） |

### 前端

| 文档 | 说明 |
|------|------|
| [bone-前端架构.md](./bone-前端架构.md) | Qiankun、workspaces |
| [frontend/frontend-ui-spec.md](./frontend/frontend-ui-spec.md) | UI 令牌与组件 |

### 其他

| 文档 | 说明 |
|------|------|
| [元数据能力-实现映射与竞品对照](../design/modules/元数据能力-实现映射与竞品对照.md) | metadata sdk/server/engine |
| [smartmeta/README.md](./smartmeta/README.md) | SmartMeta 补充 |
| ~~[初始脚本.sql](./初始脚本.sql)~~ / ~~[DDL对齐说明.md](./DDL对齐说明.md)~~ | **已废止** |
| [ADR-数据库迁移与DDL真源.md](./ADR-数据库迁移与DDL真源.md) | → [adr/0001](./adr/0001-database-ddl-single-source.md) |

## 与 `doc/design`、`doc/prd` 的关系

| 目录 | 职责 |
|------|------|
| [`doc/prd/`](../prd/) | 产品需求 |
| [`doc/design/modules/`](../design/modules/) | 模块详设（API 细节以 OpenAPI + 本目录规范为准） |

## 文档治理

- **审查清单**：[`doc/文档治理-三目录审查子任务.md`](../文档治理-三目录审查子任务.md)
- **最近审查**：
  - 2026-05-17：`Bone-DDD`、数据库、前端、测试策略与 OpenAPI 组件对齐。
  - 2026-05-20 第五轮（与 `doc/design/modules` 第四轮联动）：
    - [BONE-总体架构](./BONE-总体架构设计方案.md) §12.1 Helm/`bone-chart` 标 **[Vision]**；§22.3 拆 **As-Is 端口表**（对齐 [wiki/03](../wiki/03-本地开发与构建.md)）与 Vision 示意表，废止 `metadata_db` / `integration:8083` 等误导性默认值；
    - [bone-前端架构](./bone-前端架构.md) E2E 真源统一为 **Playwright**；
    - [adr/README](./adr/README.md) 标注 `0002` 编号冲突（IAM vs 主数据 catalog sync）。

## 交叉引用

- **扩展详设**：[design/modules/5](../design/modules/5.%20扩展管理模块详细设计方案.md)  
- **模块详设索引**：[design/modules/README.md](../design/modules/README.md)  
- **端口**：[wiki/03-本地开发与构建.md](../wiki/03-本地开发与构建.md)  
- **全库索引**：[doc/README.md](../README.md)
