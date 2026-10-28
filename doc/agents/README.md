# Bone — AI Agent 手册（拆分索引）

> 本目录是 [`AGENTS.md`](../../AGENTS.md) 的拆分正文。原单文件版（581 行）已于 2026-09-17 拆分，
> 备份文件已删除，**需要旧版请查 Git 历史**。

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
| [04 测试与代码质量](04-测试与代码质量.md) | §6 §7 | 要写测试、过 Spotless / ArchUnit / 静态分析、写或跑 E2E（`scripts/e2e`） |
| [05 数据库与安全](05-数据库与安全.md) | §8 §10 | 要动表、写 SQL、关注安全基线 |
| [06 AI 协作与编码准则](06-AI协作与编码准则.md) | §11 §12 | **任何任务**：编码准则、自主权分级、交付流程 |

## 架构规范按需加载

AGENTS.md 路由只把 agent 引到本目录六份正文；横切与专项规范在 `doc/architecture/`，
**触发才读、不整读**（判据同样是加载时机，与拆分原则一致）。架构文档全景索引见
[`doc/architecture/README.md`](../architecture/README.md)，本表只回答「什么情况读哪份」。

- **主干**（已由 AGENTS.md 路由表与 03 §5.5 / 06 §12.2 按修改路径挂载，此处不重复）：
  `Bone-DDD-最终实践方案.md`、`Bone-API-规范.md`、`数据库开发规范.md`、`BONE-总体架构设计方案.md`、`bone-前端架构.md`、`openapi/`
- **横切与专项**（触发才读）：

| 触发场景 | 按需加载 |
|---|---|
| 查/登记错误码、接口报错对账 | `doc/architecture/Bone-错误码登记.md` |
| 日志字段、脱敏、日志级别约定 | `doc/architecture/Bone-日志规范.md` |
| 指标、链路追踪、健康检查 | `doc/architecture/Bone-可观测性规范.md` |
| 新增配置项、环境变量、profile | `doc/architecture/Bone-配置与环境规范.md`（+ `config/env/README.md`） |
| 安全编码：鉴权、注入、密钥、越权 | `doc/architecture/Bone-安全开发规范.md` |
| 租户隔离、`tenant_id`、行级策略 | `doc/architecture/Bone-多租户规范.md`（方案见 `多租户数据隔离方案设计.md`、`design-single-repository-tenant-scope.md`） |
| Redis / 本地缓存、缓存键与失效 | `doc/architecture/Bone-缓存规范.md` |
| 领域事件、消息 envelope、outbox | `doc/architecture/Bone-消息与事件规范.md` |
| IAM 审计日志与系统日志写哪边 | `doc/architecture/module-boundary-iam-system.md` |
| 测试分层、测试金字塔、自动化策略 | `doc/architecture/Bone-测试策略.md` |
| 版本号、发布与回滚流程 | `doc/architecture/Bone-版本与发布规范.md` |
| 国际化文案、语言包 | `doc/architecture/Bone-国际化规范.md` |
| 模块合规文档模板（Docs-as-Code） | `doc/architecture/Docs-as-Code-模块合规模板.md`（06 §11 已挂） |
| 追溯某条约束的决策依据 | `doc/architecture/adr/`（编号索引见架构 README） |

> 单一真源：本表只做**加载指针**，不复制任何规范内容；规范正文只在其所在文档维护。

## 读法与约定

- **章节编号沿用拆分前**：拆分文档里的 `§5.2`、`§11.12` 就是原 `AGENTS.md` 的编号，历史引用（ADR、模块详设、技能说明）按编号仍可定位到对应文件。
- **路径写法**：正文里的 `` `doc/…` ``、`` `scripts/…` `` 反引号路径一律**相对仓库根**；Markdown 链接则相对本文件。
- **单一真源**：门禁与 HC 状态的唯一真源是 [Bone-DDD-最终实践方案.md §G-1.7](<../architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints>)。本目录任何文件都**不得复制 HC 表或门禁状态**——需要时只写编号 + 指针。
- **本目录属 `§12.3` L4 范围**：AI 不得自行修改 §12 相关内容（含本目录的协作条款），需架构师本人执行或明确授权。

## 13. 参考索引

| 文件/目录 | 内容 |
|---|---|
| `README.md` | 项目营销概览、快速开始 |
| [`doc/wiki/`](../wiki/README.md) | 上手型知识库：模块结构、构建运行、数据与部署、工程规范 |
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
| [`scripts/e2e/README.md`](../../scripts/e2e/README.md) | 端到端测试能力现场手册（e2e_lib 公共层 / 脚本清单 / 环境变量 / 新增规范） |
