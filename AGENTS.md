# Bone — AI Agent 项目指南

> **本文件是薄引用入口**。完整说明已按「加载时机」拆分到 [`doc/agents/`](doc/agents/README.md)——按需加载。

## 一、不可违反（违反即阻断）

1. **分层依赖**：`adapter → application → domain ← infrastructure`；`domain` 零框架依赖，`application` 不直连 `infrastructure`。
2. **持久化唯一**：只用 `bone-metadata-sdk`（`@EnableSqlRepositories`）；禁 MyBatis-Plus / JPA / Hibernate / MyBatis（HC-001 · HC-006）。
3. **入站边界**：默认语义化 `*ApplicationService`（ADR-0028）；一个用例只选一种构件，禁 Handler 与 ApplicationService 套娃；Controller 禁直注 `domain/service`、`domain/repository`、infrastructure。
4. **统一响应**：Controller 返回 `ApiResponse<T>` / `PageResult<T>`，不裸返领域对象（HC-003）。
5. **租户与审计**：新实体继承 `TenantAbstractEntity` 或 `AbstractEntity`，不漏 `tenantId` 与审计字段（HC-008）。
6. **格式**：改 Java 后执行 `mvn spotless:apply`。
7. **并发控制**：业务/应用层**禁止 `SELECT ... FOR UPDATE`**（悲观锁——易死锁、且行锁仅在单 MySQL 实例有效，多副本部署下完全失效）。聚合并发默认用 `bone-metadata-sdk` 原生 `@Version` 乐观锁（ADR-0031 / `数据库开发规范 §1`）；高冲突场景用唯一约束 / 条件更新 / 单写者串行队列，不得手写 `FOR UPDATE`。SDK 内部 `ColumnAllocator`（仅元数据列分配用 `FOR UPDATE SKIP LOCKED`）为框架自有能力，不在禁止范围。

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

> 横切/专项规范（错误码、日志、可观测、配置、安全、多租户、缓存、消息事件、测试策略、发布、国际化等）存放在 `doc/architecture/`，**触发才读、不整读**，加载路由见 [doc/agents/README.md · 架构规范按需加载](doc/agents/README.md#架构规范按需加载)。

## 三、自主权与交付流程

- **L0** 格式化/注释无需审查 ｜ **L1** 单测/DTO 须过 `./scripts/check.sh` ｜ **L2** 业务逻辑需双人 Review ｜ **L3** DDL、删码、依赖、CI 脚本需架构师审批 ｜ **L4** 生产库迁移、密钥证书、发布打 tag、**修改 §12 内容** —— 完全禁止 AI 执行。
- **分支纪律**：AI **禁止自行创建、切换、重命名、删除分支**（`git branch`/`checkout -b`/`switch`/`push --delete` 等一切改动 refs/heads 的写操作）。默认在仓库当前 checkout 的分支上工作；仅在用户**明确指示**分支名时才允许切分支或建分支，且不得用「切分支」绕开构建/门禁问题。任何 git 写操作前必须先 `git branch --show-current` 确认落点；commit 后用 `git branch --contains <sha>` 验证提交落在预期分支。
- 流程：P1 需求 → P2 加载上下文 → P3 契约 → P4 实现 → P5 本地自检（`./scripts/check.sh`）→ P6 PR 门禁 → P7 Review → P8 合并。详见 [06 §12.5](doc/agents/06-AI协作与编码准则.md)。
- 提交前逐项过 [06 §11.13 自检清单](doc/agents/06-AI协作与编码准则.md)。

## 四、门禁状态只有一处真源

HC-001～HC-008 的定义与**实测状态**见 [Bone-DDD-最终实践方案 §G-1.7](<doc/architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints>)。**本文件、`doc/agents/`、`doc/wiki/` 与 `doc/agenticx/` 一律不复制 HC 表或门禁状态**，引用只写编号；Planned / Manual 不得写成 Active；目标态门禁须显式标注「目标态/规划中」，不得写成已由 CI 拦截。

<comet-ambient-resume>
<!-- Managed by Comet. Edits inside this block may be replaced by comet init/update. -->
<!-- Contract: comet.resume_probe.v2 -->

## Comet Ambient Resume

在这个仓库中，开始处理需要改动或调查的任务前，如果可能存在活跃 Comet workflow，把当前用户请求传入只读探针：`comet resume-probe . --stdin --json`。

- 如果用户通过宿主明确调用任意 Comet Skill（例如 `@comet`、`/comet`、`@comet-native` 或 `/comet-hotfix`），显式调用优先于本恢复协议；不要运行 resume probe，直接进入被调用的 Skill。
- 如果用户通过宿主明确调用的是非 Comet 的 Skill 或斜杠命令，任务意图已由该调用明确：不要运行 resume probe，直接执行该 Skill。
- 如果你正在 Comet 流程内（包括正在等待用户回复你在流程中提出的问题），不要运行 resume probe；把这类回复（例如方案/选项选择）当作当前 change 的继续，直接按用户的选择推进。
- 只信任返回的 `workflow`、`skill` 和 `entrySource`；它们只由项目配置或无配置兼容回退决定。不得扫描或切换另一套 workflow。
- 如果 probe 返回 `auto_resume`，简短说明选中的 active change，并进入 `nextCommand` 指向的永久入口。不要把状态命令当作恢复入口直接推进。
- 如果 probe 返回 `ask_user`，只问一个简短问题并等待用户回复。
- 如果当前请求未明确调用 Comet Skill，且 probe 返回 `out_of_scope` 或 `none`，不要进入 Comet workflow。
- `out_of_scope` 或 `none` 只表示不要因为这个新请求进入 Comet workflow；它绝不表示要暂停或退出一个已在进行的 Comet 流程。
- 如果配置或状态无效且没有 `nextCommand`，停止并报告原因；不要猜测另一个 workflow。
- 不能只因为存在 active change 就把无关任务挂到该 change。Native 的未提交改动由 Native 入口检查，不由探针自动归因。
</comet-ambient-resume>
