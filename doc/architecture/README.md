# 架构设计文档（`doc/architecture`）

本目录集中存放 **与仓库实现一致** 的平台架构、前端架构与 UI 规范。

## 阅读顺序

| 文档 | 说明 |
|------|------|
| [BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md) | **平台总体**架构、模块边界、NFR、安全、数据与 DevOps 基线 |
| [Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md) | **DDD 唯一权威**：业界原则 + Bone 工程落地（铁律、包结构、Metadata SDK、读写路径） |
| [bone-前端架构.md](./bone-前端架构.md) | **前端唯一权威**：Qiankun 微前端、npm workspaces、`bone-frontend` 目录与工程约定 |
| [frontend/frontend-ui-spec.md](./frontend/frontend-ui-spec.md) | 设计令牌、布局、组件与无障碍等 **UI 规范** |
| [数据库开发规范.md](./数据库开发规范.md) | MySQL 命名、租户、审计、索引与分区约定 |
| [初始脚本.sql](./初始脚本.sql) | 文档化全量 DDL；种子与线上一致以根 `bone-init.sql` 为准 |
| [smartmeta/README.md](./smartmeta/README.md) | SmartMeta 补充说明；模块真源见 [design/modules/9](../design/modules/9.%20SmartMeta%20引擎模块技术说明.md) |

## 与 `doc/design`、`doc/prd` 的关系

| 目录 | 职责 |
|------|------|
| [`doc/prd/`](../prd/) | 产品需求（主 PRD） |
| [`doc/design/modules/`](../design/modules/) | 各模块详细设计 |

## 其他交叉引用

- **扩展模块详设**：[design/modules/5](../design/modules/5.%20扩展管理模块详细设计方案.md)
- **端口与快速开始**：根 [README.md](../../README.md)、[wiki/03-本地开发与构建.md](../wiki/03-本地开发与构建.md)
- **全文档索引**：[doc/README.md](../README.md)

## 占位跳转

| 文件 | 说明 |
|------|------|
| [core-design.md](./core-design.md) | 指向 DDD / 总体架构 |
| [deployment.md](./deployment.md) | 指向 wiki 部署与数据库规范 |

原 `doc/arch`、`doc/前端规范`、`doc/DDD` 及各类 `*_SUMMARY.md` / `dependency-tree.txt` 等历史文件**已从仓库移除**；需要时见 Git 历史。
