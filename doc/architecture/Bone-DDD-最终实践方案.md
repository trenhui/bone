# Bone 领域驱动设计（DDD）统一实践方案

> **单文档决策**：依据 [ADR-0026](./adr/0026-ddd-single-document-consolidation.md)，整合完成后本文是 Bone DDD 原则、工程决策、门禁口径与实施状态唯一、自包含的规范真源。
> **整合状态**：已完成主文档内容整合、生效引用迁移和原分册删除；本文是 Bone DDD 唯一规范入口与实施状态真源。
> **版本**：5.3.1（Application Service First 样板与门禁收敛，2026-09-14）
> **决策**：[ADR-0024](./adr/0024-ddd-v5-rule-semantics-and-document-split.md)、[ADR-0025](./adr/0025-ddd-v5-0-2-implementation-alignment.md)、[ADR-0026](./adr/0026-ddd-single-document-consolidation.md)、[ADR-0028](./adr/0028-application-service-first-selective-cqrs.md)
> **通用语言**：[glossary.md](../glossary.md)
> **版本历史**：[附录 F：版本历史](#version-history)

## 文档边界

本文区分三类内容：

- **P- 原则**：主流 DDD、六边形架构与演进式架构的共识；
- **E- 工程决策**：Bone 在当前技术栈和组织条件下的选择，不宣称为行业唯一答案；
- **G- 门禁**：机器检查、语义评审、风险提示和规划项的真实状态。

若三者出现张力：

1. 先保护业务边界、不变量和数据所有权；
2. 再选择最简单的工程形态；
3. 机器规则不能超出其真实证明能力；
4. 破坏性调整通过 ADR 记录。

## 主文档导航

- [一页纸速览](#一页纸速览)
- [第一部分 原则（P-）](#第一部分-原则p-)
- [第二部分 Bone 工程决策（E-）](#第二部分-bone-工程决策e-)
- [第三部分 门禁与实施状态（G-）](#第三部分-门禁与实施状态g-)
- [附录 A：迁移台账](#migration-ledger)
- [附录 B：历史实施快照](#附录-b历史实施快照)
- [附录 C：团队快速入门](#附录-c团队快速入门)
- [附录 D：代码与支付样板](#payment-sample)
- [附录 E：架构适应度仪表盘](#architecture-fitness-dashboard)
- [兼容入口与迁移说明](#兼容入口与迁移说明)
- [附录 F：版本历史](#version-history)
- [Owner 与修订](#owner-与修订)

### 原拆分内容锚点映射

以下映射固定原拆分内容的主文档归宿；锚点名称保持稳定。

| 原拆分内容 | 主文档唯一锚点 |
|------------|----------------|
| 业务限界上下文、关系、数据所有权与技术依赖图 | [`context-map-业务限界上下文`](#context-map-业务限界上下文) |
| 应用用例边界 | [`application-use-case-boundary`](#application-use-case-boundary) |
| CQRS 与端口位置 | [`cqrs-port-location`](#cqrs-port-location) |
| 事务、可靠发布、并发与持久化模型 | [`reliable-event-publishing`](#reliable-event-publishing) |
| 机器门禁、语义评审、Advisory 与实施状态 | [`g-1-1-hard-gate`](#g-1-1-hard-gate) |
| 存量迁移、freeze 和技术债 | [`migration-ledger`](#migration-ledger) |
| 命名风格 | [`naming-style`](#naming-style) |
| 支付聚合、回调、幂等与验签样板 | [`payment-sample`](#payment-sample) |
| 团队快速入门与外部方法概览 | [一页纸速览](#一页纸速览) |
| 示例汇编与适应度指标 | [`architecture-fitness-dashboard`](#architecture-fitness-dashboard) |
| 带日期的 ACL / ArchUnit 实施快照 | [`historical-implementation-snapshot`](#historical-implementation-snapshot) |
| 规范版本历史 | [`version-history`](#version-history) |

原拆分文件在 Task 7 删除前仅用于合并完整性核对；不得将其作为阅读入口、新引用目标、同步目标或独立规则真源。

---

# 一页纸速览

## 10 条核心规则

核心规则使用稳定的 `CORE-*` 标识，避免与 v4.x 的 R1–R9 历史编号混淆。

| # | 规则 | 最小判定 |
|---|------|----------|
| CORE-01 | 边界先于分层 | 每个限界上下文有语言、Owner、契约与数据所有权 |
| CORE-02 | 依赖向内 | domain 不依赖具体运行时技术与 IO 实现；外层经端口依赖内层 |
| CORE-03 | 领域行为保护不变量 | 状态迁移与业务决策在聚合、值对象或领域服务 |
| CORE-04 | 一个用例一个应用边界 | Handler、ApplicationService、Orchestrator 不一对一套娃 |
| CORE-05 | 写侧服务聚合，读侧服务投影 | Repository 不做报表；QueryPort 位于 application |
| CORE-06 | 默认单聚合实例事务 | 跨聚合默认最终一致；例外说明理由与失败语义 |
| CORE-07 | 先声明可靠性与并发保证 | Outbox、乐观锁是默认实现，不是唯一实现 |
| CORE-08 | 门禁不冒充领域证明 | 静态检查证明结构；业务语义由测试和评审证明 |
| CORE-09 | EAV 只承载扩展字段 | 核心事务、高频查询、聚合字段用物理列；EAV/JSON 仅限可选、动态、长尾扩展属性 |
| CORE-10 | Metadata 描述模型，不执行业务 | 元数据回答 What（模型定义）；业务规则（How）在应用/领域层执行 |

## 依赖方向

```text
adapter → application → domain
                ↓          ↑
           output ports    │
                ↑          │
          infrastructure ──┘
```

- adapter 可以调用公开应用用例。
- application 可以调用 domain 和出站端口。
- infrastructure 实现 domain/application 定义的出站端口。
- domain 不依赖 Spring 运行时、Web、数据库、MQ、Jackson 或查询 DSL；D1 仅允许 [E-6](#e-6-领域模型与持久化模型) 定义的白名单编译期注解。

## 默认交付路径

```text
写：Controller → ApplicationService → Repository.load → Aggregate.behavior → Repository.save
读：Controller → ApplicationService.get()/page() → Repository        （简单读，读模型与 domain 一致）
跨步骤：Adapter → Orchestrator → 独立事务步骤 → 补偿/重试
```

默认使用**语义化 ApplicationService** 作为入站边界（[ADR-0028](./adr/0028-application-service-first-selective-cqrs.md)）。复杂度升级时才引入 `Command` / `Handler`：写意图需显式契约或多入口时加 `Command`，命令异步/跨事务时 `CommandHandler` 落在 Outbox 消费端/任务端，读模型与聚合分歧时 ApplicationService 内升级 QueryPort（复杂读再进 `QueryHandler`）。判据见 [E-3.7](#E-37-入口构件决策)。

## 裁剪档位

| 档位 | 适用 | 最低要求 |
|------|------|----------|
| L0 | 框架、SDK、纯技术服务 | 依赖方向、公开契约、无业务模型污染 |
| L1 | CRUD 支撑域 | 边界、租户、写仓储、基础门禁（简单读经写仓储 / ApplicationService，复杂读才用 QueryPort） |
| L2 | 有状态机或复杂规则的应用域 | 聚合行为、纯领域测试、并发策略、领域事件 |
| L3 | 跨进程不可丢事件、回调、资金流程 | 原子发布、消费幂等、契约测试、补偿与可观测 |

档位由业务复杂度和集成保证决定，不由目录位置或“模块重要性”决定。

## 按角色阅读

| 角色 | 首次阅读 | 日常使用 |
|------|----------|----------|
| 新成员 | 一页纸速览 → P-2/P-3 → E-3/E-4 → E-12 | [附录 C：团队快速入门](#附录-c团队快速入门) |
| 领域/产品 | P-2 战略设计 → Context Map → glossary | 上下文、语言与业务规则评审 |
| 后端工程师 | P-3/P-5 → E-3～E-10 → G-1 | 用例、聚合、事务与端口评审 |
| 架构师 | 全文 → G-1 门禁 → 附录 A 迁移台账 | ADR、规则准入与例外治理 |
| 外部读者 | [附录 C 的问题与经验摘要](#c1-先记住四句话) | 按链接深入本文对应章节 |

第一次阅读应连续读完本文核心正文；实施状态、迁移历史和扩展示例均在本文对应章节查询。

---

# 第一部分 原则（P-）

## P-1 适用范围

系统化 DDD 适用于：

- 业务语言复杂且持续演进；
- 规则、不变量和状态迁移需要长期维护；
- 多团队或多系统之间需要明确边界；
- 团队愿意投入领域建模和契约治理成本。

纯 CRUD、无演进压力的简单能力可以使用轻量模型，不为“看起来像 DDD”制造聚合、事件和目录。

**复杂度检查**：如果一个模块的全部写操作可以用一个 `*ApplicationService` 的 5–8 个方法清晰表达（create / update / delete + 2–3 个业务操作），且读操作可以用同一服务覆盖，则不应引入 Command / Handler 分层。判断标准是"一个类能不能说清楚这个模块做什么"——能，就不拆。

违反此检查的常见信号：

- 新建模块第一天就生成 `command/`、`commandhandler/`、`query/`、`queryhandler/` 四个空目录；
- 一个只有 CRUD 的支撑域却有 15+ 个应用层类；
- ApplicationService 和 CommandHandler 各自只有一个方法且方法体只有一行委派。

## P-2 战略设计优先

<a id="context-map-业务限界上下文"></a>

### P-2.1 战略分类

Bone 当前只认定 Metadata / Smart Metadata 为核心域；这是当前产品差异化投资决策，不是“DDD 只能有一个核心域”的行业规则。分类应随产品战略复核。

| 子域 | 类型 | 分类依据 |
|------|------|----------|
| Metadata / Smart Metadata | 核心域 | 动态建模、catalog 与元数据运行时构成 Bone 当前主要差异化能力 |
| MasterData | 支撑域 | 基于元数据能力提供主数据治理 |
| Integration | 支撑域 | 连接器、流程编排与执行治理 |
| Extension | 支撑域 | 扩展点与插件生命周期 |
| Notification | 支撑域 | 通知渠道与发送记录 |
| Generator | 支撑域 | 模板、数据源与代码生成任务 |
| Payment | 支撑域样板 | 仅用于演示聚合、回调、幂等与可靠事件 |
| IAM | 通用域 | 身份、认证与授权 |
| System | 通用域 | 配置、日志与监控 |

核心域不是“最复杂模块”或“技术上不可替代模块”的同义词。复核时至少回答：

1. 客户选择 Bone 的主要购买理由是什么？
2. 该能力相对竞品形成了什么可持续差异？
3. 领域专家和研发资源是否持续优先投入？
4. 替换该能力是否会改变 Bone 的产品定位？

### P-2.2 业务限界上下文

限界上下文是模型和语言的适用边界，不等同于 Maven 模块、微服务或数据库。

每个上下文至少明确：

- 名称与职责；
- 核心通用语言；
- Owner；
- 对外 API/事件契约；
- 数据与写入规则所有权；
- 相邻上下文关系。

| 限界上下文 | 核心职责 | 主要语言 | 代码模块 |
|------------|----------|----------|----------|
| Metadata | 建模 catalog、模型校验、元数据运行时 | `MetaEntity`、`MetaField`、`MetaRelation`、Runtime | `bone-metadata-server`、`bone-metadata-engine`；SDK 为技术支撑 |
| IAM | 账号、租户、角色、权限与认证 | `Account`、`Role`、`Permission`、`Tenant` | `bone-iam` |
| MasterData | 主数据实体、记录与质量治理 | `MasterEntity`、`MasterRecord` | `bone-masterdata` |
| Integration | 连接器、流程、执行与重试 | `Connector`、`Flow`、`FlowExecution` | `bone-integration` |
| Extension | 扩展点、插件、版本与发布 | `ExtensionPoint`、`Plugin`、`PluginVersion` | `bone-extension-studio` |
| System | 平台配置、运维日志与监控 | `SysConfig`、`SysLog`、`Monitor` | `bone-system` |
| Notification | 通知、渠道与发送记录 | `NotificationRecord`、`Channel` | `bone-notification` |
| Generator | 模板、数据源与生成任务 | `Template`、`DataSource`、`GenerationTask` | `bone-engine/studio-generator` |
| Payment | 支付单生命周期样板 | `Payment`、`ChannelTradeNo` | `bone-blueprint` |

代码组织原则：

- 一个模块只能归属一个上下文；
- 一个上下文可以由多个内聚模块组成；
- 是否拆服务由发布、团队、伸缩和故障隔离决定。
- SDK、框架库和网关不因存在 Maven 模块就自动成为限界上下文。
- 表中模块名是 Maven 模块短名；实际路径以根 `pom.xml` 为准，例如 `bone-engine/bone-extension-engine/bone-extension-studio`。
- `bone-init.sql` 中的 `ic_*` 保险表示例数据尚未被认定为限界上下文。

### P-2.3 通用语言

- 产品、领域专家、研发和测试使用同一业务词汇。
- 聚合、命令、事件、API 与关键字段体现该语言。
- 同名不同义必须加上下文限定。
- 技术词不能替代业务概念。

### P-2.4 上下文映射

Context Map 只描述业务上下文之间的关系：

- Partnership；
- Customer–Supplier；
- ACL；
- OHS；
- Published Language；
- Shared Kernel；
- Conformist。

Bone 当前业务上下文关系（图与下表以关系表为真源，逐行对账绘制）：

![Bone 限界上下文映射图](assets/context-map.svg)

> 图注：实线为已登记的正式业务关系；紫色虚线为 OHS / Published Language 母线（IAM、System → 所有应用上下文）；Payment 为 blueprint 样板，虚框隔离、不入产品主链路。历史 ASCII 图中的「Metadata ─C-S→ Extension」经代码裁决废弃：`bone-extension-studio` 仅依赖 `bone-metadata-sdk` 的 Table/Column 注解、Criteria 与 Repository 等技术持久化能力，对 `bone-metadata-server` 内部包 import 为 0，按 P-2.2「SDK 与技术依赖不混入 Context Map」不属于业务级 Customer–Supplier 关系，Extension 的上游唯 Integration（Published SPI）。

| 上游 | 下游 | 模式 | 契约 | 下游责任 |
|------|------|------|------|----------|
| IAM | 所有应用上下文 | OHS / Published Language | JWT、IAM REST API | 使用 ACL 转换身份与权限语义 |
| Metadata | MasterData | Customer–Supplier | catalog API、版本化快照或事件 | 不依赖 Metadata 内部聚合类型 |
| Metadata | Generator | Customer–Supplier | catalog API / Published Language | 转换为 Generator 自身模板语言 |
| MasterData | Integration | Customer–Supplier | REST ACL 或集成事件 | 不跨库读取主数据表 |
| Integration | Extension | Published Language | 版本化扩展 SPI | SPI 保持兼容，不暴露 Studio 内部模型 |
| System | 所有上下文 | OHS | 配置、日志与监控 API | 只消费公开技术契约 |
| IAM | Notification | Customer–Supplier | IAM API | Notification 不持有 IAM 聚合 |

术语必须按以下语义使用：

- **Shared Kernel**：多个上下文共同拥有、共同维护的一小部分领域模型；变更需要相关团队共同批准。
- **Shared Library / Platform Kernel**：无业务语义的公共技术库；`bone-core`、`bone-web`、`bone-metadata-sdk` 属于此类。
- **Conformist**：下游直接接受上游领域模型且无翻译能力的上下文关系；普通 SDK 依赖不叫 Conformist。
- **ACL**：在边界处把外部语言翻译为本上下文语言，不是简单的 HTTP Client 包装。
- **OHS / Published Language**：稳定公开服务与版本化契约，不能等同于内部 Java 包。

framework、SDK、Gateway 等技术依赖另画技术图，不能混入 Context Map。

### P-2.5 核心域

DDD 不限制组织只能有一个 Core Subdomain。核心域表示当前战略差异化投资，可有多个，也可随战略变化。详见 [P-2.1 战略分类](#p-21-战略分类)。见 [ADR-0023](./adr/0023-core-domain-smart-metadata.md)。

**元数据职责边界（CORE-10）**：Metadata 只描述模型（What：Model Definition / Schema / Relationship / Constraint / Version），不执行业务逻辑（How）。业务规则与状态迁移必须落在应用层与领域层；Metadata 的 Runtime / Validator 只是「模型 → 执行」的通道，不内嵌具体业务语义。防止 Metadata 从「建模系统」膨胀为「第二个应用运行时」。

## P-3 战术建模

### P-3.1 聚合

聚合是强一致不变量与并发控制边界。

识别聚合时回答：

1. 哪些对象必须在同一提交中共同满足业务不变量？
2. 哪些对象没有脱离根的独立生命周期？
3. 某对象能否不加载根、不破坏根不变量地独立修改并提交？
4. 变更频率、冲突概率和锁定范围是否相容？

约束：

- 聚合尽量小；
- 聚合间使用 ID/值对象引用，不持有对方对象图；
- 外部只能通过聚合根修改内部实体；
- 聚合内实体可以用 `RootId + ChildId` 定位；
- 可凭 ID 寻址本身不是独立聚合判据。

### P-3.2 实体和值对象

- 实体有稳定身份和生命周期。
- 值对象无独立身份、按值比较、默认不可变。
- 值对象优先使用 `record` 或 `final` 字段。
- 强类型 ID 用于重要跨聚合引用，避免裸标量误传。

### P-3.3 领域服务

领域服务承载：

- 无法自然归属单个聚合/值对象的纯业务规则；
- 使用领域语言；
- 不依赖具体 IO 实现与框架；确需外部领域事实时，只依赖 domain 端口并由应用层控制调用时机。

领域服务不承担用例事务、DTO 转换、查询报表或技术工具职责。

### P-3.4 领域事件

- 表达已经发生的领域事实；
- 名称使用过去式；
- 载荷只含领域语义；
- 上下文内可直接使用；
- 跨上下文前转换为版本化集成事件。

`eventId`、trace、schema version 等传输字段属于集成事件信封，不强迫领域事件本体携带。

### P-3.5 Repository

Repository 是聚合根的集合抽象：

- 按身份或单一业务键加载聚合；
- 保存和删除聚合；
- 不承担分页、报表、Join 或 UI 投影；
- 接口位于 domain，实现位于 infrastructure。

### P-3.6 聚合建模工作法

新业务不要从建表或生成 Controller 开始。一次最小建模工作坊按以下顺序进行：

1. **讲业务故事**：用领域语言描述参与者、触发、决策、结果与失败。
2. **提取事实**：把“已经发生”的事实写成候选领域事件，如“订单已确认”。
3. **反推命令**：识别产生事实的意图，如“确认订单”。
4. **识别不变量**：明确哪些条件在一次提交后必须同时成立。
5. **划定聚合**：把必须共同保持强一致的对象放入同一边界，其余只保留 ID。
6. **验证冲突模型**：检查聚合是否过大、热点写是否会互相阻塞。
7. **确认边界外协作**：为跨聚合步骤定义事件、幂等、重试和补偿。

建模产物不是 UML 数量，而是以下可执行答案：

| 问题 | 最小产物 |
|------|----------|
| 谁拥有业务规则？ | 聚合行为或纯领域服务 |
| 什么必须强一致？ | 聚合不变量与事务边界 |
| 什么可以稍后完成？ | 领域事件/集成事件与状态机 |
| 谁可以写数据？ | 上下文与表 Owner |
| 失败如何恢复？ | 幂等、重试、补偿、人工入口 |

### P-3.7 最小聚合示例

以下示例展示规则放置方式，不规定具体持久化注解：

```java
public final class Order {
  private final OrderId id;
  private OrderStatus status;
  private final List<DomainEvent> events = new ArrayList<>();

  public void confirm(Instant occurredAt) {
    if (status != OrderStatus.DRAFT) {
      throw new DomainException("只有草稿订单可以确认");
    }
    status = OrderStatus.CONFIRMED;
    events.add(new OrderConfirmedEvent(id, occurredAt()));
  }
}
```

关键点：

- `confirm()` 表达领域语言并保护状态迁移；
- 调用者不通过 setter 拼装 `CONFIRMED` 状态；
- 事件只在真实迁移后产生；
- 时间、ID 等可测试变化量通过参数、工厂或领域端口注入；
- Application 负责加载和保存，Aggregate 负责决定能否确认。

## P-4 六边形与整洁架构

- 业务策略不依赖技术细节。
- 入站适配器把协议请求转换为应用用例输入。
- 应用用例协调领域和出站端口。
- 出站适配器实现数据库、消息和第三方集成。
- DTO、第三方异常和协议状态不进入 domain。

端口位置由使用者拥有：

- 领域规则需要的外部能力端口可位于 domain；
- 应用流程、查询和通知端口位于 application；
- 实现位于 infrastructure。

## P-5 CQRS、一致性与可靠性

### P-5.1 CQRS

CQRS 首先是读写关注点分离：

- 写侧围绕聚合和不变量；
- 读侧围绕投影和查询体验；
- 不默认要求读写分库、事件溯源或异步投影。

### P-5.2 事务

默认一个业务事务修改一个聚合实例。跨聚合默认最终一致。

允许例外，但必须说明：

- 为什么不能拆为多个步骤；
- 要保护的跨聚合不变量；
- 锁顺序与并发风险；
- 失败、重试和回滚语义；
- 对应测试。

### P-5.3 最终一致

跨聚合/上下文流程需要明确：

- 状态机；
- 事件或命令；
- 幂等；
- 重试和死信；
- 超时；
- 补偿；
- 可观测与人工恢复。

### P-5.4 可靠事件发布

选择机制的入口是业务保证，而不是发布 API 名称：先明确能否丢失、能否重复、是否有序、延迟与恢复要求，再选择 Best-effort、Durable 或同步确认。Outbox 是 Bone 的默认 Durable 方案，不是唯一方案；完整工程契约见 [E-5.2](#e-52-可靠发布)。

### P-5.5 回调幂等与跨聚合协作

外部回调必须：

1. 在进入领域前通过端口验签；
2. 校验时间戳/nonce，防重放；
3. 使用外部流水号或请求 ID 作为幂等键；
4. 由聚合决定重复回调是否引起状态迁移；
5. 使用唯一约束、条件更新或等价原子机制处理并发；
6. 只在真实迁移时产生领域事件。

完整样板见[附录 D：代码与支付样板](#payment-sample)。

## P-6 集成与 ACL

- 外部模型和错误语义不穿透本上下文。
- ACL 使用本上下文语言声明端口。
- 适配器负责请求、响应、错误和空值翻译。
- 跨上下文优先公开 API、Published Language 或本地事件投影。
- 禁止共享写表集成。

## P-7 演进与质量属性

- 核心规则应能无容器测试。
- 关键用例具备日志、指标和追踪。
- 跨边界契约有版本和废弃策略。
- 架构适应度函数防止可机械识别的退化。
- 机器检查不替代领域评审。

---

# 第二部分 Bone 工程决策（E-）

## E-0 规范稳定性契约

### E-0.1 新代码

新代码遵守本文 CORE-01～CORE-10、对应 P/E/G 条文和项目硬约束。

### E-0.2 存量不符合规范代码的处理

存量采用增量迁移：

- 不因重写规范要求一次性重构业务代码；
- 新增代码不得扩大明确的结构违规；
- freeze 基线只收缩；
- 规则语义发生变化时，先修规则 fixture 和声明，再迁移代码；
- 当前台账见[附录 A：迁移台账](#migration-ledger)。

### E-0.3 破坏性变更

修改边界、事务、异常根、持久化策略或门禁语义时：

1. 新增 ADR；
2. 同步本文受影响章节、AGENTS 和 glossary；
3. 区分目标态与实现态；
4. 规则先有正反 fixture，再推广到模块；
5. 不通过重置 freeze 掩盖新增问题。

### E-0.4 例外

| 范围 | 记录 |
|------|------|
| 跨模块/平台级 | ADR |
| 单模块 | 模块 README |
| 类/方法工程折中 | Javadoc |

例外包含业务理由、Owner、拆除条件和目标日期。相同例外跨两个以上模块出现时，优先复核规则是否合理，而不是机械升级例外。

## E-1 上下文与数据所有权

硬约束：

- 一个模块只属于一个上下文；
- 一张业务表只有一个写入所有者；
- 跨上下文禁止直接写表；
- 业务查询禁止直接 Join 其他上下文拥有的表；
- 跨上下文读取通过公开 API、版本化事件或本地投影；
- 分析平台需要跨域数据时，消费发布的数据产品或快照，不绕过所有权直接依赖在线写表。

每个上下文必须在模块 README 维护可审查的数据所有权声明：

```markdown
## 上下文边界

- 限界上下文：IAM
- Owner：IAM 团队
- 对外契约：JWT、`/api/v1/iam/**`
- 代码模块：`bone-platform/bone-iam`

## 本上下文拥有的表

| 表名 | 用途 | 归属聚合 |
|------|------|----------|
| iam_account | 登录账号 | Account |
```

技术模块依赖不构成业务 Context Map：

```text
应用上下文模块
  ├── bone-core / bone-web / bone-security
  ├── bone-metadata-sdk
  ├── bone-extension-sdk（需要扩展能力时）
  └── 各上下文公开契约或 Client SDK

bone-gateway
  └── HTTP 路由、认证透传与协议级横切
```

Platform Kernel 只允许实体与聚合基类、审计、租户上下文、统一响应、异常根、领域事件标记和 metadata repository SPI 等无单一上下文业务语义的能力。`QueryBuilder` 是基础设施查询能力，不属于领域 Shared Kernel；`bone-gateway` 不承担跨上下文业务编排；公共 SDK 通过语义化版本和契约测试治理兼容性。

出现以下任一情况时执行 Context Map 演进检查：

- 产品战略出现新的差异化投资方向；
- 团队所有权或独立发布边界变化；
- 一个模块开始同时使用两套冲突的业务语言；
- 出现跨上下文写表、直接依赖对方 domain 包或跨库 Join；
- 上下文过大，已无法由单个团队理解、测试和演进。

## E-2 多租户

- `TenantContext` 由框架过滤器/拦截器写入，业务代码不写。
- 业务层通过统一 `TenantProvider` 或认证上下文读取。
- 异步、MQ、Outbox 和定时任务显式携带/恢复租户。
- 跨边界 Command、事件和 RPC 显式携带 `tenantId`。
- 查询和写入必须包含租户隔离。
- 平台超管跨租户操作必须授权并审计。
- 外层不得修改聚合 `tenantId`。

<a id="application-use-case-boundary"></a>

## E-3 应用用例

应用层执行一个用户或系统用例：建立事务、授权与幂等边界，加载聚合并调用领域行为，持久化结果，调用出站端口，转换用例级失败并协调事件发布。聚合内部不变量仍由 domain 负责。

> **默认入站边界是语义化 ApplicationService**（详见 [E-3.7](#E-37-入口构件决策)，决策依据 [ADR-0028](./adr/0028-application-service-first-selective-cqrs.md)）。Command / Handler 是**复杂度驱动的可选件**，不是默认三件套；读写统一走 ApplicationService，CQRS 指读写职责分离，而非类文件数量翻倍。

### E-3.1 允许的入口

每个用例只选择一种入口构件：

| 构件 | 适用场景 | 是否可直接作为入站端口 |
|------|----------|------------------------|
| `{语义}ApplicationService` | **默认入口**：简单读写 / CRUD / 一组高度内聚、共享同一应用级策略的操作 | 是 |
| `*CommandHandler` | 单个写用例，且命令需要独立路由、生命周期、异步跨事务或多入口统一执行 | 是 |
| `*QueryHandler` | 单个复杂读用例（分析/报表/搜索等 read model 场景） | 是 |
| `*Orchestrator` | 跨步骤、可重试或可补偿的流程 | 是 |
| `*Facade` | 稳定 Client SDK 契约或多个入站适配器共享用例集合 | 是，但内部只路由到前述构件 |

构件选型遵循"复杂度驱动"：默认 ApplicationService 即可；仅当写意图需显式契约、命令数量多、命令异步化/多入口时引入 Command / CommandHandler，读模型脱离聚合结构时 ApplicationService 内升级 QueryPort，极少数需要独立读路由时引入 QueryHandler（见 [E-3.7](#E-37-入口构件决策) 与 [ADR-0028](./adr/0028-application-service-first-selective-cqrs.md)）。

### E-3.2 一个用例一个边界

Controller 可直接依赖 Handler 或合法 ApplicationService。禁止（含 Ceremonial Architecture 检查）：

- `Controller → Handler → 同义 ApplicationService` 一对一委派；
- `Controller → ApplicationService → 同义 Handler` 一对一委派；
- Handler 为复用而调用另一个 Handler；
- Facade 直接操作 Repository、聚合或 infrastructure；
- 应用构件实现聚合内部不变量。

**Ceremonial Architecture（仪式感架构）**：当中间层只做透传而不增加事务、策略、路由或生命周期管理时，该层是仪式性的。典型信号：`handle()` 方法体只有一行 `return applicationService.xxx(command);`，或 `ApplicationService.handle()` 只有一行 `repository.save(aggregate)`。Ceremonial 层增加类数量、认知负担和修改点，不增加任何架构能力。Bone 禁止 Ceremonial Architecture。

检测方式：若 `handle()` 方法的有效业务逻辑（排除日志、租户获取等横切）不超过 2 行，且不包含条件分支、事务提交、事件发布或多聚合协调，则判定为 Ceremonial。评审时应删除该层，将调用上移或下推到实际执行层。

**正反例**：

```java
// ✅ 合法：有事务 + 聚合加载 + 行为调用 + 保存，即使只有 4 行
@Transactional
public void handle(ConfirmOrderCommand cmd) {
    Order order = repository.load(cmd.orderId());
    order.confirm();
    repository.save(order);
}

// ❌ Ceremonial：3 行但全是透传，无独立逻辑
public void handle(CreateOrderCommand cmd) {
    log.info("create order");
    return applicationService.create(cmd);
}

// ❌ Ceremonial：ApplicationService 只做转发
public OrderDTO getOrder(OrderId id) {
    return orderQueryPort.findById(id);  // 一行委派
}
```

关键判据不是行数，而是**是否有独立的事务/策略/路由/协调逻辑**。

ApplicationService 合法条件：

- 表达清晰应用用例或强内聚用例族；
- 只编排，不承载聚合内部规则；
- 不依赖 infrastructure 实现；
- 不使用 QueryBuilder/SQL；
- 不成为 `CommonService`/`BaseService` 通用桶。

`*UseCase` 不是 DDD 禁词，但 Bone 的代码生成、门禁和团队语言已经统一到 Handler/ApplicationService。新代码不再新增 `*UseCase`；这是 Bone 命名兼容策略，不是行业 DDD 原则。

### E-3.3 Orchestrator

仅用于跨步骤、重试、超时或补偿流程。每一步使用明确事务边界；技术轮询和 Outbox 中继放 adapter/infrastructure。仅为减少构造参数或统一类名，不新增 Orchestrator。

### E-3.4 Facade

仅在以下场景使用：

- 对外提供稳定、粗粒度且需要独立版本治理的 Client SDK/模块契约；
- 多个入站适配器需要复用上述稳定契约，而不是直接复用单个 Handler。

Facade 不持有领域规则和写事务，不直接操作 Repository。

### E-3.5 事务位置与代理规则

- 写事务位于最外层写用例边界：CommandHandler、写 ApplicationService 或 Orchestrator 的单步执行器。
- QueryHandler 使用只读事务或无事务查询，按一致性需求决定。
- Facade 默认不持有写事务。
- 同一类内部方法调用不能依赖 Spring 代理产生新事务。
- Orchestrator 需要“每步独立事务”时，使用独立步骤 Bean 或 `TransactionTemplate`，并为失败定义重试与补偿。
- 不得用 `REQUIRES_NEW` 或 `TransactionTemplate` 隐藏本应显式建模的跨步骤业务流程。

### E-3.6 写用例参考形态

> 示例与 `bone-blueprint` 惯用法保持同步：修改样板模块的写用例形态时，必须同步更新本节。

> **以下为 Command + Handler 路径的参考形态**，适用于写意图需显式契约、命令数量较多或异步化的场景（见 E-3.7 决策树）。简单写用例直接使用 ApplicationService，参考 [E-3.7 默认路径](#E-37-入口构件决策)。

```java
@Transactional
public void handle(ConfirmOrderCommand command) {
  // 租户隔离：命令显式携带优先（异步/定时入口），否则取当前请求上下文
  long tenantId =
      command.tenantId() != null ? command.tenantId() : tenantProvider.currentTenantId();

  // SDK findById 返回裸值（未找到为 null），用 findByIdInTenant 在 SQL 层完成租户过滤
  Order order =
      Optional.ofNullable(orderRepository.findByIdInTenant(command.orderId(), tenantId))
          .orElseThrow(() -> new NotFoundException("订单不存在: " + command.orderId()));

  order.confirm();
  orderRepository.save(order);
  // publishFrom 的可靠性取决于实现契约，不能仅凭方法名推断
  domainEventPublisher.publishFrom(order);
}
```

职责边界：

| 构件 | 应做 | 不应做 |
|------|------|--------|
| Controller | 鉴权结果接入、协议 DTO 转换、统一响应 | 状态判断、事务、Repository 调用 |
| CommandHandler | 事务、加载、调用行为、保存、发布 | 复制聚合规则、SQL/QueryBuilder |
| Aggregate | 不变量、状态迁移、领域事件 | 调用数据库、MQ、HTTP |
| Repository Adapter | 聚合映射与持久化 | 业务决策、报表投影 |

若 Handler 中出现 `if (order.getStatus() == ...)` 并据此修改状态，通常说明领域行为泄漏；若聚合中出现 Repository、HTTP Client 或 `@Transactional`，说明应用/基础设施职责泄漏。

### E-3.7 入口构件决策

默认从**语义化 ApplicationService** 出发；按复杂度逐级升级，不为简单 CRUD 预生成 Command / Query 三件套。Application Service First 是 Bone 的默认架构形态，不是"退而求其次"。

**Application Service First 6 条规则**：

| # | 规则 | 判据 |
|---|------|------|
| AS-01 | Application Service 是默认的应用入口 | 新模块、新上下文一律从 ApplicationService 开始 |
| AS-02 | Command 在写意图需要显式契约时引入 | 命令需要跨异步边界传递、多入口统一执行、或需独立版本化契约 |
| AS-03 | Command Handler 在命令需独立路由 / 生命周期 / 多入口时引入 | 命令进入 Outbox / MQ / Scheduler，或同一 Command 被 REST / Kafka / 定时任务共同消费 |
| AS-04 | 读用例默认由 ApplicationService 直接处理，与写用例共用同一入口 | 仅当读模型与聚合结构分歧时，升级到 QueryPort；读编排本身复杂（多端口组合）时考虑拆分读方法 |
| AS-05 | Dedicated Read Model 仅在读复杂度或扩展性要求时引入 | 分析 / 报表 / 搜索等需要独立 read database 或物化视图 |
| AS-06 | CQRS 是一个光谱，不是开关 | 不存在"全项目 CQRS"或"全项目不 CQRS"的二选一；每个模块按自身复杂度选择光谱上的位置 |

AS-01～AS-06 的优先级高于目录模板：即使 Blueprint 生成器输出了 `command/` 目录，开发者仍应按上述规则判断是否需要——不需要就删掉空目录。

```text
简单读写 / CRUD / 后台配置？
  └─ 是（默认起点）→ 语义化 ApplicationService（读方法、写方法同一入口）
      │
      └─ 复杂化后升级：

写侧升级：
  写意图需要显式契约（intent）或命令数量较多？
    └─ 是 → 引入 Command
  命令需独立路由 / 生命周期 / 异步多入口 / 跨事务？
    └─ 是 → 引入 CommandHandler（命令异步化时，Handler 位于 Outbox 消费端/任务端）

读侧升级：
  读模型与聚合结构分歧 / 需要定制投影 / 统计报表？
    └─ 是 → ApplicationService 内引入 QueryPort
  分析 / 搜索等复杂读，需独立 read model / read database？
    └─ 是 → QueryHandler → QueryPort → QueryAdapter → QueryBuilder

其他入口：
跨多个独立事务步骤，并有重试/补偿？
  └─ 是 → Orchestrator
多个入站协议需要稳定复用同一用例集合？
  └─ 是 → Facade
否则 → 不新增中间层
```

**复杂度与类数量参考**（以 5 个写用例 + 3 个读用例的模块为例）：

| 模式 | 写侧构件 | 读侧构件 | 应用层总类数 |
|------|----------|----------|-------------|
| Everything CQRS | 5 Command + 5 Handler | 3 Query + 3 Handler | ~16+ |
| Application Service First | 1 ApplicationService（5 写方法） | 1 ApplicationService（3 读方法，直查或 QueryPort） | ~3–5 |
| 选择性 CQRS（写复杂） | 3 Command + 3 Handler + 1 ApplicationService | 1 ApplicationService（3 读方法） | ~8–10 |
| 选择性 CQRS（读复杂） | 1 ApplicationService（5 写方法） | 1 ApplicationService + QueryPort（多端口组合） | ~5–7 |

每减少一个 Ceremonial 层，减少一个维护点、一个测试点、一个新人理解障碍。Blueprint 生成器应以 "Application Service First" 行为默认输出。

**Blueprint 生成器默认行为**：代码生成器的默认输出为 `Controller → ApplicationService → Repository`。`command/`、`query/handler/`、`query/port/` 目录默认不生成。开发者按本决策树确认需要后手动创建。理由：生成器默认输出的每一层都会成为团队的"标准做法"，错误默认值的影响被模板放大到所有使用该 Blueprint 的模块。

### E-3.8 构件职责速查

| 构件 | 回答的问题 | 职责 | 何时引入 |
|------|-----------|------|----------|
| `ApplicationService` | 一个业务用例怎么被协调执行？ | 编排用例：事务/授权/加载聚合/调用行为/保存/发布 | **默认入口** |
| `Command` | 调用方想让系统做什么？（Intent） | 承载写意图与参数 | 写意图需显式契约 / 命令数量多 |
| `CommandHandler` | 这个 Command 由谁执行？ | 独立路由 / 生命周期 / 异步 / 多入口 / 限流重试幂等 | 命令需独立执行策略或异步化 |
| `Read Model` | 查询结构与领域模型不一致怎么办？ | 独立读模型 / 投影 | 读写模型明显分离或需独立读写 |

> **抽象判断准则**：一个抽象是否该存在，不看"是否符合 DDD"，而看**它是否承担了独立职责**。纯透传的 `Handler → service.method()` 是包装层，不是架构。

### E-3.9 场景 × 模式决策矩阵

| 场景 | 推荐模式 | ApplicationService | Command | CommandHandler | QueryPort | ReadModel |
|------|----------|:---:|:---:|:---:|:---:|:---:|
| 简单 CRUD / 字典 / 配置 | ApplicationService | ✅ | ❌ | ❌ | ❌ | ❌ |
| 简单业务规则 | ApplicationService | ✅ | 可选 | ❌ | ❌ | ❌ |
| 复杂业务动作 / 状态机 | Command + ApplicationService | ✅ | ✅ | 仅异步时 | ❌ | ❌ |
| MQ / 异步 / 多入口执行 | CommandHandler | ✅ | ✅ | ✅ | ❌ | 可选 |
| 复杂查询 / 报表 | ApplicationService + QueryPort | ✅ | ❌ | ❌ | ✅ | 可选 |
| 高性能查询 / 独立读模型 | ApplicationService + QueryPort + ReadModel | ✅ | ❌ | ❌ | ✅ | ✅ |
| Event Sourcing / 完整读写分离 | Full CQRS | ✅ | ✅ | ✅ | ✅ | ✅ |

### E-3.10 AI/Agent 选型判据

不要让 Agent 猜测要不要 Command/Handler，改用固定问题定档（0 个 YES → ApplicationService；1–2 个 → +Command；3–4 个 → +Handler；复杂读 → ApplicationService + QueryPort；独立读写 → Full CQRS）：

1. 存在明确业务意图（intent）？
2. 存在复杂状态变化 / 多领域规则组合？
3. 多个入口触发同一操作（REST/MQ/定时/工作流）？
4. 需要异步执行（Queue/Retry/DLQ）？
5. 需要显式幂等 / 限流 / 重试策略？
6. 读模型明显不同于领域模型（跨聚合/报表）？
7. 需要独立的读写扩展（专门读库/投影/ES）？

铁律三则：① Blueprint 生成器默认输出 ApplicationService；② Agent/开发按本矩阵与判据选型；③ 命令异步化一律走 **Outbox + 消费端幂等**（[ADR-0021](./adr/0021-outbox-and-consumer-idempotency-platformization.md)），不得在 `@Transactional` 调用方内 inline 持有长事务。

<a id="cqrs-port-location"></a>

## E-4 CQRS 与端口

**CQRS 的"读写职责分离"是默认语义**（Command 可改状态、Query 不改状态）；但**是否把它实现成 CommandHandler / QueryHandler / ReadModel 的物理结构，由复杂度驱动，且与领域复杂度正交**——DDD 复杂度和 CQRS 复杂度是两个独立维度：

| | 低读写复杂度 | 高读写复杂度 |
|---|---|---|
| **低领域复杂度** | ApplicationService（简单 CRUD） | ApplicationService + QueryPort + ReadModel / Cache |
| **高领域复杂度** | DDD + ApplicationService + Command | Full CQRS |

领域复杂并不强制 Full CQRS（同步单入口带大量规则只是 DDD + ApplicationService + Command）；查询量大也不强制复杂聚合（简单领域配 ApplicationService + QueryPort + ReadModel + Cache 即可）。CQRS 是一个频谱（Light → Selective → Full），不是硬开关。

### E-4.1 写侧

```text
adapter
  → CommandHandler / ApplicationService
    → domain/repository/*Repository.load
    → Aggregate.behavior
    → domain/repository/*Repository.save
      ← infrastructure/persistence/*RepositoryImpl
```

- Repository 位于 `domain/repository`。
- 返回聚合、`Optional<聚合>`、boolean 或 void。
- Repository 面向聚合根，按 ID 或单一业务键加载，并保存或删除聚合。
- 多条件、分页、Join、统计不进入写仓储。
- 当前 SDK `findById` 的 null 在应用边界立即转换。

### E-4.2 读侧

简单读默认由 **ApplicationService** 直查（读模型与聚合结构一致时）：

```text
adapter
  → ApplicationService.get()/page() → Repository       （简单读，读模型与聚合一致）
```

读模型与聚合结构分歧 / 复杂读（报表、搜索、跨聚合组合）时，ApplicationService 内引入 QueryPort：

```text
adapter
  → ApplicationService → QueryPort（多数场景）
  → QueryHandler（需独立路由 / 异步时）
    → application/query/port/*QueryPort
      ← infrastructure/query/*QueryAdapter
        → Criteria / QueryBuilder / SQL
```

- 新查询端口位于 `application/query/port`。
- 查询结果位于 `application/query/dto` 或 `application/query/projection`。
- QueryHandler 返回 application projection，不返回聚合供外层修改。
- QueryBuilder/Criteria/SQL 位于 infrastructure。
- domain 不依赖查询 DTO、QueryPort 或查询 DSL。
- 存量 `domain/gateway/*ReadPort` 随功能修改迁移，不一次性搬包。Blueprint 读侧（`domain/gateway/*ReadPort` + `domain/{aggregate}/read/*Row`）即此存量形态：迁移计划见[附录 A](#migration-ledger)，新模块一律按 `application/query/port` 目标位置实现，不得照抄样板读侧。

**读侧统一走 ApplicationService**：读用例与写用例共用同一 ApplicationService 入口，不单独引入 QueryService。简单读由 ApplicationService 直查 Repository；读模型与聚合分歧时，ApplicationService 内部调用 QueryPort 访问数据，不直接持有 SQL / QueryBuilder / Criteria。数据访问隔离仍由 QueryPort → QueryAdapter 保证。禁止绕过 QueryPort 直连基础设施——这会破坏读写端口分离的一致性。

```text
Controller → ApplicationService → QueryPort → QueryAdapter → SQL/DSL
                         └→ QueryPort → QueryAdapter（多端口组合时）
```

当读编排需要组合多个 QueryPort（如"客户详情页"聚合订单+支付+风控数据）时，在 ApplicationService 内拆分独立读方法，不新增独立 QueryService 类。仅在读用例本身需要独立路由、生命周期或异步执行时，才引入 QueryHandler（见 AS-03 的读侧对等场景）。

**判据（ADR-0028）**：默认简单读走 ApplicationService（与写用例共用同一入口）。简单单表分页、按 ID 详情、1:1 映射 DTO 的列表——即使分页——仍可直查写 Repository（见 [D.1.1](#d11-极简-applicationService-样板l1-简单-crud--支撑域) 的 `page()`）。仅当读模型与聚合**结构分歧**时——需要跨聚合组合、Join、统计、UI 定制投影，或其分页/列表结果与聚合列明显不一致（列裁剪、连表、报表）——才在 ApplicationService 内引入 QueryPort。存量按 ID 使用写 Repository 的简单详情查询可以保留；一旦读模型与聚合结构分歧，即按需升级到 QueryPort。

**读侧不需要与写侧对称**：写侧用了 `Command/CommandHandler` 不代表读侧也要 `Query/QueryHandler`。一个复杂列表（Customer + Order + Payment + Risk）不建议硬套 `Query → QueryHandler → Aggregate`；更合适的是 `Controller → ApplicationService → QueryPort → SQL/View → DTO`。读写各自按复杂度独立演进，读/写不必镜像。

CommandHandler 需要读取业务决策数据时，优先加载聚合；确需专用读取时依赖语义化 application 出站端口，并显式说明一致性要求。

查询端口参考（以下为极少数需要独立路由/异步读场景的 QueryHandler 示例；大多数复杂读由 ApplicationService 直接调 QueryPort 即可）：

```java
public interface OrderQueryPort {
  PageResult<OrderSummary> search(OrderSearchCriteria criteria);
}

public final class SearchOrdersQueryHandler {
  private final OrderQueryPort orderQueryPort;

  public PageResult<OrderSummary> handle(SearchOrdersQuery query) {
    return orderQueryPort.search(OrderSearchCriteria.from(query));
  }
}
```

`OrderSummary` 是应用投影，不是聚合。读侧可以针对页面或调用方组合数据，但不能反向用投影修改业务状态。

### E-4.3 ACL 端口

- 领域规则直接需要、且能用本上下文业务语言表达的外部业务能力，可在 `domain/gateway` 定义 **Domain Gateway** 端口；不得放查询投影、消息客户端、缓存、时钟等技术端口。
- 应用流程需要的通知、时钟、身份生成、幂等、文件等技术能力位于 `application/port/out`；查询能力位于 `application/query/port`。
- 第三方 HTTP/RPC Client、MQ Producer 等出站实现位于 infrastructure，并负责协议 DTO、错误、超时和空值翻译。
- 端口按使用者拥有，而不是按实现技术或供应商命名；不存在第二种实现也不是省略端口的理由，是否建端口由隔离边界和测试需求决定。
- Domain Gateway 可以表达领域所需的外部事实，但远程 IO 不得隐藏在聚合方法中。聚合只接收已经取得的事实或纯端口结果；应用用例或 Orchestrator 控制调用时机、超时、重试、熔断和事务范围。
- 外部 DTO、SDK 异常和协议状态不得穿透 application/domain。

## E-5 事务、事件与并发

### E-5.1 默认事务

- 写事务位于最外层写用例。
- 默认一个业务事务修改一个聚合实例；跨聚合协作使用最终一致流程。
- 跨聚合采用事件或 Orchestrator。
- 若两个对象始终共同维护同一不变量，先复核是否应属于同一聚合。
- 同构批处理必须说明逐项失败、并发冲突和部分成功语义。
- 多聚合本地强一致例外必须记录理由、锁顺序、失败语义并测试。
- Outbox、幂等记录等技术写不是业务聚合，但必须与业务状态满足所声明的原子性。
- 远程 IO 不得隐藏在聚合方法中；确需同步外部确认时，由应用用例或 Orchestrator 显式决定调用是否进入数据库事务范围，并控制超时、重试、熔断、幂等和失败语义。

现有 `oneAggregatePerTransaction()` 只按 Repository 类型扫描，属于 Advisory：

- 无法区分同类型的多个聚合实例；
- 无法完整追踪 Lambda、反射、代理和跨包委派；
- 不能判断两个写操作是否属于同一业务不变量；
- 因此只能发现部分结构风险，不能证明事务语义正确。

<a id="reliable-event-publishing"></a>

### E-5.2 可靠发布

每个跨边界业务事实先声明：是否允许丢失、重复和乱序，生产与消费如何重试，幂等键、最大可接受延迟、告警和人工恢复方式。

| 发布契约 | 最低保证 | 默认实现 |
|----------|----------|----------|
| **Durable** | 业务写与发布记录在同一事务；记录失败必须向上传播并使事务失败；至少一次交付；消费端幂等 | Transactional Outbox |
| **Best-effort** | 明确允许丢失；必须在类型、配置或调用点显式标识，并记录失败日志和指标 | `AFTER_COMMIT` 异步事件 |
| 分区内有序 | Durable 基础上增加稳定分区键、序号或版本检查 | Outbox + 有序分区 |
| 同步确认 | 明确超时、重试、熔断、幂等和失败契约 | 同步 API |

Durable 发布只有在发布记录成功交接后才能清除聚合事件；写发布记录失败必须向上传播，禁止吞错后清事件。Best-effort 可以在提交后发送，但丢失风险必须是业务接受的显式决定。

禁止仅凭 `publishFrom()` 方法名推断可靠性。其实现必须明确声明 Durable 或 Best-effort 契约；若 Durable 实现需要先提取事件再写 Outbox，则应在同一事务内完成，并在 Outbox 记录成功后清事件。禁止“先写数据库，再无事务地发送 MQ”承载不可丢业务事实。

Outbox 是 Bone 的默认 Durable 实现。CDC、数据库事务日志或其他平台能力只有在证明同等原子性、恢复性和可观测性后才可替代，并通过 ADR 记录。

领域事件表达上下文内事实；集成事件是跨上下文的版本化 Published Language，由发布适配器转换。传输元数据放在 **Integration Event Envelope**，不污染领域事件本体：

| 字段 | 语义 |
|------|------|
| `eventId` | 全局稳定事件标识，也是消费幂等候选键 |
| `tenantId` | 租户隔离标识 |
| `type` | 稳定事件类型 |
| `version` | 契约/schema 版本 |
| `occurredAt` | 领域事实发生时间 |
| `traceId` | 端到端追踪标识 |
| `causationId` | 导致本事件的命令或前序事件标识 |
| `payload` | 版本化业务载荷 |

### E-5.3 并发

每个可并发写聚合必须声明并验证策略。乐观锁是新聚合的默认候选，不是唯一合法实现。

| 策略 | 适用场景 | 最低必测行为 |
|------|----------|--------------|
| 乐观版本锁 | 低到中冲突、交互式写入 | 版本冲突不覆盖新数据，并映射稳定错误 |
| 条件更新 | 简单状态机、幂等迁移 | 条件不满足时可区分重复与冲突 |
| 唯一约束 | 唯一业务键、回调流水 | 并发重复写只有一个成功 |
| 悲观锁 | 短事务、高冲突且可控 | 锁等待、超时和死锁重试 |
| 单写者/串行队列 | 高频热点聚合 | 分区顺序和消费者恢复 |

幂等键优先使用外部业务键、`eventId` 或客户端 `Idempotency-Key`；存储层以唯一约束或等价原子机制兜底。保留期覆盖最长重试窗口，重复请求返回语义稳定，且租户必须参与幂等隔离。

### E-5.4 一致性决策树

```text
多个状态是否必须在一次提交后同时满足同一业务不变量？
  ├─ 否 → 独立聚合 + 事件/Orchestrator
  └─ 是
      ├─ 是否属于同一生命周期和并发边界？
      │   └─ 是 → 考虑合并为同一聚合
      └─ 无法合并
          ├─ 同库且本地事务风险可控 → 记录多聚合事务例外
          └─ 跨库/跨上下文 → 状态机 + Outbox + 幂等 + 补偿
```

事件发布决策：

```text
事件丢失是否影响业务正确性？
  ├─ 否 → AFTER_COMMIT/best-effort，并记录日志与指标
  └─ 是 → 业务写与发布记录必须原子
           ├─ 默认：Transactional Outbox
           └─ 替代：证明同等原子性、恢复性、可观测性并记录 ADR
```

### E-5.5 跨步骤流程参考

以“确认订单后创建支付单”为例：

1. 订单用例在本地事务中确认 `Order`，并原子记录 `OrderConfirmedIntegrationEvent`。
2. Outbox relay 至少一次发布事件。
3. Payment 消费者以 `eventId + tenantId` 幂等消费。
4. Payment 创建支付聚合并记录处理结果。
5. 重复事件返回已有结果，不重复创建支付单。
6. 达到重试上限进入死信并告警，提供人工重放入口。

不要用跨上下文分布式事务隐藏失败状态。业务流程必须能回答“卡在哪一步、能否重试、重复会怎样、谁负责恢复”。

## E-6 领域模型与持久化模型

### E-6.1 两个独立维度

| 维度 | 选项 | 定义 |
|------|------|------|
| 领域模型纯净度 | D0 Pure | 只依赖 JDK、Bone 最小领域抽象和 domain 端口 |
| 领域模型纯净度 | D1 Annotated | 允许 metadata-sdk 映射注解、Lombok、`org.springframework.lang` 等白名单编译期注解 |
| 持久化模型关系 | Shared | 领域对象同时作为 metadata-sdk 持久化对象 |
| 持久化模型关系 | Separated | infrastructure 使用独立 `*PO` 与 Converter |

`*PO` 不再称为 D2 领域模型：它是 infrastructure 持久化对象，不参与领域纯净度评级。一个模块可以采用 `D0 + Separated`，也可以采用 `D1 + Shared`。

### E-6.2 D1 使用条件

D1 是 Bone 为 metadata-sdk 提供的受控工程例外。仅当映射简单、白名单注解不引入运行时行为且不扭曲领域结构时使用，不只限于“无行为 CRUD”。

门禁 `domainMustNotDependOnOuterLayers` 的编译期注解白名单**已显式放行** `org.springframework.lang.*`（如 `@NonNull`）、Lombok 与 metadata-sdk 映射注解；若合法 D1 领域类被误判为违规，应先扩展该白名单，而非放宽领域纯净度要求。

### E-6.3 PO 分离信号

- 需要独立落表的嵌套集合；
- 模型与表字段语义分歧；
- 多存储或遗留映射；
- 存储级加密、脱敏、复杂审计；
- SDK 无法正确恢复/保存；
- 更换存储会迫使修改领域行为。

跨聚合 ID 和状态机本身都不是自动拆 PO 的充分条件。

### E-6.4 反贫血

- 状态迁移通过领域行为；
- 不变量在构造、工厂或行为方法中保护；
- 外层不通过 setter 序列修改业务状态；
- 领域服务只承载纯业务规则；
- 测试覆盖拒绝路径。

### E-6.5 EAV 与扩展字段边界

Bone 扩展字段支持三种存储模式（预留列默认 / JSON / EAV，见《元数据能力-实现映射与竞品对照》），EAV 为极低频、非推荐首选。为防止 EAV 从扩展能力退化为默认数据模型，定义硬边界（CORE-09）：

**EAV / JSON 扩展字段只允许承载**：可选、动态、长尾扩展属性（租户自定义字段、行业特殊属性、低频业务扩展）。

**禁止进入 EAV / JSON**：核心域字段、事务字段、高频查询字段、聚合/统计字段。此类字段必须建模为物理列（含预留列分配），不得以动态属性实现。

判定优先级：**物理列 > 预留列 > JSON > EAV**；EAV 仅限「无法预知键集合」的极低频场景。

## E-7 ID 与错误

### E-7.1 ID

- 非 `IDENTITY` 主键尊重调用方预置非空 ID。
- 领域不依赖具体 ID 生成器。
- 重要跨聚合引用优先强类型 ID。
- 遗留自增主键按模块记录。

### E-7.2 错误模型

异常归入 `DomainException`、`BizException`、`SystemException/InfrastructureException` 三类根。

模块可以定义有业务意义的子类，必须：

- 继承正确根；
- 绑定稳定错误码；
- 不暴露技术类型；
- 由统一异常处理映射；
- 避免无价值的“一场景一异常类”。

## E-8 测试

- 聚合和值对象优先纯 JUnit。
- 关键聚合覆盖主路径、拒绝路径、幂等和并发。
- Repository/QueryAdapter 使用集成测试验证映射和租户。
- ACL 使用契约测试验证翻译和错误隔离。
- 测试组合按风险和反馈成本决定，不规定固定比例。

`AggregatePureUnitTestGuard` 是卫生检查，不是反贫血或聚合正确性的证明。

## E-9 模块适用性

| 模块性质 | 适用 |
|----------|------|
| 应用/BFF | CORE-01～CORE-10 与完整应用分层 |
| SDK/框架库 | 依赖方向、契约、领域纯净度；无应用层则不套 CQRS 目录 |
| 基础设施服务 | 技术职责与依赖方向；无业务模型则不套聚合规则 |
| 简单 CRUD 支撑域 | 边界、租户、写仓储、QueryPort；领域行为按真实复杂度 |

性质按职责判定，不按 `bone-platform` / `bone-engine` 物理目录判定。

## E-10 包结构参考

```text
com.bone.{module}/
├── adapter/
│   ├── web/                         # HTTP 入站
│   ├── messaging/                   # MQ Consumer
│   ├── rpc/                         # RPC Provider
│   └── schedule/                    # 定时触发
├── application/
│   ├── command/
│   │   ├── cmd/
│   │   └── handler/
│   ├── query/
│   │   ├── qry/
│   │   ├── handler/
│   │   ├── port/
│   │   └── dto/
│   ├── service/                     # 可选：语义化 ApplicationService
│   ├── orchestration/               # 可选：跨步骤 Orchestrator
│   └── port/
│       └── out/                     # 应用需要的技术能力端口
├── domain/
│   ├── model/
│   │   ├── aggregate/
│   │   ├── entity/
│   │   ├── valueobject/
│   │   └── event/
│   ├── repository/
│   ├── service/
│   └── gateway/                     # 外部业务能力端口，按需创建
└── infrastructure/
    ├── persistence/                 # 写模型持久化适配器
    ├── query/                       # QueryPort 实现
    ├── messaging/                   # MQ Producer / Outbox relay
    └── integration/                 # 外部 HTTP/RPC/第三方适配器
```

这是**目标参考结构**，展示所有可能的目录。**Blueprint 代码生成器默认只输出 `adapter`、`application/service`（含 ApplicationService）、`domain`、`infrastructure` 四个顶级目录下的最小骨架**；`application/command/`、`application/query/handler/`、`application/orchestration/` 等目录按 E-3.7 决策树的实际需要创建，不预生成空目录。存量不要求一次性搬包；空的 `service`、`orchestration`、`gateway`、`port/out` 不得作为模板占位生成。

顶级目录按依赖方向保持稳定；模块较大时，在 `model`、`command`、`query`、`persistence` 等目录内部再按业务能力或聚合细分，避免全模块只有一个巨大的 `entity`/`service` 横切桶。

domain 内部分组有两种合法形态，同一模块内只能选一种，不得并存：

- `domain/model/{aggregate|entity|valueobject|event}`：按构件角色分组，即本节参考图形态；
- `domain/{aggregate}`：按聚合平铺，事件等构件放 `{aggregate}/event` 等子包。Blueprint、IAM 等模块现行采用此形态并符合其余分层规则，属合法变体而非存量债务。

两套形态不设优劣，选择后在模块 README 登记并保持一致；跨模块不要求统一，禁止在同一 `domain` 包内混用两套分组标准。

协议方向必须明确：

- `adapter` 只放入站适配器；MQ Consumer、RPC Provider、定时触发属于入站。
- `infrastructure` 放出站适配器；MQ Producer、RPC/HTTP Client、数据库实现属于出站。
- 同一种协议同时存在入站和出站时按方向分开，不以“都用了 MQ/RPC”为由放进同一包。
- `QueryPort` 固定在 `application/query/port`，实现固定在 `infrastructure/query`。
- `domain/gateway` 只用于领域语言表达的外部业务能力；存量读端口按 [E-4.2](#e-42-读侧) 迁移，技术端口进入 `application/port/out`。

### E-10.1 各层职责

| 层 | 输入 | 输出 | 允许依赖 |
|----|------|------|----------|
| adapter（入站） | HTTP、MQ 消息、RPC 请求、定时触发 | 应用 Command/Query、协议响应 | application、协议框架 |
| application | Command/Query | 用例结果、出站端口调用 | domain、application ports |
| domain | 领域命令与事实 | 状态变化、领域事件 | JDK、最小领域抽象、受控 domain ports |
| infrastructure（出站） | application/domain 端口 | DB、MQ、外部 HTTP/RPC 结果 | application/domain 端口、技术框架 |

转换边界：

```text
Req/Message
  → Command/Query
    → Aggregate/Value Object
      ↔ PO（需要分离时）
    → Application Projection
  → Resp/Integration Event
```

禁止把 Web Req 直接传入聚合，也禁止从 Controller 裸返回聚合。跨边界对象应明确版本、空值和错误语义。

### E-10.2 Port、Repository 与 Gateway 放置决策

```text
聚合写模型持久化？
  └─ domain/repository → infrastructure/persistence
页面、列表、统计或组合查询？
  └─ application/query/port → infrastructure/query
应用流程需要通知、时钟、文件、幂等等技术能力？
  └─ application/port/out → infrastructure/{具体能力}
领域规则直接需要外部业务概念？
  └─ domain/gateway → infrastructure/integration
否则
  └─ 不创建抽象目录或单实现薄包装
```

`Repository`、`QueryPort`、`Gateway` 都是出站端口，但服务对象不同：Repository 服务聚合一致性，QueryPort 服务读投影，Gateway 隔离外部业务语义。命名不得互换。

## E-11 Flow / AI

- `@Capability` 标注合法应用用例边界。
- 能力发现不要求新增 `*UseCase` 薄门面。
- AI 调度遵守同一事务、租户、权限和错误边界。
- 生成代码前先判断上下文、聚合和一致性。

## E-12 新模块 5 步

1. 在 README 定义上下文、Owner、语言、契约和表所有权。
2. 判断模块性质与 L0–L3 档位。
3. 选择最小包结构和一个应用用例边界。**默认只创建 Controller + ApplicationService + Repository 三层**；`command/`、`query/handler/`、`query/port/`、`orchestration/` 目录按 E-3.7 决策树的实际需要创建，不预生成空目录。Blueprint 代码生成器应遵循此默认行为。
4. 写侧用聚合+Repository，读侧默认 ApplicationService 直查；读模型分歧时 ApplicationService 内升级 QueryPort。
5. 接入 Hard gate；语义规则通过测试和评审验证。

### E-12.1 新模块完成定义

新模块满足以下条件才算完成 DDD 基线，而不是仅创建了目录：

- [ ] README 能说明上下文职责、不负责什么、Owner 和上游/下游；
- [ ] glossary 已补充新增业务词，代码与 API 使用同一语言；
- [ ] 每张业务表有唯一写 Owner，跨上下文没有写表或在线 Join；
- [ ] 至少一个关键用例能从 Adapter 追踪到应用边界和聚合行为；
- [ ] 聚合测试覆盖成功路径与拒绝路径；
- [ ] 读侧不借用 Repository 返回页面投影；
- [ ] 并发写声明冲突策略，跨边界事件声明可靠性保证；
- [ ] Hard gate、受影响测试和契约检查实际通过。

### E-12.2 代码评审提问

评审按风险提问，不按目录数量打分：

1. 这个改动属于哪个上下文，使用了什么业务语言？
2. 新增规则保护了哪个不变量，为什么放在当前对象？
3. 事务修改了几个聚合实例，失败时状态如何解释？
4. 跨边界数据由谁拥有，是否经过公开契约或 ACL？
5. 重复、并发、超时和事件丢失时会发生什么？
6. 哪些结论由机器门禁证明，哪些仍依赖测试和评审？

<a id="naming-style"></a>

## E-13 命名约定

命名属于团队工程一致性，不属于 DDD 原则；默认是 Advisory。模块可以为一致性将其升级为阻断规则，但不得宣称后缀能证明 DDD 语义。

### E-13.1 命令、查询与协议 DTO

| 层级 / 包 | 后缀 | 唯一语义 |
|-----------|------|----------|
| `application/command/cmd/` | `*Command` | 应用写用例输入；新代码默认不用 `*Cmd` 类名，目录名 `cmd/` 可保留 |
| `application/query/qry/` | `*Query` | 应用读用例输入；新代码默认不用 `*Qry` 类名，目录名 `qry/` 可保留 |
| `application/command/handler/` | `*CommandHandler` | 单个写用例入口 |
| `application/query/handler/` | `*QueryHandler` | 单个读用例入口 |
| `adapter/web/dto/request/` | `*Req` | adapter 写请求 DTO |
| `adapter/web/dto/request/` | `*Qry` | adapter 查询请求 DTO，可选；不得进入 application |
| `adapter/web/dto/response/` | `*Resp` | adapter 协议响应 DTO |
| `infrastructure/persistence/` | `*PO` | 与领域模型分离时的持久化对象，不是领域模型 |

`Command`、`Query` 是 application 对象；`Req`、`Qry`、`Resp` 是 adapter 协议对象，二者不得混用。`handle()` 的对象参数推荐命名为 `command` / `query`，该约定只改善可读性。

### E-13.2 领域与应用构件

| 类型 | 示例 | 语义 |
|------|------|------|
| 聚合根 / 实体 / 值对象 | `Order`、`Payment`、`Money` | 使用通用语言名词，不加类目后缀 |
| 领域服务 | `OrderRefundService` | 仅 `domain/service` 的无状态纯业务规则 |
| 领域事件 | `OrderPaidEvent` | 上下文内已发生事实，使用过去式 |
| 集成事件 | `OrderPaidIntegrationEvent` | 跨上下文版本化事实 |
| 应用服务 | `OrderShippingApplicationService` | 可直接作为语义化用例边界，不与同义 Handler 套娃 |
| 编排器 | `OrderRefundOrchestrator` | 跨聚合、可重试或可补偿流程 |
| 入站门面 | `OrderFacade` | 符合 E-3.4 的稳定、粗粒度入站契约 |
| SDK 入站契约 | `MetadataApi` | 新增使用 `*Api`（目标命名示例，非现存类）；存量 `MetadataService` 不追溯 |

**`*Service` 后缀的两种含义**：

| 后缀 | 位置 | 职责 | 典型内容 |
|------|------|------|----------|
| `*DomainService` / `*Service`（domain 层） | `domain/service` | 纯业务规则，无 IO、无事务 | 跨聚合定价策略、复杂业务校验 |
| `*ApplicationService` | `application/service` | 用例编排，有事务、有 IO | 加载聚合、调用行为、保存、发布事件 |

禁止将领域服务命名为 `*ApplicationService`，也禁止在 ApplicationService 中承载聚合内部状态迁移规则。二者不是"简单 vs 复杂"的替代关系，而是不同层的职责分工。

参考：P-3.3 领域服务、E-3.1 允许的入口。

### E-13.3 端口、适配器与触发器

| 后缀 | 独占语义 | 禁止 |
|------|----------|------|
| `*Repository` | domain 写侧聚合仓储 | 读侧投影、`*Store` / `*Dao` / `*Mapper` 替代命名 |
| `*QueryPort` | application 模块内列表、搜索、统计等读侧端口 | 跨边界 ACL |
| `*Gateway` | 以本上下文语言声明的出站 ACL / Domain Gateway | 模块内查询、技术 Client |
| `*Adapter` | infrastructure 对某个 application/domain 出站端口的技术实现 | 业务用例入口或领域对象 |
| `*Job` | `adapter/schedule` 定时触发或 Outbox 中继 | 跨聚合业务编排 |
| `*Listener` | MQ / 事件入站监听 | 定时任务 |
| `*Api` | SDK 入站契约 | application 内部类型 |

`Repository`、`QueryPort`、`Gateway` 都是出站端口，但分别服务聚合一致性、读投影和外部业务语义隔离；`Adapter` 是这些端口的外层实现，不是第四种业务端口。

### E-13.4 后缀语义保险

一个后缀只承载一种构件类别；冲突时使用更具体的业务语义，例如 `OrderPaidIntegrationEvent` 优于 `OrderPaidMessage`。

| 后缀 | 独占语义 | 禁止 |
|------|----------|------|
| `*Event` | 领域事件 | 聚合根 / 实体；存量 `AlertEvent` 应演进为 `AlertRecord` |
| `*IntegrationEvent` | 集成事件 | 进程内领域事件 |
| `*Orchestrator` | 跨聚合业务编排 | 技术轮询 / Outbox 中继 |
| `*Facade` | 条件入站门面 | 不满足 E-3.4 的薄委托 |
| `*Service` | domain 领域服务 | application 用例边界应使用 `*ApplicationService` |
| `*ApplicationService` | 语义化 application 用例边界 | 与同义 Handler 套娃 |
| `*Job` | 定时触发 | 业务编排 |
| `*Listener` | MQ / 事件监听 | 定时任务 |

后缀不能替代职责评审。聚合根、实体和值对象必须优先使用 `glossary.md` 中的通用语言。

---

# 第三部分 门禁与实施状态（G-）

## G-1 测试与 CI

规则实现位于 `bone-framework/bone-architecture-test`；模块实际状态以各自 `ArchitectureTest` 为准。“Hard gate”表示规则适合机器阻断，不代表已在所有模块启用；`FreezingArchRule` 只阻断基线之外的新增违规。

门禁状态分三层，避免把"已定义"误读为"已强制"：

- **Defined**：共享规则库已实现（ArchUnit 规则存在）；
- **Enforced-in-CI**：模块 `ArchitectureTest` 实际接入，并在 GitHub Actions 等 CI 中运行；
- **Advisory / Planned**：声明方向但未强制，或仅本地脚本可运行。

凡仅在本地脚本运行、未进入 CI 的规则（如 12a/12b/12c）不得称为"已阻断"；`Active` 仅表示规则已定义在共享规则库，不代表全模块已强制。评审不得把 Planned/Advisory 声称为全仓已证明（见 [G-1.4](#g-14-planned)）。

<a id="g-1-1-hard-gate"></a>

### G-1.1 Hard gate

以下状态截至 2026-09-11；变动必须同步本文。`Frozen` 表示已有违规已登记且禁止新增，不表示当前代码完全符合目标。

| # | Hard gate 条款 | 实现（共享规则 / 模块自有） | 启用状态 |
|---|----------------|---------------------------|----------|
| 1 | domain 不依赖 adapter、application、infrastructure | `domainMustNotDependOnOuterLayers` | Active（blueprint 等已启用模块） |
| 2 | application 不依赖 infrastructure 实现 | `applicationMustNotDependOnInfrastructure` | Active（blueprint 等已启用模块） |
| 3 | adapter 不直接依赖 infrastructure 实现 | 暂无共享规则；integration 出站 Job 存在存量直连 | Frozen；补端口后升级 Active |
| 4 | domain 不使用 QueryBuilder/Criteria/SQL | `domainMustNotUseQueryBuilder` + Criteria `@ReadSideOnly` | Active（blueprint）；其余见模块测试 |
| 5 | Command 用例不使用 QueryBuilder | `commandHandlersMustNotUseQueryBuilder` | Active/Frozen 混合，见 G-1.5 |
| 6 | Controller 不直接操作 Repository 或领域服务 | `adapterControllersMustNotDependOnDomainRepository` / `adapterControllersMustNotDependOnDomainService` | Active（已接入模块） |
| 7 | 禁止业务模块依赖已删除的 `com.bone.core.usecase.*` | `noUseCaseClassesInApplication` | Active（兼容期） |
| 8 | 禁止外层业务代码修改聚合 `id` / `tenantId` | `outerLayersMustNotMutateAggregateIdentity` | Active（blueprint + 已启用应用模块） |
| 9 | 禁止 JPA、Hibernate、MyBatis、MyBatis-Plus | 暂无门禁实现；当前业务模块按约定维持 | Planned：maven-enforcer bannedDependencies |
| 10 | Controller 返回统一响应契约 | 暂无 ArchUnit 实现 | Planned：待确定可稳定静态判别的实现 |
| 11 | 业务层不直读 TenantContext | `businessLayersMustNotReadTenantContextDirectly` | Active（blueprint）；其余 Frozen/Planned |
| 12a | 通用密钥/Token 扫描 | `scripts/scan-secrets.sh` 等本地脚本；当前 GitHub Actions 未调用 | Manual / Planned：不得宣称 CI 已阻断 |
| 12b | 租户边界检查 | `businessLayersMustNotReadTenantContextDirectly` + 部分模块测试 | Partial：Blueprint Active，部分模块 Frozen；不能证明所有 SQL 均含租户隔离 |
| 12c | DDL 必备字段与文档同步 | `scripts/check-ddl-doc-sync.py`、`scripts/ci-check.sh` 可本地执行；当前 GitHub Actions 未调用 | Manual / Planned：当前无工作流硬门禁 |
| 12d | 第三方依赖漏洞扫描 | `.github/workflows/ci.yml` 的 OWASP dependency-check（CVSS ≥ 7 失败） | Active；只扫描依赖 CVE，不是密钥、租户或 DDL 检查 |

Hard gate 只保护结构和明确 API 使用，不证明领域模型正确。

### G-1.2 Semantic review

- 上下文和聚合边界；
- 不变量与领域行为；
- 多聚合事务例外；
- Orchestrator 补偿；
- 并发和可靠发布保证；
- ACL 翻译；
- D0/D1/PO 选择。

### G-1.3 Advisory

- Handler/DTO 后缀；
- Repository 方法名白名单；
- 事务注解存在性；
- `oneAggregatePerTransaction()`；
- `AggregatePureUnitTestGuard`。

### G-1.4 Planned

- freeze 到期 CI；
- 规则库完整正反 fixture；
- 跨上下文表所有权与 SQL Join 的完整静态检测；
- QueryPort 新位置门禁；
- adapter 入站与 infrastructure 出站方向门禁；
- application/domain 端口位置门禁；
- 平台 Integration Event Envelope；
- 并发策略模板；
- 禁 ORM（banned-dependencies）与统一响应契约的静态门禁。

评审不得把 Planned 或 Advisory 声称为全仓已证明。

### G-1.5 规则证明能力与模块启用状态

| 规则 | 分级 | 能证明 | 不能证明 |
|------|------|--------|----------|
| `domainMustNotDependOnOuterLayers` | Hard gate | domain 无外层类型依赖 | 领域规则完整 |
| `applicationMustNotDependOnInfrastructure` | Hard gate | application 无具体基础设施依赖 | 端口语义合理 |
| `domainMustNotUseQueryBuilder` | Hard gate | domain 不依赖已识别查询 DSL | 所有动态查询都被识别 |
| `commandHandlersMustNotUseQueryBuilder` | Hard gate | 已识别 Handler 不直用 DSL | 写用例没有隐式复杂读 |
| `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` | Advisory | 方法名落入当前白名单 | Repository 语义一定正确 |
| `noUseCaseClassesInApplication` | Bone 兼容门禁 | 不新增旧命名形态 | Use Case 概念错误 |
| `noApplicationUseCasePackage` | Bone 兼容门禁 | application 无 `usecase` 包（多数模块 freeze 防扩张） | 无其他套娃形态 |
| `noBoneCoreUseCaseApiDependency` | Bone 兼容门禁 | 无已删除的 `com.bone.core.usecase` 依赖（无存量，Active） | 其他遗留 API 依赖 |
| `noStudioGeneratorUseCaseAnnotation` | Bone 兼容门禁 | 业务模块不依赖 generator 自造 UseCase SPI（freeze） | 生成代码符合 DDD |
| `noNewDomainStorePackage` | Bone 兼容门禁 | 不新增 `domain.store` 包（freeze） | 存量 store 语义正确 |
| `noCustomBusinessException` | Bone 兼容门禁 | 不新增 `BusinessException` 命名冲突异常根（freeze） | 错误码绑定与归属正确 |
| `noBusinessExceptionSuffix` | Bone 兼容门禁 | 不新增 `*BusinessException` 后缀类（freeze） | 异常语义归属正确 |
| `applicationServicesMustNotOwnDomainRules` | Hard gate 目标 | ApplicationService 不承载状态迁移决策 | 规则放置是否最优 |
| `adapterControllersMustNotDependOnApplicationService` | 上帝对象守护（ADR-0028） | 方法名沿用历史名但仅禁 `Common*/Base*/Business*/*Manager` 命名；不再禁 Controller → 合法 `*ApplicationService` | 应用层编排一定正确 |
| `adapterControllersMustNotDependOnDomainRepository` | Hard gate | Controller 不越层访问写仓储 | Controller 无业务规则 |
| `adapterControllersMustNotDependOnDomainService` | Hard gate | Controller 不直调领域服务 | 应用编排正确 |
| `commandHandlersShouldBeNamedCommandHandler` | Advisory | 类后缀一致 | 类承担正确命令语义 |
| `queryHandlersShouldBeNamedQueryHandler` | Advisory | 类后缀一致 | 查询模型合理 |
| `commandHandlersShouldBeTransactional` | Advisory | 注解存在 | 事务代理真实生效 |
| `queryHandlersShouldBeReadOnlyTransactional` | Advisory | 只读注解存在 | 数据库真正只读 |
| `outerLayersMustNotMutateAggregateIdentity` | Hard gate | 已识别外层不调用身份 setter | 反射/映射无越权 |
| `readSideDslOnlyInQueryLayer` | Hard gate 目标 | DSL 只在基础设施 | 查询契约合理 |
| `noCrossContextModelDependency` | Hard gate 目标 | 已识别跨包模型依赖 | SQL/运行时无越界 |
| `noCrossContextDomainDependency` | Hard gate 目标 | 已声明根包的 domain 不依赖其他上下文 domain（Shared Kernel 白名单除外，按模块参数化启用） | 白名单合理性、运行时 SQL 越界 |
| `oneAggregatePerTransaction` | Advisory | 一个方法直接调用多个 Repository 类型 | 单聚合实例事务 |
| `businessLayersMustNotReadTenantContextDirectly` | Hard gate 目标 | 业务层不直接读上下文 | 异步租户传递正确 |
| `AggregatePureUnitTestGuard` | Advisory / 卫生检查 | 测试类、行为调用、断言形式存在 | 不变量完整、反贫血成立 |

“Hard gate 目标”表示规则方向适合硬门禁；是否在模块启用仍以模块测试为准。

| 规则 | Blueprint | IAM | MasterData | Integration | 其他模块 |
|------|-----------|-----|------------|-------------|----------|
| `commandHandlersMustNotUseQueryBuilder` | Active | Frozen | Frozen | Active | bone-notification、bone-extension-studio Active；System、Metadata Server、Studio Generator Frozen；其余待盘点 |
| `readSideDslOnlyInQueryLayer` | Active | Frozen | Frozen | Frozen | Planned inventory |
| `businessLayersMustNotReadTenantContextDirectly` | Active | Frozen | Frozen | Frozen | Planned inventory |
| `adapterControllersMustNotDependOnApplicationService` | Active（仅上帝对象守护，ADR-0028 收窄，不 freeze） | Active（legacy 收敛） | Active | Active | Active |

新增或更新状态必须同时提交对应 `archunit_store`。

### G-1.6 CORE / E 条文与门禁映射

为便于评审“这条原则由哪条门禁实际守住”，下表建立核心条文到其主要 enforcing gate 的映射。空白表示当前仅由语义评审守护，无机器门禁——这正符合 CORE-08：机器门禁证明结构，业务语义仍靠测试和评审。

| 条文 | 主要守护门禁 | 状态 |
|------|------------|------|
| CORE-01 边界先于分层 | 无直接门禁（语义 + 模块 README 数据所有权声明） | Semantic |
| CORE-02 依赖向内 | `domainMustNotDependOnOuterLayers` | Hard gate（部分模块） |
| CORE-03 领域行为保护不变量 | `applicationServicesMustNotOwnDomainRules`（目标） | Hard gate 目标 |
| CORE-04 一个用例一个边界 | `adapterControllersMustNotDependOnApplicationService`（收窄为上帝对象守护，ADR-0028） | Hard gate（收窄） |
| CORE-05 写聚合 / 读投影 | `commandHandlersMustNotUseQueryBuilder`、`readSideDslOnlyInQueryLayer` | Hard gate / 目标 |
| CORE-06 单聚合事务 | `oneAggregatePerTransaction` | Advisory |
| CORE-07 可靠性与并发声明 | 无直接门禁（语义 + [E-5](#p-5-cqrs-一致性与可靠性)） | Semantic |
| CORE-08 门禁不冒充领域证明 | 全文门禁说明 | 文档约定 |
| CORE-09 EAV 边界 | 无直接门禁（语义 + 评审） | Semantic |
| CORE-10 Metadata 不执行业务 | 无直接门禁（语义 + 评审） | Semantic |
| E-3 应用用例边界 | `adapterControllersMustNotDependOnDomainRepository` / `...DomainService` | Hard gate |
| E-4.2 读侧端口位置 | `readSideDslOnlyInQueryLayer`（目标） | Hard gate 目标 |
| E-6 D1 纯净度 | `domainMustNotDependOnOuterLayers`（白名单放行编译期注解） | Hard gate |
| E-7 错误模型 | `noCustomBusinessException` / `noBusinessExceptionSuffix`（freeze） | 兼容门禁 |

## G-2 Freeze

Freeze 用于阻止存量违规继续增加，不把违规永久合法化：

- 基线只允许收缩，新增违规直接失败；
- 每项债务有 Owner、拆除条件和目标日期；
- 到期机制未被 CI 消费前，只能称人工治理，不称硬门禁；
- 语义例外优先记录在 ADR 或模块 README，不能只加入 freeze 文件；
- 同一规则在多个模块反复例外时，应复核规则本身是否过度约束；
- 规则改口时先更新分类、正反 fixture 和声明，不通过重置基线掩盖新增问题。

目标 `freeze-ledger.yaml` 字段：

```yaml
- rule: adapterControllersMustNotDependOnApplicationService
  classification: adjusted-god-object-guard
  violations: 0
  items: []
  owner: platform-arch
  removalCondition: "ADR-0028 已收窄为上帝对象命名守护；freeze 条目随规则收窄清零"
  lastRenewal: null
```

`rule` 是稳定标识；`classification` 取 `debt`、`exception`、`planned-adjustment`；`items` 必须可定位；`removalCondition` 必须可验证；`lastRenewal` 记录续期。在 CI 真正读取该文件前，到期不会自动阻断构建，不得为满足台账数字而重写 freeze 基线。

### G-2.1 TEST-HYGIENE-01 限制

`AggregatePureUnitTestGuard` 只提醒关键聚合应有无容器测试，并检查测试至少调用行为且包含断言。不得据此推导聚合非贫血、不变量完整、断言有业务价值或聚合边界正确。

关键聚合评审至少检查主状态迁移、一个以上非法迁移、幂等行为、并发冲突或条件更新，以及领域事件只在真实迁移时产生。测试组合按风险、反馈速度和边界成本决定，不规定固定比例。

## G-3 规则准入

新增共享规则必须：

1. 有正例和反例 fixture；
2. 记录误报/漏报；
3. 在样板模块验证；
4. 文案不超过实际证明能力；
5. 区分 Hard gate、Semantic review、Advisory、Planned。
6. 存量模块使用 freeze 或分期迁移，不通过更新基线掩盖新增问题。

```text
原则/风险说明
  → ADR 或规范修订
  → 正反 fixture
  → 规则实现
  → blueprint 严格启用
  → 存量模块分期接入
  → 文档标记为已实现
```

应用模块至少启用 domain 依赖方向、application 不依赖 infrastructure、domain/Command 禁查询 DSL、Controller 不直连 Repository/领域服务、聚合身份不可被外层修改、禁止遗留 UseCase API，以及统一持久化栈检查。命名、事务注解、Repository 方法名和 R9 扫描默认作为 Advisory；模块若提升为阻断，必须说明这是工程策略而非 DDD 语义证明。

## G-4 交付验收清单

提交前逐项确认：

### 战略边界

- [ ] 上下文、Owner、通用语言和数据所有权清晰；
- [ ] 新依赖没有直接引用其他上下文的 domain/PO；
- [ ] API/事件变更具有版本与兼容策略。

### 战术模型

- [ ] 状态迁移通过聚合行为完成；
- [ ] 值对象在构造时保持有效且默认不可变；
- [ ] 领域服务无 IO、框架和事务职责；
- [ ] Repository 只服务聚合写模型。

### 应用与可靠性

- [ ] 一个用例只有一个入口边界，没有同义层套娃；
- [ ] 写事务位于最外层写用例；
- [ ] 多聚合事务例外有理由、失败语义和测试；
- [ ] 并发、幂等、重试、事件丢失与乱序策略明确。

### 验证

- [ ] 纯领域测试覆盖成功与拒绝路径；
- [ ] Repository/QueryAdapter/ACL 有适当集成或契约测试；
- [ ] ArchUnit 和受影响测试实际执行；
- [ ] 文档描述没有把 Advisory/Planned 写成已实现 Hard gate。

---

# 附录 A：迁移台账

<a id="migration-ledger"></a>

> 本附录记录存量实现与 v5.1.0 目标的差距，不属于 DDD 原则。台账目前由人工维护；`freeze-ledger.yaml` 到期检查尚未接入 CI。

## A.1 v5.0 新增迁移项

| 项目 | 当前状态 | 目标 | 迁移策略 |
|------|----------|------|----------|
| 读侧端口位置 | 尚无统一目标形态；存量使用 `domain/gateway/*ReadPort` 或 QueryHandler 直连 Repository/DSL | 新端口位于 `application/query/port` | 先在 blueprint/metadata-server 建样板；新增复杂查询使用新位置，存量随功能修改迁移 |
| ApplicationService 入站 | 当前 ArchUnit 禁止 Controller 直注 `application.service` | 合法语义化 ApplicationService 可作为用例边界 | 先补正反 fixture 并调整规则；完成前属于目标态 |
| R9 分级 | 当前按 Repository 类型扫描并可能阻断 | Advisory 风险提示 | 调整规则文案与接入级别，不把结果当语义证明 |
| 语义异常 | 当前禁止模块新增异常 | 允许三根异常的语义子类 | 先修改规则与错误码校验，再按业务需要引入 |
| 历史 R8 定位 | v4.x 指 `AggregatePureUnitTestGuard`，曾称反贫血主判据 | `TEST-HYGIENE-01` 测试卫生检查 | 保留 Guard，停止复用 R8；v5 使用 `CORE-*` |
| 固定测试比例 | 曾以约 70/20/10 为目标 | 按风险选择测试组合 | 删除固定比例，不要求代码迁移 |
| 并发策略 | L2+ 曾默认强制 version | 每个可并发写聚合声明策略 | 新聚合先声明；存量按高风险流程补齐 |
| 可靠发布 | Integration 已改为 Outbox 失败向上传播；其他发布器尚未逐项声明 | 原子发布能力契约，Outbox 为默认实现 | 按事件容忍度分类；Durable publisher 禁止吞异常或提前清事件 |
| Hard gate 启用范围 | 各模块接入不一致，部分规则仅 freeze 或未接入 | 每条规则标明 eligible/Active/Frozen/Planned | IAM、MasterData、Integration 先建新增违规基线；其他模块逐项盘点 |
| SDK Repository 读能力 | 基础接口同时暴露 `pageByCriteria`、`aggregate`、`queryByCondition` 等 | domain 写仓储不使用继承的读侧能力 | 短期加调用约束；中期评估拆分 `AggregateRepository` 与 `QueryOperations` |
| ArchUnit 规则文案 | 部分 `because(...)` 与注释仍引用 v4.x P0/R8/R9 | 使用 `CORE-*`、`TEST-HYGIENE-*` 或稳定兼容规则 ID | fixture 与 freeze 描述同步迁移，避免基线失联 |

## A.2 v4.x 延续迁移项

| 不符合形态 | 处置 | 状态说明 |
|------------|------|----------|
| `t_order_item` 明细未落库 | D0 + Separated（PO + Converter），确保聚合明细原子保存 | ADR-0019 阻塞已解除，仍需实现 |
| 纯委派 `*UseCase` | 删除薄层，入站直接依赖实际用例边界 | 存量按模块迁移 |
| `application/usecase/**` | 迁到 Handler/ApplicationService/Orchestrator | 禁止新增 |
| 自造 `@UseCase` / `UseCaseExecutor` | 删除并使用 `@Capability` 做能力发现 | 禁止新增 |
| `domain/store/*Store` | 改为 `domain/repository/*Repository` | 禁止新增 |
| 模块根包下 `controller/` | 迁到 `adapter/web/controller/` | 随模块重构 |
| `*Cmd` / `*Qry` 类名 | 改为 `*Command` / `*Query` | 命名一致性，不是 DDD 原则 |
| 平行 `BusinessException` 根 | 继承项目异常根并使用稳定错误码 | v5 允许语义子类 |
| Integration 异常继承错误根 | `SystemException`、`NotFoundException` 继承正确平台异常根 | 已修复，保留回归测试 |
| 聚合根使用 `*Event` 后缀 | 改为通用语言名词 | `AlertEvent` 需单独 ADR |
| Orchestrator 承载技术中继 | 定时触发放 `adapter/schedule/*Job`，中继实现放 infrastructure | blueprint 尚需删除中转层 |
| Job 直连 infrastructure 实现 | 依赖 domain/application 端口 | integration 尚需补端口 |
| `handle()` 参数名 `cmd` | 可按命名风格改为 `command` | 非硬门禁，批量重命名非优先 |
| `application/service` 技术工具类 | 迁到 infrastructure 或 SDK | extension-studio 存量待分流 |
| Controller 直注 domain service | 改为应用用例边界编排 | Hard gate |

已删除且不再迁移：`ConfigChangeListener`、`ConfigurationChangeListener` 死代码，以及“所有领域服务必须改名为 `*DomainService`”的旧计划。

## A.3 模块命名基线

`bone-integration`、`bone-masterdata` 等模块存在 `{Action}{Entity}Handler`，尚未统一为 `*CommandHandler`。现有 freeze 继续防止无意扩张；新代码优先保持模块内部一致性；是否全量改名由模块 Maintainer 决定。类后缀属于 Advisory，不得据此判定用例语义错误；迁移完成后收缩 freeze 并删除 README 例外。

## A.4 迁移优先级

1. **P0 正确性**：订单明细落库；不可丢事件原子发布与消费幂等；多租户异步传递和跨租户打洞可审计；外层不能修改聚合身份与租户归属。
2. **P1 边界**：新读路径使用 application QueryPort；跨上下文依赖改为 API、事件或 ACL；去除 Handler/ApplicationService 套娃；技术工具退出 application service。
3. **P2 一致性与风格**：Handler 后缀、参数名、包结构和历史命名差异清理。

## A.5 关闭定义

迁移项只有同时满足以下条件才可关闭：

1. 代码或文档达到目标形态；
2. 受影响测试实际通过；
3. 对应 freeze/待补清单已收缩；
4. 模块 README 的例外已删除或更新；
5. 规则实现状态与文档声明一致；
6. 未通过扩大白名单或重置基线掩盖问题。

# 附录 B：历史实施快照

<a id="historical-implementation-snapshot"></a>

> **历史快照，不代表当前 CI。** 以下记录分别截至 2026-05 与 2026-08；当前门禁结果只能以各模块 CI 的 `ArchitectureTest` 为准。

## B.1 `application → infrastructure` 清零记录（2026-05）

| 模块 | 当时端口 | 说明 |
|------|----------|------|
| bone-iam | `AccessTokenIssuer`、`RefreshTokenIssuer`、`AccountAuthorityCache` | JWT / Redis 缓存 |
| bone-masterdata | `MetaEntityCatalogPort`、`MasterDataExcelImportPort` | catalog 读取、Excel 导入 |
| bone-integration | `CamelFlowExecutionPort`、`IntegrationExecutionRecorder` 等 | 当时已用 application/port |

2026-06 重构曾重新引入违规（如 integration `TestConnectorHandler` 直注 `IntegrationExecutionMetrics`），后续已整改；此记录不应被用作今天的合规证明。

## B.2 ArchitectureTest 记录（截至 2026-08）

- 2026-05 已通过：bone-iam、bone-masterdata、bone-blueprint、bone-extension-studio；
- 2026-08 整改后通过：bone-integration、bone-system、studio-generator；
- bone-metadata-server：作为 SDK 库豁免当时 P0-4/5/6/7，架构测试 18 项通过。

## B.3 关键整改记录

- extension-studio 仓储拆分（ADR-0013，2026-05-22）：写侧 `domain/repository/*` 仅保留白名单方法；读侧当时迁入 `domain/gateway/*ReadPort`，包括 `ExtensionReadPort`、`ExtPointReadPort`、`PluginVersionReadPort`、`PluginExecutionLogReadPort`、`StudioAuditReadPort`。v5 新目标为 `application/query/port/*QueryPort`，存量随功能修改迁移；`repository_methods_whitelist` 已移出 freeze。
- bone-iam 聚合根基类（ADR-0011 阶段 1）：`TenantAggregateRoot` 用于 `Account`、`Role`、`AuditLog`；`AggregateRoot` 用于 `Tenant`、`Permission`。
- 仓储 `*And*` 禁令：`findBy*And*` 已由 ArchRule 拦截，例如 `findByPluginIdAndVersion` 调整为 `findByPluginVersion`。
- 已知技术债：bone-iam 的 `LocalDateTime` 审计统一按 ADR-0018 阶段 2 按需推进。

---

# 兼容入口与迁移说明

以下标题保留为 v4.x 仓内链接的兼容入口。

> **淘汰计划**：随下一个大版本（v5.2+）复核仓内引用清零后移除本节；新增文档不得链接到本节锚点。

<a id="e-53-应用层结构"></a>

## Legacy-E-5.3 应用层结构

v5.0.2 允许 Handler 或语义化 ApplicationService 直接作为用例边界，禁止互相套娃。详见[应用用例边界](#application-use-case-boundary)。

### Legacy-E-5.3.1 ApplicationService

ApplicationService 不再是 Handler 后的隐藏共享层。合法时直接作为最外层用例边界；不合法的通用桶、技术工具和领域规则仍需分流。

### Legacy-E-5.3.2 Facade

Facade 仍只用于多入站适配器复用或稳定 Client SDK 契约，不承载写事务和领域规则。

<a id="e-54-模块适用性"></a>

## Legacy-E-5.4 模块适用性

见 [E-9 模块适用性](#e-9-模块适用性)。模块性质按职责判定。

<a id="e-8-领域模型与纯净度"></a>

## Legacy-E-8 领域模型与纯净度

见 [E-6 领域模型与持久化模型](#e-6-领域模型与持久化模型)。

<a id="e-95-读侧端口"></a>

## Legacy-E-9.5 读侧端口

v5.0.2 新读侧端口位于 application；存量 `domain/gateway/*ReadPort` 增量迁移。见 [CQRS 与端口位置](#cqrs-port-location)。

<a id="b3-archunit-模板"></a>

## Legacy-B.3 ArchUnit 模板

模板与真实规则状态见 [G-1 测试与 CI](#g-1-测试与-ci)。复制规则前先核对分级和模块实现态。

---

# 附录 C：团队快速入门

## C.1 先记住四句话

1. 先确定限界上下文和业务语言，再讨论包和类。
2. Application 编排一个用例，Domain 决定业务是否允许。
3. Repository 服务聚合写模型，QueryPort 服务读投影。
4. 机器门禁保护结构，领域正确性仍靠测试和评审。

Bone 解决的不是“让每个模块看起来像 DDD”，而是企业平台长期演进中的三类问题：产品、接口、代码和表的术语不一致；模块共享数据和内部模型导致连锁影响；目录分层存在但业务规则仍散落在 Controller、脚本和数据库中。

可复用经验是：DDD 的首要产物是共同语言和决策边界；工程默认值必须说明适用条件；门禁必须声明证明边界；事务、事件和并发从失败语义反推；存量治理采用可收缩基线，避免大重写阻断业务。

## C.2 10 分钟阅读路径

1. [一页纸速览](#一页纸速览)；
2. [业务限界上下文](#context-map-业务限界上下文)；
3. `doc/glossary.md` 中当前模块的业务词；
4. [应用用例](#application-use-case-boundary)与 [CQRS 端口](#cqrs-port-location)。

读完应能回答：改动属于哪个上下文、谁拥有数据、写请求进入哪个用例、哪个聚合保护规则，以及查询是否需要加载聚合。

## C.3 第一个写用例

```text
Controller
  → 语义化 ApplicationService（默认入口）
    → Repository.load → Aggregate.behavior → Repository.save → 可靠发布（需要时）
  （写意图需显式契约 / 命令数多 / 异步多入口时 → 升级 Command / CommandHandler，见 E-3.7）
```

- Controller 只做协议转换和统一响应；
- 写事务位于最外层写用例；
- Handler 不通过 setter 拼装业务状态；
- 聚合行为同时处理允许与拒绝路径；
- Repository 接口在 domain，实现使用 bone-metadata-sdk；
- 跨聚合或跨上下文步骤声明失败与恢复语义。

## C.4 第一个读用例

```text
简单读（按 ID 详情 / 简单分页）：
Controller → ApplicationService.get/page → Repository

复杂读（列表 / 搜索 / 统计 / Join / 跨聚合组合）：
Controller → ApplicationService → QueryPort → QueryAdapter → SQL/DSL
（需独立路由 / 异步时 → QueryHandler）
```

- 默认简单读由 ApplicationService 直查写 Repository，与写用例共用同一入口（见 [E-4.2](#e-42-读侧)）；
- 仅当读模型与聚合结构分歧时，在 ApplicationService 内调用 `QueryPort`；需要独立路由才引入 `QueryHandler`；
- QueryPort 返回应用投影，不返回聚合；
- 列表、搜索、统计和 Join 不进入写侧 Repository；
- QueryBuilder、Criteria 和 SQL 只在 infrastructure；
- 查询包含租户隔离；
- 跨上下文数据来自公开 API、事件投影或数据产品。

## C.5 提交前与分歧处理

- [ ] 业务词已进入 glossary 或模块 README；
- [ ] 状态迁移在聚合内，外层没有直接修改身份、租户和业务状态；
- [ ] 写侧只用 bone-metadata-sdk，读写端口位置正确；
- [ ] 成功、拒绝、重复和并发风险有测试或明确说明；
- [ ] 受影响测试、ArchUnit 与格式检查已实际执行。

遇到分歧时依次判断：业务边界、不变量和数据所有权 → P- 原则 → E- 工程决策 → G- 门禁真实证明能力 → 命名与目录风格。平台级偏离新增 ADR；单模块折中记录 Owner、拆除条件和目标日期。

# 附录 D：代码与支付样板

## D.1 最小代码链

**默认路径（Application Service First）**：

```java
@RestController
@RequiredArgsConstructor
public class OrderController {
  private final OrderApplicationService orderService;

  @PostMapping("/orders")
  public ApiResponse<Long> create(@Valid @RequestBody CreateOrderReq req) {
    return ApiResponse.success(orderService.create(req.name(), req.customerId()));
  }
}
```

ApplicationService 编排用例：加载聚合、调用行为、保存、发布事件。完整示例见 E-3.6。选择性 CQRS（Command + Handler）路径见 E-3.6 参考形态。

```java
public final class Order extends AuditableAggregateRoot<Long> {
  private OrderStatus status;

  public static Order create(Long id, Long customerId) {
    var order = new Order();
    order.setId(id);
    order.status = OrderStatus.DRAFT;
    order.addDomainEvent(new OrderCreatedEvent(id, customerId));
    return order;
  }

  public void pay() {
    if (status != OrderStatus.DRAFT) {
      throw new DomainException("ORDER_NOT_PAYABLE");
    }
    status = OrderStatus.PAID;
    addDomainEvent(new OrderPaidEvent(getId()));
  }
}
```

```java
public record Money(long cents, String currency) {
  public Money {
    if (cents < 0) throw new DomainException("金额不能为负");
    if (currency == null || currency.isBlank()) throw new DomainException("币种不能为空");
  }

  public Money add(Money other) {
    if (!currency.equals(other.currency)) throw new DomainException("币种不一致");
    return new Money(cents + other.cents, currency);
  }
}
```

ACL 端口使用本地语言，例如 `AccessTokenIssuer`；application 只依赖端口，`infrastructure/security/JwtTokenService` 实现端口。读侧由 QueryHandler 依赖 `application/query/port/*QueryPort`，查询 DSL 只出现在 QueryAdapter。

领域事件的最小时序是：聚合记录事件；写用例保存聚合；Durable 场景在清事件前提取载荷并写入同事务 Outbox；Outbox 成功交接后才清事件。发布失败抛 `InfrastructureException` / `SystemException`，不转换成 `BizException`。

ID 生成遵循：`IDENTITY` 由数据库生成；非 `IDENTITY` 尊重调用方提供的非空 ID，否则由 metadata-sdk 生成并回填；创建用例返回持久化确认后的 ID；领域模型不依赖具体生成器。

### D.1.1 极简 ApplicationService 样板（L1 简单 CRUD / 支撑域）

以下为**默认形态**（多数 CRUD、配置、字典、简单主数据）。不使用 `Command` / `Handler` / `QueryHandler`，读写共用一个语义化 `ApplicationService`（详见 [E-3.7](#E-37-入口构件决策) 与 [ADR-0028](./adr/0028-application-service-first-selective-cqrs.md)）：

```java
@Service
@Transactional
public class CustomerApplicationService {

  private final CustomerRepository customerRepository;
  private final CustomerQueryPort customerQueryPort; // 仅复杂读时引入

  /** L1 简单写：直接编排聚合行为，无需 Command/Handler 三件套 */
  public CustomerId create(String name, Phone phone) {
    Customer customer = Customer.create(name, phone);
    customerRepository.save(customer);
    return customer.getId();
  }

  public void update(CustomerId id, String name, Phone phone) {
    Customer customer = customerRepository.getById(id);
    customer.update(name, phone);            // 状态迁移在聚合内
  }

  public void delete(CustomerId id) {
    customerRepository.deleteById(id);
  }

  /** 简单读：按 ID 详情 / 简单分页，直查写 Repository，无需 QueryPort */
  public CustomerDTO get(CustomerId id) {
    return CustomerDTO.from(customerRepository.getById(id));
  }

  public PageResult<CustomerDTO> page(CustomerPageQuery query) {
    return customerRepository.pageByQuery(query).map(CustomerDTO::from);
  }

  /** 复杂读（列表/搜索/统计/Join）才在内部升级 QueryPort，不新增独立 QueryService */
  public CustomerDetailDTO detail(CustomerId id) {
    return customerQueryPort.loadDetail(id);
  }
}
```

要点：

- 默认入口就是 `CustomerApplicationService`，新人一眼看懂"客户业务入口"。
- 写事务位于最外层 `@Transactional`；状态迁移在 `Customer` 聚合内，ApplicationService 只编排。
- 简单读与写共用同一服务；仅当读模型与聚合结构分歧时，才在方法内调用 `QueryPort`。
- 复杂度上升（命令数多 / 多入口 / 异步）再按 [E-3.7](#E-37-入口构件决策) 升级 `Command` / `CommandHandler`。

<a id="payment-sample"></a>

## D.2 支付聚合状态机

`Payment` 是独立聚合根，通过 `orderId` 引用订单：

```text
PENDING ──markPaying()──▶ PAYING ──confirmSuccess()──▶ SUCCESS
                              │──markFailed()──────▶ FAILED
                              │──close()───────────▶ CLOSED
SUCCESS ──refund()──▶ 记录 refundAmount/refundedAt + 领域事件
```

| 方法 | 前置 | 关键语义 |
|------|------|----------|
| `create(...)` | 必填字段且金额 > 0 | 创建 PENDING |
| `markPaying()` | 仅 PENDING | 进入支付中 |
| `confirmSuccess(channelTradeNo, paidAmount)` | PENDING/PAYING；FAILED/CLOSED 不可 | 幂等且金额一致；仅真实迁移发事件 |
| `markFailed(channelTradeNo)` | 非 SUCCESS | 重复失败静默 |
| `close()` | 非 SUCCESS | 超时/取消；重复关闭静默 |
| `refund(refundAmount)` | 仅 SUCCESS 且未退款 | 同金额重复返回 false；金额不同抛错 |

回调幂等必须在聚合内，以 `channelTradeNo` 为幂等键：

```java
public boolean confirmSuccess(String channelTradeNo, BigDecimal paidAmount) {
  if (status == PaymentStatus.SUCCESS) {
    if (!Objects.equals(this.channelTradeNo, channelTradeNo)) {
      throw new DomainException("支付单已成功，但回调渠道流水号不一致");
    }
    return false;
  }
  if (status == PaymentStatus.FAILED || status == PaymentStatus.CLOSED) {
    throw new DomainException("已失败或已关闭的支付单无法确认成功");
  }
  if (paidAmount == null || paidAmount.compareTo(amount) != 0) {
    throw new DomainException("实付金额与应付金额不一致");
  }
  status = PaymentStatus.SUCCESS;
  this.channelTradeNo = channelTradeNo;
  paidAt = Instant.now();
  addDomainEvent(
      new PaymentSucceededEvent(
          getId(), getTenantId(), orderId, amount, channelTradeNo, Instant.now()));
  return true;
}
```

并发重复回调必须再由存储兜底。Blueprint 已在 `bp_payment` 实现唯一索引 `uk_bp_payment_tenant_channel (tenant_id, channel_trade_no)`，`HandlePaymentCallbackCommandHandler` 已将对应唯一约束冲突按幂等结果处理，并有并发重复回调测试。当前仍缺少乐观锁或等价条件更新，因此不能宣称并发覆盖写问题已完整解决。

<a id="payment-sample-signature"></a>

## D.3 回调验签

所有回调（包括失败、关闭等回调）必须在 adapter/ACL 边界验签；签名不可信直接拒绝，不进入 Handler 和领域。

```java
public interface PaymentSignaturePort {
  boolean verify(long paymentId, String channelTradeNo, BigDecimal amount, String signature);
}
```

adapter 直接把回调报文中的 `paymentId`、`channelTradeNo`、`amount` 和 `signature` 标量传给验签端口；验签前不加载、也不向验签端口传递 `Payment` 聚合。验签通过后，adapter 才把已验证的回调标量组装为 Command，应用用例再按租户加载聚合并执行状态迁移。

演示实现可使用 HMAC-SHA256，但生产密钥必须外部化，并加入 nonce/时间戳缓存窗口防重放，按渠道采用 RSA/证书体系。仅成功回调验签是错误实现。

## D.4 超时、退款与生产补齐

`PENDING`/`PAYING` 超过 5 分钟未支付时，定时 Job 只执行“查找 → 调用 `payment.close()` → save”，不在 Job 中复制状态判断。关闭后再收到成功回调，应在验签通过后进入渠道对账或人工处理。

| 项 | 样板现状 | 生产要求 |
|----|----------|----------|
| 并发幂等兜底 | 聚合幂等 + `uk_bp_payment_tenant_channel` + 唯一冲突处理/测试已实现；乐观锁缺失 | 补乐观锁或等价条件更新，并验证数据库迁移已应用唯一索引 |
| 验签 | 演示 HMAC | 密钥外部化 + 防重放 + 渠道证书 |
| 退款/对账 | 单次全额退款 | 分批累计退款 + 对账任务 |
| 渠道适配 | 模拟渠道 | 真实渠道 ACL 适配 |

# 附录 E：架构适应度仪表盘

<a id="architecture-fitness-dashboard"></a>

建议每季度复核：

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| `archunit_store` 新增违规 | 0 | CI `ArchitectureTest`；季度收缩基线 |
| `domain` 依赖外层 | 0 | `domainMustNotDependOnOuterLayers` |
| 平行异常根 | 0 | `noCustomBusinessException` + 异常继承规则 |
| 仓储 `findBy*And*` 方法 | 0 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` |
| 关键领域拒绝路径 | 全覆盖 | 领域测试 + 语义评审；不规定固定测试比例 |

统计口径是“新增为 0 + 季度收缩基线”，不是违规数按比例环比；当违规趋近 0 时，比例指标失去约束力。季度复核还应核对规则 Active/Frozen/Planned 状态、过期例外、Context Map 演进触发条件和迁移关闭定义。

---

<a id="version-history"></a>

# 附录 F：版本历史

- **v5.3.1（2026-09-14）**：依据 ADR-0028 与「Application Service First + Selective CQRS」方案评审，收敛样板与入门示例的自相矛盾。① 附录 D 新增 D.1.1 极简 ApplicationService（L1）样板，使 Blueprint 默认形态自身体现简单路径，避免重模板被复制放大；② 附录 C.3/C.4 第一个写/读用例改为 ApplicationService 优先，与 E-3.7/E-4.2 对齐；③ G-1.1 明确 Defined / Enforced-in-CI / Advisory 三态，防止把未接入 CI 的规则称为已阻断；④ E-6.2 显式 D1 门禁白名单放行编译期注解，防合法 D1 类误报；⑤ 新增 G-1.6 CORE/E 条文与门禁映射表，提升原则→门禁可追溯性；⑥ 澄清 E-4.2 读侧判据「分页」口径：简单单表分页 / 按 ID 详情 / 1:1 映射 DTO 的列表仍直查写仓储，仅跨聚合、Join、统计、定制投影等结构分歧才升级 QueryPort，与 D.1.1 `page()` 样板对齐。
- **v5.3.0（2026-09-14）**：Application Service First 强化。① 新增 Ceremonial Architecture（仪式感架构）反模式定义与检测标准（E-3.2）；② 新增 AS-01～AS-06 六条编号规则（E-3.7）；③ 新增复杂度与类数量对比表（E-3.7）；④ Blueprint 代码生成器默认行为约束：默认不生成 command/query 目录（E-3.7、E-12）；⑤ P-1 增加复杂度检查标准，防止过度设计；⑥ E-13.2 区分 `*DomainService` 与 `*ApplicationService` 的职责边界；⑦ 读侧取消 QueryService 独立层级，读用例统一由 ApplicationService 处理，复杂读升级 QueryPort（E-3.7、E-4.2）。依据「Application Service First + Selective CQRS」方案评审。
- **v5.2.0（2026-09-14）**：确定默认入站边界为**语义化 ApplicationService**，采用「Application Service First + Selective CQRS + Complexity-driven DDD」（ADR-0028）。① E-3：入站边界默认改为 ApplicationService，Command/QueryService/Handler 为复杂度驱动可选件（E-3.1 表、E-3.7 决策流程重写）；② E-3.8 新增构件职责速查（ApplicationService/Command/CommandHandler/ReadModel 各回答什么问题；QueryService 独立类已在 v5.3.0 读侧取消，读侧统一走 ApplicationService + QueryPort），并明确「抽象价值 = 承担独立职责，而非符合 DDD」；③ E-3.9 新增「场景 × 模式」决策矩阵；④ E-3.10 新增 AI/Agent 7 问选型判据（含命令异步化必须走 Outbox + 消费端幂等）；⑤ E-4 新增 DDD 复杂度与 CQRS 复杂度**正交**原则（二维模型，领域复杂≠Full CQRS）；⑥ E-4.2：读侧默认改为 AppService 直查、明确读写不必对称；⑦ 默认交付路径、E-12 新模块 5 步同步；⑧ 门禁口径：用例构件呈现与 rule #19 降为裁定（Advisory），硬门禁只保留不变量。
- **v5.1.2（2026-09-14）**：新增核心红线 CORE-09（EAV 只承载扩展字段）与 CORE-10（Metadata 描述模型、不执行业务），核心规则从 8 条扩为 10 条；E-6 新增 E-6.5 EAV 边界判定（物理列 > 预留列 > JSON > EAV）；P-2.5 补充元数据职责边界。依据外部架构评审收敛建议。
- **v5.1.1（2026-09-11）**：状态对账（不改语义）。① G-1.5 表②：修正 Blueprint 列 `adapterControllersMustNotDependOnApplicationService` 为 Active（实测 0 违规样板、不 freeze），并与 IAM/MasterData/Integration 的 Frozen 事实区分；② G-1.5 表①补登 `noApplicationUseCasePackage`、`noBoneCoreUseCaseApiDependency`、`noStudioGeneratorUseCaseAnnotation`、`noNewDomainStorePackage`、`noCustomBusinessException`、`noBusinessExceptionSuffix`、`noCrossContextDomainDependency`、`applicationServicesMustNotOwnDomainRules` 8 条已在共享规则库与模块测试中启用的规则；③ E-10：认定 `domain/{aggregate}` 平铺分组为合法变体（与 `domain/model/{role}` 二选一），依据 Blueprint/IAM 现行形态；④ E-4.2：标注 Blueprint 读侧为存量形态，新模块按 `application/query/port` 实现；⑤ G-2：freeze 台账 `rule` 标识统一为共享规则名；⑥ E-13.2：`MetadataApi` 标注为目标命名示例；⑦ P-2.4：文本版上下文关系图替换为 `assets/context-map.svg`（依关系表逐行对账绘制，OHS 用母线表达全覆盖）；⑧ 历史 ASCII 图中「Metadata ─C-S→ Extension」线经代码裁决废弃：`bone-extension-studio` 仅依赖 `bone-metadata-sdk` 的 Table/Column 注解、Criteria 与 Repository 等技术持久化能力，对 `bone-metadata-server` 内部包 import 为 0，按 P-2.2「SDK 与技术依赖不混入 Context Map」不构成业务级 Customer–Supplier 关系，图注已同步，不补关系表。
- **v5.0.2 追记（2026-09-11，同日第二次复核）**：
  1. E-3.5 参考形态对齐 blueprint 的 `findByIdInTenant`、`Optional.ofNullable(...).orElseThrow` 和 `publishFrom`；事件发布统一为 `publishFrom` 口径，并登记 Outbox“先取载荷后发布”的时序约束。
  2. 逐条登记 12 条 Hard gate 的实现载体与 Active/Frozen/Planned 状态，并补录 Planned 盘点。原变更记录曾将这项状态登记关联到拟议的 `ADR-0026 hard-gate-per-rule-status`；现行 ADR-0026 已用于单文档整合，规则状态以本文 G-1 为准。
  3. 当时曾补齐拆分文档索引并指定 `ddd/README.md` 为索引真源；该双源安排已被 v5.1.0 与 ADR-0026 取代。
  4. Context Map 模块列改为可定位路径口径并增加“模块定位”图例；兼容入口补充 v5.2+ 淘汰计划。
  5. 新增 `scripts/ci/check-ddd-doc-code-sync.py` 并接入 CI，检查 v4 编号残留、文档引用的 bone-core 符号存在性和 `publishFrom` 口径一致性。
- **v5.0.2（2026-09-11）**：核心规则改用 `CORE-*`，历史 R8 改为 `TEST-HYGIENE-01`；D0/D1 与 Shared/Separated 拆为双维度；明确 Hard gate 类别不等于全仓启用；QueryPort 统一到 application；Criteria 增加 `@ReadSideOnly` 并建立 freeze；修复 Integration 异常根和 Outbox 失败语义；登记 metadata-sdk Repository 读能力迁移项。关联 ADR-0025。
- **v5.0.1（2026-09-10）**：主文档从索引型入口扩充为可独立学习正文；增加角色阅读路径、聚合建模工作法、最小聚合示例、读写用例与一致性决策树；新增团队入门和外部概览。
- **v5.0（2026-09-10）**：
  1. 分离 Context Map 与技术模块依赖图，修正 Shared Kernel、Conformist 等术语。
  2. 保留 Metadata 为 Bone 当前核心域的决策，删除“DDD 通常只能有一个核心域”的行业断言。
  3. R9 改为“默认一个事务修改一个聚合实例”；按 Repository 类型扫描降为 Advisory。
  4. Handler 与 ApplicationService 统一为应用用例边界，禁止一对一套娃。
  5. QueryPort 目标位置迁到 application，查询 DSL 只留在 infrastructure。
  6. 删除“跨聚合 ID 必须 D0 + PO”的错误触发条件。
  7. Outbox、乐观锁改为默认参考实现，规范改为约束可靠性与并发保证。
  8. 允许项目异常根下的语义子类。
  9. v4.x R8 降为 `TEST-HYGIENE-01` 测试卫生检查，删除固定 70/20/10 测试比例。
  10. 主文档当时重写为规范入口，并依据 ADR-0024 将上下文、应用一致性、门禁、迁移台账与版本记录拆为分册；该结构后被 ADR-0026 取代。
- **v4.9（2026-09-10）**：ADR-0023 将 Metadata 定为唯一已认定核心域；MasterData、Integration、Extension 调整为支撑域；共享内核增加白名单；D1 默认收缩到简单 CRUD 支撑域；区分硬门禁、建议项和未实装能力；新增 glossary。
- **v4.8（2026-09-08）**：收敛 Handler、领域服务、Job、Listener、Facade、API 命名；Orchestrator 禁止承载技术中继；SDK 入站契约统一 `*Api`；删除不可达 Listener 死代码。
- **v4.7（2026-09-08）**：明确目标态与实现态不可混用；记录 #19–#22 规则启用范围；承认共享规则缺少逐条正反 fixture；承认 `freeze-ledger.yaml` 与到期硬失败尚未实现。
- **v4.6（2026-09-05）**：新增 R9“一事务一聚合”及扫描；按事件是否允许丢失选择发布方式；ApplicationService 从命名禁令改为内容约束；多租户读取收敛到统一入口；新增 D1 退出、并发幂等、强类型 ID 和数据所有权约定。
- **v4.5（2026-08-30）**：建立 R1–R8 铁律与 L0–L3 能力档位；明确 Bone 为厚共享技术内核的模块化单体；引入聚合纯单测 Guard；收敛应用层结构、异常根与 CRUD 支撑域豁免。
- **更早版本**：v4.4 及以前的支付样板、回调验签、聚合基类和读侧拆分等决策，以对应 ADR 与 Git 历史为准；历史 Blueprint 版本号不再作为当前规范依据。

---

# Owner 与修订

- Owner：Bone 架构组。
- 规范语义变更必须有 ADR。
- 边界变化先更新 Context Map 与 glossary。
- 机器规则变化先补 fixture，再更新实现状态。
- 版本变化记录在[附录 F：版本历史](#version-history)。
