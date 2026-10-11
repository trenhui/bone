# Bone 文档总索引

本目录是仓库文档的入口。按**消费方 + 生命周期**分界：

| 目录 | 角色 | 谁来维护 | 是否手改 |
|------|------|----------|----------|
| `agents/` | AI Agent 手册（`AGENTS.md` 拆分正文） | 架构组 | 协作条款属 L4，AI 不自行改 |
| `architecture/` | 平台架构与工程规范（含 `adr/`、CI baseline） | 架构组 | 是；**baseline/gate 路径封箱** |
| `architecture/reports/` | 人工收敛报告 | 架构组 | 是 |
| `prd/` | 产品需求 | 产品 | 是 |
| `design/modules/` | 模块详设 + 横切方案 | 各模块负责人 | 是；`review-report-*` 路径封箱 |
| `design/lowcode/` | 低代码 Mode B 专题包（迭代 PRD + runtime / cases / ui） | 产品 + 元数据 + 前端 | 是 |
| `design/assets/` | 设计配图 | 全员 | 是 |
| `plans/` | 升级/迁移草案（未落地） | 架构组 | 是 |
| `wiki/` | 导航型知识库 | 全员 | 是 |
| `agenticx/` | Agentic 工程指南 | 工程 | 是 |
| `evidence/` | 联调/演示证据截图 | 工程 | 是 |
| `_generated/` | CI 派生合规产物 | 脚本 | **禁手改** |
| `archive/` | 历史归档 | — | 否 |
| `deployment/` | 部署用 SQL 与模型图 | 运维 | 是 |

## 权威文档

| 文档 | 路径 |
|------|------|
| 总体架构 | [architecture/BONE-总体架构设计方案.md](./architecture/BONE-总体架构设计方案.md) |
| DDD 与门禁（含 HC 状态真源 `#hc-hard-constraints`） | [architecture/Bone-DDD-最终实践方案.md](./architecture/Bone-DDD-最终实践方案.md) |
| API / 错误码 / 日志 | [architecture/Bone-API-规范.md](./architecture/Bone-API-规范.md) |
| 数据库 DDL（含表清单） | [architecture/数据库开发规范.md](./architecture/数据库开发规范.md)，真源 `../../bone-init.sql` |
| 前端 | [architecture/bone-前端架构.md](./architecture/bone-前端架构.md) · [UI 规范](./architecture/frontend/frontend-ui-spec.md) |
| 低代码 Mode B | [design/lowcode/](./design/lowcode/README.md)（含 [迭代 PRD](./design/lowcode/BONE-低代码最小闭环PRD.md) + runtime / cases / ui） |
| 通用语言 | [glossary.md](./glossary.md) |
| 架构决策 | [architecture/adr/](./architecture/adr/) |
| 架构分层总览图 | [architecture/architecture-layers.svg](./architecture/architecture-layers.svg) |
| MVP 范围清单 | [wiki/10-MVP-范围清单.md](./wiki/10-MVP-范围清单.md) |
| 升级草案 | [plans/](./plans/README.md) |

## 入口顺序

1. 先读 [wiki/README.md](./wiki/README.md)（上手与导航）。
2. AI 任务按 [AGENTS.md](../AGENTS.md) → `doc/agents/` 路由加载对应章节。
3. 写需求看 `prd/`；写模块看 `design/modules/`；**低代码 Mode B** 看 [`design/lowcode/`](./design/lowcode/README.md)；定架构看 `architecture/`；升级计划看 `plans/`。

## 治理要点（Clean Core 文档化）

- **机器读路径封箱**：`architecture/*-baseline.json`、`gate-state.json`、规范正文 SSOT、`design/_*.yaml`、`modules/review-report-*` —— 不挪目录换名。
- **人读靠索引**：规范不下沉 `standards/`（避免打断 CI/`AGENTS.md`）；用各 README 分区导航。
- **专题聚合**：Mode B → `design/lowcode/`；勿再散落到已删除的 `design/ui` / `design/cases`。

历史草稿已删除，需要旧版本请查 Git 历史。
