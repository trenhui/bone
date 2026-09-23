# Bone — AI Agent 手册（拆分索引）

> 本目录是 [`AGENTS.md`](../../AGENTS.md) 的拆分正文。原单文件版（581 行）已于 2026-09-17 拆分，
> 备份见 [`doc/archive/AGENTS-单文件版-2026-09-17.md`](../archive/AGENTS-单文件版-2026-09-17.md)。

## 为什么拆

单文件 581 行 / 3.2 万字符，任何任务都要整体灌入上下文；而其中「改 domain 代码时才需要的分层细节」与「只有部署时才需要的端口表」，与「每次都必须遵守的编码准则」混在一起，导致：

- **AI 加载成本高**，且关键约束被淹没在详情里；
- **状态类内容被重复抄写**（`AGENTS.md §12.1` 曾复制一份 HC 表，实测 8 条中 5 条与实现不符）。

拆分的判据是**加载时机**，不是主题分类。

## 文件清单

| 文件 | 承载原章节 | 什么时候读 |
|---|---|---|
| [01 项目概览与模块结构](01-项目概览与模块结构.md) | §1 §2 §3 | 要了解项目定位、技术栈、模块与端口 |
| [02 构建·运行·部署](02-构建运行与部署.md) | §4 §9 | 要跑构建命令、起服务、部署、查端口 |
| [03 架构分层规范](03-架构分层规范.md) | §5 | **要改后端代码**：分层、依赖规则、基础类、设计模式 |
| [04 测试与代码质量](04-测试与代码质量.md) | §6 §7 | 要写测试、过 Spotless / ArchUnit / 静态分析 |
| [05 数据库与安全](05-数据库与安全.md) | §8 §10 | 要动表、写 SQL、关注安全基线 |
| [06 AI 协作与编码准则](06-AI协作与编码准则.md) | §11 §12 | **任何任务**：编码准则、自主权分级、交付流程 |

## 读法与约定

- **章节编号沿用拆分前**：拆分文档里的 `§5.2`、`§11.12` 就是原 `AGENTS.md` 的编号，历史引用（ADR、模块详设、技能说明）按编号仍可定位到对应文件。
- **路径写法**：正文里的 `` `doc/…` ``、`` `scripts/…` `` 反引号路径一律**相对仓库根**；Markdown 链接则相对本文件。
- **单一真源**：门禁与 HC 状态的唯一真源是 [Bone-DDD-最终实践方案.md §G-1.7](<../architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints>)。本目录任何文件都**不得复制 HC 表或门禁状态**——需要时只写编号 + 指针。
- **本目录属 `§12.3` L4 范围**：AI 不得自行修改 §12 相关内容（含本目录的协作条款），需架构师本人执行或明确授权。

## 13. 参考索引

| 文件/目录 | 内容 |
|---|---|
| `README.md` | 项目营销概览、快速开始 |
| `doc/archive/CODE_WIKI.md` | 项目知识库：四大引擎说明、关键类、依赖树、运行说明 |
| `doc/architecture/Bone-DDD-最终实践方案.md` | DDD 与分层门禁唯一权威（HC 状态真源见 `#hc-hard-constraints`） |
| `doc/glossary.md` | 通用语言起步表 |
| `doc/architecture/adr/0023-core-domain-smart-metadata.md` | Bone 当前核心域 = Metadata |
| `doc/architecture/adr/0024-ddd-v5-rule-semantics-and-document-split.md` | DDD v5.0 规则语义与文档分册 |
| `doc/architecture/adr/0025-ddd-v5-0-2-implementation-alignment.md` | DDD v5.0.2 规则标识与实现状态对齐 |
| `doc/architecture/adr/0028-application-service-first-selective-cqrs.md` | Application Service First + Selective CQRS |
| `doc/architecture/README.md` | 架构文档索引 |
| `doc/wiki/07-P0-TODO看板.md` | 平台未完成项与工程债 |
| `doc/README.md` | `doc/` 总索引 |
| `bone-frontend/SCRIPT_USAGE.md` | 前端批量启动脚本说明 |
| `bone-init.sql` | 数据库初始化脚本（DDL 唯一真源） |
| `bone-parent/pom.xml` | 依赖版本锁定与全局插件配置（`jacoco.minimum.coverage` 真源） |
| `bone-engine/bone-metadata-sdk/` | 默认持久化 SDK（[README](../../bone-engine/bone-metadata-sdk/README.md) + [doc/](../../bone-engine/bone-metadata-sdk/doc/)） |
| `doc/design/modules/元数据能力-实现映射与竞品对照.md` | sdk / server / engine 定义、协作、竞品 |
| `bone-engine/README.md` | 引擎层模块索引 |
