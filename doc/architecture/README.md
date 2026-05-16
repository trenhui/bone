# 架构设计文档（`doc/architecture`）

本目录集中存放 **与仓库实现一致** 的平台架构、前端架构与 UI 规范，避免 `doc/arch`、`doc/前端规范` 等多处重复或互相矛盾。

## 阅读顺序

| 文档 | 说明 |
|------|------|
| [BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md) | **平台总体**架构、模块边界、NFR、安全、数据与 DevOps 基线 |
| [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) | **DDD 唯一权威**：业界原则 + Bone 工程落地（铁律、包结构、Metadata SDK、读写路径） |
| [bone-前端架构.md](./bone-前端架构.md) | **前端唯一权威**：Qiankun 微前端、npm workspaces、`bone-frontend` 目录与工程约定 |
| [frontend/frontend-ui-spec.md](./frontend/frontend-ui-spec.md) | 设计令牌、布局、组件与无障碍等 **UI 规范** |
| [数据库开发规范.md](./数据库开发规范.md) | MySQL 命名、租户、审计、索引与分区约定 |
| [初始脚本.sql](./初始脚本.sql) | 文档化全量 DDL（表数量随演进变化，以文件为准）；种子与线上一致以仓库根 `bone-init.sql` 为准 |
| [smartmeta/README.md](./smartmeta/README.md) | SmartMeta **遗留文档导航**（实现真源见 `design/modules/9` 与 `bone-metadata-engine`） |

## 与 `doc/design`、`doc/prd` 的关系

| 目录 | 职责 |
|------|------|
| **本目录** | 平台级架构、DDD 门禁、前端工程、数据库规范 |
| [`doc/prd/`](../prd/) | 产品需求、优先级与验收（主 PRD） |
| [`doc/design/modules/`](../design/modules/) | 各业务/平台模块详细设计；**SmartMeta / Metadata Engine** 见 [9. SmartMeta 引擎模块技术说明.md](../design/modules/9.%20SmartMeta%20引擎模块技术说明.md) |

## 其他交叉引用

- **扩展模块详设**：[扩展管理模块详细设计](../design/modules/5.%20扩展管理模块详细设计方案.md)（ExtPoint / Studio / Phase 1 MVP）
- **默认端口与快速开始**：根目录 [README.md](../../README.md)、[doc/wiki/03-本地开发与构建.md](../wiki/03-本地开发与构建.md)
- **全栈模块地图**：根目录 [AGENTS.md](../../AGENTS.md)

## 历史草案（勿作实现真源）

| 类型 | 文件 | 权威替代 |
|------|------|----------|
| 前端 | `bone前端*.md`（9 篇，文首已标废止） | [bone-前端架构.md](./bone-前端架构.md)、[frontend/frontend-ui-spec.md](./frontend/frontend-ui-spec.md) |
| 后端包结构 | [overview.md](./overview.md)、[工程结构.md](./工程结构.md)、[temp.md](./temp.md) | [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) |
| SmartMeta | `smartmeta/smartmeta-design.md` 等（文首已标历史稿） | [design/modules/9](../design/modules/9.%20SmartMeta%20引擎模块技术说明.md)、`bone-metadata-engine/` |
| 占位 | [core-design.md](./core-design.md)、[deployment.md](./deployment.md) | 见各文件内跳转 |

原 `doc/arch`、`doc/前端规范` 路径已废止；外部链接请改指向上表或本 README「阅读顺序」。
