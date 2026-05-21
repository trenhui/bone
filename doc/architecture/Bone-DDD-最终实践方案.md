# Bone 领域驱动设计（DDD）统一实践方案

> **唯一权威**：本文档置于 `doc/architecture/`，为 Bone 仓库内 **DDD 与分层门禁** 的唯一权威规范（与同目录总体架构、数据库规范并列维护）。  
> **结构**：**第一部分**为与具体框架解耦的**业界共识与架构原则（北向星）**；**第二部分**为 **Bone 平台工程落地**（包结构、铁律、SDK、极简策略）。修订时先对齐原则，再调整落地条文。  
> **定位说明**：本文是 **Bone 仓库内 DDD 与分层门禁的权威规范**，对齐主流 DDD/整洁架构共识，并含 **D1 元数据注解** 等工程折中；**非**全行业唯一标准，复杂域请结合 ADR 裁剪。  
> **关联文档**：[BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md)（平台总体架构、NFR、安全与数据一致性策略，与本方案互补）；[README.md](./README.md) 为架构文档索引；模块详设见 [doc/design/modules/README.md](../design/modules/README.md)。  
> **版本**：3.6 | **日期**：2026-05-21
>
> **v3.6 关键变更（规范冻结 — 减少反复返工）**：
> - 新增 [§0 规范稳定性契约](#0-规范稳定性契约)：区分**新代码 Profile**与**存量 Grandfather**；破坏性变更须 ADR + 下游文档同步清单。
> - 补齐 [§12.1.1 SDK 豁免](#1211-sdk--框架库豁免p0-4567)、[§14.3.1 application/service](#1431-applicationservice-白名单)、[§14.5 Store→Repository](#145-store--repository-迁移)、[§17.2 持久化对象决策树](#172-持久化对象决策树)。
> - 附录 B 升为 **v3.6 执行矩阵**（批次 / Owner / 完成定义）；`bone-blueprint` 标注为**迁移中参考**，非终态样板。
> - 共享门禁：`bone-architecture-test` 模块 + ArchUnit **Freezing**（存量登记、禁止新增违规）。
>
> **v3.5 关键变更（适用范围按性质判定）**：
> - 新增 [§14.4 模块适用性](#144-模块适用性按性质而非物理位置)，**适用范围按「模块性质」判定**（应用 / SDK / 基础设施），**不再以** `bone-platform/` / `bone-engine/` 物理目录划分。
> - `bone-engine/bone-extension-engine/bone-extension-studio`、`bone-engine/studio-generator` 等**控制面 BFF**明确归类为**应用模块**，完全适用 §12 P0 全 7 条 + §14.1/14.3 + §21 ArchUnit 规则集。
> - §12.1 标题修正为「全局七条」（与 v3.4 P0-7 一致），并显式标注**仅对应用模块强制**；SDK 库豁免 P0-4/5/6/7。
> - 附录 B.2 模块符合度矩阵按 §14.4 三类（应用 / 引擎 SDK / 基础设施）重组，列出每个模块的物理位置、`*UseCase` 存量、自造 UseCase 类型、`application/service` 存量、端口命名 gap。
>
> **v3.4 关键变更（去 UseCase）**：
> - **禁止**新代码引入 `application/usecase` 包；Controller 必须**直接注入** `*CommandHandler` / `*QueryHandler`。
> - `bone-core` 的 `UseCaseExecutor` / `@UseCase` 注解标 **`@Deprecated`（计划 2026-12-31 删除）**；存量 `*UseCase` 类按 §22 迁移路径下沉为 Handler 调用。
> - §20 编排相关章节同步收紧，**禁止「UseCase 门面」**；AI/Flow 能力发现请走 `@Capability` 或独立 capability 注册表，不复用 UseCase 名义。
> - 详情见 [§14.3 应用层结构（强制）](#143-应用层结构强制)、[§22 演进路径](#22-演进建议三阶段)、[附录 B 废止登记](#附录-b废止登记)。

---

# 第一部分　业界共识与领域架构原则（北向星）

本部分描述在**不绑定** Bone 实现细节的前提下，与主流 DDD、整洁架构、有界系统实践一致的**理想形态**，用于架构评审、演进目标与差距分析。

---

## 1. 原则适用范围（何时值得系统化 DDD）

- 业务复杂度值得用**领域模型**长期表达与演进（纯 CRUD 且无演进压力时可弱化）。  
- 团队愿意为**边界、通用语言与一致性**投入建模与治理成本。  
- 技术中立：持久化、消息、RPC、UI 均为**可替换实现**，不应用实现细节绑架领域语义。

---

## 2. 战略设计（应优先于战术）

### 2.1 限界上下文（Bounded Context）

- 每个上下文有**明确的业务能力范围**与**独立演进的通用语言**。  
- **数据与写入规则的所有权**清晰：默认不与其他上下文共享「写模型」。  
- 上下文规模以**团队可认知、可测试、可发布**为上限；过大则拆分。

### 2.2 通用语言（Ubiquitous Language）

- 产品、领域专家与研发使用**同一套词汇**；聚合、命令、事件、API **体现**该语言。  
- 禁止「技术词冒充业务词」；禁止边界两侧**同名不同义**而不加限定。

### 2.3 上下文映射（Context Map）

- 与相邻上下文的关系**显式标注**（合作、客户–供应方、防腐层、开放主机服务、发布语言等）。  
- 跨边界集成**不依赖**对方内部模型；经 **API / 事件契约 / 防腐层** 完成。

### 2.4 子域类型（推荐）

- 区分**核心域 / 支撑域 / 通用域**；创新与资源优先投向核心域；通用域倾向采购或标准化。

---

## 3. 战术设计（领域模型）

### 3.1 聚合（Aggregate）

- 聚合是**一致性边界**与**不变量**的载体；**一事务内**宜只提交**一个**聚合的变更（跨聚合用最终一致）。  
- 聚合尽量**小**；聚合间仅通过 **ID** 引用，不持有对方对象图。  
- 规则与状态迁移在**聚合根或领域服务**中表达，避免贫血模型与外层「隐式领域逻辑」。

### 3.2 实体与值对象

- **实体**：有稳定标识，关注生命周期与连续性。  
- **值对象**：无独立标识、不可变（或等价约束）、按值比较。

### 3.3 领域事件

- 表达**已发生的领域事实**（过去式、不可变载荷）；可用于上下文内协作或作为跨上下文**发布语言**的载体之一。  
- **领域事件**与**集成事件**职责应区分：前者偏领域语义，后者偏系统间契约。

### 3.4 仓储（Repository）

- 面向**聚合根**的持久化抽象，语义接近「集合」：**按 ID 加载、持久化整体变更**；**不承担**通用报表与任意条件查询。

---

## 4. 架构风格（六边形 / 整洁架构）

- **依赖方向**恒指向领域：外层依赖内层，**领域不依赖**框架与 IO。  
- **入站端口**：应用层 Handler / Orchestrator API（**禁止**以 `*UseCase` 作为入站端口，见第二部分 §14.3）；**出站端口**：仓储、消息、第三方抽象；**适配器**实现技术细节。

### 4.1 领域纯净度（理想目标）

- **理想**：领域为纯模型 + 领域服务，**不依赖**具体数据库、ORM、Web、消息 SDK；映射在**基础设施或应用边界**完成。  
- **工程折中**：Bone 允许 **D1**（见第二部分 §17）：在领域类型上使用 **bone-metadata-sdk** 元数据注解，须在评审中**显式承认**与纯 POJO 理想之间的差距及收益（元数据驱动、少样板）。

---

## 5. CQRS 与一致性（概念 + 物理）

### 5.1 概念层（默认建议）

- **读写关注点分离**：写服务聚合不变量；读服务查询、报表与组合展示。  
- **不必**默认独立读库或事件溯源；物理拆分**按需演进**。

### 5.2 物理层（按需）

- 读写性能、模型形状或发布节奏**显著分叉**时，引入**独立读模型**（物化视图、投影、专用存储等）。  
- 跨聚合、跨上下文的**强一致读**应为**例外**，须显式设计与成本说明。

### 5.3 事务边界

- **聚合内**：强一致事务与不变量。  
- **跨聚合 / 跨上下文**：默认**最终一致**；用领域事件、集成事件、重试、幂等、补偿等表达可接受的滞后与失败语义。

### 5.4 跨上下文一致性（与平台架构对齐）

跨聚合、跨限界上下文的**强一致**应为例外；默认采用 **最终一致**（领域事件、集成事件、Outbox、Saga/补偿等）。具体模式选择与 **幂等、重试、死信** 要求见 **[总体架构设计方案](./BONE-总体架构设计方案.md)** 第二十七部分等章节，本文不重复展开。

---

## 6. 集成与防腐（理想实践）

- 外部模型与错误语义**不穿透**核心领域；经 **ACL** 转为内部通用语言。  
- **开放主机服务 / 发布语言**（版本化 API 或事件 schema）优于「共享数据库集成」。

---

## 7. 可观测与质量属性

- **可测试性**：核心领域规则宜能在无容器、无 DB 的测试中验证。  
- **可观测性**：关键用例与跨边界调用具备追踪、指标与日志策略。  
- **安全与隐私**：边界与数据分级在架构层可见。

---

## 8. 演进与组织

- **康威定律**：上下文边界与团队边界、发布单元尽量对齐。  
- **演进式架构**：契约测试、架构适应度函数（如依赖规则测试）防止非计划腐化。  
- **废弃策略**：对外 API 与事件显式版本与弃用周期。

---

## 9. 原则小结（最小共识）

| 维度 | 应做到 |
|------|--------|
| 战略 | 有界上下文 + 通用语言 + 上下文映射 + 数据所有权 |
| 战术 | 小聚合、充血模型、仓储服务聚合、领域事件表事实 |
| 架构 | 依赖向内、端口–适配器、领域与技术解耦 |
| CQRS | 概念上读写分离；物理拆分与强一致读按需 |
| 一致性 | 单聚合事务内强一致；跨边界默认最终一致（见 §5.3～§5.4） |
| 集成 | 防腐与发布语言优于共享写库 |
| 演进 | 团队–边界对齐 + 契约与架构守护 |

---

# 第二部分　Bone 平台工程落地

本部分规定 **Bone** 仓库内模块的**可执行约束**与推荐结构。**价值排序**：以**第一部分**为演进北向星；**交付门禁**以**第二部分**为准。二者常规不互斥（如纯 POJO 理想与 **D1** 元数据注解的取舍，已在第一部分 §4.1 与第二部分 §17 显式衔接）；若仍存张力，在评审中记录取舍理由。

---

## 0. 规范稳定性契约

> **目的**：避免「规范周更 → 应用模块反复改结构」。v3.6 起，下列条款**冻结**；后续仅允许**收缩存量违规**或**追加 ADR 批准的例外**，不得再引入第三套应用层形态。

### 0.1 新代码 Profile（强制，PR 拦截）

适用于 [§14.4](#144-模块适用性按性质而非物理位置) 认定的**应用 / 控制面 BFF** 模块：

| 维度 | 要求 |
|------|------|
| 分层 | §14.1 标准树（或 §14.2 极简树） |
| 应用层 | 仅 `command` / `query` / `event` / `integration` / §14.3.1 白名单内的 `service` |
| 入站 | Controller → `*CommandHandler` / `*QueryHandler`（或 ADR 批准的 `*Orchestrator`） |
| 禁止 | `application/usecase/**`、`*UseCase`、`com.bone.core.usecase.*`、自造 `@UseCase` 注解 |
| 命名 | 命令后缀统一 `*Command`（**禁止**新代码 `*Cmd`）；端口统一 `*Repository`（**禁止**新代码 `domain.store.*Store`） |
| 门禁 | §12 P0 + `bone-architecture-test` ArchUnit（含 Freezing，见 §21） |

### 0.2 存量 Grandfather（登记制，只减不增）

- 附录 [B.2](#b2-模块符合度与迁移矩阵v36-执行) 列出的 `*UseCase`、`domain/store`、顶层 `controller/` 等：**不得新增类/包**；删除一项即从 Freezing 基线中移除对应条目。
- **截止**：`*UseCase` 与 `bone-core.usecase.*` 依赖在 **2026-12-31** 前清零（见 §22.1）。
- **例外扩张**：仅能通过 ADR（含模块 Owner 签字 + 回滚方案）。

### 0.3 破坏性变更流程

1. 在 `doc/architecture/adr/` 新增 ADR（动机、影响模块、迁移步骤、回滚）。  
2. 同步更新本方案版本号与「关键变更」摘要。  
3. **必须**同步的下游文档（任一项涉及则更新）：

| 文档 | 路径 |
|------|------|
| 项目 Agent 指南 | `AGENTS.md` |
| 模块详设（按影响面） | `doc/design/modules/*.md` |
| 蓝图 / 生成器 | `bone-blueprint`、`studio-generator` 模板与 README |
| API 规范（若影响异常/响应） | `doc/architecture/Bone-API-规范.md` |

4. 先合并 **ArchUnit / CI** 与文档，再启动业务代码批量迁移（避免「代码已改、规范又变」）。

### 0.4 模块性质判定（冻结规则）

**仅**按 [§14.4](#144-模块适用性按性质而非物理位置) 三张表增删模块名；**不再**以 `bone-platform/` vs `bone-engine/` 目录推断适用性。

---

## 10. 目标（Bone）

| 层级 | 目标 |
|------|------|
| **战略** | 限界上下文清晰、通用语言一致、跨边界集成可治理 |
| **战术** | 依赖可证明（ArchUnit）、读写分离、聚合不变量集中、反贫血 |
| **工程** | 与 **bone-core**、**bone-metadata-sdk**、多租户、扩展点一致 |

---

## 11. 与第一部分对齐（Bone 落点摘要）

| 原则维度 | Bone 落点 |
|----------|-----------|
| 边界与数据所有权 | 上下文 → 模块或 `domain.{context}`；跨边界经 ACL / 契约 API / 事件 |
| 通用语言 | 术语表 + 命令/事件/REST 命名一致 |
| 防腐 | `domain.gateway`（或等价端口包），`infrastructure` 实现；禁止应用层直连 Feign **实现类型** |
| 一致性 | 一事务一改一个聚合根；跨聚合默认最终一致 |
| 读写分离 | 写走聚合 + 仓储；读走 `Criteria` / `QueryBuilder` + 投影 DTO |

---

## 12. 铁律（P0 必守）

### 12.1 P0 — 全局七条（CI / ArchUnit 建议硬门禁）

> **适用范围**：本节 P0 七条**仅强制适用于「应用 / 控制面 BFF」模块**（详见 [§14.4](#144-模块适用性按性质而非物理位置)）。SDK / 框架库豁免 P0-4/5/6/7。

1. **依赖方向**：`adapter → application → domain ← infrastructure`，禁止反向依赖；**application 层禁止** import **infrastructure** 具体实现类（须经 **domain 端口** 或 **adapter 已解析的 Bean** 注入接口实现，与总体架构方案中依赖倒置一致）。  
2. **领域规则**：状态流转、不变量在 **聚合根或领域服务**；应用层只做编排与事务边界——**禁止在 Handler 内实现仅属于某一聚合的业务不变量**（可做的仅为用例级前置校验，如参数组合、权限已判定等）。  
3. **外部系统**：仅经 **端口 + infrastructure 实现**；`domain` / `application` 不依赖 Feign、HttpClient、MQ Producer **实现类型**。  
4. **写侧仓储**：`domain.repository` 只做聚合 **加载与持久化**（如 `save`、`remove`、`findById`）；**禁止**在仓储接口上增加组合条件列表/分页等查询方法。  
5. **读侧查询**：凡 **WHERE 含多个业务条件**、**分页/排序**、**Join/子查询/聚合报表** 的读路径，统一用 **`Criteria` / `QueryBuilder`**，结果映射到 `application.query.dto`（或 `projection`）；**禁止在 `domain` 包内**使用查询构建器。仅按 **主键或单一业务键** 加载聚合（如 `findById`、§18.2 白名单方法）仍走仓储，不视为本条「读侧复杂查询」。  
6. **CQRS**：CommandHandler 内 **禁止** 使用 `QueryBuilder`（特例「读己之写」须注释 + 评审）。**QueryHandler** 建议标注 **`@Transactional(readOnly = true)`**（或框架等价只读事务），且 **禁止** 调用写侧仓储修改聚合。
7. **应用层单层（v3.4）**：**禁止** 新增 `application/usecase/**` 包与 `*UseCase` 类；Controller **必须**直接注入 `*CommandHandler` / `*QueryHandler`（编排例外见 [§14.3](#143-应用层结构强制)）。**禁止** 新代码 import `com.bone.core.usecase.UseCaseExecutor` 与 `@UseCase`。

#### 12.1.1 SDK / 框架库豁免（P0-4/5/6/7）

适用于 [§14.4](#144-模块适用性按性质而非物理位置) 中的**引擎 SDK / 框架库**（无 `@RestController` 业务 API）：

| P0 条 | 应用模块 | SDK / 框架库 |
|-------|----------|----------------|
| P0-1 依赖方向 | 强制 | 强制（领域库不向 adapter/infrastructure 业务模块反向依赖） |
| P0-2 领域规则在 domain | 强制 | 强制（若有 `domain` 包） |
| P0-3 外部经端口 | 强制 | 强制（若有出站集成） |
| P0-4 写侧仓储方法收敛 | **强制** | **豁免**（SDK 自身 `Repository` SPI 除外） |
| P0-5 读侧 QueryBuilder 位置 | **强制** | **豁免** |
| P0-6 Command 禁 QueryBuilder | **强制** | **豁免**（无 CommandHandler） |
| P0-7 禁止 UseCase | **强制** | **豁免**（无 application 层）；但 `bone-core` 中 `UseCaseExecutor`/`@UseCase` 仍 **@Deprecated** 且业务模块不得新增引用 |
| §14.1 包结构 | **强制** | **豁免**（按 SPI/库习惯组织） |
| §14.3 应用层 | **强制** | **豁免** |
| §17 D0/D1/D2 | 建议 | **强制**（元数据注解白名单） |

新增 SDK 能力若需豁免上述条目，须在 `bone-framework` 或对应引擎模块 **README + ADR** 登记。

---

## 13. 战略 DDD（Bone 最小必做）

### 13.1 限界上下文

每个上下文具备：**名称**、**职责一句**、**对外契约**、**数据所有权**。

- **单体**：一上下文对应 `com.bone.{module}` 或 `domain.{context}`；禁止跨上下文直接引用对方聚合 **类型**。  
- **多部署**：一上下文一服务边界；不默认共享写库。

### 13.2 通用语言

- **L0 / 小模块**：至少在模块 README 中固定**核心术语中英对照与禁用同义词**（可视为一页纸级术语表）。  
- **L1 起**：建议维护 `doc/glossary.md` 或 Wiki，命令、事件、REST 与表字段、消息字段对齐（与 **§22 演进 L1**「术语表 + 上下文职责说明」一致；**勿与 §23 混淆**——§23 为极简交付心态，非术语表章节）。

### 13.3 上下文映射（轻量）

与邻域标注 **合作 / 客户-供应 / 防腐 / 发布语言** 之一；跨边界不经隐式共享写路径。

---

## 14. 标准包结构（推荐）

### 14.1 完整参考树

```text
com.bone.{module}/
├── adapter/
│   ├── web/
│   │   ├── controller/
│   │   ├── dto/request/
│   │   ├── dto/response/
│   │   └── assembler/
│   ├── rpc/
│   ├── mq/
│   └── schedule/
├── application/
│   ├── command/
│   │   ├── cmd/
│   │   └── handler/
│   ├── query/
│   │   ├── qry/
│   │   ├── handler/
│   │   └── dto/
│   └── integration/               # 可选：入站消息编排等
├── domain/
│   ├── {aggregate}/
│   ├── repository/
│   ├── service/
│   └── gateway/
└── infrastructure/
    ├── persistence/
    ├── gateway/
    ├── config/
    └── query/
```

### 14.2 极简树（小模块默认够用）

仅 **Web + 一聚合** 时，可只保留：`adapter/web`、`application/command` + `application/query`、`domain/{aggregate}` + `domain/repository`；无外部系统则暂不建 `gateway`。读极简单时仍建议保留 `query` 包，避免把列表查询塞回 `CommandHandler`。

**分组原则**：按 **业务/聚合** 分包，不按「全 entities / 全 vo」横切。

### 14.3 应用层结构（强制 — v3.4 去 UseCase）

**唯一允许的应用层包**：

```text
application/
├── command/{cmd,handler}/
├── query/{qry,handler,dto}/
├── event/                          # 应用事件（可选）
└── integration/                    # 入站消息编排（可选；归 application 而非 adapter）
```

**禁止**（PR/ArchUnit 拦截）：

| 禁项 | 替代方案 |
|------|----------|
| `application/usecase/**`（任何子目录，含 `standard/`、`simple/`） | 删除该层；Controller 直接注入对应 `*CommandHandler` / `*QueryHandler` |
| 类名 `*UseCase` | 改名为 `*CommandHandler` / `*QueryHandler`；若仅是命名差异，**合并**到现有 Handler，不留空壳 |
| Controller 注入 `*UseCase` | Controller 注入 Handler；事务边界仍在 Handler 上 |
| `com.bone.core.usecase.UseCaseExecutor` / `@UseCase` 新引用 | 已 `@Deprecated`；新代码不得 import |
| 「UseCase 仅 delegate 到 Handler」的薄门面 | 反模式；本来就属于规范禁止的「无业务规则的空壳层」 |

**为什么去 UseCase**（决策依据）：

1. **职责重复**：在 Bone 既有 §15 应用层职责（编排 + 事务边界）的前提下，`*UseCase → *Handler` 的两层调用 95% 是 1:1 delegate，无新增语义。
2. **认知成本**：Controller → UseCase → Handler → Domain 比 Controller → Handler → Domain 多一层，对外部贡献者与生成器都是负担。
3. **与 §14 / §24 一致**：原 §14.1 树本就只有 `command/query`，UseCase 是事实上的「第二套形态」；规范化 = 收敛到一套。
4. **AI / Flow 能力发现**不依赖 UseCase 类型：用 `@Capability` 或独立 capability 注册表（§20）即可，不应复用「UseCase」名义混淆 DDD 用例与 AI 能力两层语义。

**例外**（须 ADR）：

- 仅当出现「跨多个聚合 Handler 编排 + 无法在单一 Handler 表达事务/补偿」时，可新增 **`application/orchestration/*Orchestrator`**（注意：**不叫 UseCase**），并在 ADR 写明：编排步骤、补偿策略、是否引入 Saga。

> 与 [§20 Flow / AI 编排](#20-flow--ai-编排可选) 串联：能力声明走 `@Capability`，业务执行走 Handler（必要时 Orchestrator）。

#### 14.3.1 `application/service` 白名单

§14.3 未列出 `application/service/`，但允许多个 Handler **共享**非门面型逻辑。新代码仅允许下列用途（须类级 Javadoc 标明类别）：

| 类别 | 允许 | 禁止 |
|------|------|------|
| **S1 绑定/协调** | 多 Handler 复用的权限绑定、关联表维护（如 `AccountRoleBindingService`） | 仅 `handler.handle(cmd)` 一行 delegate |
| **S2 流程运行时** | 技术编排引擎封装（如 `FlowExecutionService`、`FlowRuntime`），无 HTTP 入站 | 替代 CommandHandler 承载业务用例 |
| **S3 缓存/失效** | 横切缓存失效、权限快照刷新（如 `AuthorityCacheEvictionService`） | 承载聚合不变量 |

- **事务**：写事务边界仍在 `*CommandHandler`（或 §14.3 的 `*Orchestrator`）；`application/service` 方法默认**不加** `@Transactional`，除非 S2 明确文档化。  
- **依赖**：可依赖 `domain` 端口与**其他** Handler；**禁止**依赖 `infrastructure` 实现类。  
- **命名**：`*Service`；**禁止** `*UseCase`、`*Manager`（除非遗留 ADR）。

#### 14.4 模块适用性（按**性质**而非物理位置）

DDD 规范是否适用，**按模块性质判定**，不以 `bone-platform/` / `bone-engine/` 目录划分。

| 性质 | 判定标准 | 规范适用度 | 仓库示例 |
|------|----------|------------|----------|
| **应用 / 控制面 BFF** | 对外提供 REST API；含 Controller + 业务编排 + 持久化；服务于人类用户或前端 | **完全适用**：§12 P0 全 7 条 + §14.1/14.2/14.3 包结构 + §21 ArchUnit 7 条规则集 | `bone-platform/bone-iam`、`bone-platform/bone-masterdata`、`bone-platform/bone-integration`、`bone-platform/bone-system`、`bone-platform/bone-notification`、**`bone-engine/bone-extension-engine/bone-extension-studio`**（扩展控制台 BFF）、**`bone-engine/studio-generator`**（代码生成控制台 BFF）、`bone-blueprint`（参考样板） |
| **引擎 SDK / 框架库** | 被业务模块依赖、无 Controller、提供 SPI/注解/工具类 | **部分适用**：§12.1.1（依赖向内）+ §17 D0/D1/D2 + §19 ACL；**豁免** §14（包结构按 SPI/库习惯）、§14.3（无 application 概念）、§21 中 P0-4/5/6/7 | `bone-framework/bone-core`、`bone-framework/bone-web`、`bone-engine/bone-metadata-sdk`、`bone-engine/bone-metadata-engine`、`bone-engine/bone-extension-engine/bone-extension-sdk`、`bone-engine/bone-workflow`、`bone-sdk/*` |
| **基础设施服务** | 纯技术中转，无业务规则 | **部分适用**：依赖方向 + 配置规范；**豁免** §14、§21 业务相关规则 | `bone-platform/bone-gateway`、`bone-platform/bone-file`（若仅做对象存储中转） |

**判定通用规则**：

1. **看 Controller**：有 `@RestController` 且暴露业务路径 → **应用模块**，全规范  
2. **看依赖方向**：被业务模块 `import` → **SDK / 框架**，可豁免包结构  
3. **混合情况**：含 SDK 子模块和 Studio 子模块（如 `bone-extension-engine`）→ **按子模块**分别判定，**Studio = 应用，SDK = 引擎**

**门禁**：豁免模块的新增写 ADR 说明；任何"应用模块伪装成引擎以逃避规范"在 CR 阻断。

**示例（不容歧义）**：

- `bone-extension-engine/bone-extension-studio` = **应用**（暴露 `/api/v1/extension/*`）→ 必须 ArchitectureTest + 不得有 `*UseCase` + 端口命名 `Repository`
- `bone-extension-engine/bone-extension-sdk` = **SDK**（被业务进程 import）→ 守 D0/D1 即可
- `studio-generator` = **应用**（暴露 `/api/v1/generator/*`）→ 必须 ArchitectureTest + 删除自造 `UseCaseExecutor`/`@UseCase` + UseCase 全量迁移

#### 14.5 `Store` → `Repository` 迁移

| 阶段 | 规则 |
|------|------|
| **新代码（v3.6 起）** | 持久化端口**仅** `domain/repository/*Repository`；**禁止**新增 `domain/store/*Store` |
| **存量别名期** | 至 **2026-11-30**：允许保留 `*Store` 接口，但须在模块 README 列出清单；实现类仍在 `infrastructure/persistence` |
| **迁移动作** | 接口重命名 `FooStore` → `FooRepository`；包 `domain.store` → `domain.repository`；调用方批量替换；ArchUnit 在别名期结束后启用 `noClassesInPackage("..domain.store..")` |
| **典型模块** | `bone-extension-studio`（`ExtPointStore` 等）见附录 B **Batch D** |

---

## 15. 各层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| **adapter** | 协议转换、入参校验、路由；**Controller 直接注入** `*CommandHandler` / `*QueryHandler` | 业务规则、直接调仓储实现、注入 `*UseCase`（§14.3 已废止） |
| **application** | 用例编排、**写事务边界**（`@Transactional` 置于 CommandHandler 或等价边界）、调领域与端口 | **实现**本属聚合内的业务不变量、直接 SQL、**直接**依赖 infrastructure **实现类**；**新增 `application/usecase` 包或 `*UseCase` 类** |
| **domain** | 规则、聚合、事件、端口定义 | Spring/JPA/MyBatis/Jackson、查询构建器 |
| **infrastructure** | ACL 实现、SDK 配置、技术适配 | 领域业务规则 |

---

## 16. bone-framework 要点

### 16.1 模块职责（摘要）

| 模块 | 职责 |
|------|------|
| **bone-core** | `AggregateRoot`、`AbstractEntity`、`TenantAbstractEntity`、`ApiResponse`、`PageResult`、`TenantContext`、`DomainException`、`DistributedIdGenerator` 等 |
| **bone-metadata-sdk** | `@EnableSqlRepositories`、`Repository<T,ID>`、`QueryBuilder` / `FluentQuery`、`@Table` 等 |
| **bone-extension-sdk** | `@ExtensionPoint` / `@Extension` |
| **bone-security** | 认证、JWT、密码编码等 |

### 16.2 实体继承

```
Entity<ID>
  ↑ AbstractEntity<ID>
  ↑ TenantAbstractEntity<ID>
聚合根：AggregateRoot<ID>（常用 Long 雪花或模块内统一值对象 ID）
```

- **多租户**：`TenantAbstractEntity` + `TenantContext` 传递与清理。  
- **元数据注解**：仅 **bone-metadata-sdk**（见 §17 D1），禁止 JPA `@Entity` 等。

### 16.3 异常与 API（建议）

> **HTTP 与错误体真源**：[Bone-API-规范.md](./Bone-API-规范.md)（Problem Details、错误码台账、201/204/412、LRO、幂等等）。下表仅规定**抛出层级**；Controller **不得**手写错误 JSON，由全局异常处理器转规范响应（API 规范 §12）。

| 异常 | 层级 | 说明 |
|------|------|------|
| `DomainException` | domain | **聚合/值对象规则被违反**时抛出 |
| `BizException`、`NotFoundException` | application | 用例级失败、资源不存在（**非**领域不变量已能表达时） |
| `InvalidRequestException` | adapter | 入参协议不合法 |
| `ServiceException`、`SystemException` | infrastructure | 技术故障、下游错误封装 |

**边界**：若规则属于「仅在该聚合内成立」，优先 **DomainException**；跨聚合编排失败或应用策略拒绝可用 **BizException**（错误码与 API 规范台账一致）。统一 `ApiResponse<T>`、`PageResult<T>`（bone-core）。

---

## 17. 领域模型与纯净度 D0 / D1

| 级别 | 说明 |
|------|------|
| **D0** | 核心业务逻辑：纯 Java + `java.util`；Lombok 限 `@Getter`、私有 `@NoArgsConstructor`；禁止 `@Setter`/`@Data` |
| **D1** | 允许 **bone-metadata-sdk** 的 `@Table`、`@Id`、`@GeneratedValue` 等；禁止 Spring / Jackson / JPA。**禁止**在领域类型上新增其它持久化/Web 注解；若 SDK 升级带来新注解，须经架构评审再纳入「D1 白名单」 |
| **D2（基础设施基类例外）** | `bone-core` 的 `AbstractEntity`、`TenantAbstractEntity`、PO（`*PO.java`）等**框架基类与持久化对象**允许使用 `@Data`、`@AllArgsConstructor` 等便利注解；该例外**不向业务聚合根/实体传染**。新增此类例外须在 `bone-framework` 模块评审登记 |

**聚合**：小聚合、工厂方法、领域行为、事件过去式命名。**扩展点**：多租户/多场景用 **bone-extension-sdk**，禁止超长 `if-else`。

### 17.1 空值与 Optional 约定（与 `CLAUDE.md` 对齐）

| 场景 | 约定 |
|------|------|
| 仓储查询返回 | **禁止返回 `null`**，返回 `Optional<T>`（`findById` 等）或抛 `DomainException`（聚合不变量强制存在时） |
| Application/Adapter 内部返回 | 集合返回空集合（`List.of()`），单对象按业务语义返回 `Optional` 或显式 DTO；**禁止**用 `null` 表达「未找到」 |
| 字段 / Setter | 领域字段是否可空在工厂方法或 `Value Object` 构造中显式校验；JSON 序列化层不依赖 `null` 表达业务语义 |
| 三方/遗留 ACL | 在 `infrastructure.gateway` 适配器内**立即**把外部 `null` 转为 `Optional` 或抛错，不让 `null` 漂入应用/领域层 |

ArchUnit 建议：对 `domain.repository.*Repository` 中签名做 `returnsOptionalOrCollection()` 类断言，逐步收敛。

### 17.2 持久化对象决策树

```
需要持久化？
├─ 否 → domain 聚合/值对象（D0 或 D1）
└─ 是
   ├─ 是否为「领域模型」且走 Metadata SDK @Table？
   │  └─ 是 → 聚合根/实体放 domain/{aggregate}/（D1），禁止另建平行 PO
   └─ 否（仅基础设施映射、遗留 JPA 等）
      └─ *PO / *Impl 放 infrastructure/persistence/entity/（D2）
         - 通过 infrastructure Converter 与 domain 互转
         - 禁止 Controller/Handler 直接依赖 *PO
         - 禁止 domain 依赖 *PO
```

| 场景 | 放置 | 示例 |
|------|------|------|
| 元数据驱动聚合 | `domain` + D1 注解 | IAM `Account` |
| Studio 表映射（过渡期） | `infrastructure/.../entity/*` + Converter | `ExtStudioExtensionPoint` |
| 内存实现 | `infrastructure/persistence/InMemory*` 实现 `domain.repository` 或过渡期 `domain.store` | extension-studio |

---

## 18. ID、仓储与 CQRS（Bone）

### 18.1 ID

- **默认**：应用层生成全局 ID（推荐 `DistributedIdGenerator`），传入聚合工厂；**领域不**依赖 ID 生成器。  
- **主键策略**：**默认禁止**以数据库 **`IDENTITY` / 自增列** 作为**领域主标识**（避免与分布式 ID、跨库迁移、合并冲突处理不一致）。  
- **例外**：遗留表或强约束场景必须使用自增时，须在 **模块 README 或 ADR 登记**（表名、字段、范围、退役计划），且领域层仍不直接依赖 ID 生成技术；新表 **原则上不新开** 自增领域主键。

### 18.2 写侧 Repository

子接口 **仅继承** SDK 基 `Repository`，**不新增**带 **多个业务条件组合**、分页、排序、Join 的方法（此类一律走读侧 §18.3）。

**单键辅助方法（可选、须收敛）**：仅允许 **单一路径键** 的 `existsByXxx` / `findByBusinessKey`（**单一**等值条件、语义为业务外键或唯一码），**禁止** `findByStatusAndType` 等形式。具体方法名须在 **模块内 ArchUnit 白名单或模块 README 列表**中列出；新增须走评审，避免仓储接口再度膨胀。

### 18.3 读侧

`QueryBuilder.from(Entity.class)` 或等价 `Criteria`；复杂 SQL 放 `infrastructure.query`。`QueryBuilder` **禁止**出现在 `domain` 与 `application.command.handler`。

### 18.4 启动扫描（参考）

```java
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.{module}.domain.repository")
public class Application { }
```

---

## 19. ACL（Bone）

出站端口在 **domain**（如 `PaymentGateway`），实现在 **infrastructure**；禁止在 `application` 直接依赖第三方 HTTP/RPC **实现类型**。

---

## 20. Flow / AI 编排（可选）

若使用 Flow、`@Capability` 等：

- **能力声明**：在 `*CommandHandler` / `*QueryHandler`（或 §14.3 例外的 `*Orchestrator`）上加 `@Capability` 元数据；**禁止**为「让 AI 发现」而新建 `*UseCase` 门面。
- **能力发现**：通过独立的 capability 注册表（如 `bone-core` 的能力扫描器）按注解汇总，**与 DDD 用例命名解耦**。
- **执行入口**：Flow / AI 调度统一通过注入 Handler/Orchestrator 调用，遵循 §15 事务边界。
- **底线**：编排能力**不替代**聚合与 §12 铁律；命名对齐通用语言。

> v3.4 起，**已彻底废止** `application/usecase/**` 与 `com.bone.core.usecase.UseCaseExecutor` / `@UseCase`；相关迁移见 [§22 演进](#22-演进建议三阶段) 与 [附录 B](#附录-b废止登记)。

---

## 21. 测试与 CI

**共享规则库**：`bone-framework/bone-architecture-test`（`BoneDddArchRules`），各应用模块 `test` 依赖引用，避免每模块复制规则。

**ArchUnit 最小规则集**（每应用模块 `src/test/java/.../architecture/ArchitectureTest.java`，**与 §12 P0 一一对应**）：

1. `domain` 不依赖 `adapter` / `application` / `infrastructure`（P0-1）
2. `application` 不依赖 `..infrastructure..` 具体类（P0-1，仅依赖 `domain` 端口与 `bone-core`）
3. `..domain..` 不依赖 `QueryBuilder`（P0-5）
4. `..application.command.handler..` 不依赖 `QueryBuilder`（P0-6）
5. `..domain.repository..` 接口方法名仅 `save/remove/findById/existsByXxx/findByBusinessKey`（P0-4 + §18.2）
6. **禁止新增 `*UseCase` / `application/usecase/**`**（P0-7）— 对存量模块使用 **`FreezingArchRule`**（基线文件 `src/test/resources/archunit_store/`），仅失败于**新增**违规
7. **禁止业务模块新增 `com.bone.core.usecase.*` 依赖**（P0-7）；`studio-generator` 另禁自造 `com.bone.studio.generator.application.usecase.UseCase` 注解的新引用

```java
// 示例：P0-7 冻结存量、拦截新增
@ArchTest
static final ArchRule no_new_use_cases =
    FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());
```

> 模块覆盖与批次：见 [附录 B.2](#b2-模块符合度与迁移矩阵v36-执行)。**Batch A** 合并共享规则库后，各模块运行一次 `mvn test -Dtest=ArchitectureTest` 生成/更新 Freezing 基线。

**单测**：聚合与值对象规则（无容器）；**集成测**：用例与端口（Testcontainers 等）；**跨服务**：契约测试（OpenAPI / Pact 等）。**覆盖率**：与仓库质量门禁对齐，核心域优先提高阈值。

---

## 22. 演进建议（三阶段）

1. **L0**：满足 P0；**Controller 直接注入 Handler**（§14.3）；已存在的 `application/usecase` 标记 `@Deprecated` 并停止扩张。
2. **L1**：术语表 + 上下文职责说明（对齐第一部分 §2）；**完成 `*UseCase` → Handler 收敛**，删除空壳门面与 `application/usecase` 目录。
3. **L2**：跨上下文事件、读模型独立演进（对齐第一部分 §5.2）；必要时按 §14.3 例外引入 `*Orchestrator`；编排类能力（Flow、AI 等）按需引入，见 §20。

### 22.1 `*UseCase` → Handler 迁移指南（v3.4）

| 现状形态 | 迁移动作 |
|----------|----------|
| `*UseCase` 仅 delegate 到同名 `*Handler`（占多数） | **删除** `*UseCase`；Controller 改注入 `*Handler`；测试同步 rename |
| `*UseCase` 在 delegate 前包含 1–2 行参数装配 | 把装配逻辑下沉到 `adapter/web/converter` 或 Handler 入参；删 UseCase |
| `*UseCase` 调用 2+ 个 Handler（真有编排） | 改名为 `*Orchestrator`，放在 `application/orchestration/`；附 ADR 说明编排理由 |
| `*UseCase` 已被 AI/Flow 通过 `@UseCase` 发现 | 改为在 Handler/Orchestrator 上加 `@Capability`；调度方按 `@Capability` 发现 |
| 模块所有 UseCase 迁完 | 删除 `application/usecase` 目录；移除该模块对 `bone-core.usecase.*` 的依赖 |

**截止节点**：建议 L1 模块在 **2026-12-31** 前完成；`bone-core` 的 `UseCaseExecutor` / `@UseCase` 同日删除。**新模块自始禁止使用**。

---

## 23. 极简 / 轻量 / 低成本（Bone 默认心态）

| 做法 | 建议 |
|------|------|
| **CQRS** | 保留 command/query 分包；不默认独立读库、事件投影。 |
| **应用层** | Controller → Handler → Domain **三层即可**；**不引入** UseCase；`application/service` 仅 §14.3.1 白名单。 |
| **扩展点 / ACL / MQ / RPC / 定时** | 无真实需求则不建。 |
| **战略文档** | 小模块一页纸；术语表 L1 再补。 |
| **领域事件** | 无跨聚合协调时可少发。 |

**bone-blueprint** 为全特性参考；新建业务对齐 **§14.2（极简包）+ §12.1（P0 铁律）+ §14.3（去 UseCase）**，再按 §22 加码。

---

## 24. 命名约定

| 类型 | 示例 | 备注 |
|------|------|------|
| 命令 | `CreateOrderCommand` | **v3.6 新代码统一 `*Command`**；存量 `*Cmd` 随 Handler 迁移逐步改名，不再双轨 |
| 查询 | `OrderByIdQry` | 与 `query/qry/` 对齐 |
| 处理器 | `CreateOrderCommandHandler`、`OrderByIdQryHandler` | `command/handler/`、`query/handler/` |
| 领域事件 | `OrderPaidEvent` | 过去式 |
| 编排器（例外） | `OrderRefundOrchestrator` | 仅 §14.3 例外允许；放 `application/orchestration/` |
| ~~用例（已废止）~~ | ~~`CreateOrderUseCase`~~ | **v3.4 禁止**新代码使用（§14.3）；存量按 §22.1 迁移 |

---

## 25. 修订与 Owner

边界变更：**先更新术语表与第一部分相关认知**，再改第二部分门禁与代码。

---

## 附录 A：与旧 Bone-Blueprint 版本号的关系

历史版本号（v7 / v9.5 / v16.3 / v24 等）仅表示过往迭代；**门禁以第二部分** §12（铁律）、§14～§24 中与**结构、领域持久化、ACL、测试、演进、极简、命名**相关的条文为准（§10～§11 为 Bone 目标与对齐摘要，非逐条 CI）。产品文档中「Bone-Blueprint」可与本方案同义指称。

---

## 附录 B：废止登记

### B.1 v3.4：去 UseCase

| 项 | 状态 | 截止 | 替代 / 迁移 |
|----|------|------|--------------|
| 新代码新增 `application/usecase/**` 包 | **禁止**（PR 拦截） | v3.4 起即时 | 直接写 `*CommandHandler` / `*QueryHandler` |
| 新代码新增 `*UseCase` 类 | **禁止**（PR 拦截） | v3.4 起即时 | 同上；多 Handler 组合用 `*Orchestrator`（§14.3 例外） |
| Controller 注入 `*UseCase` | **禁止**（新代码） | v3.4 起即时 | 注入 Handler；事务边界在 Handler 上 |
| `com.bone.core.usecase.UseCaseExecutor` | **`@Deprecated`** → **删除** | 2026-12-31 | 删除接口；业务模块取消依赖 |
| `com.bone.core.usecase.@UseCase` | **`@Deprecated`** → **删除** | 2026-12-31 | 改用 `@Capability`（AI/Flow 能力发现）或纯 `@Service`/`@Component` |
| 存量 `*UseCase` 类（IAM / masterdata / integration / system / studio-generator / blueprint） | **登记 + 迁移** | 2026-12-31 | 按 [§22.1 迁移指南](#221-usecase--handler-迁移指南-v34) |
| `application/usecase/standard/`、`application/usecase/simple/` 子目录 | **删除** | 同上 | 内容合并到 `command/handler` 或 `query/handler` |

### B.2 模块符合度与迁移矩阵（v3.6 执行）

**完成定义（DoD）**：① 无新增 `*UseCase`（ArchUnit Freezing 无新违规）② Controller 不注入 `*UseCase` ③ 已引入 `ArchitectureTest` + `bone-architecture-test` ④ 模块 README 勾选本表对应批次。

#### B.2.0 迁移批次（冻结排期）

| 批次 | 范围 | 内容 | 目标日期 | Owner |
|------|------|------|----------|-------|
| **A** | 平台 | `bone-architecture-test`、`bone-core` 废弃 UseCase、各模块 ArchUnit + Freezing 基线 | 2026-05-31 | 架构组 |
| **B** | bone-platform 四模块 | IAM / masterdata / integration / system：删 `*UseCase`、Controller→Handler | 2026-09-30 | 各模块 Maintainer |
| **C** | studio-generator | 删自造 `UseCase`/`UseCaseExecutor`；迁 Handler；统一 `*Command` | 2026-09-30 | Studio 组 |
| **D** | bone-extension-studio | `store→repository`、`controller`→`adapter/web`、CQRS 分包 | 2026-11-30 | 扩展引擎组 |
| **E** | bone-blueprint | 删除 UseCase 示例；生成模板对齐 §14.1（**在 Batch B 完成后**） | 2026-12-31 | 架构组 |

#### B.2.1 应用 / 控制面 BFF（**完全适用 §12 P0 全 7 条**）

| 模块 | 批次 | ArchitectureTest | `*UseCase` 存量 | 自造 UseCase | `application/service` | 端口 | 主要 gap |
|------|------|-----------------|-----------------|--------------|----------------------|------|----------|
| `bone-iam` | B | ✅ 部分 | 19 | — | 2（S1 合规） | `repository` | Freezing 基线；删 UseCase |
| `bone-masterdata` | B | ✅ | 10 | — | — | `repository` | 同上 |
| `bone-integration` | B | ✅ | 7 | — | 5（S2 流程运行时） | 混用 | UseCase + 端口统一 |
| `bone-system` | B | A 起补齐 | 14 | — | — | — | 新建 ArchUnit |
| `bone-notification` | B | A 起补齐 | 0 | — | — | — | 新建 ArchUnit |
| **`bone-extension-studio`** | D | A 起补齐 | 0 | — | — | **`store`** | 结构迁移 §14.5 |
| **`studio-generator`** | C | A 起补齐 | 21+ | **自造注解** | 1 | — | 删自造类型 + UseCase |
| `bone-blueprint` | E | ✅ | 5 | — | — | `repository` | **迁移中参考**，非终态样板 |

#### B.2.2 引擎 SDK / 框架库（**仅适用 §12.1.1 + §17；豁免 §14、§21 业务规则**）

| 模块 | 物理位置 | 强制项 | 备注 |
|------|----------|--------|------|
| `bone-framework/bone-core` | bone-framework/ | D0/D1/D2、依赖向内 | `@UseCase` / `UseCaseExecutor` 已 `@Deprecated`（v3.4） |
| `bone-framework/bone-web` / `bone-security` / `bone-utils` / `bone-datasource` | bone-framework/ | 同上 | 工具/基础设施类 |
| `bone-engine/bone-metadata-sdk` / `bone-metadata-server` / `bone-metadata-engine` | bone-engine/ | 同上 | metadata SPI |
| `bone-engine/bone-extension-engine/bone-extension-sdk` | bone-engine/ | 同上 | 业务进程内嵌 SDK |
| `bone-engine/bone-workflow`、`bone-procurement` | bone-engine/ | 同上 | 引擎库 |
| `bone-sdk/*` | bone-sdk/ | 同上 | 客户端 SDK |

#### B.2.3 基础设施服务（**依用例判定；默认豁免 §14**）

| 模块 | 物理位置 | 备注 |
|------|----------|------|
| `bone-platform/bone-gateway` | bone-platform/ | 纯路由 / 协议转换，无业务模型 |
| `bone-platform/bone-file` | bone-platform/ | 若仅对象存储中转可豁免；含业务规则按应用处理 |

> **新模块**自始按 [§14.4](#144-模块适用性按性质而非物理位置) 性质判定 + §14.1 + §14.3 构建，**不进入**本矩阵的「待迁移」清单。

### B.3 ArchUnit 模板（`bone-architecture-test`）

```java
import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());
}
```

`studio-generator` 额外：`BoneDddArchRules.noStudioGeneratorUseCaseAnnotation()`.

**Freezing 基线**：文件位于各模块根目录 `archunit_store/`（须提交 Git）。首次生成：`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true`（见 `bone-framework/bone-architecture-test/README.md`）。

本文结束。
