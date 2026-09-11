# Bone 项目文档索引（`doc/`）

> **新人入口**：优先 [wiki/README.md](./wiki/README.md) → 根 [README.md](../README.md) → [AGENTS.md](../AGENTS.md)。

## 权威文档（与实现一致）

| 目录 | 说明 |
|------|------|
| [architecture/](./architecture/) | **平台架构**：总体方案、DDD 门禁、前端、数据库规范 |
| [prd/](./prd/) | **产品需求**：主 PRD 与写作模板 |
| [design/](./design/) | **模块详设**：控制台、元数据、主数据、集成、扩展、IAM、系统、Generator、SmartMeta |
| [design/modules/元数据能力-实现映射与竞品对照.md](./design/modules/元数据能力-实现映射与竞品对照.md) | **元数据 sdk/server/engine** 定义、协作、API As-Is、竞品 |
| [文档治理-三目录审查子任务.md](./文档治理-三目录审查子任务.md) | PRD / 架构 / 详设 **审查子任务与 DDL 单轨口径** |
| [wiki/](./wiki/) | 本地构建、模块地图、P0 看板、Blueprint 对齐 |
| [_generated/](./_generated/) | **CI 派生**（勿手改）：扩展模块 As-Is 证据与 Backlog |
| [Agenticx编程/](./Agenticx编程/) | Agentic 工程指南（运行时见 `.claude/`） |
| 引擎源码 | [bone-engine/README.md](../bone-engine/README.md)（Metadata SDK、扩展、集成等模块索引） |

### 架构目录内唯一权威（摘要）

| 文档 | 路径 |
|------|------|
| 总体架构 | [architecture/BONE-总体架构设计方案.md](./architecture/BONE-总体架构设计方案.md) |
| API / 错误码 / 日志 | [architecture/Bone-API-规范.md](./architecture/Bone-API-规范.md) |
| 数据库 DDL | [architecture/数据库开发规范.md](./architecture/数据库开发规范.md) |
| DDD | [architecture/Bone-DDD-最终实践方案.md](./architecture/Bone-DDD-最终实践方案.md)（v5.0） |
| 通用语言 | [glossary.md](./glossary.md) |
| 前端 | [architecture/bone-前端架构.md](./architecture/bone-前端架构.md) |
| UI | [architecture/frontend/frontend-ui-spec.md](./architecture/frontend/frontend-ui-spec.md) |
| 数据库 | [architecture/数据库开发规范.md](./architecture/数据库开发规范.md)（含 `bone-init.sql` 表清单） |

原 `doc/arch`、`doc/DDD`、`doc/前端规范`、`doc/数据库` 及根目录 `PROJECT_SUMMARY.md`、`*_SUMMARY.md`、`*PLAN*` / `*ANALYSIS*` 草稿等**已删除**；架构与规范统一见 [architecture/](./architecture/)，需要历史版本时查 Git。

## 其他

| 目录 | 说明 |
|------|------|
| [deployment/](./deployment/) | 部署用 SQL、模型图等 |
| [Agenticx编程/](./Agenticx编程/) | Agentic 长文；运行时见 `.claude/` |
