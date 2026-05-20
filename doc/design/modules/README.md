# 模块详细设计索引（`doc/design/modules`）

本目录为 **控制台、元数据、主数据、集成、扩展、IAM、系统管理、Studio Generator、SmartMeta 引擎** 等模块的详细设计；与 `doc/prd`、`doc/architecture` 分工如下：

| 层级 | 目录 | 职责 |
|------|------|------|
| 产品 | [`doc/prd/`](../prd/) | 做什么、优先级、验收口径（主 PRD） |
| 平台架构 | [`doc/architecture/`](../architecture/) | 总体架构、DDD 门禁、前端、数据库规范 |
| 模块详设 | 本目录 | 单模块功能、数据模型、接口与工程结构（**规划 + As-Is 对照**） |

**真源优先级**：默认端口、可运行模块边界以 [**doc/wiki/03-本地开发与构建.md**](../../wiki/03-本地开发与构建.md)「常见服务端口」及各模块 **`application.yml`** 为准；详设中的端口/API 若为历史规划示例，文中会标注「规划示例」。

**元数据能力族**（sdk / server / engine / generator）：统一以 [**元数据能力-实现映射与竞品对照.md**](./元数据能力-实现映射与竞品对照.md) 为定义与协作真源（含 **§1.1 两类 Field**、**§8 与 masterdata 边界**）；详设 §2、PRD §4.4、BONE X 文首表与之对齐。

### 详设阅读约定

- **DDL 唯一真源**：根目录 [`bone-init.sql`](../../../bone-init.sql) + [数据库开发规范.md](../../architecture/数据库开发规范.md)。
- **无增量迁移**：改表即改 init，开发库 `DROP DATABASE` 后重建。
- **Hybrid**：部分能力仍在实现中（如 Generator 持久化），以各文 §0 与 P0 看板为准。
- **审查清单**：[`doc/文档治理-三目录审查子任务.md`](../../文档治理-三目录审查子任务.md)。
- **最近审查**：
  - 2026-05-17 第一/二轮：As-Is/Vision 分层、模块路径与端口真源、§5 与 Controller 1:1 对照。
  - 2026-05-20 第三轮（业界最佳实践）：每模块文首补 **HTTP 真源 + 错误码前缀**；控制台/集成新增 **SLI/SLO 契约**；集成/主数据/元数据新增 **§5.0/§5.A 横切约定**（成功信封 / `ProblemDetail` / **`Idempotency-Key`** / **`If-Match`** 乐观锁 / LRO 202 + `Location`）；主数据补 **数据血缘 + catalog 同步 ADR 链**；IAM 补 **安全栈真源**（`JwtAuthenticationFilter` / `SecurityConfig` / `TenantContext`）；Studio Generator / 元数据 / 控制台补 **术语表**（Mode A/B 防混淆）。
  - 2026-05-20 第四轮（结构瘦身 · DRY）：
    - **§6/§7/§8/§9 通用 boilerplate** 收敛至架构文档（[`Bone-API-规范`](../../architecture/Bone-API-规范.md)、[`Bone-多租户规范`](../../architecture/Bone-多租户规范.md)、[`Bone-测试策略`](../../architecture/Bone-测试策略.md)、[`Bone-可观测性规范`](../../architecture/Bone-可观测性规范.md)），各模块仅保留**特有项**；
    - **§10 工程结构**：模块 1/2/3/4/6 删除 200+ 行虚构 directory tree 与"命名规范"逐类描述，统一指向 [`Bone-DDD-最终实践方案 §14`](../../architecture/Bone-DDD-最终实践方案.md) 与 `bone-blueprint`；
    - **虚构事实修正**：模块 1 `console-service:8081`（与 IAM 冲突）/ 模块 2 `metadata_db`+`metadata-service` / 模块 7 `bone-chart` Helm 目录 / 模块 7 Prometheus/ELK 全栈 → 全部明确为 **[Vision]**；
    - **术语统一**：模块 8 §8 `Mode A/B` 改为「轻量用例/编排用例」，避免与 README 产品级 **模式 A/B** 混淆；前端 E2E 工具从 `Selenium + TestNG` 改为 **Playwright**；测试覆盖率门禁全部指向 [`Bone-测试策略.md`](../../architecture/Bone-测试策略.md)。

## 模块文档

| 编号 | 文档 | 仓库模块（As-Is） | 默认端口（开发） | PRD |
|------|------|-------------------|------------------|-----|
| 1 | [控制台](./1.%20控制台与仪表盘模块详细设计方案.md) | **规划中**（无 `bone-console`；能力分散在 Shell + 各微应用） | — | §4.3 |
| 2 | [元数据](./2.%20元数据管理模块详细设计方案.md) · [**三模块定义/竞品**](./元数据能力-实现映射与竞品对照.md) | sdk + server + engine；UI：`bone-metadata-app` **3004**、`bone-generator-app` **3009**；生成见 §8 | **9001**（server） | §4.4 |
| 3 | [主数据](./3.%20主数据管理模块详细设计方案.md) | `bone-platform/bone-masterdata` | **8080** | §4.5 |
| 4 | [集成](./4.%20集成管理模块详细设计方案.md) | `bone-platform/bone-integration` | **8085** | §4.7 |
| 5 | [扩展](./5.%20扩展管理模块详细设计方案.md)（**v2.2** As-Is/Vision 分层） | `bone-extension-engine`、`bone-extension-studio` | Studio **8088**（`BONE_EXTENSION_STUDIO_PORT`）；`bone-extension-app` **3008** | §4.6 |
| 6 | [IAM](./6.%20IAM账号权限管理模块详细设计方案.md) | `bone-platform/bone-iam` | **8081** | §4.8 |
| 7 | [系统管理](./7.%20系统管理模块详细设计方案.md) | `bone-platform/bone-system` | **8083** | §4.9 |
| 8 | [Studio Generator](./8.Studio%20Generator%20详细设计方案.md) | `bone-engine/studio-generator` 等 | **8085**（与平台 integration 默认冲突时注意） | §4 + Studio |
| 9 | [SmartMeta](./9.%20SmartMeta%20引擎模块技术说明.md) | `bone-engine/bone-metadata-engine` | 随宿主应用 | 引擎能力 |

**跨模块**： [BONE X Studio 详细设计](../BONE-X-Studio-详细设计方案.md)

## 工程债与实现状态

- **P0 看板**：[`doc/wiki/07-P0-TODO看板.md`](../../wiki/07-P0-TODO看板.md)（扩展等模块 §0 与 EXT-* 项同步维护）
- **架构门禁**：[`doc/architecture/Bone-DDD-最终实践方案.md`](../../architecture/Bone-DDD-最终实践方案.md)
