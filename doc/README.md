# Bone 文档总索引

本目录是仓库文档的入口。文档按角色分九类，各有边界：

| 目录 | 角色 | 谁来维护 | 是否手改 |
|------|------|----------|----------|
| `agents/` | AI Agent 手册（`AGENTS.md` 拆分正文） | 架构组 | 协作条款属 L4，AI 不自行改 |
| `architecture/` | 平台架构与工程规范（含 `adr/`） | 架构组 | 是 |
| `prd/` | 产品需求 | 产品 | 是 |
| `design/modules/` | 模块详细设计 | 各模块负责人 | 是 |
| `wiki/` | 导航型知识库（新人/协作者上手） | 全员 | 是 |
| `agenticx/` | Agentic 工程指南 | 工程 | 是 |
| `_generated/` | CI 派生的合规产物（As-Is / Backlog） | 脚本 | **禁手改** |
| `archive/` | 已归档、仅供追溯的历史文档 | — | 否 |
| `deployment/` | 部署用 SQL 与模型图 | 运维 | 是 |

## 权威文档

| 文档 | 路径 |
|------|------|
| 总体架构 | [architecture/BONE-总体架构设计方案.md](./architecture/BONE-总体架构设计方案.md) |
| DDD 与门禁（含 HC 状态真源 `#hc-hard-constraints`） | [architecture/Bone-DDD-最终实践方案.md](./architecture/Bone-DDD-最终实践方案.md) |
| API / 错误码 / 日志 | [architecture/Bone-API-规范.md](./architecture/Bone-API-规范.md) |
| 数据库 DDL（含表清单） | [architecture/数据库开发规范.md](./architecture/数据库开发规范.md)，真源 `../../bone-init.sql` |
| 前端 | [architecture/bone-前端架构.md](./architecture/bone-前端架构.md) · [UI 规范](./architecture/frontend/frontend-ui-spec.md) |
| 通用语言 | [glossary.md](./glossary.md) |
| 架构决策 | [architecture/adr/](./architecture/adr/) |

## 入口顺序

1. 先读 [wiki/README.md](./wiki/README.md)（上手与导航）。
2. AI 任务按 [AGENTS.md](../AGENTS.md) → `doc/agents/` 路由加载对应章节。
3. 写需求看 `prd/`；写模块看 `design/modules/`；定架构看 `architecture/` 与 `adr/`。

历史草稿（`PROJECT_SUMMARY.md`、`*_SUMMARY.md`、`*PLAN*`、`*ANALYSIS*` 等）已删除，需要旧版本请查 Git 历史。
