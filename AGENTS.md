# Bone — AI Agent 项目指南

> **本文件是薄引用入口**。完整说明已按「加载时机」拆分到 [`doc/agents/`](doc/agents/README.md)——按需加载，不要整体灌入。
> 原单文件版（581 行）已于 2026-09-17 拆分，备份文件已删除；**需要旧版请查 Git 历史**。拆分文档**沿用原章节编号**（`§5.2`、`§11.12` 可直接定位）。

## 一、不可违反（违反即阻断）

1. **分层依赖**：`adapter → application → domain ← infrastructure`；`domain` 零框架依赖，`application` 不直连 `infrastructure`。
2. **持久化唯一**：只用 `bone-metadata-sdk`（`@EnableSqlRepositories`）；禁 MyBatis-Plus / JPA / Hibernate / MyBatis（HC-001 · HC-006）。
3. **入站边界**：默认语义化 `*ApplicationService`（ADR-0028）；一个用例只选一种构件，禁 Handler 与 ApplicationService 套娃；Controller 禁直注 `domain/service`、`domain/repository`、infrastructure。
4. **统一响应**：Controller 返回 `ApiResponse<T>` / `PageResult<T>`，不裸返领域对象（HC-003）。
5. **租户与审计**：新实体继承 `TenantAbstractEntity` 或 `AbstractEntity`，不漏 `tenantId` 与审计字段（HC-008）。
6. **格式**：改 Java 后执行 `mvn spotless:apply`。

## 二、按修改路径加载上下文

| 你要改的 | 先读 |
|---|---|
| `**/domain/**`、`**/application/**` | [03 架构分层规范](doc/agents/03-架构分层规范.md) |
| `**/adapter/web/**` | 同上 + `doc/architecture/Bone-API-规范.md` |
| `**/infrastructure/**` | [05 数据库与安全](doc/agents/05-数据库与安全.md) |
| 测试 / 质量门禁 | [04 测试与代码质量](doc/agents/04-测试与代码质量.md) |
| 构建 / 部署 / 端口 | [02 构建·运行·部署](doc/agents/02-构建运行与部署.md) |
| 模块与依赖定位 | [01 项目概览与模块结构](doc/agents/01-项目概览与模块结构.md) |
| **任何任务** | [06 AI 协作与编码准则](doc/agents/06-AI协作与编码准则.md) |

## 三、自主权与交付流程

- **L0** 格式化/注释无需审查 ｜ **L1** 单测/DTO 须过 `./scripts/check.sh` ｜ **L2** 业务逻辑需双人 Review ｜ **L3** DDL、删码、依赖、CI 脚本需架构师审批 ｜ **L4** 生产库迁移、密钥证书、发布打 tag、**修改 §12 内容** —— 完全禁止 AI 执行。
- 流程：P1 需求 → P2 加载上下文 → P3 契约 → P4 实现 → P5 本地自检（`./scripts/check.sh`）→ P6 PR 门禁 → P7 Review → P8 合并。详见 [06 §12.5](doc/agents/06-AI协作与编码准则.md)。
- 提交前逐项过 [06 §11.13 自检清单](doc/agents/06-AI协作与编码准则.md)。

## 四、门禁状态只有一处真源

HC-001～HC-008 的定义与**实测状态**见 [Bone-DDD-最终实践方案 §G-1.7](<doc/architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints>)。**本文件、`doc/agents/`、`doc/wiki/` 与 `doc/agenticx/` 一律不复制 HC 表或门禁状态**，引用只写编号；Planned / Manual 不得写成 Active；目标态门禁须显式标注「目标态/规划中」，不得写成已由 CI 拦截。
