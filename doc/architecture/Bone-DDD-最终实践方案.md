# Bone 领域驱动设计（DDD）统一实践方案

> **唯一权威**：本文档置于 `doc/architecture/`，为 Bone 仓库内 **DDD 与分层门禁** 的唯一权威规范（与同目录总体架构、数据库规范并列维护）。  
> **结构**：**第一部分**为业界共识与架构原则（北向星），含 **§10 Bone 上下文映射参考**（将 §2.3 原则实例化为 Bone 各上下文关系图）；**第二部分**为 **Bone 平台工程落地**（包结构、铁律、SDK、极简策略）。修订时先对齐原则，再调整落地条文。  
> **定位说明**：本文是 **Bone 仓库内 DDD 与分层门禁的权威规范**，对齐主流 DDD/整洁架构共识，并含 **D1 元数据注解** 等工程折中；**非**全行业唯一标准，复杂域请结合 ADR 裁剪。  
> **关联文档**：[BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md)（平台总体架构、NFR、安全与数据一致性策略，与本方案互补）；[README.md](./README.md) 为架构文档索引；模块详设见 [doc/design/modules/README.md](../design/modules/README.md)。  
> **版本**：**4.3** | **日期**：2026-05-26  
> **分册索引**：[ddd/README.md](./ddd/README.md)（原则 / 工程落地 / CQRS / 附录 / **补充条文与示例**）  
> **近期 ADR**：[0011 AggregateRoot 继承链](./adr/0011-aggregate-root-inheritance.md)、[0012 SystemException 层次](./adr/0012-system-exception-hierarchy.md)、[0013 extension-studio 读侧 ReadPort](./adr/0013-extension-studio-repository-read-side.md)
>
> **使用说明**：
> - 本方案为**稳定规范**，**不再附迁移时间表 / 批次 / Owner**。**凡不符合本规范的代码均须迁移**；落地节奏由各模块负责人在内部排期，不写入本文。  
> - **新代码**：必须 100% 满足 [§0 规范稳定性契约](#0-规范稳定性契约) + [§12 铁律](#12-铁律p0-必守)，由 ArchUnit 共享规则库（`bone-framework/bone-architecture-test`）在 CI 强制。  
> - **历史变更**：删除（仓库 Git 历史可查）。本文只描述「最终态」。

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
- **Bone 各上下文的具体映射关系**见 [§10 Bone 上下文映射（参考）](#10-bone-上下文映射参考)。

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

跨聚合、跨限界上下文的**强一致**应为例外；默认采用 **最终一致**（领域事件、集成事件、Outbox、Saga/补偿等）。具体模式选择与 **幂等、重试、死信** 要求见 **[总体架构设计方案](./BONE-总体架构设计方案.md)** 第二十七部分等章节。

| 场景 | Bone 默认模式 | 实现位置 |
|------|--------------|----------|
| 单聚合内强一致 | `@Transactional` on `*CommandHandler` | `application.command.handler` |
| 跨聚合同模块 | 领域事件 → `AFTER_COMMIT` 发布 + Outbox（可选） | `infrastructure` + `DomainEventPublisher` |
| 跨模块 / 跨服务 | 集成事件（`*IntegrationEvent` 后缀）→ MQ / Saga | `infrastructure`、integration 模块 |

领域事件发布最小模式见 [ddd/07-supplements.md §3.3.1](./ddd/07-supplements.md#331-领域事件发布最小模式)。

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

## 10. Bone 上下文映射（参考）

> 本节是 §2.3 上下文映射原则在 Bone 仓库内的具体实例化，将通用原则落地为可读的关系图与可执行的边界守护建议。**维护人**：架构组；随系统演进更新。

### 10.1 限界上下文与子域类型

| 上下文 | 核心职责 | 子域类型 | 主要聚合根（示例） |
|--------|----------|----------|-------------------|
| **IAM** | 身份、认证、授权；用户 / 角色 / 权限生命周期管理 | 通用域 | `User`、`Role`、`Permission` |
| **MasterData** | 主数据建模、数据质量治理、记录管控 | 核心域 | `MasterEntity`、`MasterRecord` |
| **Integration** | 多协议连接器、流程编排、执行日志 | 核心域 | `Connector`、`Flow`、`FlowExecution` |
| **Extension** | 扩展点注册、插件生命周期管理 | 核心域 | `ExtensionPoint`、`Plugin` |
| **System** | 系统配置、运维日志、监控告警 | 通用域 | `SysConfig`、`SysLog` |
| **Notification** | 通知渠道、消息发送记录 | 支撑域 | `NotificationRecord` |
| **Generator** | 代码生成模板、数据源、生成任务历史 | 支撑域 | `Template`、`GenerationTask` |

### 10.2 上下文映射图

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  bone-framework  共享内核（Shared Kernel）                                        │
│  bone-core / bone-web / bone-security / bone-datasource                          │
│  bone-metadata-sdk  遵奉者（Conformist，D1 工程折中，见 §4.1 / §17）              │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                 │ 所有应用模块 Maven 依赖
        ┌────────────────────────┼────────────────────────────────┐
        ▼                        ▼                                ▼
┌──────────────┐         ┌──────────────┐               ┌─────────────────────────┐
│   Gateway    │         │     IAM      │               │  bone-extension-sdk     │
│  (纯路由BFF) │         │ (port 8081)  │               │  共享内核（SPI）         │
└──────┬───────┘         └──────┬───────┘               └───────────┬─────────────┘
       │                        │                                   │
       │ 路由（无业务语义）       │ OHS / JWT                         │ SPI（@ExtensionPoint）
       │                        │ 下游须 ACL 防腐                    │
       ├──── 路由 ──────────────►│◄── ACL ── 所有应用上下文           ▼
       │                        │                       ┌──────────────────────┐
       ├── 路由 ──► ┌────────────┴────────────┐         │  Extension Studio     │
       │            │      MasterData         │  C-S    │  (扩展引擎应用)        │
       │            │  （主数据，核心域）       │──MQ/ACL►│                       │
       │            └────────────┬────────────┘         └──────────────────────┘
       │                         │ C-S (MQ / ACL)
       ├── 路由 ──► ┌─────────────▼───────────┐
       │            │      Integration        │──── 扩展点SPI ─────►Extension
       │            │  (集成引擎, port 8085)   │
       │            └─────────────────────────┘
       │
       ├── 路由 ──► ┌─────────────────────────┐
       │            │         System           │──── OHS ──────────►所有（配置/日志）
       │            │  (系统管理, port 8083)    │
       │            └─────────────────────────┘
       │
       ├── 路由 ──► ┌─────────────────────────┐
       │            │      Notification        │◄─── C-S / ACL ─────IAM（用户信息）
       │            └─────────────────────────┘
       │
       └── 路由 ──► ┌─────────────────────────┐
                    │       Generator          │◄─── C-S / ACL ─────MasterData / Extension
                    └─────────────────────────┘
```

### 10.3 跨上下文集成关系明细

| 上游（U） | 下游（D） | 关系模式 | 集成方式 | 防腐要求 |
|-----------|-----------|----------|----------|----------|
| bone-framework | 所有模块 | **共享内核** | Maven 依赖 | 变更须全平台架构评审；任何模块不可私自修改 |
| bone-metadata-sdk | 所有应用模块 | **遵奉者**（D1 折中） | Maven 依赖 + `@Table`/`@Id` 注解 | 不可混入 JPA/Hibernate 注解；见 §4.1 / §17 |
| bone-extension-sdk | Extension Studio + 业务嵌入进程 | **共享内核** | Maven 依赖 | SPI 接口变更须向下兼容 |
| IAM | 所有应用上下文 | **OHS（开放主机服务）** | JWT Token + `/api/v1/iam/` REST | 下游须 ACL 转换；**禁止直接依赖 `com.bone.iam.domain.*`** |
| MasterData | Integration | **客户–供应方（C-S）** | 集成事件（MQ）或 REST ACL | Integration 须 ACL 隔离；不持有 MasterData 聚合对象 |
| Integration | Extension | **客户–供应方（C-S）** | 扩展点 SPI（extension-sdk） | 经 SPI 接口调用；不依赖 Extension domain 包 |
| System | 所有 | **OHS** | REST + SkyWalking 日志采集 | 仅消费技术指标；无业务耦合 |
| IAM | Notification | **客户–供应方（C-S）** | REST ACL | Notification 通过 ACL 获取用户联系方式；不持 IAM domain 模型 |
| MasterData / Extension | Generator | **客户–供应方（C-S）** | REST ACL | Generator 通过 ACL 消费元数据与扩展描述 |
| bone-gateway | 所有应用上下文 | **纯路由（无业务语义）** | HTTP 反向代理 | 不持有任何业务模型；不做跨上下文聚合逻辑 |

### 10.4 跨上下文边界 ArchUnit 守护（推荐，按需添加）

目前跨上下文边界主要依赖代码评审约束。随各模块集成关系明确，推荐在**下游模块**的 `ArchitectureTest` 中逐步添加越界依赖拦截规则：

```java
// 示例：Integration 模块禁止直接 import IAM 或 MasterData 的 domain 包
// 跨上下文集成须通过公开 API jar 或集成事件契约，而非直接依赖对方 domain 类
@ArchTest
static final ArchRule no_direct_iam_domain_dependency =
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.iam.domain..")
        .allowEmptyShould(true)
        .because("跨上下文集成须经 IAM 公开 API，禁止直接依赖其 domain 包（§10.4）");
```

> **使用说明**：此类规则**不统一放入 `BoneDddArchRules`**（跨模块 classpath 通常未加载对方内部类，规则会空命中），而是在各自下游模块 `ArchitectureTest` 中**按需自行添加**；优先在新建跨上下文集成关系时同步落地，存量按需覆盖。

---

# 第二部分　Bone 平台工程落地

本部分规定 **Bone** 仓库内模块的**可执行约束**与推荐结构。**价值排序**：以**第一部分**为演进北向星；**交付门禁**以**第二部分**为准。二者常规不互斥（如纯 POJO 理想与 **D1** 元数据注解的取舍，已在第一部分 §4.1 与第二部分 §17 显式衔接）；若仍存张力，在评审中记录取舍理由。

---

## 0. 规范稳定性契约

> 本节是 Bone 仓库内 DDD 与分层门禁的**唯一稳定面**。新代码须满足下列条文；**存量违规**通过 `FreezingArchRule` 登记快照，CI 拦截**新增**违规，迁移完成后收缩基线。

### 0.1 新代码强制 Profile（PR 拦截）

适用于 [§14.4](#144-模块适用性按性质而非物理位置) 认定的**应用 / 控制面 BFF** 模块。模块性质**仅按 §14.4 三类（应用 / SDK / 基础设施）判定**，不以 `bone-platform/` vs `bone-engine/` 目录推断适用性。

| 维度 | 要求 |
|------|------|
| 分层 | §14.1 标准树（或 §14.2 极简树） |
| 应用层 | 基础包 `command` / `query`；可选 `event` / `integration` / 满足 §14.3.1 约束的 `service`；ADR 例外可加 `orchestration`（§14.3）；满足 §14.3.2 F1/F2/F3 可加 `facade` |
| 入站 | Controller → `*CommandHandler` / `*QueryHandler`（或 ADR 批准的 `*Orchestrator`，或 §14.3.2 条件下的 `*Facade`）；**禁止** Controller 直接注入 `application/service`、`domain/service`（领域服务）、`domain/repository`（ArchUnit 见 §21 #11/#12/#17） |
| 命名 | 应用层命令/查询类名 `*Command` / `*Query`（禁 `*Cmd` / `*Qry`）；Handler 类名 `*CommandHandler` / `*QueryHandler`；adapter 入参 DTO 见 [§23](#23-命名约定) 分层表；端口 `domain/repository/*Repository`；异常 `BizException` |
| 禁止 | `application/usecase/**`、`*UseCase`、任何自造 `@UseCase` / `UseCaseExecutor`；**禁止**业务模块依赖已删除的 `com.bone.core.usecase.*` |
| AI/Flow | 能力发现仅用 `com.bone.core.capability.@Capability` + `HandlerRegistry`（§20） |
| 门禁 | §12 P0 + `bone-framework/bone-architecture-test` 共享 ArchUnit 规则（见 §21） |

### 0.2 存量不符合规范代码的处理

- **统一原则**：不符合本规范的代码（含但不限于 `*UseCase` 类、`application/usecase/**` 包、`domain/store/*Store`、Controller 直注 `*UseCase`、命令后缀 `*Cmd`、查询后缀 `*Qry`、模块根包下的 `controller/`、自造 `@UseCase` 注解、自建 `BusinessException`）**一律迁移**到符合规范的形态；落地节奏由模块 Maintainer 在内部安排，**不在本文规定**。
- **CI 防回退**：通过 `FreezingArchRule` 登记存量违规快照；**新增**违规直接 CI 失败。基线随每次迁移收缩，**不允许扩张**。
- **例外登记**：规范允许的例外（如 §14.3 `*Orchestrator`、§17.D2 框架基类新增）统一按 [§0.4](#04-例外登记的统一形态) 登记。破坏性变更 ADR 模板见 [adr/0000-template.md](./adr/0000-template.md)。
- **命名**：子包 `cmd/`、`qry/` **仅作短目录名**；类名后缀必须为 `*Command` / `*Query`（禁 `*Cmd` / `*Qry` 类名）。

### 0.3 破坏性变更流程

1. 在 `doc/architecture/adr/` 新增 ADR（动机、影响模块、迁移路径、回滚）。  
2. 同步以下下游文档（任一项涉及则更新）：

| 文档 | 路径 |
|------|------|
| 项目 Agent 指南 | `AGENTS.md` |
| 模块详设（按影响面） | `doc/design/modules/*.md` |
| 蓝图 / 生成器 | `bone-blueprint`、`studio-generator` 模板与 README |
| API 规范（若影响异常/响应） | `doc/architecture/Bone-API-规范.md` |

3. **先合并** ArchUnit / CI 规则与文档，**再启动**业务代码迁移；避免「代码已改、规范又变」。

### 0.4 例外登记的统一形态

> 全文各处提到的「例外」统一按**影响半径**登记，避免读者每次回忆走哪个流程。

| 影响半径 | 登记形式 | 典型场景 |
|----------|----------|----------|
| **跨模块 / 平台级** | `doc/architecture/adr/` 新增 ADR | §14.3 `*Orchestrator`；§0.3 破坏性变更；§17 D2 框架基类新增例外 |
| **单模块内** | 模块 `README.md` 例外列表 | §18.1 自增主键例外；§14.4 模块性质边界用例 |
| **类内决策 / 工程折中** | 类级 / 方法级 Javadoc 标注理由 | §14.3.1 应用服务类别（S1/S2/S3）；§12.1 P0-6「读己之写」CommandHandler 特例 |

- **同一规范的例外升级**：单模块例外若被第二个模块复用，须升级为 ADR。
- **例外不堆积**：每条例外**必须**注明「何种条件下可拆除」；架构组复核时拆除过期例外（复核节奏由架构组自行掌握，不在本文规定）。
- **ADR 不为「规避规范」开口子**：若例外仅为绕过条款而无业务理由，应拒绝。

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
| 边界与数据所有权 | 一上下文对应一个 Maven 模块（`com.bone.{module}`）；跨边界经 ACL / 契约 API / 事件 |
| 通用语言 | 术语表 + 命令/事件/REST 命名一致 |
| 防腐 | `domain/gateway/` 定义出站 ACL 与读侧 `*ReadPort`（§18.5）；`infrastructure/gateway/` 或 `infrastructure/persistence/` 实现；禁止应用层直连 Feign **实现类型** |
| 一致性 | 一事务一改一个聚合根；跨聚合默认最终一致 |
| 读写分离 | 写走聚合 + 仓储；读走 `Criteria` / `QueryBuilder` + 投影 DTO |

---

## 12. 铁律（P0 必守）

### 12.1 P0 — 全局七条（CI / ArchUnit 硬门禁）

> **适用范围**：本节 P0 七条**仅强制适用于「应用 / 控制面 BFF」模块**（详见 [§14.4](#144-模块适用性按性质而非物理位置)）。SDK / 框架库豁免 P0-4/5/6/7。

1. **依赖方向**：`adapter → application → domain ← infrastructure`，禁止反向依赖；**application 层禁止** import **infrastructure** 具体实现类（须经 **domain 端口** 或 **adapter 已解析的 Bean** 注入接口实现，与总体架构方案中依赖倒置一致）。  
2. **领域规则**：状态流转、不变量在 **聚合根或领域服务**；应用层只做编排与事务边界——**禁止在 Handler 内实现仅属于某一聚合的业务不变量**（可做的仅为用例级前置校验，如参数组合、ACL 权限是否已判定通过等）。  
3. **外部系统**：仅经 **端口 + infrastructure 实现**；`domain` / `application` 不依赖 Feign、HttpClient、MQ Producer **实现类型**。  
4. **写侧仓储**：`domain.repository` 只做聚合 **加载与持久化**（如 `save`、`remove`、`findById`）；**禁止**在仓储接口上增加组合条件列表/分页等查询方法。  
5. **读侧查询**：凡 **WHERE 含多个业务条件**、**分页/排序**、**Join/子查询/聚合报表** 的读路径，统一用 **`Criteria` / `QueryBuilder`**，结果映射到 `application.query.dto`（或 `projection`）；**禁止在 `domain` 包内**使用查询构建器。仅按 **主键或单一业务键** 加载聚合（如 `findById`、§18.2 白名单方法）仍走仓储，不视为本条「读侧复杂查询」。  
6. **CQRS**：CommandHandler 内 **禁止** 使用 `QueryBuilder`（特例「读己之写」须注释 + 评审）。**QueryHandler** 建议标注 **`@Transactional(readOnly = true)`**（或框架等价只读事务），且 **禁止** 调用写侧仓储修改聚合。
7. **应用层单层**（禁止无意义薄门面）：**禁止** `application/usecase/**` 包与 `*UseCase` 类；Controller **直接注入** `*CommandHandler` / `*QueryHandler` / `*Orchestrator`（编排例外见 [§14.3](#143-应用层结构强制)）或 `*Facade`（有条件的入站门面，见 [§14.3.2](#1432-applicationfacade-约束条件追加非默认)，不同于被禁止的无意义 UseCase 薄门面）；**禁止**直接注入 `application/service`、`domain/service`（领域服务）、`domain/repository`（写侧仓储）。**禁止**任何模块 import 已删除的 `com.bone.core.usecase.*` 或自造 `@UseCase` / `UseCaseExecutor`。

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
| P0-7 禁止 UseCase | **强制** | **豁免**（非应用 / 控制面 BFF，无 Controller 业务 API）；`com.bone.core.usecase.*` **已从 bone-core 删除**，业务模块**不得**引用 |
| §14.1 包结构 | **强制** | **豁免**（按 SPI/库习惯组织） |
| §14.3 应用层 | **强制** | **豁免** |
| §17 D0/D1/D2 | 建议 | **强制**（元数据注解白名单） |

新增 SDK 能力若需豁免上述条目，须在 `bone-framework` 或对应引擎模块 **README + ADR** 登记。

---

## 13. 战略 DDD（Bone 最小必做）

### 13.1 限界上下文

每个上下文具备：**名称**、**职责一句**、**对外契约**、**数据所有权**。

- **单体**：一上下文对应一个 Maven 模块（`com.bone.{module}`）；禁止跨上下文直接引用对方聚合 **类型**。  
- **多部署**：一上下文一服务边界；不默认共享写库。

### 13.2 通用语言

- **小模块**：至少在模块 README 中固定**核心术语中英对照与禁用同义词**（一页纸级术语表）。  
- **大模块 / 跨模块**：维护 `doc/glossary.md` 或 Wiki，命令、事件、REST 与表字段、消息字段对齐。

### 13.3 上下文映射（轻量）

与邻域标注 **合作 / 客户-供应 / 防腐 / 发布语言** 之一；跨边界不经隐式共享写路径。

### 13.4 多租户在 DDD 中的约定

| 约定 | 说明 |
|------|------|
| 写侧 | 多租户实体优先 `TenantAbstractEntity`；聚合根 + 租户 + 事件用 `TenantAggregateRoot`（ADR-0011） |
| 仓储 | `findById` 等须在实现层结合 `TenantContext` 过滤；禁止跨租户无审计的批量写 |
| 读侧 | `QueryBuilder` 条件**必须**含 `tenantId`（或等价隔离键），见 [Bone-API-规范](./Bone-API-规范.md) |
| 事件 | 领域/集成事件载荷**应**携带 `tenantId` |
| 打洞 | 平台超管跨租户查询须在模块 README 登记，Handler 内显式校验角色 |

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
│   │   ├── cmd/                   # 目录短名 cmd/ 保留；类名必须 *Command（禁 *Cmd 类名）
│   │   └── handler/
│   ├── query/
│   │   ├── qry/                   # 目录短名 qry/ 保留；类名必须 *Query（禁 *Qry 类名）
│   │   ├── handler/
│   │   └── dto/
│   ├── event/                     # 可选：领域事件订阅 / 应用事件转发
│   ├── integration/               # 可选：入站消息编排（MQ / Kafka 消费）
│   ├── orchestration/             # ADR 例外：跨 Handler 编排（§14.3）
│   ├── facade/                    # 条件追加：多入口 / SDK 门面（§14.3.2）
│   └── service/                   # §14.3.1 约束（共享逻辑，禁 Controller 直注）
├── domain/
│   ├── {aggregate}/
│   ├── repository/
│   ├── service/
│   └── gateway/
└── infrastructure/
    ├── persistence/
    │   ├── entity/                # *PO（D2，§17.2）
    │   ├── converter/             # PO ↔ domain 互转
    │   └── repository/            # *RepositoryImpl
    ├── gateway/
    ├── config/
    └── query/
```

### 14.2 极简树（小模块默认够用）

仅 **Web + 一聚合** 时，可只保留以下包：

- `adapter/web/`
- `application/command/`、`application/query/`
- `domain/{aggregate}/`、`domain/repository/`
- `infrastructure/persistence/`（如需 PO 映射或仓储实现）

无外部系统则暂不建 `gateway`。读极简单时仍建议保留 `query` 包，避免把列表查询塞回 `CommandHandler`。

**分组原则**：按 **业务/聚合** 分包，不按「全 entities / 全 vo」横切。

### 14.3 应用层结构（强制）

**允许的应用层包**（按角色划分）：

| 包 | 角色 | 必选 / 可选 |
|----|------|-------------|
| `application/command/{cmd,handler}/` | 写用例执行器（`*CommandHandler`） | **必选**（凡有写操作） |
| `application/query/{qry,handler,dto}/` | 读用例执行器（`*QueryHandler`） | **必选**（凡有读操作） |
| `application/event/` | 应用事件（领域事件订阅/转发） | 可选 |
| `application/integration/` | 入站消息编排（如 MQ 消费、Kafka Source） | 可选 |
| `application/orchestration/` | 跨多 Handler 编排（`*Orchestrator`） | **ADR 例外** |
| `application/facade/` | 多入口 / Client SDK 入站门面（`*Facade`） | **§14.3.2 条件追加** |
| `application/service/` | 多 Handler 共享逻辑（`*Service`） | **§14.3.1 约束** |

**禁止**（PR/ArchUnit 拦截）：

| 禁项 | 替代方案 |
|------|----------|
| `application/usecase/**`（任何子目录） | 删除；Controller 直接注入对应 `*CommandHandler` / `*QueryHandler` |
| 类名 `*UseCase` | 改名为 `*CommandHandler` / `*QueryHandler`；纯 delegate 直接合并到现有 Handler，不留空壳 |
| Controller 注入 `application/service/*Service` | Controller 仅注入 Handler / Orchestrator / Facade（§14.3.2）；事务边界仍在 Handler |
| Controller 注入 `domain/service/*`（领域服务）或 `domain/repository/*`（写侧仓储） | **禁止**；经 Handler 编排后间接调用，不直接穿透到 domain 层（ArchUnit #12、#17） |
| **业务模块** import `com.bone.core.usecase.*` | **禁止**（包已从 bone-core 删除） |
| 自造 `@UseCase` 注解 / `UseCaseExecutor` 接口 | **禁止**；按本规范删除并改 Controller 直注 Handler |
| 无触发条件（F1/F2/F3 均不满足）的 Facade 门面 | 反模式；删除 Facade，Controller 改直注 Handler（见 §14.3.2）|

**决策依据**：

1. **职责重复**：在 §15 应用层职责（编排 + 事务边界）下，`*UseCase → *Handler` 多为 1:1 delegate，无新增语义。
2. **认知成本**：Controller → UseCase → Handler → Domain 比 Controller → Handler → Domain 多一层，对人/AI/生成器都是负担。
3. **形态收敛**：§14.1 标准树原本仅 `command/query`，UseCase 是事实上的第二套形态，本规范明确收敛为一套。
4. **AI / Flow 能力发现** 用 `@Capability` 即可（§20），不应复用「UseCase」名义混淆 DDD 用例与 AI 能力两层语义。

**例外**（按 [§0.4](#04-例外登记的统一形态) 登记 ADR）：

- 仅当「跨 2+ 聚合 Handler 编排 + 无法在单一 Handler 表达事务/补偿」时，可使用 **`application/orchestration/*Orchestrator`**（**不叫 UseCase**），ADR 写明编排步骤、补偿策略、是否引入 Saga。
- **反例**（不构成例外，应改写为单 Handler 或 Orchestrator 内联）：
  - 「Handler A 同步调用 Handler B」且 B 仅复用查询 → 把查询下沉到共享 `application/service/*Service`（§14.3.1）。
  - 「多个 Handler 顺序调用、无补偿」→ 合并到一个 Handler，或抽取共享 `*Service`；不必引入 Orchestrator。

> 能力声明走 `@Capability`，业务执行走 Handler（必要时 Orchestrator）；详见 [§20](#20-flow--ai-编排可选)。

#### 14.3.1 `application/service` 约束

`application/service/` 用于多个 Handler **共享**非门面型逻辑。**两条铁律 + 两条建议**即可：

**铁律**：

1. **禁止** `Controller` 直接注入 `application/service/*Service`（入站统一为 Handler / Orchestrator，见 §12.1 P0-7）——**ArchUnit #11 机器拦截**。
2. **禁止** `*Service` 承载聚合不变量（不变量在聚合根或领域服务，见 §12.1 P0-2）——**CR 拦截**，ArchUnit 无对应规则。

**建议**（CR 经验，不机器拦截）：

- 命名 `*Service`；避免 `*Manager`（除非遗留 ADR）。
- 默认**不加** `@Transactional`（写事务边界仍在 `*CommandHandler` / `*Orchestrator`）；若必须，须在类级 Javadoc 说明。
- 依赖 `domain` 端口与其他 Handler；不依赖 `infrastructure` 实现类。

**参考分类**（建议在类级 Javadoc 注明 `S1` / `S2` / `S3` 之一，供 CR 与 AI 生成校验；CR 约束，ArchUnit 不拦截）：

| 标签 | 典型用途 | 仓库示例 |
|------|----------|----------|
| `S1 绑定/协调` | 多 Handler 复用的权限绑定、关联表维护 | `AccountRoleBindingService` |
| `S2 流程运行时` | 技术编排引擎封装，无 HTTP 入站 | `FlowExecutionService`、`FlowRuntime` |
| `S3 缓存/失效` | 横切缓存失效、权限快照刷新 | `AuthorityCacheEvictionService` |

> 反模式：仅 `handler.handle(cmd)` 一行 delegate 的 Service —— 直接删除，调用方改注入 Handler。

#### 14.3.2 `application/facade` 约束（条件追加，非默认）

`application/facade/` 为**可选入站门面层**，仅在满足以下任一触发条件时追加；默认**不建**。

**触发条件（满足任一即可追加）**：

| 编号 | 条件 | 典型场景 |
|------|------|---------|
| **F1** | 同一组用例被 **≥ 2 个入站适配器**复用（HTTP + Dubbo/Feign + MQ + Scheduler） | RPC Provider 与 Controller 需调用同一批 CommandHandler |
| **F2** | 模块对外发布**稳定 Client SDK jar**，需要独立的 `*ServiceI` 接口定义 | `client/api/XxxServiceI` 由 Facade 实现，消费方依赖接口 jar |
| **F3** | 单个 Controller 构造函数注入的 Handler 数量 **> 7**，影响可读性 | 大型聚合（如权限、订单）的 Controller |

不满足以上任一条件时，**不应**建 Facade；Controller 直接注入 Handler。

**铁律**：

1. **Facade 不写领域业务规则**（CR）：聚合不变量、状态流转、业务决策**必须**在 domain；Facade 只做应用级协调（参数组装、权限前置、路由到 Handler）。
2. **Facade 不持有写事务**（CR）：`@Transactional` 的写边界**仍在 `*CommandHandler`**；Facade 若标 `@Transactional` 须在类级 Javadoc 注明原因（`@Transactional(readOnly = true)` 读汇聚除外）。
3. **不取代 Handler**（CR）：Facade 内部**必须**路由到对应 `*CommandHandler` / `*QueryHandler`（或 `*Orchestrator`）；禁止在 Facade 内直接操作 Repository / Domain / Infrastructure。
4. **触发条件不满足则删除**（CR）：若 Facade 每个方法仅一行 `handler.handle(cmd)` 且 F1/F2/F3 均不满足 → 删除 Facade，Controller 改直注 Handler。

**建议**：

- 类命名 `*Facade`；按**业务域/模块**聚合（如 `OrderFacade` 覆盖订单域全部入站用例），不必为每个用例建独立 Facade，也不应建一个覆盖全模块所有聚合的单一 Facade。
- Facade 允许承载**应用级**横切逻辑（入口级幂等检查、批量参数预校验等），但此类逻辑**必须无领域语义**。
- Controller 注入 `*Facade` 不触发 ArchUnit #11（#11 仅拦截 `..application.service..`，`..application.facade..` 不在拦截范围；其余 §14.3.2 铁律由 CR 保证）。
- Facade 可向下调用 `*Orchestrator`（跨聚合编排场景：Controller → Facade → Orchestrator → Handler → Domain）。

**与 COLA 的对照**：

> `*Facade` ≡ COLA `AppService`（应用层入站门面）；`*CommandHandler` ≡ COLA `CmdExe`（用例执行器）。两者共存且职责互补：Facade 解决**入口复用与聚合注入**，Handler 承担**用例执行与事务边界**，Facade **不能替代** Handler。COLA 里 AppService 也是条件存在的（有 Client SDK / 多入口时才有价值），与本节判定逻辑一致。

### 14.4 模块适用性（按**性质**而非物理位置）

DDD 规范是否适用，**按模块性质判定**，不以 `bone-platform/` / `bone-engine/` 目录划分。仓库快照见 [附录 B.1](#b1-模块适用性快照)。

| 性质 | 判定标准 | 规范适用度 |
|------|----------|------------|
| **应用 / 控制面 BFF** | 对外提供 REST API；含 Controller + 业务编排 + 持久化；服务于人类用户或前端 | **完全适用**：§12 P0 全 7 条 + §14（全套包结构与命名）+ §16.3 异常 + §17 D0/D1/D2 + §21 ArchUnit 规则集 |
| **引擎 SDK / 框架库** | 被业务模块依赖、无 Controller、提供 SPI/注解/工具类 | **部分适用**：§12.1.1（依赖向内）+ §17 D0/D1/D2 + §19 ACL；**豁免** §14（包结构按 SPI/库习惯）、§14.3（无 application 概念）、§21 中 P0-4/5/6/7 |
| **基础设施服务** | 纯技术中转，无业务规则 | **部分适用**：依赖方向 + 配置规范；**豁免** §14、§21 业务相关规则 |

**判定通用规则**：

1. **看 Controller**：有 `@RestController` 且暴露业务路径 → **应用模块**，全规范
2. **看依赖方向**：被业务模块 `import` → **SDK / 框架**，可豁免包结构
3. **混合情况**：含 SDK 子模块和 Studio 子模块（如 `bone-extension-engine`）→ **按子模块**分别判定，**Studio = 应用，SDK = 引擎**

**门禁**：新增豁免模块或扩大豁免范围须按 [§0.4](#04-例外登记的统一形态) 写 ADR；任何「应用模块伪装成引擎以逃避规范」在 CR 阻断。

**判定示例（不容歧义）**：

- `bone-extension-engine/bone-extension-studio` = **应用**（暴露 `/api/v1/extension/*`）→ 完全适用 §12 P0；持久化端口命名 `*Repository`，包 `domain/repository/`。
- `bone-extension-engine/bone-extension-sdk` = **SDK**（被业务进程 import）→ 守 §17 D0/D1 即可。
- `studio-generator` = **应用**（暴露 `/api/v1/generator/*`）→ 完全适用 §12 P0；不得存在自造 `@UseCase` / `UseCaseExecutor` 类型与 `*UseCase` 类。

### 14.5 持久化端口命名（强制）

| 维度 | 规则 |
|------|------|
| 包 | `domain/repository/`（**唯一**）；**禁止** `domain/store/` 或任何同义包名 |
| 接口命名 | `*Repository`（如 `OrderRepository`）；**禁止** `*Store`、`*Dao`、`*Mapper` 作为领域端口名 |
| 实现位置 | `infrastructure/persistence/repository/`（或 SDK 代理生成） |
| 与 §18.2 联动 | 仅允许聚合保存 / 删除 / 按 ID 加载（`save` / `saveAll` / `remove` / `removeAll` / `findById` / `findAllById` / `existsById`），加 §18.2 单键白名单（`findBy*` / `existsBy*` 前缀） |

> 既有 `*Store` / `domain.store/` 视为不符合规范，按 [§0.2](#02-存量不符合规范代码的处理) 一律迁移；CI 由 `BoneDddArchRules.noNewDomainStorePackage()` 拦截新增。

---

## 15. 各层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| **adapter** | 协议转换、入参校验、路由；**Controller 直接注入** `*CommandHandler` / `*QueryHandler` / `*Orchestrator`；满足 §14.3.2 F1/F2/F3 时可注入 `*Facade` | 业务规则；注入 `*UseCase`、`application/service/*Service`、`domain/service/*`（领域服务）、`domain/repository/*Repository`；直接操作持久化 |
| **application** | 用例编排、**写事务边界**（`@Transactional` 置于 CommandHandler 或等价边界）、调领域与端口 | 实现本属聚合内的业务不变量；直接拼写/执行 SQL；直接依赖 infrastructure 实现类；`application/usecase` 包或 `*UseCase` 类 |
| **domain** | 规则、聚合、事件、端口定义 | Spring/JPA/MyBatis/Jackson、查询构建器 |
| **infrastructure** | ACL 实现、SDK 配置、技术适配；持久化 `*PO` / `*Converter` / `*RepositoryImpl` | 领域业务规则 |

---

## 16. bone-framework 要点

### 16.1 模块职责（摘要）

| 模块 | 职责（实际类一览）|
|------|------|
| **bone-core** | 实体基类：`Entity`、`AbstractEntity`、`TenantAbstractEntity`、`AggregateRoot`；响应：`ApiResponse`、`PageResult`；上下文：`TenantContext`；异常根：`BizException`、`DomainException`、`NotFoundException`、`InvalidRequestException`、`SystemException` 等（§16.3）；ID：`DistributedIdGenerator`；事件：`DomainEvent` |
| **bone-metadata-sdk** | `@EnableSqlRepositories`、`Repository<T,ID>`、`QueryBuilder` / `FluentQuery`、`@Table` / `@Id` / `@GeneratedValue` 等 |
| **bone-extension-sdk** | `@ExtensionPoint` / `@Extension` |
| **bone-security** | 认证、JWT、密码编码等 |

### 16.2 实体继承（按代码实际）

bone-core 中实体基类形成**两条平行链**（非单一链），业务实体须**二选一**：

```text
Entity<ID>
    ├── AggregateRoot<ID>                 // 领域事件（默认聚合根）
    │       └── TenantAggregateRoot<ID>   // + tenantId（多租户聚合根，阶段 1）
    ├── AbstractEntity<ID>                // Date 审计 + 软删
    │       └── AuditableAggregateRoot<ID> // + domainEvents（Date 审计聚合根）
    └── TenantAbstractEntity<ID>          // + tenantId（com.bone.core.tenant）
```

| 选用基类 | 适用场景 | 业务实体等级 |
|----------|----------|------------|
| `extends AggregateRoot<ID>` | 聚合根 + 领域事件；审计字段自管（如 IAM `LocalDateTime`） | D1 |
| `extends TenantAggregateRoot<ID>` | 多租户聚合根 + 事件；`setTenantId`；审计自管 | D1 |
| `extends AuditableAggregateRoot<ID>` | 聚合根 + 框架 `Date` 审计 + 事件 | D1 |
| `extends TenantAbstractEntity<ID>` | 多租户非聚合根 / 简单 CRUD | D1 |
| `extends AbstractEntity<ID>` | 单租户非聚合根 | D1 |
| `extends Entity<ID>` | 极简 | D1 |

- IAM 等使用 `LocalDateTime` 的模块**暂不**继承 `AuditableAggregateRoot`/`TenantAbstractEntity` 作聚合根（见 [ADR-0011](./adr/0011-aggregate-root-inheritance.md) 阶段 2）。
- **元数据注解**：仅 **bone-metadata-sdk**（见 §17 D1），禁止 JPA `@Entity` 等。
- **D 等级**：上述基类均属 D2（含 `@Data` 等便利注解）；业务实体属 D1；继承产生的 setter 不算违反 D0/D1，见 §17 D2 注脚。

### 16.3 异常与 API（规范）

> **HTTP 与错误体真源**：[Bone-API-规范.md](./Bone-API-规范.md)（Problem Details、错误码台账、201/204/412、LRO、幂等等）。Controller **不得**手写错误 JSON，由全局异常处理器转规范响应（API 规范 §12）。

bone-core 提供的异常类型（`com.bone.core.exception.*`）：

| 异常 | 主要抛出层 | 说明 |
|------|-----------|------|
| `DomainException` | domain | 聚合 / 值对象规则被违反 |
| `BizException` | application | 业务（用例级）异常**根类**，含错误码；用例失败 / 跨聚合策略拒绝 |
| `NotFoundException` | application | 资源不存在（非领域不变量场景） |
| `IdempotentException` | application | 幂等键冲突 / 重复提交 |
| `InvalidRequestException` | adapter | 入参协议不合法 |
| `InfrastructureException` | infrastructure | 基础设施故障的统一抽象 |
| `SystemException` | infrastructure | 系统级故障；**继承 `InfrastructureException`**（ADR-0012），由全局处理器映射 5xx |
| `ServiceException` | infrastructure | 下游服务调用失败 |
| `DistributedLockException` / `LockAcquireFailedException` | infrastructure | 分布式锁相关 |

**强制要求**：

- **业务（用例级）异常**统一以 `com.bone.core.exception.BizException` 为根类（`SystemException` 等可继承）。**禁止**模块自建 `BusinessException` / `*BusinessException`（ArchUnit `noCustomBusinessException` 拦截）。
- 若规则属于「仅在该聚合内成立」，优先 **`DomainException`**；跨聚合编排失败或应用策略拒绝用 **`BizException`**（错误码与 API 规范台账一致）。
- 统一响应：`ApiResponse<T>` / `PageResult<T>`（bone-core）。

---

## 17. 领域模型与纯净度 D0 / D1

| 级别 | 说明 |
|------|------|
| **D0** | 核心业务逻辑：纯 Java + `java.util`；Lombok 仅 `@Getter` + 私有 `@NoArgsConstructor`；**禁止** `@Setter`/`@Data`/`@AllArgsConstructor` |
| **D1** | 在 D0 基础上，**额外允许** `bone-metadata-sdk` 的 `@Table`、`@Id`、`@GeneratedValue` 等元数据注解；**同样禁止** `@Setter`/`@Data`/Spring/Jackson/JPA 注解。若 SDK 升级带来新注解，须经架构评审纳入「D1 白名单」 |
| **D2（基础设施基类例外）** | `bone-core` 的 `AbstractEntity`、`TenantAbstractEntity`、PO（`*PO.java`）等**框架基类与持久化对象**允许 `@Data`、`@AllArgsConstructor` 等便利注解。新增此类例外按 [§0.4](#04-例外登记的统一形态) 在 `bone-framework` 模块 README + ADR 登记 |

> **D2 继承注脚**：业务实体继承 D2 基类（如 `AbstractEntity`、`TenantAbstractEntity`、`AggregateRoot` 的父类 `Entity`）后，通过 Lombok `@Data` 在**编译期获得** setter。这是工程折中，**不视为违反 D0/D1**；但业务代码**不得调用** setter，状态变更必须经领域行为。CR 审查为主；后续可酌情加 PMD/SpotBugs 自定义规则辅助（非强制）。

**聚合**：小聚合、工厂方法、领域行为、事件过去式命名。**扩展点**：跨场景的可插拔逻辑用 **bone-extension-sdk** 的 `@ExtensionPoint` / `@Extension`，禁止超长 `if-else`。

### 17.1 空值与 Optional 约定（与 `CLAUDE.md` 对齐）

| 场景 | 约定 |
|------|------|
| 仓储查询返回 | **禁止返回 `null`**，返回 `Optional<T>`（`findById` 等）或抛 `DomainException`（聚合不变量强制存在时） |
| Application/Adapter 内部返回 | 集合返回空集合（`List.of()`），单对象按业务语义返回 `Optional` 或显式 DTO；**禁止**用 `null` 表达「未找到」 |
| 聚合 / 值对象 | 字段是否可空在工厂方法或值对象构造中显式校验；状态变更经领域行为而非 setter；JSON 序列化层不依赖 `null` 表达业务语义 |
| 三方/遗留 ACL | 在 `infrastructure.gateway` 适配器内**立即**把外部 `null` 转为 `Optional` 或抛错，不让 `null` 漂入应用/领域层 |

> ArchUnit 建议：对 `domain.repository.*Repository` 中方法签名加 `returnsOptionalOrCollection()` 类断言；该规则非 P0，由各模块按需引入。

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
| 非 SDK 表映射 / 遗留 JPA | `infrastructure/.../entity/*PO` + Converter | `ExtStudioExtensionPoint` |
| 内存实现 | `infrastructure/persistence/InMemory*` 实现 `domain.repository` | extension-studio |

---

## 18. ID、仓储与 CQRS（Bone）

### 18.1 ID

- **默认**：应用层生成全局 ID（推荐 `DistributedIdGenerator`），传入聚合工厂；**领域不**依赖 ID 生成器。  
- **主键策略**：**默认禁止**以数据库 **`IDENTITY` / 自增列** 作为**领域主标识**（避免与分布式 ID、跨库迁移、合并冲突处理不一致）。  
- **例外**：遗留表或强约束场景必须使用自增时，按 [§0.4](#04-例外登记的统一形态) 在模块 README 登记（表名、字段、范围、退役计划），且领域层仍不直接依赖 ID 生成技术；新表 **原则上不新开** 自增领域主键。

### 18.2 写侧 Repository

子接口 **仅继承** SDK 基 `Repository`，**不新增**带 **多个业务条件组合**、分页、排序、Join 的方法（此类一律走读侧 §18.3）。

**单键辅助方法（可选）**：仅允许 **单一等值条件** 的 `existsByXxx` / `findByXxx`，语义须为业务外键或唯一码（如 `findByCode`、`existsByEmail`）。**禁止** `findByStatusAndType` 等多条件组合形式（ArchUnit `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` 拦截 `findBy*And*` 等）。新增方法在 PR 评审中按本条判定，不另设模块级二次白名单。

### 18.3 读侧

`QueryBuilder.from(Entity.class)` 或等价 `FluentQuery` / `Criteria`；读侧 DSL 类型须标注 `@ReadSideOnly`（`bone-core`），ArchUnit 按注解依赖检测。复杂 SQL 放 `infrastructure.query`。读侧 DSL **禁止**出现在 `domain` 与 `application.command.handler`。

### 18.4 启动扫描（参考）

```java
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.{module}.domain.repository")
public class Application { }
```

### 18.5 读侧端口（`*ReadPort`，ADR-0013）

当列表/搜索/统计等读操作**不宜**或**无法**用 `QueryBuilder`（§18.3）表达时（如内存仓储、元数据混合持久化、专用读模型），在 **`domain/gateway/*ReadPort`** 声明读侧专用端口；`*QueryHandler` 只依赖 `*ReadPort`，写侧仍用 `domain/repository/*Repository` 白名单方法（§18.2）。实现类可在 `infrastructure/persistence/` **同一类**双接口实现（`implements XxxRepository, XxxReadPort`）。

**读路径决策树**：

```text
需要读取领域数据？
├─ 主键 / 单业务键加载聚合 → 写侧 Repository.findById / findByCode（§18.2）
├─ 多条件 / 分页 / 排序 / Join / 报表 → QueryBuilder + @ReadSideOnly（§18.3）
├─ 列表 / 搜索 / count，且 QueryBuilder 不适用 → domain/gateway/*ReadPort（本条）
└─ 跨限界上下文 / 外部系统 → ACL Gateway（§19）或集成事件投影
```

**`domain/gateway/` 包内职责区分**（命名后缀区分，勿混用）：

| 后缀 / 用途 | 示例 | 职责 |
|-------------|------|------|
| `*ReadPort` | `ExtPointReadPort` | **模块内**读侧：列表、搜索、统计 |
| `*Gateway` / `*Port`（出站） | `PaymentGateway`、`InventoryGateway` | **跨边界**出站 ACL（外部 HTTP/RPC/MQ） |

新增读方法**只加在 `*ReadPort`**，禁止回写到 `*Repository` 规避白名单。首版见 [ADR-0013](./adr/0013-extension-studio-repository-read-side.md)（`bone-extension-studio`）。

---

## 19. ACL（Bone）

出站端口在 **domain**（如 `PaymentGateway`），实现在 **infrastructure**；禁止在 `application` 直接依赖第三方 HTTP/RPC **实现类型**。

---

## 20. Flow / AI 编排（可选）

若使用 Flow、`@Capability` 等：

- **能力声明**：在 `*CommandHandler` / `*QueryHandler` / `*Orchestrator` 上加 `@Capability` 元数据；**禁止**为「让 AI 发现」而新建 `*UseCase` 门面。
- **能力发现**：通过独立的 capability 注册表（如 `bone-core` 的能力扫描器）按注解汇总，**与 DDD 用例命名解耦**。
- **执行入口**：Flow / AI 调度统一通过注入 Handler / Orchestrator 调用，遵循 §15 事务边界。
- **底线**：编排能力**不替代**聚合与 §12 铁律；命名对齐通用语言。

---

## 21. 测试与 CI

**共享规则库**：`bone-framework/bone-architecture-test`（`BoneDddArchRules`），各应用模块以 `test` scope 依赖引用，避免每模块复制规则。

**ArchUnit 最小规则集**（每应用模块 `src/test/java/.../architecture/ArchitectureTest.java`，**与 §12 P0 一一对应**）：

| # | 规则方法（`BoneDddArchRules.*`） | 对应规范 |
|---|----------------------------------|----------|
| 1 | `domainMustNotDependOnOuterLayers` | P0-1 |
| 2 | `applicationMustNotDependOnInfrastructure` | P0-1 |
| 3 | `domainMustNotUseQueryBuilder` | P0-5 |
| 4 | `commandHandlersMustNotUseQueryBuilder` | P0-6 |
| 5 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` | P0-4 + §18.2 |
| 6 | `noUseCaseClassesInApplication` + `noApplicationUseCasePackage` | P0-7 + §14.3 |
| 7 | `noBoneCoreUseCaseApiDependency`；`studio-generator` 加 `noStudioGeneratorUseCaseAnnotation` | P0-7 |
| 8 | `noNewDomainStorePackage` | §14.5 |
| 9 | `noCustomBusinessException` | §16.3 |
| 10 | `noBusinessExceptionSuffix` | §16.3 |
| 11 | `adapterControllersMustNotDependOnApplicationService` | P0-7 + §15（仅拦截 `..application.service..`；`..application.facade..` 不受此规则限制） |
| 12 | `adapterControllersMustNotDependOnDomainRepository` | §15 |
| 13 | `commandHandlersShouldBeNamedCommandHandler` | §23 |
| 14 | `queryHandlersShouldBeNamedQueryHandler` | §23 |
| 15 | `commandHandlersShouldBeTransactional` | §15 |
| 16 | `queryHandlersShouldBeReadOnlyTransactional` | §12.1 P0-6（建议） |
| 17 | `adapterControllersMustNotDependOnDomainService` | P0-7 + §15（Controller 禁直注 `domain/service` 领域服务） |

读侧检测：`domainMustNotUseQueryBuilder` / `commandHandlersMustNotUseQueryBuilder` 拦截对 `@ReadSideOnly` 类型的依赖（非写死类名）。`noBoneCoreUseCaseApiDependency` **不 freeze**（防回滚、无存量命中）。

**Freeze 建议**（2026-05-23）：`applicationMustNotDependOnInfrastructure`、仓储白名单、QueryBuilder 禁令、adapter/Handler 命名与事务规则在 **`bone-blueprint` 参考样板不 freeze**（须 0 违规）；其它应用模块对上述 #11–#17 规则 **freeze 存量**，迁移后 `allowStoreUpdate=true` 收缩基线。`noUseCase*` / `noNewDomainStore` / `noCustomBusinessException*` 继续 freeze 防回潮。

**存量违规处理**：使用 `FreezingArchRule` 登记当前违规快照（基线 `archunit_store/`），仅拦截**新增**；迁移后基线收缩，**不允许扩张**。

```java
@ArchTest
static final ArchRule no_new_use_cases =
    FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());
```

> 首次集成或基线更新：`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true`。详见 `bone-framework/bone-architecture-test/README.md`。

**单测**：聚合与值对象规则（无容器）；**集成测**：用例与端口（Testcontainers 等）；**跨服务**：契约测试（OpenAPI / Pact 等）。**覆盖率**：与仓库质量门禁对齐，核心域优先提高阈值。

---

## 22. 极简 / 轻量 / 低成本（Bone 默认心态）

| 做法 | 建议 |
|------|------|
| **CQRS** | 保留 `command/query` 分包；不默认独立读库、事件投影。 |
| **应用层** | 默认 Controller → Handler → Domain **三步**（adapter → application → domain）；**不引入** UseCase；满足 §14.3.2 F1/F2/F3 时按条件追加 `*Facade`；`application/service` 仅按 §14.3.1 约束；跨聚合编排按 §14.3 ADR 加 `*Orchestrator`。 |
| **扩展点 / ACL / MQ / RPC / 定时** | 无真实需求则不建。 |
| **战略文档** | 小模块一页纸术语表起步。 |
| **领域事件** | 无跨聚合协调时可少发。 |

**bone-blueprint** 为全特性参考样板；新建业务对齐 **§14.2 极简包 + §12.1 P0 铁律 + §14.3 应用层结构**，按需求再扩。

### 22.1 新模块快速入门（5 步）

> 新建一个**应用 / 控制面 BFF** 模块时，按以下顺序操作即可满足本规范。复杂用例再按对应章节扩展。

1. **定边界**：在模块 `README.md` 写一页纸：上下文名称、职责一句、对外契约、数据所有权（[§13.1](#131-限界上下文)）；核心术语 5–20 条对照表（[§13.2](#132-通用语言)）。
2. **起包结构**：按 [§14.2](#142-极简树小模块默认够用) 极简树创建 `adapter/web/`、`application/command/`、`application/query/`、`domain/{aggregate}/`、`domain/repository/`；持久化层按 [§17.2](#172-持久化对象决策树) 决策树放 D1（元数据驱动）或 `infrastructure/persistence/entity/` 的 PO（遗留映射）。
3. **写第一个用例**：Controller → `CreateXxxCommandHandler` → 聚合根工厂方法 → `XxxRepository.save(...)`；命名遵守 [§23](#23-命名约定)；异常按 [§16.3](#163-异常与-api规范) 选择（聚合不变量用 `DomainException`，用例级失败用 `BizException`，资源不存在用 `NotFoundException`，**禁止**自建 `BusinessException`）。
4. **接 ArchUnit**：`pom.xml` 加 `bone-architecture-test` 测试依赖；复制 [附录 B.3 模板](#b3-archunit-模板bone-architecture-test) 到 `src/test/java/<module>/architecture/ArchitectureTest.java`，把包名改成本模块；首次跑 `-Darchunit.freeze.store.default.allowStoreCreation=true` 生成基线后提交 Git。
5. **持续守护**：日常 CI 不开启 `allowStoreCreation`；任何例外按 [§0.4](#04-例外登记的统一形态) 按影响半径登记；每次重构后 freeze 基线**只收缩、不扩张**。

> 不在以上 5 步默认范围内的（事件发布、ACL、Orchestrator、Flow / AI）按需引入，并对应阅读 §3.3 / §19 / §14.3 / §20。

### 22.2 AI / Agentic 生成守则

1. 禁止生成 `*UseCase`、`application/usecase/**`、模块自建 `*BusinessException`。
2. 禁止在 `domain/**` 使用 `@Data` / `@Setter`；状态变更经领域行为。
3. 修改 `domain/**` 后 ArchUnit freeze 基线**只收缩、不扩张**。
4. 跨 2+ 聚合编排须产出 `*Orchestrator` + ADR 草稿，不得新增 UseCase 门面。
5. 不得修改 `BizException` / `DomainException` / `InfrastructureException` 根类型语义（ADR 流程除外）。
6. 生成 `*Facade` 前须验证触发条件（§14.3.2 F1/F2/F3）；生成时在类级 Javadoc 注明触发条件编号；禁止在 Facade 内写领域规则或直接依赖 Repository/Domain。

代码示例与适应度指标见 [ddd/07-supplements.md](./ddd/07-supplements.md)。

---

## 23. 命名约定

### 23.1 分层命名（应用层 vs adapter DTO）

| 层级 / 包 | 命令 | 查询 | 说明 |
|-----------|------|------|------|
| `application/command/cmd/` | `*Command` | — | **禁** `*Cmd` 类名；目录名 `cmd/` 可保留 |
| `application/query/qry/` | — | `*Query` | **禁** `*Qry` 类名；目录名 `qry/` 可保留 |
| `application/command/handler/` | — | — | 类名 **必须** `*CommandHandler` |
| `application/query/handler/` | — | — | 类名 **必须** `*QueryHandler` |
| `adapter/web/dto/request/` 或 `adapter/web/dto/` | `*Req` | `*Qry`（可选） | 与 [Bone-API-规范](./Bone-API-规范.md) §12 对齐；**仅 adapter 入参**，非 application 层 Query 对象 |
| `adapter/web/dto/response/` | — | `*Resp` | REST 出参 |

> **易混点**：`FlowPageQuery`（application）与 `ExtPointPageQry`（adapter 入参）可并存；禁止把 application 层查询类命名为 `*Qry`。

### 23.2 其它命名

| 类型 | 示例 | 备注 |
|------|------|------|
| 领域事件 | `OrderPaidEvent` | 过去式 |
| 集成事件 | `OrderPaidIntegrationEvent` | 跨边界契约；后缀 `IntegrationEvent` |
| 编排器（例外） | `OrderRefundOrchestrator` | 仅 §14.3 例外允许；放 `application/orchestration/` |
| 入站门面（条件） | `OrderFacade` | 仅 §14.3.2 F1/F2/F3 条件下追加；放 `application/facade/`；禁止叫 `*AppService` / `*Service` |
| 写侧端口 | `OrderRepository` | §14.5：禁 `*Store`/`*Dao`/`*Mapper` |
| 读侧端口 | `OrderReadPort` | §18.5：列表/搜索/统计 |
| 出站 ACL | `PaymentGateway` | §19 |
| 应用服务（受约束） | `OrderShippingService` | §14.3.1：禁 Controller 直注、禁承载聚合不变量；禁 `*Manager` |

> **与 COLA 术语对照**（仅供跨框架沟通参考，不改 Bone 命名规范）：
> `*CommandHandler` ≡ COLA `*CmdExe`；`*QueryHandler` ≡ COLA `*QryExe`；`*Facade` ≡ COLA `AppService`（入站门面）；`application/service/*Service`（S1/S2/S3）无 COLA 直接等价物（COLA 里此类逻辑通常内聚在 CmdExe/DomainService 内）。
> Bone 选用全词 `*CommandHandler` / `*QueryHandler` 而非 COLA 缩写 `*CmdExe` / `*QryExe`，原因：与 CQRS/MediatR/Axon 业界通用术语对齐；全词命名在 Code Review、日志、堆栈中可读性更优；`Executor` 缩写在 Java 生态与 `java.util.concurrent.Executor` 存在语义歧义。

---

## 24. 修订与 Owner

- **唯一权威**：架构组；规则变更须 [§0.3](#03-破坏性变更流程) 流程。
- **边界变更**：先更新术语表与第一部分相关认知，再改第二部分门禁与代码。

---

## 附录 A：与旧 Bone-Blueprint 版本号的关系

历史版本号（v7 / v9.5 / v16.3 / v24 等）仅表示过往迭代；**门禁以第二部分** §12（铁律）、§14～§23 中与**结构、领域持久化、ACL、测试、极简、命名**相关的条文为准（**第二部分** §10「目标」～§11「与第一部分对齐」为 Bone 目标与对齐摘要，非逐条 CI）。产品文档中「Bone-Blueprint」可与本方案同义指称。

---

## 附录 B：模块适用性快照与不符合规范的处置

> 本附录**只描述客观分类与处置形态**，不规定时间、批次、Owner。落地排期由各模块 Maintainer 在内部跟踪。

### B.1 模块适用性快照

#### B.1.1 应用 / 控制面 BFF（完全适用 §14.4）

| 模块 | 物理位置 |
|------|----------|
| `bone-platform/bone-iam` | bone-platform/ |
| `bone-platform/bone-masterdata` | bone-platform/ |
| `bone-platform/bone-integration` | bone-platform/ |
| `bone-platform/bone-system` | bone-platform/ |
| `bone-platform/bone-notification` | bone-platform/ |
| `bone-engine/bone-extension-engine/bone-extension-studio` | bone-engine/（按 §14.4 性质判定为应用） |
| `bone-engine/studio-generator` | bone-engine/（按 §14.4 性质判定为应用） |
| `bone-blueprint` | 根目录（参考样板） |

#### B.1.2 引擎 SDK / 框架库（按 §12.1.1 豁免 + §17 强制）

| 模块 | 备注 |
|------|------|
| `bone-framework/bone-core` | 提供 `com.bone.core.capability`（`@Capability`、`HandlerRegistry`）；**已删除** `UseCaseExecutor` / `@UseCase` |
| `bone-framework/bone-web` / `bone-security` / `bone-utils` / `bone-datasource` | 工具 / 基础设施类 |
| `bone-engine/bone-metadata-sdk` / `bone-metadata-server` / `bone-metadata-engine` | metadata SPI |
| `bone-engine/bone-extension-engine/bone-extension-sdk` | 业务进程内嵌 SDK |
| `bone-engine/bone-workflow`、`bone-procurement` | 引擎库 |
| `bone-sdk/*` | 客户端 SDK |

#### B.1.3 基础设施服务（依用例判定；默认豁免 §14）

| 模块 | 备注 |
|------|------|
| `bone-platform/bone-gateway` | 纯路由 / 协议转换，无业务模型 |
| `bone-platform/bone-file` | 若仅对象存储中转可豁免；含业务规则按应用处理 |

### B.2 不符合规范代码的处置形态

| 不符合规范形态 | 处置 | 推荐工具 |
|----------------|------|---------|
| `*UseCase` 仅 delegate 到同名 `*Handler` | 删除 `*UseCase`；Controller 改注入 `*Handler`，测试同步 rename | `scripts/migrate-usecase-to-handler.py` |
| `*UseCase` 在 delegate 前含参数装配 | 装配下沉到 `adapter/web/converter` 或 Handler 入参；删 `*UseCase` | 手工 + 上述脚本 |
| `*UseCase` 调用 2+ 个 Handler（真编排） | 改名 `*Orchestrator` 放 `application/orchestration/`；ADR 记录编排步骤、补偿、Saga | 手工 |
| `*UseCase` 被 AI/Flow 通过 `@UseCase` 发现 | 在 Handler / Orchestrator 上加 `@Capability`；调度方按 `@Capability` 发现 | 手工 |
| `application/usecase/**` 目录残留 | 全部清空后删除目录；**禁止**再引用已删除的 `com.bone.core.usecase.*` | `scripts/migrate-usecase-to-handler.py` |
| 自造 `@UseCase` 注解 / `UseCaseExecutor` 接口 | 整体删除；调用方按上面四类分别迁移 | 手工 |
| `domain/store/*Store` | 接口重命名 `*Repository`，包改名 `domain/repository/`；调用方批量替换 | `scripts/migrate-extension-studio-store-to-repository.py` 可改造为通用模板 |
| **模块根包**下的 `controller/`（如 `com.bone.xxx.controller.*`，**不在** `adapter/web/controller/`） | 迁移到 `adapter/web/controller/`；按 §14.1 重组 `dto/request\|response`、`assembler/` | `git mv` + StrReplace |
| 命名 `*Cmd` / `*Qry` | 类名重命名为 `*Command` / `*Query`；子包 `cmd/` / `qry/` 可保留作短目录名（§14.1） | `scripts/ddd-rename-cmd-qry.py <module> --apply` |
| 模块自建 `BusinessException` / `*BusinessException` | 全部改用 `com.bone.core.exception.BizException` 或 `*BizException` 后缀（如 `MetadataEngineBizException`、`ExtensionBizException`）；删除自建 `BusinessException` 类 | 手工 + ArchUnit `noBusinessExceptionSuffix` |
| `Controller` 直接注入 `application/service/*Service` | Controller 改注入 `*CommandHandler` / `*QueryHandler` / `*Orchestrator`；`*Service` 仅供 Handler 内部复用 | `scripts/migrate-extension-studio-service-to-application.py`（模板） |
| `Controller` 直接注入 `domain/service/*`（领域服务） | Controller 改注入对应 Handler；领域服务由 Handler 在应用层编排调用（ArchUnit #17 机器拦截） | 手工 + ArchUnit `adapterControllersMustNotDependOnDomainService` |
| 缺少 ArchUnit 守护（新模块或老模块） | 使用 `scripts/ddd-archtest-template.py <module> <root-package> [--extra-archunit ...]` 一行生成 `ArchitectureTest`，再 `allowStoreCreation=true` 生成基线 | `scripts/ddd-archtest-template.py` |

### B.3 ArchUnit 模板（`bone-architecture-test`）

> **使用说明**：
> 1. 将下方 `com.bone.iam` 替换为本模块根包（如 `com.bone.masterdata`、`com.bone.system` 等）。
> 2. 所有 `FreezingArchRule.freeze(...)` 包裹的规则需要先生成基线再提交：`mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true`。基线放在 `<module>/archunit_store/`，提交入库。
> 3. **Freeze 策略（2026-05-26）**：`application_no_infra`、`repository_methods_whitelist`、`noBoneCoreUseCaseApiDependency`、QueryBuilder 禁令 **不 freeze**（直接门禁）；`noUseCase*`、`noNewDomainStore`、`noCustomBusinessException*` **继续 freeze** 防回潮；**`adapter_no_*`（#11/#12/#17）、Handler 命名与事务规则（#13–#16）在非 `bone-blueprint` 模块首次接入时 freeze 存量**，迁移后收缩基线。见 `bone-architecture-test/README.md`。

```java
import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.iam", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // P0-1
    @ArchTest static final ArchRule domain_independent =
            BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest static final ArchRule application_no_infra =
            BoneDddArchRules.applicationMustNotDependOnInfrastructure();

    // P0-5
    @ArchTest static final ArchRule domain_no_query_builder =
            BoneDddArchRules.domainMustNotUseQueryBuilder();

    // P0-6
    @ArchTest static final ArchRule command_no_query_builder =
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

    // P0-4 + §18.2（仓储方法名白名单）
    @ArchTest static final ArchRule repository_methods_whitelist =
            BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

    // P0-7 + §14.3
    @ArchTest static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

    @ArchTest static final ArchRule no_bone_core_usecase =
            BoneDddArchRules.noBoneCoreUseCaseApiDependency();

    // §14.5
    @ArchTest static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

    // §16.3
    @ArchTest static final ArchRule no_custom_business_exception =
            FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

    @ArchTest static final ArchRule no_business_exception_suffix =
            FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

    // P0-7 + §15 + §23（参考样板 bone-blueprint：以下 7 条不 freeze；其它模块 freeze 存量）
    @ArchTest static final ArchRule adapter_no_application_service =
            BoneDddArchRules.adapterControllersMustNotDependOnApplicationService();

    @ArchTest static final ArchRule adapter_no_domain_repository =
            BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository();

    @ArchTest static final ArchRule adapter_no_domain_service =
            BoneDddArchRules.adapterControllersMustNotDependOnDomainService();

    @ArchTest static final ArchRule command_handler_naming =
            BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler();

    @ArchTest static final ArchRule query_handler_naming =
            BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler();

    @ArchTest static final ArchRule command_handler_transactional =
            BoneDddArchRules.commandHandlersShouldBeTransactional();

    @ArchTest static final ArchRule query_handler_transactional =
            BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional();
}
```

`studio-generator` 额外：`BoneDddArchRules.noStudioGeneratorUseCaseAnnotation()`。

**Freezing 基线**：各模块根目录 `archunit_store/`，须提交 Git。

| 场景 | 命令 |
|------|------|
| 首次生成 | `mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true` |
| **收缩基线**（存量违规已消除） | `mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreUpdate=true` |
| 全量覆盖快照（慎用） | `-Darchunit.freeze.store.default.refreeze=true`（见 `bone-architecture-test/README.md`） |

详见 `bone-framework/bone-architecture-test/README.md`。

本文结束。
