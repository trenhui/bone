# Bone 领域驱动设计（DDD）统一实践方案

> **唯一权威**：本文档置于 `doc/architecture/`，为 Bone 仓库内 **DDD 与分层门禁** 的唯一权威规范（与同目录总体架构、数据库规范并列维护）。  
> **结构**：本文统一为**三段编号**——**P-**（原则，业界共识，不含 Bone 实现词）、**E-**（工程规范，Bone 平台落地）、**G-**（门禁，ArchUnit / Checkstyle / freeze 台账），消除「第一部分 §10 / 第二部分 §10」双编号歧义。修订时先对齐原则，再调整落地条文。  
> **定位说明**：本文是 **Bone 仓库内 DDD 与分层门禁的权威规范**，对齐主流 DDD/整洁架构共识，并含 **D1 元数据注解** 等工程折中；**非**全行业唯一标准，复杂域请结合 ADR 裁剪。  
> **关联文档**：[BONE-总体架构设计方案.md](./BONE-总体架构设计方案.md)（平台总体架构、NFR、安全与数据一致性策略，与本方案互补）；[README.md](./README.md) 为架构文档索引；模块详设见 [doc/design/modules/README.md](../design/modules/README.md)。  
> **版本**：**4.6** | **日期**：2026-09-05  
> **分册索引**：[ddd/README.md](./ddd/README.md)（原则 / 工程落地 / CQRS / 附录 / **补充条文与示例**）  
> **近期 ADR**：[0011 AggregateRoot 继承链](./adr/0011-aggregate-root-inheritance.md)、[0012 SystemException 层次](./adr/0012-system-exception-hierarchy.md)、[0013 extension-studio 读侧 ReadPort](./adr/0013-extension-studio-repository-read-side.md)、[0019 ID 生成契约](./adr/0019-id-generation-contract-respect-non-null-id.md)、[0020 反贫血机制](./adr/0020-anti-anemia-weak-form-strong-behavior.md)、[0021 Outbox 平台化](./adr/0021-outbox-and-consumer-idempotency-platformization.md)、[0022 回调验签端口化](./adr/0022-external-callback-signature-verification-port.md)
>
> **v4.6 收敛摘要（2026-09-05）**：本版为**语义约束补强**，不新增风格规则（命名/后缀类规则反而继续降级）。① 新增 **R9 一事务一聚合**（[E-3.1.2](#e-312-r9-一事务一聚合)）——补齐 v4.5「聚合是一致性边界」有原则无门禁的缺口；② 修 [P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 决策表**自相矛盾**（进程内事件既要求 Spring Event 又要求 Outbox 落库），改按「容忍度」分档；③ **E-5.3.1 改口**：`{语义}ApplicationService` 由「命名禁令」改为「**内容禁令**」（[E-5.3.1](#e-531-应用层共享逻辑按性质分流--语义applicationservice-内容禁令v46-修订)），承认用例级编排是合法构件，取消三套例外通道；④ **E-4.4 改口**：租户取值从「禁止读线程上下文」改为「**单一取值入口 + 异步显式传递**」，与现实对齐；⑤ 读侧判据由「`@ReadSideOnly` 标记」升级为「**位置白名单**」（[E-9.3](#e-93-读侧v46位置判据替代注解判据--目标态收敛)），并给出读侧 DSL 退出 application 层的目标态；⑥ 新增 [E-8.3 D1 退出条件](#e-83-d1-退出条件与-po-分离迁移路径)、[E-9.6 并发与幂等原语](#e-96-并发控制与幂等原语v46-新增)、[E-9.7 强类型 ID](#e-97-强类型-idv46-新增过渡期约定)、[E-4.1.1 数据所有权判据](#e-411-数据所有权的可执行判据v46-新增)；⑦ **例外与 freeze 收紧**：到期日改为 CI **硬失败**、新增例外预算（单模块 ≤5 条）与 freeze 总量上限、台账 yaml 化（[B.3.1](#b31-freeze-台账模板违规数单调不增)）；⑧ **门禁落地（本版）**：R8 判据由三条强化到**五条**（追加「调用行为方法」「含断言」，堵 `assertEquals(1,1)` 式空测试，见 [E-8](#e-8-领域模型与纯净度d0--d1--d2) 判定口径）；`outerLayersMustNotMutateAggregateIdentity`（#18）补齐到 **bone-blueprint + 全部 8 个应用模块**（此前已实现未启用），判据修正为豁免持久化适配器角色与 PO 映射；`AggregatePureUnitTestGuard` 新增 `verifyAllowingPending` **待补清单**形态供存量模块过渡（只许收缩、防永久豁免）。
>
> **v4.5 收敛摘要（2026-08-30）**：① 铁律 P0 七条 → **R1–R8 八条**（新增 R8「每个聚合根一个纯单测」，见 [E-3](#e-3-铁律r1r9-必守)）；② 文档三段编号 P-/E-/G-；③ 三处公开改口：D1 更名「可持久化充血模型」（[E-8](#e-8-领域模型与纯净度d0--d1--d2)）、承认「厚共享内核的模块化单体」（[P-10](#p-10-bone-上下文映射参考)）、`bone-gateway` 归为基础设施服务（[E-5.4](#e-54-模块适用性按性质而非物理位置)）；④ 应用层减负（删 Facade F3、Orchestrator 降级、允许 `{语义}ApplicationService`，见 [E-5.3](#e-53-应用层结构强制)）；⑤ Outbox 按可靠性触发、回调验签全局硬规则（[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发)、[P-5.5](#p-55-回调幂等与跨聚合协作支付场景样板)）；⑥ 异常收敛三根、多租户参数化、CRUD 支撑域豁免（[E-7.3](#e-73-异常与-api规范)、[E-4.4](#e-44-多租户在-ddd-中的约定)、[E-5.4](#e-54-模块适用性按性质而非物理位置)）。
>
> **使用说明**：
> - 本方案为**稳定规范**，**不再附迁移时间表 / 批次 / Owner**。**凡不符合本规范的代码均须迁移**；落地节奏由各模块负责人在内部排期，不写入本文。  
> - **新代码**：必须 100% 满足 [E-0 规范稳定性契约](#e-0-规范稳定性契约) + [E-3 铁律](#e-3-铁律r1r9-必守)，由 ArchUnit 共享规则库（`bone-framework/bone-architecture-test`）在 CI 强制。  
> - **历史变更**：删除（仓库 Git 历史可查）。本文只描述「最终态」。

---

# 一页纸速览（v4.7）

> 目标是：未读过本规范的人可在 **5 分钟内**读完本节并复述 9 条铁律与其自身模块所处的复杂度档位。

## 9 条铁律（R1–R9）

| # | 铁律 | 一句话 | 判定入口 |
|---|------|--------|----------|
| R1 | 依赖方向 | `adapter → application → domain ← infrastructure`，领域不依赖框架与 IO | ArchUnit（[G-1](#g-1-测试与-ciarchunit-规则集)） |
| R2 | 领域规则在 domain | 状态流转 / 不变量在聚合根或领域服务；Handler 只编排（反贫血，[E-8](#e-8-领域模型与纯净度d0--d1--d2)） | ArchUnit + CR |
| R3 | 外部经端口 | 外部系统仅经端口 + infrastructure 实现；DTO / 异常不穿透领域 | ArchUnit |
| R4 | 写侧仓储收敛 | 仓储只做聚合加载与持久化，返回类型受约束（[E-9.2](#e-92-写侧-repository)） | ArchUnit |
| R5 | 读侧查询收敛 | 复杂查询走 QueryBuilder / QueryHandler；读路径「两条 + `*ReadPort` 例外」（[E-9.5](#e-95-读侧端口readportadr-0013)） | ArchUnit |
| R6 | CQRS 与事务边界 | CommandHandler 禁 QueryBuilder；写事务在 Handler | ArchUnit |
| R7 | 应用层单层 | 禁 UseCase；入站 Controller → Handler / Orchestrator / Facade（[E-5.3](#e-53-应用层结构强制)） | ArchUnit |
| R8 | 聚合纯单测（Bone 门禁） | 每个聚合根一个**无容器纯单测**（主状态机 + ≥1 拒绝路径） | `AggregatePureUnitTestGuard`（存在性 + ≥1 `@Test` + 无容器 + **调用行为方法 + 含断言**） |
| **R9** | **一事务一聚合**（v4.6） | 一个写事务内只持久化**一个**聚合根；跨聚合走事件 / Outbox / Orchestrator | ArchUnit `oneAggregatePerTransaction`（[G-1 #21](#g-1-测试与-ciarchunit-规则集)） |

## 能力开关 L0–L3（按模块复杂度逐级启用）

| 档位 | 适用 | 启用内容 |
|------|------|----------|
| **L0** | 所有模块 | [E-0 规范稳定性契约](#e-0-规范稳定性契约) + R1/R2/R3 + [E-5.2 极简树](#e-52-极简树小模块默认够用) + ArchUnit 基础规则 |
| **L1** | 小型应用模块 | L0 + R4/R5/R6/R7/**R9** + [E-5.3 应用层结构](#e-53-应用层结构强制) + [E-8 反贫血](#e-8-领域模型与纯净度d0--d1--d2) |
| **L2** | 核心业务域模块 | L1 + R8 聚合纯单测 + 领域事件 + 事件契约版本化（[E-4.5](#e-45-事件建模与事件契约演进2026-08-补充)）+ 完整读侧 CQRS |
| **L3** | 核心域 + 跨进程 / 对外回调 | L2 + Outbox（[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发)）+ 回调验签（[P-5.5](#p-55-回调幂等与跨聚合协作支付场景样板)）+ ACL 契约测试 + freeze 台账（[附录 B.3](#b3-archunit-模板bone-architecture-test)） |

> **L 档与 R1–R9 的关系（v4.7 澄清）**：R1–R9 对所有「应用 / 控制面 BFF」模块**强制**（[E-3.1](#e-31-r1r9--全局九条ci--archunit-硬门禁) 适用范围），与 L 档**无关**——L0/L1 模块同样必须满足 R1/R2/R3/R9；L 档只是**增强包**（聚合纯单测、Outbox、回调验签、契约测试、freeze 台账），**不构成豁免**。**L 档按集成形态与演进压力启用**（有无跨进程发布 / 对外回调 / 资金语义），不以 [P-10.1](#p-101-限界上下文与子域类型) 子域分类为唯一依据（三个核心域待复核，见该节 ⚠️ 与处置）。

## 按角色阅读路径

| 角色 | 路径 |
|------|------|
| **新人 / 新模块** | 一页纸速览 → [E-0](#e-0-规范稳定性契约) → [E-12.1 新模块入门（5 步）](#e-121-新模块快速入门5-步) → [附录 B.3 ArchUnit 模板](#b3-archunit-模板bone-architecture-test) |
| **评审** | R1–R9 逐条对照（G-1 判定入口）→ [E-8 反贫血](#e-8-领域模型与纯净度d0--d1--d2) → [E-5.3 应用层](#e-53-应用层结构强制) → [P-10 上下文映射](#p-10-bone-上下文映射参考) |
| **AI / 生成器** | [E-12.2 AI / Agentic 生成守则](#e-122-ai--agentic-生成守则)（先判断一致性边界，再生成结构） |

---

# 第一部分　原则（P-）——业界共识与领域架构（北向星）

本部分为 **P-** 段：描述在**不绑定** Bone 实现细节的前提下，与主流 DDD、整洁架构、有界系统实践一致的**理想形态**，用于架构评审、演进目标与差距分析。

---

## P-1. 原则适用范围（何时值得系统化 DDD）

- 业务复杂度值得用**领域模型**长期表达与演进（纯 CRUD 且无演进压力时可弱化，见 [E-5.4](#e-54-模块适用性按性质而非物理位置) CRUD 支撑域豁免档）。  
- 团队愿意为**边界、通用语言与一致性**投入建模与治理成本。  
- 技术中立：持久化、消息、RPC、UI 均为**可替换实现**，不应用实现细节绑架领域语义。

---

## P-2. 战略设计（应优先于战术）

### P-2.1 限界上下文（Bounded Context）

- 每个上下文有**明确的业务能力范围**与**独立演进的通用语言**。  
- **数据与写入规则的所有权**清晰：默认不与其他上下文共享「写模型」。  
- 上下文规模以**团队可认知、可测试、可发布**为上限；过大则拆分。

### P-2.2 通用语言（Ubiquitous Language）

- 产品、领域专家与研发使用**同一套词汇**；聚合、命令、事件、API **体现**该语言。  
- 禁止「技术词冒充业务词」；禁止边界两侧**同名不同义**而不加限定。

### P-2.3 上下文映射（Context Map）

- 与相邻上下文的关系**显式标注**（合作、客户–供应方、防腐层、开放主机服务、发布语言等）。  
- 跨边界集成**不依赖**对方内部模型；经 **API / 事件契约 / 防腐层** 完成。  
- **Bone 各上下文的具体映射关系**见 [P-10 Bone 上下文映射（参考）](#p-10-bone-上下文映射参考)。

### P-2.4 子域类型（推荐）

- 区分**核心域 / 支撑域 / 通用域**；创新与资源优先投向核心域；通用域倾向采购或标准化。

---

## P-3. 战术设计（领域模型）

### P-3.1 聚合（Aggregate）

- 聚合是**一致性边界**与**不变量**的载体；**一事务内**宜只提交**一个**聚合的变更（跨聚合用最终一致）。  
- 聚合尽量**小**；聚合间仅通过 **ID** 引用，不持有对方对象图。  
- 规则与状态迁移在**聚合根或领域服务**中表达，避免贫血模型与外层「隐式领域逻辑」。

**聚合边界识别 4 问（2026-08 补充，对齐 Vernon《实现DDD》聚合四原则）**：

| 问 | 判定 |
|----|------|
| ① 哪些对象在同一**事务内必须同时满足不变量**？ | 是 → 同属一个聚合；否 → 拆开，用 ID 引用 |
| ② 删除/重建该根时，哪些子对象**没有独立存在意义**？ | 是 → 作为聚合内实体/值对象；否 → 独立聚合 |
| ③ 外部能仅凭 ID 完成对该子对象的操作吗？ | 是 → 独立聚合（如 `OrderLine` 通过订单上下文操作）；否 → 留在聚合内 |
| ④ 变更频率与共享边界是否一致？ | 高频变更子对象与低频根混在一个聚合 → 拆开（避免整根锁竞争） |

**反例警示**：
- **God Aggregate**：一个根持有全部业务实体（如 `Order` 内含 `Customer`、`Product` 全对象图）—— 违反 ①③，事务放大、并发冲突剧增。
- **聚合退化为一组 getter**：聚合内实体无行为方法、状态由外部 get-set 序列修改—— 违反 [E-8](#e-8-领域模型与纯净度d0--d1--d2) 反贫血红线。
- **跨聚合直接持有对象引用**（非 ID）—— 违反 ②③，破坏一致性边界。

### P-3.2 实体与值对象

- **实体**：有稳定标识，关注生命周期与连续性。  
- **值对象**：无独立标识、**不可变**（或等价约束）、按值比较。**2026-08 补充**：Bone 内值对象建议**一律使用 `record`（或全 `final` 字段 + 无 setter）**，如 IAM 的 `Username` / `Email`（`record` 实现）；禁止"可变值对象 + setter"形态，CR 按此审查。

### P-3.3 领域事件

- 表达**已发生的领域事实**（过去式、不可变载荷）；可用于上下文内协作或作为跨上下文**发布语言**的载体之一。  
- **领域事件**与**集成事件**职责应区分：前者偏领域语义，后者偏系统间契约。

### P-3.4 仓储（Repository）

- 面向**聚合根**的持久化抽象，语义接近「集合」：**按 ID 加载、持久化整体变更**；**不承担**通用报表与任意条件查询。

---

## P-4. 架构风格（六边形 / 整洁架构）

- **依赖方向**恒指向领域：外层依赖内层，**领域不依赖**框架与 IO。  
- **入站端口**：应用层 Handler / Orchestrator API（**禁止**以 `*UseCase` 作为入站端口，见 [E-5.3](#e-53-应用层结构强制)）；**出站端口**：仓储、消息、第三方抽象；**适配器**实现技术细节。

### P-4.1 领域纯净度（理想目标）

- **理想**：领域为纯模型 + 领域服务，**不依赖**具体数据库、ORM、Web、消息 SDK；映射在**基础设施或应用边界**完成。  
- **工程折中**：Bone 允许 **D1**（见 [E-8](#e-8-领域模型与纯净度d0--d1--d2)）：在领域类型上使用 **bone-metadata-sdk** 元数据注解，须在评审中**显式承认**与纯 POJO 理想之间的差距及收益（元数据驱动、少样板）。

---

## P-5. CQRS 与一致性（概念 + 物理）

### P-5.1 概念层（默认建议）

- **读写关注点分离**：写服务聚合不变量；读服务查询、报表与组合展示。  
- **不必**默认独立读库或事件溯源；物理拆分**按需演进**。

### P-5.2 物理层（按需）

- 读写性能、模型形状或发布节奏**显著分叉**时，引入**独立读模型**（物化视图、投影、专用存储等）。  
- 跨聚合、跨上下文的**强一致读**应为**例外**，须显式设计与成本说明。

### P-5.3 事务边界

- **聚合内**：强一致事务与不变量。  
- **跨聚合 / 跨上下文**：默认**最终一致**；用领域事件、集成事件、重试、幂等、补偿等表达可接受的滞后与失败语义。

### P-5.4 跨上下文一致性：事件发布决策表（v4.6，按容忍度触发）

跨聚合、跨限界上下文的**强一致**应为例外；默认采用 **最终一致**。事件发布**按「能否容忍丢失」**选择模式（v4.6 修订：不再按「进程内 / 跨进程」分档——进程内 Spring Event 与跨进程 MQ 在「事务已提交即不可回滚」这一点上并无区别，按部署位置分档会掩盖真实的失败语义）：

| 场景 | 模式 | 失败语义（必须补齐） |
|------|------|----------------------|
| **可容忍丢失**（通知、缓存失效、指标、审计留痕） | Spring Event（`AFTER_COMMIT` 发布）或直接异步发送 | **best-effort**：事务已提交即无法回滚，订阅者抛异常 → **仅日志留痕 + 指标告警**，不重试、不补偿。**若该事件后续变得不可丢失，必须升级为 Outbox** |
| **不可容忍丢失**（资金、状态同步、跨上下文业务事实）——含进程内订阅 | **必须 Outbox**（与业务数据同本地事务落库 + 至少一次投递 + 消费端按 `eventId` 幂等） | 投递失败 → 中继重试 + 死信；重复投递由消费端按 `eventId` 幂等去重（[ADR-0021](./adr/0021-outbox-and-consumer-idempotency-platformization.md)） |
| **跨进程 / 跨服务发布** | **必须 Outbox**（同上） | **先写库再发 MQ 的无 Outbox 双写在跨进程场景禁止** |

> **v4.6 修正说明**：v4.5 表格第一行曾同时声明「模式 = Spring Event」与「失败语义 = 事件必须已随业务数据落库（Outbox）」，**二者逻辑上不可同时成立**——Spring Event 是内存发布，不存在落库动作。现按「容忍度」重新分档：判定入口是「**丢了这个事件会不会造成业务错误**」，而不是「订阅者是不是在本进程」。

**平台能力**：本地事件 + 消费端幂等为**平台级原语**（Outbox 组件、消费幂等守卫，见 [ADR-0021](./adr/0021-outbox-and-consumer-idempotency-platformization.md)），**禁止每个 Handler 各自实现幂等表**。具体实现位置与幂等 / 重试 / 死信要求见 **[总体架构设计方案](./BONE-总体架构设计方案.md)** 第二十七部分等章节。

领域事件发布最小模式见 [ddd/07-supplements.md §3.3.1](./ddd/07-supplements.md#331-领域事件发布最小模式）。

### P-5.5 回调幂等与跨聚合协作（支付场景样板）

真实支付/渠道回调遵循两条**全局硬约束**（支付聚合状态机、回调命令、退款、超时关闭等完整样板已外置到 [ddd/samples/payment.md](./ddd/samples/payment.md)，`bone-blueprint` 已按样板落地）：

1. **幂等在聚合内**：回调幂等判断在**聚合内**（领域规则），不在 Handler 用 `if` 重复判断；幂等键用渠道流水号 `channelTradeNo`（非业务自增主键）；成功回调返回 `boolean`（true=真正迁移，false=幂等跳过），领域事件在真正迁移时**只发一次**。**生产必须有存储级兜底**（① 乐观锁：`version` + `UPDATE ... WHERE status='PAYING'`（影响行数 0 则已被处理）；② `channel_trade_no` **唯一索引**，重复写入抛约束冲突）抵御并发重复回调。
2. **回调验签（所有回调，含失败回调，必须）**：经**验签端口**在 Handler 进入领域前验签，签名不可信抛 `BizException` 拒绝（[ADR-0022](./adr/0022-external-callback-signature-verification-port.md)）；真实接入要求**密钥外部化**（配置中心/密钥管理）、**防重放**（nonce/时间戳）、按渠道证书。

> 使用范围：本文为权威规范；`Payment` 上下文标注「参考样板」表示仅在 `bone-blueprint` 落地演示，平台模块按需裁剪，无需复制全部演示能力（[E-12](#e-12-极简--轻量--低成本bone-默认心态)）。生产接入支付必须补齐：并发幂等兜底、真实渠道验签（防重放）、真实渠道退款/对账、真实渠道适配。

---

## P-6. 集成与防腐（理想实践）

- 外部模型与错误语义**不穿透**核心领域；经 **ACL** 转为内部通用语言。  
- **开放主机服务 / 发布语言**（版本化 API 或事件 schema）优于「共享数据库集成」。

---

## P-7. 可观测与质量属性

- **可测试性**：核心领域规则宜能在无容器、无 DB 的测试中验证。  
- **可观测性**：关键用例与跨边界调用具备追踪、指标与日志策略。  
- **安全与隐私**：边界与数据分级在架构层可见。

---

## P-8. 演进与组织

- **康威定律**：上下文边界与团队边界、发布单元尽量对齐。  
- **演进式架构**：契约测试、架构适应度函数（如依赖规则测试）防止非计划腐化。  
- **废弃策略**：对外 API 与事件显式版本与弃用周期。

---

## P-9. 原则小结（最小共识）

| 维度 | 应做到 |
|------|--------|
| 战略 | 有界上下文 + 通用语言 + 上下文映射 + 数据所有权 |
| 战术 | 小聚合、充血模型、仓储服务聚合、领域事件表事实 |
| 架构 | 依赖向内、端口–适配器、领域与技术解耦 |
| CQRS | 概念上读写分离；物理拆分与强一致读按需 |
| 一致性 | 单聚合事务内强一致；跨边界默认最终一致（见 P-5.3～P-5.4） |
| 集成 | 防腐与发布语言优于共享写库 |
| 演进 | 团队–边界对齐 + 契约与架构守护 |

---

## P-10. Bone 上下文映射（参考）

> 本节是 [P-2.3](#p-23-上下文映射context-map) 上下文映射原则在 Bone 仓库内的具体实例化，将通用原则落地为可读的关系图与可执行的边界守护建议。**维护人**：架构组；随系统演进更新。
>
> **目标态说明（2026-08 补充）**：本节上下文表与映射图描述的是**目标态**（规划的能力边界与关系），**不保证全部已实现**。当前实现进度以各模块 README 与[附录 B.1](#b1-模块适用性快照) 为准——例如 `Gateway` 目前仅 traceId 透传 filter（非完整路由）、`Notification` 目前为站内信 + Alert 告警通道（站内信 API 已就绪、消息发送记录等扩充中）、`bone-file` 为空壳模块；「目标态 ≠ 现状」不构成对相关能力的承诺。
>
> **v4.5（D2-2）**：Bone 是**厚共享内核的模块化单体**——所有上下文共享同一套基类 / 异常 / 查询构建器 / 元数据注解，本就不是可独立替换持久化的独立 BC；本节映射图描述的是**上下文间集成关系**，不意味着各上下文各自独立演进框架。

### P-10.1 限界上下文与子域类型

| 上下文 | 核心职责 | 子域类型 | 主要聚合根（示例） |
|--------|----------|----------|-------------------|
| **IAM** | 身份、认证、授权；用户 / 角色 / 权限生命周期管理 | 通用域 | `User`、`Role`、`Permission` |
| **MasterData** | 主数据建模、数据质量治理、记录管控 | 核心域 | `MasterEntity`、`MasterRecord` |
| **Integration** | 多协议连接器、流程编排、执行日志 | 核心域 | `Connector`、`Flow`、`FlowExecution` |
| **Extension** | 扩展点注册、插件生命周期管理 | 核心域 | `ExtensionPoint`、`Plugin` |
| **System** | 系统配置、运维日志、监控告警 | 通用域 | `SysConfig`、`SysLog` |
| **Notification** | 通知渠道、消息发送记录 | 支撑域 | `NotificationRecord` |
| **Generator** | 代码生成模板、数据源、生成任务历史 | 支撑域 | `Template`、`GenerationTask` |
| **Payment**（参考样板） | 支付单生命周期：发起、渠道预下单、回调幂等确认 | 支撑域 | `Payment`（`bone-blueprint`） |

> **⚠️ 子域分类待复核（v4.6 标注，非定稿）**：上表将 **MasterData / Integration / Extension 三个同时列为核心域**。按 Evans 的战略设计，核心域是「**差异化竞争力所在**」，资源应集中投入，通常**只有一个或极少**；一列三个通常意味着战略设计尚未真正完成，而是把「重要」等同于「核心」。
>
> 对一个「**元数据驱动**的快速开发平台」而言，真正的核心域更可能是**元数据 / 建模引擎**（平台区别于竞品的根本），而主数据治理、集成编排、扩展机制应属支撑域（重要但可被替代或采购）。
>
> **为何要较真**：子域分类直接决定资源投向与架构投入强度（核心域才值得 R8/R9/L3 全套门禁，支撑域按 L1–L2 即可）。当前给三个核心域全上 L3 档，成本显著而收益存疑。
>
> **处置**：本节分类**保留现状**，标记为待复核项，由架构组在下一个规划周期产出结论（结论写入 ADR，不直接改表）。在此之前，各模块按当前分类执行，但**不得引用本节作为「必须上 L3 全套门禁」的依据**。

### P-10.2 上下文映射图

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  bone-framework  厚共享内核（模块化单体：基类/异常/查询构建器/元数据 全平台共用） │
│  bone-core / bone-web / bone-security / bone-datasource                          │
│  bone-metadata-sdk  元数据注解（D1 工程折中，见 P-4.1 / E-8）                    │
└────────────────────────────────┬─────────────────────────────────────────────────┘
                                 │ 所有应用模块 Maven 依赖
        ┌────────────────────────┼────────────────────────────────┐
        ▼                        ▼                                ▼
┌──────────────┐         ┌──────────────┐               ┌─────────────────────────┐
│   Gateway    │         │     IAM      │               │  bone-extension-sdk     │
│ (基础设施服务)│         │ (port 8081)  │               │  共享内核（SPI）         │
└──────┬───────┘         └──────┬───────┘               └───────────┬─────────────┘
       │                        │                                   │
       │ 路由（无业务语义）       │ OHS / JWT                         │ SPI（@ExtensionPoint）
       │                        │ 下游须 ACL 防腐                    │
       ├──── 路由 ──────────────►│◄── ACL ── 所有应用上下文           ▼
       │                        │                       ┌──────────────────────┐
       ├── 路由 ──► ┌────────────┴────────────┐         │  Extension Studio     │
       │            │      MasterData         │  C-S    │  (扩展引擎应用)        │
       │            │  （主数据，核心域*）      │──MQ/ACL►│                       │
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

### P-10.3 跨上下文集成关系明细

| 上游（U） | 下游（D） | 关系模式 | 集成方式 | 防腐要求 |
|-----------|-----------|----------|----------|----------|
| bone-framework | 所有模块 | **共享内核** | Maven 依赖 | 变更须全平台架构评审；任何模块不可私自修改 |
| bone-metadata-sdk | 所有应用模块 | **遵奉者**（D1 折中） | Maven 依赖 + `@Table`/`@Id` 注解 | 不可混入 JPA/Hibernate 注解；见 P-4.1 / E-8 |
| bone-extension-sdk | Extension Studio + 业务嵌入进程 | **共享内核** | Maven 依赖 | SPI 接口变更须向下兼容 |
| IAM | 所有应用上下文 | **OHS（开放主机服务）** | JWT Token + `/api/v1/iam/` REST | 下游须 ACL 转换；**禁止直接依赖 `com.bone.iam.domain.*`** |
| MasterData | Integration | **客户–供应方（C-S）** | 集成事件（MQ）或 REST ACL | Integration 须 ACL 隔离；不持有 MasterData 聚合对象 |
| Integration | Extension | **客户–供应方（C-S）** | 扩展点 SPI（extension-sdk） | 经 SPI 接口调用；不依赖 Extension domain 包 |
| System | 所有 | **OHS** | REST + SkyWalking 日志采集 | 仅消费技术指标；无业务耦合 |
| IAM | Notification | **客户–供应方（C-S）** | REST ACL | Notification 通过 ACL 获取用户联系方式；不持 IAM domain 模型 |
| MasterData / Extension | Generator | **客户–供应方（C-S）** | REST ACL | Generator 通过 ACL 消费元数据与扩展描述 |
| bone-gateway | 所有应用上下文 | **基础设施服务（豁免 E-5，见 [E-5.4](#e-54-模块适用性按性质而非物理位置)）** | HTTP 反向代理 | 纯路由 / 协议转换，无业务模型；不做跨上下文聚合逻辑 |

### P-10.4 跨上下文边界 ArchUnit 守护（推荐，按需添加）

目前跨上下文边界主要依赖代码评审约束。随各模块集成关系明确，推荐在**下游模块**的 `ArchitectureTest` 中逐步添加越界依赖拦截规则：

```java
// 示例：Integration 模块禁止直接 import IAM 或 MasterData 的 domain 包
// 跨上下文集成须通过公开 API jar 或集成事件契约，而非直接依赖对方 domain 类
// v4.5（D9）：按全限定名正则匹配（匹配字节码中记录的引用，与 classpath 无关）；
//            去掉 allowEmptyShould(true)——空匹配视为配置错误，避免「对方类不在
//            classpath 时规则空命中即通过」等于没有守护。
@ArchTest
static final ArchRule no_direct_iam_domain_dependency =
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com\\.bone\\.iam\\.domain\\..*")
        .because("跨上下文集成须经 IAM 公开 API，禁止直接依赖其 domain 包（P-10.4）；空匹配视为配置错误");
```

> **使用说明**：此类规则**统一按全限定名正则匹配**，在各自下游模块 `ArchitectureTest` 中**按需自行添加**；优先在新建跨上下文集成关系时同步落地，存量按需覆盖。`BoneDddArchRules` 内同类跨上下文规则的 FQN 正则化在门禁重配（[G-1](#g-1-测试与-ciarchunit-规则集) / M2）统一改造。

---

# 第二部分　工程规范（E-）与门禁（G-）

本部分规定 **Bone** 仓库内模块的**可执行约束**与推荐结构，其中 **E-** 为工程规范、**G-** 为门禁。**价值排序**：以**第一部分（P-）**为演进北向星；**交付门禁**以**第二部分（E-/G-）**为准。二者常规不互斥（如纯 POJO 理想与 **D1** 元数据注解的取舍，已在 P-4.1 与 E-8 显式衔接）；若仍存张力，在评审中记录取舍理由。

---

## E-0. 规范稳定性契约

> 本节是 Bone 仓库内 DDD 与分层门禁的**唯一稳定面**。新代码须满足下列条文；**存量违规**通过 `FreezingArchRule` 登记快照，CI 拦截**新增**违规，迁移完成后收缩基线。

### E-0.1 新代码强制 Profile（PR 拦截）

适用于 [E-5.4](#e-54-模块适用性按性质而非物理位置) 认定的**应用 / 控制面 BFF** 模块。模块性质**仅按 E-5.4 三类（应用 / SDK / 基础设施）判定**，不以 `bone-platform/` vs `bone-engine/` 目录推断适用性。

| 维度 | 要求 |
|------|------|
| 分层 | E-5.1 标准树（或 E-5.2 极简树） |
| 应用层 | 基础包 `command` / `query`；可选 `event` / `integration`；例外可加 `orchestration`（E-5.3）；满足 E-5.3.2 F1/F2 可加 `facade`（F3 已删除，见 E-5.3.2）；`application/service` **条件允许**（E-5.3.1 内容禁令，Controller 仍禁直注） |
| 入站 | Controller → `*CommandHandler` / `*QueryHandler`（或模块 README 三级例外的 `*Orchestrator`，或 E-5.3.2 条件下的 `*Facade`）；**禁止** Controller 直接注入 `application/service`、`domain/service`（领域服务）、`domain/repository`（ArchUnit 见 G-1 #11/#12/#17） |
| 命名 | 应用层命令/查询类名 `*Command` / `*Query`（禁 `*Cmd` / `*Qry`）；Handler 类名 `*CommandHandler` / `*QueryHandler`；adapter 入参 DTO 见 [E-13](#e-13-命名约定) 分层表；端口 `domain/repository/*Repository`；异常 `BizException` |
| 禁止 | `application/usecase/**`、`*UseCase`、任何自造 `@UseCase` / `UseCaseExecutor`；**禁止**业务模块依赖已删除的 `com.bone.core.usecase.*` |
| AI/Flow | 能力发现仅用 `com.bone.core.capability.@Capability` + `HandlerRegistry`（E-11） |
| 门禁 | E-3 铁律 + `bone-framework/bone-architecture-test` 共享 ArchUnit 规则（见 G-1） |

### E-0.2 存量不符合规范代码的处理

- **统一原则**：不符合本规范的代码（含但不限于 `*UseCase` 类、`application/usecase/**` 包、`domain/store/*Store`、Controller 直注 `*UseCase`、命令后缀 `*Cmd`、查询后缀 `*Qry`、模块根包下的 `controller/`、自造 `@UseCase` 注解、自建 `BusinessException`）**一律迁移**到符合规范的形态；落地节奏由模块 Maintainer 在内部安排，**不在本文规定**。
- **CI 防回退**：通过 `FreezingArchRule` 登记存量违规快照；**新增**违规直接 CI 失败。基线随每次迁移收缩，**不允许扩张**。
- **例外登记**：规范允许的例外（如 E-5.3 `*Orchestrator`、E-8 D2 框架基类新增）统一按 [E-0.4](#e-04-例外登记的统一形态) 登记。破坏性变更 ADR 模板见 [adr/0000-template.md](./adr/0000-template.md)。
- **命名**：子包 `cmd/`、`qry/` **仅作短目录名**；类名后缀必须为 `*Command` / `*Query`（禁 `*Cmd` / `*Qry` 类名）。
- **命名冲突裁决路径**（2026-08 补充）：当**存量模块**既有命名风格与 E-13 不一致（如 Handler 不带 `*CommandHandler` 后缀、`masterdata`/`integration` 的 `{Action}{Entity}Handler` 风格），按 [E-0.4](#e-04-例外登记的统一形态) 在**模块 README 例外列表**登记该风格差异；该登记**同时覆盖新增同类代码**（新增 Handler 遵循模块既有风格不视为新违规，避免冻结基线「只收缩不扩张」被持续违反）。登记时模块须在 README 给出**命名迁移计划**（目标全量对齐 E-13，如随功能重构逐类更名）；例外被第二个模块复用前，升级为 ADR。

### E-0.3 破坏性变更流程

1. 在 `doc/architecture/adr/` 新增 ADR（动机、影响模块、迁移路径、回滚）。  
2. 同步以下下游文档（任一项涉及则更新）：

| 文档 | 路径 |
|------|------|
| 项目 Agent 指南 | `AGENTS.md` |
| 模块详设（按影响面） | `doc/design/modules/*.md` |
| 蓝图 / 生成器 | `bone-blueprint`、`studio-generator` 模板与 README |
| API 规范（若影响异常/响应） | `doc/architecture/Bone-API-规范.md` |

3. **先合并** ArchUnit / CI 规则与文档，**再启动**业务代码迁移；避免「代码已改、规范又变」。

### E-0.4 例外登记的统一形态

> 全文各处提到的「例外」统一按**影响半径**登记，避免读者每次回忆走哪个流程。

| 影响半径 | 登记形式 | 典型场景 |
|----------|----------|----------|
| **跨模块 / 平台级** | `doc/architecture/adr/` 新增 ADR | E-5.3 `*Orchestrator`；E-0.3 破坏性变更；E-8 D2 框架基类新增例外 |
| **单模块内** | 模块 `README.md` 例外列表 | E-9.1 自增主键例外；E-5.4 模块性质边界用例 |
| **类内决策 / 工程折中** | 类级 / 方法级 Javadoc 标注理由 | E-5.3.1 应用服务类别（S1/S2/S3）；E-3.1 R6「读己之写」CommandHandler 特例；E-3.1.2 R9 同构批量写特例 |

- **同一规范的例外升级**：单模块例外若被第二个模块复用，须升级为 ADR。
- **例外必须带到期日（v4.6 收紧）**：每条例外**必须**注明「拆除条件」**+「到期日」**。到期未拆除 → CI **失败**（**不是 warn**）。确需延期的，每个条目最多续期一次，且须记录续期原因与上次续期日。
  > **为何从「warn」改为「失败」**：v4.5 规定「到期目标到达仍未拆除的 freeze 条目由 CI 提示（warn 或按模块排期强制执行）」——warn 不阻断任何构建，等于没有到期日。技术债必须付利息，否则永远不会被偿还。
- **例外预算（v4.6 新增）**：单模块 README 例外条目**上限 5 条**；超出须先拆除既有条目或升级为 ADR。理由：例外通道的价值与其数量成反比——当每条硬规则都能找到后门时，规范就退化为参考意见，评审时「总能找到一条例外」。
- **统一台账**：模块 `README.md` 例外列表为例外唯一登记处，禁止散落在 Confluence / 提交信息 / 口头约定。架构组复核时优先清理临期条目。
- **ADR 不为「规避规范」开口子**：若例外仅为绕过条款而无业务理由，应拒绝。

---

## E-1. 目标（Bone）

| 层级 | 目标 |
|------|------|
| **战略** | 限界上下文清晰、通用语言一致、跨边界集成可治理 |
| **战术** | 依赖可证明（ArchUnit）、读写分离、聚合不变量集中、反贫血 |
| **工程** | 与 **bone-core**、**bone-metadata-sdk**、多租户、扩展点一致 |

---

## E-2. 与第一部分对齐（Bone 落点摘要）

| 原则维度 | Bone 落点 |
|----------|-----------|
| 边界与数据所有权 | 一上下文对应一个 Maven 模块（`com.bone.{module}`）；跨边界经 ACL / 契约 API / 事件 |
| 通用语言 | 术语表 + 命令/事件/REST 命名一致 |
| 防腐 / 出站 | `domain/gateway/*Gateway` 定义跨边界出站 ACL（E-10）；模块内读侧用 `domain/gateway/*ReadPort`（E-9.5，**非防腐**）；`infrastructure/gateway/` 或 `infrastructure/persistence/` 实现；禁止应用层直连 Feign **实现类型** |
| 一致性 | 一事务一改一个聚合根；跨聚合默认最终一致 |
| 读写分离 | 写走聚合 + 仓储；读走 `Criteria` / `QueryBuilder` + 投影 DTO |

---

## E-3. 铁律（R1–R9 必守）

### E-3.1 R1–R9 — 全局九条（CI / ArchUnit 硬门禁）

> **适用范围**：本节 R1–R9 **仅强制适用于「应用 / 控制面 BFF」模块**（详见 [E-5.4](#e-54-模块适用性按性质而非物理位置)）。SDK / 框架库豁免 R4/5/6/7/9（R8 视 SDK 是否含 domain 聚合而定）。

1. **R1 依赖方向**：`adapter → application → domain ← infrastructure`，禁止反向依赖；**application 层禁止** import **infrastructure** 具体实现类（须经 **domain 端口** 或 **adapter 已解析的 Bean** 注入接口实现，与总体架构方案中依赖倒置一致）。  
2. **R2 领域规则**：状态流转、不变量在 **聚合根或领域服务**；应用层只做编排与事务边界——**禁止在 Handler 内实现仅属于某一聚合的业务不变量**（可做的仅为用例级前置校验，如参数组合、ACL 权限是否已判定通过等）。**反贫血**：聚合/实体的状态变更**必须经领域行为方法**（如 `account.enable()`），禁止通过 getter+setter 序列在外部修改聚合内部状态（见 [E-8](#e-8-领域模型与纯净度d0--d1--d2) 反贫血红线）。  
3. **R3 外部系统**：仅经 **端口 + infrastructure 实现**；`domain` / `application` 不依赖 Feign、HttpClient、MQ Producer **实现类型**。  
4. **R4 写侧仓储**：`domain.repository` 只做聚合 **加载与持久化**（如 `save`、`remove`、`findById`）；返回类型只能是**聚合根 / `Optional<聚合根>` / `boolean` / `void`**；**禁止**在仓储接口上增加组合条件列表/分页等查询方法、禁止返回投影/分页对象（[E-9.2](#e-92-写侧-repository)）。  
5. **R5 读侧查询**：凡 **WHERE 含多个业务条件**、**分页/排序**、**Join/子查询/聚合报表** 的读路径，统一用 **`Criteria` / `QueryBuilder`**，结果映射到 `application.query.dto`（或 `projection`）；**禁止在 `domain` 包内**使用查询构建器。仅按 **主键或单一业务键** 加载聚合（如 `findById`、E-9.2 白名单方法）仍走仓储，不视为本条「读侧复杂查询」。  
6. **R6 CQRS**：CommandHandler 内 **禁止** 使用 `QueryBuilder`（特例「读己之写」须注释 + 评审）。**QueryHandler** 建议标注 **`@Transactional(readOnly = true)`**（或框架等价只读事务），且 **禁止** 调用写侧仓储修改聚合。
7. **R7 应用层单层**（禁止无意义薄门面）：**禁止** `application/usecase/**` 包与 `*UseCase` 类；Controller **直接注入** `*CommandHandler` / `*QueryHandler` / `*Orchestrator`（编排例外见 [E-5.3](#e-53-应用层结构强制)）或 `*Facade`（有条件的入站门面，见 [E-5.3.2](#e-532-applicationfacade-约束条件追加非默认)，不同于被禁止的无意义 UseCase 薄门面）；**禁止**直接注入 `application/service`、`domain/service`（领域服务）、`domain/repository`（写侧仓储）。**禁止**任何模块 import 已删除的 `com.bone.core.usecase.*` 或自造 `@UseCase` / `UseCaseExecutor`。
8. **R8 聚合纯单测（v4.5 新增，v4.6 判据强化，Bone 门禁）**：每个聚合根（继承 `AggregateRoot` / `TenantAggregateRoot`）必须有一个**无容器纯单测**（纯 JUnit，不启动 Spring、不连 DB、不接 MQ），覆盖**主状态机**与 **≥1 条拒绝路径**（不变量失败路径）。机器判定为 `AggregatePureUnitTestGuard.verify("<模块根包>")`（存量缺口登记用 `verifyAllowingPending(<待补简名集合>, ...)`，见 [附录 B.3](#b3-archunit-模板bone-architecture-test) 说明），**五条全满足**才算通过：① 同名 `*Test` 类存在；② 该类**至少 1 个 `@Test` 方法**（堵住空类绕过）；③ 该类**无容器注解**（`@SpringBootTest` / `@DataJpaTest` / `@ExtendWith(SpringExtension.class)` 等）；④ 该类**调用了聚合的行为方法**（getter 以外的方法调用，仅 `getXxx()` 无法证明任何不变量生效）；⑤ 该类**含断言或异常期望**（堵住 `assertEquals(1, 1)` 式空测试）。统计范围仅 `..domain..` 下的**具体**聚合根，`..domain.outbox..` 等基础设施技术对象不计入。因 ArchUnit 的 `@AnalyzeClasses` 默认排除测试类，本判据由模块内**独立测试类**承载（非 `ArchRule`）；内容质量由 CR 与测试模板保证。这是反贫血（R2）的**主判据**，取代「ArchUnit 命名全绿即达标」。  
9. **R9 一事务一聚合（v4.6 新增）**：一个写事务（`@Transactional` CommandHandler 的入口方法）内**只允许持久化一个聚合根**——即对不同 `*Repository.save(...)` 的调用目标类型不得超过 **1** 个。跨聚合的一致性变更一律走**领域事件 / Outbox / Orchestrator** 的最终一致（[P-5.3](#p-53-事务边界)）。机器判定为 `BoneDddArchRules.oneAggregatePerTransaction()`（[G-1 #21](#g-1-测试与-ciarchunit-规则集)）。

> **门禁映射补充（2026-08-30 复核，反贫血相关）**：本条「领域规则在 domain / 反贫血」按「**弱约束代码形式、强约束行为**」分层落地——
> - **R8 聚合纯单测**为反贫血**主判据**：每个聚合根须有可在无容器下运行的纯单测（主状态机 + ≥1 条拒绝路径）。实现为 `AggregatePureUnitTestGuard.verify(...)`，校验「存在性 + ≥1 个 `@Test` + 无容器 + 调用行为方法 + 含断言」**五条**；v4.6 追加后两条（④⑤），堵住「`new` 出来断言字段不变」的形有神无假单测。内容质量由 CR 与测试模板保证。参考样板 `bone-blueprint` 已接入（`AggregatePureUnitTestCoverageTest`，严格模式 137 测试全绿）。
> - **`outerLayersMustNotMutateAggregateIdentity()` 为 A 类强制规则**（禁止外层篡改 `setId` / `setTenantId`），守护租户隔离与身份完整性，**非**可选风格约束，**不得降级**。判据口径（v4.6 实现修正）：豁免**持久化适配器角色**（`*Repository` / `*Converter` / `*Mapper` / `*Assembler` 等——主键回填、ORM 恢复、PO 映射是其法定职责，E-9.1）与**基础设施 PO 目标**（`..infrastructure..` 包下类型不是聚合、不承载一致性边界）；真正的越权风险（`application` / `adapter` 业务代码改聚合租户归属或主键）仍被完整拦截。
> - 命名 / 事务注解类规则（如 Handler 后缀、`@Transactional` 标注）为**风格约束**，不作硬门禁。

#### E-3.1.1 SDK / 框架库豁免（R4/5/6/7）

适用于 [E-5.4](#e-54-模块适用性按性质而非物理位置) 中的**引擎 SDK / 框架库**（无 `@RestController` 业务 API）：

| 铁律 | 应用模块 | SDK / 框架库 |
|-------|----------|----------------|
| R1 依赖方向 | 强制 | 强制（领域库不向 adapter/infrastructure 业务模块反向依赖） |
| R2 领域规则在 domain | 强制 | 强制（若有 `domain` 包） |
| R3 外部经端口 | 强制 | 强制（若有出站集成） |
| R4 写侧仓储方法收敛 | **强制** | **豁免**（SDK 自身 `Repository` SPI 除外） |
| R5 读侧 QueryBuilder 位置 | **强制** | **豁免** |
| R6 Command 禁 QueryBuilder | **强制** | **豁免**（无 CommandHandler） |
| R7 禁止 UseCase | **强制** | **豁免**（非应用 / 控制面 BFF，无 Controller 业务 API）；`com.bone.core.usecase.*` **已从 bone-core 删除**，业务模块**不得**引用 |
| R9 一事务一聚合 | **强制** | **豁免**（无写事务概念） |
| R8 聚合纯单测 | 强制（有聚合根即适用） | **豁免**（若 SDK 无 domain 聚合则不适用） |
| E-5.1 包结构 | **强制** | **豁免**（按 SPI/库习惯组织） |
| E-5.3 应用层 | **强制** | **豁免** |
| E-8 D0/D1/D2 | 建议 | **强制**（元数据注解白名单） |

新增 SDK 能力若需豁免上述条目，须在 `bone-framework` 或对应引擎模块 **README + ADR** 登记。

#### E-3.1.2 R9 一事务一聚合（v4.6）

> **为什么单列一条**：聚合在 DDD 中的定义就是**一致性边界**（Vernon）。若一个事务里同时写多个聚合，等于宣称「这两个聚合的不变量必须同时成立」——那么它们本就该是**同一个聚合**；若不是，则事务边界与一致性边界背离，会放大锁竞争、制造分布式事务假象（本地事务只能保证本库原子，跨聚合的最终一致被伪装成强一致）。R9 是 R2/R6 的必然推论，也是一个**真正能被机器判定**的语义约束。

**判定口径**（`BoneDddArchRules.oneAggregatePerTransaction()`）：

1. 扫描 `..application.command.handler..` / `..application.service..` / `..application.orchestration..` 下所有**具体类**的方法（ApplicationService 与 Orchestrator 同为写事务入口，**同样受 R9 约束**——Handler 委托给 ApplicationService 后，仅扫 Handler 将完全不可见，v4.7 修正此盲区）；
2. 收集方法调用图中对 `..domain.repository..` 接口 `save*` / `remove*` 方法的调用；
3. 按**调用目标的 Repository 接口类型**去重计数；
4. 计数 **> 1** 即违规。

> **为何按 Repository 类型去重、而非按调用次数**：`save(order)` 调两次仍是同一个聚合（幂等 upsert），不构成跨聚合写；而 `save(order)` + `save(payment)` 才是真正的边界越界。按类型去重既准确又不误伤循环。

**允许的特例**（须按 [E-0.4](#e-04-例外登记的统一形态) 在方法级 Javadoc 标注理由）：

| 特例 | 条件 | 要求 |
|------|------|------|
| 同构批量写 | 同一聚合类型的集合批量保存（`saveAll(orders)`） | 视为单聚合，不违规 |
| 技术对象写（一致性关键，v4.7 修正） | Outbox 记录、幂等表等与业务状态**必须原子**的技术写入 | **必须与业务聚合同一本地事务**（[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 核心保证）；不计入聚合计数——它们不是聚合根，且实现位于 `infrastructure`（不在 `..domain.repository..` 包内），门禁天然豁免。**禁止** `REQUIRE_NEW` / AFTER_COMMIT 另起事务（commit 后崩溃即丢事件，Outbox 模式失效） |
| 技术对象写（纯留痕，v4.7 区分） | 审计流水、指标、通知等**可容忍丢失**的写入 | 允许 AFTER_COMMIT 或独立事务（`REQUIRES_NEW`），不强制与业务聚合共享事务（[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 首行容忍度判据） |

**违规时的正确改法**（按优先级）：

1. **合并聚合**：若两个聚合的不变量确实必须同时成立 → 说明聚合边界划错，合并为一个聚合根（用 [P-3.1 聚合边界 4 问](#p-31-聚合aggregate) 复核）。
2. **改为最终一致**：主聚合并发领域事件 → Outbox → 订阅方更新另一聚合（[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发)）。
3. **显式编排**：确需跨聚合流程时改用 `*Orchestrator`（[E-5.3](#e-53-应用层结构强制)），每步一个独立事务，并声明补偿策略。

**接入建议**：存量模块首次接入用 `FreezingArchRule.freeze(...)` 登记快照，按 [附录 B.3.1](#b31-freeze-台账模板违规数单调不增) 台账逐批清零；`bone-blueprint` 参考样板**不 freeze**（须 0 违规）。规则登记号 [G-1 #21](#g-1-测试与-ciarchunit-规则集)。

---

## E-4. 战略 DDD（Bone 最小必做）

### E-4.1 限界上下文

每个上下文具备：**名称**、**职责一句**、**对外契约**、**数据所有权**。

- **单体**：一上下文对应一个 Maven 模块（`com.bone.{module}`）；禁止跨上下文直接引用对方聚合 **类型**。  
- **多部署**：一上下文一服务边界；不默认共享写库。

#### E-4.1.1 数据所有权的可执行判据（v4.6 新增）

> **为何补**：限界上下文若只有「文档上的职责划分」而无**数据层面的所有权约束**，则上下文边界是可被随时穿透的——两个上下文可以任意 Join 对方的表，边界随即名存实亡。当前 `noCrossContextDomainDependency` 只拦截 **`domain` 包类型依赖**，管不到读侧跨表查询，这是守护上的实际缺口。

**两条硬约束**：

1. **表归属登记**：每个上下文在模块 `README.md` 维护「本上下文拥有的表」清单（表名 → 用途）。新表创建时同步登记；**一张表只能有一个归属上下文**。跨上下文需要该数据时，走**公开 API** 或**事件投影**，不直接读表。
2. **跨上下文读禁令**：禁止 `QueryBuilder.from(其他上下文的实体/PO)`、禁止跨上下文 `JOIN`、禁止跨上下文写。机器判定见 `noCrossContextModelDependency()`（[G-1 #20](#g-1-测试与-ciarchunit-规则集)）。

**豁免与演进**：模块化单体阶段允许**共库**；共库不等于共享所有权——共库期间的隔离靠上述两条约束 + 代码评审维持，出现 [E-4.6](#e-46-模块化单体--服务拆分信号2026-08-补充) 拆分信号时按表归属清单切库。

### E-4.2 通用语言

- **小模块**：至少在模块 README 中固定**核心术语中英对照与禁用同义词**（一页纸级术语表）。  
- **大模块 / 跨模块**：维护 `doc/glossary.md` 或 Wiki，命令、事件、REST 与表字段、消息字段对齐。

### E-4.3 上下文映射（轻量）

与邻域标注 **合作 / 客户-供应 / 防腐 / 发布语言** 之一；跨边界不经隐式共享写路径。

### E-4.4 多租户在 DDD 中的约定

| 约定 | 说明 |
|------|------|
| **注入与消费分离**（v4.6 修订） | **注入**：`TenantContext` 由**框架层**（过滤器 / 拦截器 / JWT 解析）统一写入，业务代码**只读不写**。**消费**：Handler / 读侧取值**必须走模块统一取值入口**（端口，如 `domain/gateway/TenantProvider.currentTenantId()`），**禁止**在业务代码中散落 `TenantContext.get*()` 直调 |
| **为何改为「单一入口」而非「全链路显式传参」** | v4.5 要求「禁止从线程上下文猜取、必须显式参数化传递」，但线程本地变量恰恰是唯一能在**所有调用深度**无侵入传递租户的机制；全链路显式传参在实践中会退化为两种坏结果：① 大量 Handler 仍直调 `TenantContext`（现状：`bone-iam` 16 处），规范空转；② 为传参而给每个方法加 `tenantId`，污染签名且仍无法覆盖异步分支。**真正要防的不是「读线程上下文」，而是「读得分散、无法审查、异步丢失」**——收敛到单一端口入口后，可在该实现内统一做空值校验、异步传递（`TransmittableThreadLocal` / 装饰器）、越权审计与平台租户打洞登记 |
| **异步与线程切换** | `@Async`、线程池、MQ 消费、Outbox 中继、定时任务**必须显式传递**租户上下文（装饰器或任务包装器）；进入这些分支时若上下文缺失，**必须失败或显式降级为平台租户并留审计**，禁止静默按 `null` 放行（会导致全租户数据泄漏） |
| **跨边界显式携带** | 事件载荷、Command、Feign/RPC 调用**必须显式携带** `tenantId`（不依赖对端线程上下文）；这是对「显式参数化」要求的正确适用位置 |
| 写侧 | 多租户实体优先 `TenantAbstractEntity`；聚合根 + 租户 + 事件用 `TenantAggregateRoot`（ADR-0011） |
| 仓储 | `findById` 等须在实现层结合租户上下文过滤；禁止跨租户无审计的批量写 |
| 读侧 | `QueryBuilder` 条件**必须**含 `tenantId`（或等价隔离键），见 [Bone-API-规范](./Bone-API-规范.md) |
| 事件 | 领域/集成事件载荷**应**携带 `tenantId` |
| 身份完整性 | 聚合根 `setId` / `setTenantId` 禁止被外层篡改（`outerLayersMustNotMutateAggregateIdentity()`，A 类强制规则）——租户隔离与身份完整由门禁守护，不依赖领域显式携带 |
| 打洞 | 平台超管跨租户查询须在模块 README 登记，Handler 内显式校验角色 |

### E-4.5 事件建模与事件契约演进（2026-08 补充）

**事件风暴入门（新模块/新流程起步用，可选但推荐）**：

1. 邀请业务 + 研发，按时间轴贴出**业务事件**（过去式，如「订单已支付」）→ 聚合出**命令/触发者**与**聚合** → 标注**读模型**（报表/列表）。
2. 产出物：事件清单（含载荷字段）、聚合清单、命令清单——直接作为模块 README 通用语言表的输入。
3. Bone 最小产出：**事件清单 + 每事件字段表**（含 `tenantId`），事件名对齐 E-13.2 命名（领域事件 `*Event` 过去式、集成事件 `*IntegrationEvent` 后缀）。

**事件契约版本化（跨模块/跨服务集成事件的强制要求）**：

- 事件载荷**只增不删**：新增字段向后兼容；删除/改名/改类型属于**破坏性变更**，须走 [E-0.3](#e-03-破坏性变更流程)。
- 消费者**容忍未知字段**（反序列化忽略多余字段），生产者**不依赖**消费者回传字段。
- 事件 schema 建议沉淀为**版本化契约**（如 `integration-events-v1` 文档或 schema 文件）；升级期保留旧事件名或加版本后缀（`order-paid-v2`），迁移窗口由双方协调。
- 领域事件（上下文内）可不版本化（随代码演进）；**集成事件**（跨边界）必须遵守本节。

### E-4.6 模块化单体 → 服务拆分信号（2026-08 补充）

Bone 默认**模块化单体**（一上下文一 Maven 模块）。**何时把模块拆为独立服务**（不默认微服务化），出现以下信号**任一**时评估：

| 信号 | 说明 |
|------|------|
| 发布耦合 | 该模块独立发版频率显著高于其他模块，或频繁被其他模块变更拖累发布 |
| 团队边界 | 有独立团队长期负责该上下文（康威定律），单体仓库协作成本上升 |
| 资源/伸缩 | 该模块负载特征与其他模块显著不同（如异步消费 vs 高频 API），需独立伸缩 |
| 故障隔离 | 该模块故障不应拖垮整体（如第三方集成超时风暴） |

拆分**不是**把包结构搬进新服务，而是按 [P-10](#p-10-bone-上下文映射参考) 的边界重做集成契约（API/事件/ACL），拆分后原模块内跨上下文引用改为经契约交互。

---

## E-5. 标准包结构（推荐）

### E-5.1 完整参考树

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
│   ├── orchestration/             # 例外：跨 Handler 编排（E-5.3，模块 README 例外）
│   └── facade/                    # 条件追加：多入口 / SDK 门面（E-5.3.2）
│   # 禁止 application/service 通用桶——共享逻辑按性质分流（领域规则→domain/service；技术横切→domain 端口+infrastructure 实现，E-5.3.1）
├── domain/
│   ├── {aggregate}/
│   ├── repository/
│   ├── service/
│   └── gateway/
└── infrastructure/
    ├── persistence/
    │   ├── entity/                # *PO（D2，E-8.2）
    │   ├── converter/             # PO ↔ domain 互转
    │   └── repository/            # *RepositoryImpl
    ├── gateway/
    ├── config/
    └── query/
```

### E-5.2 极简树（小模块默认够用）

仅 **Web + 一聚合** 时，可只保留以下包：

- `adapter/web/`
- `application/command/`、`application/query/`
- `domain/{aggregate}/`、`domain/repository/`
- `infrastructure/persistence/`（如需 PO 映射或仓储实现）

无外部系统则暂不建 `gateway`。读极简单时仍建议保留 `query` 包，避免把列表查询塞回 `CommandHandler`。

**分组原则**：按 **业务/聚合** 分包，不按「全 entities / 全 vo」横切。

### E-5.3 应用层结构（强制）

**允许的应用层包**（按角色划分）：

| 包 | 角色 | 必选 / 可选 |
|----|------|-------------|
| `application/command/{cmd,handler}/` | 写用例执行器（`*CommandHandler`） | **必选**（凡有写操作） |
| `application/query/{qry,handler,dto}/` | 读用例执行器（`*QueryHandler`） | **必选**（凡有读操作） |
| `application/event/` | 应用事件（领域事件订阅/转发） | 可选 |
| `application/integration/` | 入站消息编排（如 MQ 消费、Kafka Source） | 可选 |
| `application/orchestration/` | 跨多 Handler 编排（`*Orchestrator`，可重试编排） | **模块 README 例外** |
| `application/facade/` | 多入口 / Client SDK 入站门面（`*Facade`） | **E-5.3.2 条件追加** |
| `application/service/` | 用例级编排组合（`{语义}ApplicationService`） | **E-5.3.1 条件允许**（受内容禁令约束，**非**通用共享桶；Controller 仍禁直注） |

**禁止**（PR/ArchUnit 拦截）：

| 禁项 | 替代方案 |
|------|----------|
| `application/usecase/**`（任何子目录） | 删除；Controller 直接注入对应 `*CommandHandler` / `*QueryHandler` |
| 类名 `*UseCase` | 改名为 `*CommandHandler` / `*QueryHandler`；纯 delegate 直接合并到现有 Handler，不留空壳 |
| Controller 注入 `application/service/*Service` | Controller 仅注入 Handler / Orchestrator / Facade（E-5.3.2）；事务边界仍在 Handler |
| Controller 注入 `domain/service/*`（领域服务）或 `domain/repository/*`（写侧仓储） | **禁止**；经 Handler 编排后间接调用，不直接穿透到 domain 层（ArchUnit #12、#17） |
| **业务模块** import `com.bone.core.usecase.*` | **禁止**（包已从 bone-core 删除） |
| 自造 `@UseCase` 注解 / `UseCaseExecutor` 接口 | **禁止**；按本规范删除并改 Controller 直注 Handler |
| 无触发条件（F1/F2 均不满足）的 Facade 门面 | 反模式；删除 Facade，Controller 改直注 Handler（见 E-5.3.2）|

**决策依据**：

1. **职责重复**：在 E-6 应用层职责（编排 + 事务边界）下，`*UseCase → *Handler` 多为 1:1 delegate，无新增语义。
2. **认知成本**：Controller → UseCase → Handler → Domain 比 Controller → Handler → Domain 多一层，对人/AI/生成器都是负担。
3. **形态收敛**：E-5.1 标准树原本仅 `command/query`，UseCase 是事实上的第二套形态，本规范明确收敛为一套。
4. **AI / Flow 能力发现** 用 `@Capability` 即可（E-11），不应复用「UseCase」名义混淆 DDD 用例与 AI 能力两层语义。

**例外**（Orchestrator 为**模块 README 例外**，复用前升级 ADR；见 [E-0.4](#e-04-例外登记的统一形态)）：

- 当「跨 2+ 聚合 Handler 编排 + 无法在单一 Handler 表达事务/补偿」时，可使用 **`application/orchestration/*Orchestrator`**（**不叫 UseCase**），模块 README 写明编排步骤、补偿策略、是否引入 Saga。
- **可重试编排**：Orchestrator 允许承载「逐步推进 + 补偿」的跨聚合流程（如确认订单 + 扣库存），失败可重试 / 补偿；重试语义必须显式声明。
- **反例**（不构成例外，应改写为单 Handler 或 Orchestrator 内联）：
  - 「Handler A 同步调用 Handler B」且 B 仅复用查询 → 查询本属读侧，下沉到 `application/query` 复用读端口；不引入 `*Service`。
  - 「多个 Handler 顺序调用、无补偿」→ 合并到一个 Handler；不必引入 Orchestrator。

> 能力声明走 `@Capability`，业务执行走 Handler（必要时 Orchestrator）；详见 [E-11](#e-11-flow--ai-编排可选)。

#### E-5.3.1 应用层「共享逻辑」按性质分流 + `{语义}ApplicationService` 内容禁令（v4.6 修订）

> **v4.6 立场修正（本次改口）**：v4.5 全面禁止 `application/service`，论证是「可复用逻辑要么是领域规则、要么是技术横切」。该**二分法漏掉了第三类**——**用例级编排组合**：跨多个聚合、含外部 ACL 调用、含事务边界，却不属于任何单一聚合的领域规则（例如「下单」= 校验库存 ACL + 创建订单 + 发事件 + 预留库存）。这正是 Evans 第 4 章 Application Service 的经典职责，是**一等架构构件**而非反模式。
>
> **禁令的实际后果印证了这一点**：真实需求并未消失，而是被逼进 `*Orchestrator` / `*Facade` / `{语义}ApplicationService` **三套例外通道**。规范于是呈现「禁止一个合法构件、同时开三个后门」的自我稀释——净复杂度上升，而约束力下降。（实证：`bone-iam` 现存 10 个 `application/service/*`，Handler 直接注入，规范宣称禁止而代码视为常态。）
>
> **v4.6 结论**：`{语义}ApplicationService` 作为**用例级编排载体**是**合法构件**。约束方式从「**命名禁令**」改为「**内容禁令**」——管住「里面不许有什么」，而不是「不许叫这个名字」。

**三层分流（先分流，再决定是否需要 ApplicationService）**：任何被多个 Handler 复用的逻辑，**不按「共享」这一事实**决定位置，而**按逻辑性质分流**：

| 逻辑性质 | 判断 | 归处 |
|----------|------|------|
| **领域规则 / 不变量**（跨聚合或跨实体的纯领域行为） | 是否表达业务规则？ | `domain/service/*DomainService`（领域服务，Handler 编排调用） |
| **技术横切**（租户上下文、保存+发布事件、调用外部网关） | 依赖具体技术？ | `domain/gateway/*Port`（端口接口）+ `infrastructure/**/*Impl`（实现），Handler 经端口注入 |
| **用例级编排组合**（跨多聚合 + 事务边界，非领域规则） | 是否可并入单个 Handler？ | 先试 Handler 内联；确实跨多用例 → `{语义}ApplicationService`（本条）或 `application/orchestration/*Orchestrator`（模块 README 例外） |

**`{语义}ApplicationService` 的内容禁令**（五条：2/3/5 ArchUnit 可判，1/4 由 CR）：

1. **不得承载领域业务规则**：聚合不变量、状态流转、业务决策**必须**在 `domain`；ApplicationService 只做加载 → 调领域行为 → 保存 → 发事件的编排。
2. **不得 `new` 领域对象**（ArchUnit `applicationServicesMustNotOwnDomainRules`）：领域对象只能经聚合根的**工厂方法**或 `Repository` 获取，杜绝「应用层组装领域对象」。
3. **不得调用聚合 setter 修改状态**（同上，ArchUnit 可判）：状态变更必须经领域行为方法（反贫血 R2）。
4. **不得持有 `@Transactional` 之外的基础设施细节**：不直接依赖 `infrastructure` 实现类（R1），不拼 SQL / QueryBuilder（R5/R6）。

**命名与放置**：

- 命名**必须语义化**：`OrderShippingService`、`AccountRoleBindingService`；**命名黑名单**：`ApplicationService`、`OrderService`、`CommonService`、`BaseService`、`Business*`、`*Manager`——无语义通用桶一律禁止（ArchUnit #11 已拦截 `Common*`/`Base*`/`Business*`/`*Manager`）。
- 放置：`application/service/`；**每个类只服务一个业务流程/一组强内聚用例**，不得建覆盖全模块所有聚合的单一 Service。
- **Controller 仍禁止直接注入**（ArchUnit #11）：入站统一为 Handler / Orchestrator / Facade；ApplicationService 由 **Handler 调用**，不是入站门面。这条保留了 v4.5 的合理部分——禁止的是「Controller 越过应用层事务边界直连 Service」，不是「Service 本身」。

**存量示例迁移**（`bone-blueprint`）：
- 租户隔离 → **下沉仓储查询层**：`OrderRepository.findByIdInTenant(id, tenantId)`（default 方法，用 SDK `Criteria` 过滤，SQL 层即过滤跨租户订单）；Handler 传入 `TenantProvider.currentTenantId()`。
- 技术横切租户上下文 → `domain/gateway/TenantProvider`（端口）+ `infrastructure/context/TenantProviderAdapter` 实现（命名约定：端口-适配器实现类统一 `Adapter`/`Impl` 后缀，保证「接口→实现」可一键跳转），Handler 经端口注入。
- **聚合持久化 + 领域事件发布（标准写法）**：`repository.save(aggregate); domainEventPublisher.publishFrom(aggregate);`
  - `publishFrom` 是 `DomainEventPublisher` 的 **default 方法**（内部 `publishAll` + `clearDomainEvents`），为业界 Spring Data `@DomainEvents` + `@AfterDomainEventPublication` 的显式等价物。
  - **不设 `AggregatePersister` 端口**：原设计需把 `Repository` 当参数传入端口（Service Locator 反模式），且 `save/update` 两个方法实现完全相同（`Repository.save` 本身即 upsert），制造「该用哪个」的伪决策。**显式两行 > 隐藏的 AOP 魔法**：样板工程要让团队一眼看到事件何时发出。
- **应用层与领域层都不承载"加载后校验租户"**——多租户隔离属于横切关注点，理想由基础设施（如 SQL 拦截器）统一处理；在 SDK 未内置时，用仓储 default 方法 + Criteria 实现查询层过滤。

> **硬约束**：**禁止** `Controller` 直接注入 `domain/service`（领域服务）或 `application/service`，入站统一为 Handler / Orchestrator / Facade（ArchUnit #11/#12/#17）。`application/service` 若存在于存量模块，**不要求整体删除**——按 E-5.3.1 内容禁令逐项整改：承载了领域规则的抽到 `domain/service`，属技术横切的抽到端口 + infrastructure，剩下的纯编排部分保留并**语义化命名**。

#### E-5.3.2 `application/facade` 约束（条件追加，非默认）

`application/facade/` 为**可选入站门面层**，仅在满足以下任一触发条件时追加；默认**不建**。

**触发条件（满足任一即可追加）**：

| 编号 | 条件 | 典型场景 |
|------|------|---------|
| **F1** | 同一组用例被 **≥ 2 个入站适配器**复用（HTTP + Dubbo/Feign + MQ + Scheduler） | RPC Provider 与 Controller 需调用同一批 CommandHandler |
| **F2** | 模块对外发布**稳定 Client SDK jar**，需要独立的 `*ServiceI` 接口定义 | `client/api/XxxServiceI` 由 Facade 实现，消费方依赖接口 jar |

> **v4.5（D4-2）**：原 F3「Controller 注入 Handler 数量 > 7」已**删除**——数量是风格问题，不应构成门面触发条件；Controller 构造函数注入多个 Handler 属正常形态（可用构造分组 / 包聚合优化可读性，但不得为此引入 Facade）。
>
> **命名澄清（v4.7）**：F2 的 `*ServiceI` 是 **Client SDK 对外契约接口**（位于 SDK jar，如 `client/api/XxxServiceI`），由 `application/facade/*Facade` 实现；`*ServiceI` 不在 `application/facade` 包内，与 E-13.2「Facade 类禁止叫 `*Service`」不冲突——前者是 SDK 侧对外接口命名，后者是应用层实现类命名。

不满足以上任一条件时，**不应**建 Facade；Controller 直接注入 Handler。

**铁律**：

1. **Facade 不写领域业务规则**（CR）：聚合不变量、状态流转、业务决策**必须**在 domain；Facade 只做应用级协调（参数组装、权限前置、路由到 Handler）。
2. **Facade 不持有写事务**（CR）：`@Transactional` 的写边界**仍在 `*CommandHandler`**；Facade 若标 `@Transactional` 须在类级 Javadoc 注明原因（`@Transactional(readOnly = true)` 读汇聚除外）。
3. **不取代 Handler**（CR）：Facade 内部**必须**路由到对应 `*CommandHandler` / `*QueryHandler`（或 `*Orchestrator`）；禁止在 Facade 内直接操作 Repository / Domain / Infrastructure。
4. **触发条件不满足则删除**（CR）：若 Facade 每个方法仅一行 `handler.handle(cmd)` 且 F1/F2 均不满足 → 删除 Facade，Controller 改直注 Handler。

**建议**：

- 类命名 `*Facade`；按**业务域/模块**聚合（如 `OrderFacade` 覆盖订单域全部入站用例），不必为每个用例建独立 Facade，也不应建一个覆盖全模块所有聚合的单一 Facade。
- Facade 允许承载**应用级**横切逻辑（入口级幂等检查、批量参数预校验等），但此类逻辑**必须无领域语义**。
- Controller 注入 `*Facade` 不触发 ArchUnit #11（#11 仅拦截 `..application.service..`，`..application.facade..` 不在拦截范围；其余 E-5.3.2 铁律由 CR 保证）。
- Facade 可向下调用 `*Orchestrator`（跨聚合编排场景：Controller → Facade → Orchestrator → Handler → Domain）。

### E-5.4 模块适用性（按**性质**而非物理位置）

DDD 规范是否适用，**按模块性质判定**，不以 `bone-platform/` / `bone-engine/` 目录划分。仓库快照见 [附录 B.1](#b1-模块适用性快照)。

| 性质 | 判定标准 | 规范适用度 |
|------|----------|------------|
| **应用 / 控制面 BFF** | 对外提供 REST API；含 Controller + 业务编排 + 持久化；服务于人类用户或前端 | **完全适用**：E-3 铁律 R1–R9 + E-5（全套包结构与命名）+ E-7.3 异常 + E-8 D0/D1/D2 + G-1 ArchUnit 规则集 |
| **引擎 SDK / 框架库** | 被业务模块依赖、无 Controller、提供 SPI/注解/工具类 | **部分适用**：E-3.1.1（依赖向内）+ E-8 D0/D1/D2 + E-10 ACL；**豁免** E-5（包结构按 SPI/库习惯）、E-5.3（无 application 概念）、G-1 中 R4/5/6/7 |
| **基础设施服务**（含 `bone-gateway`，v4.5） | 纯技术中转，无业务规则（gateway：HTTP 反向代理 / 协议转换，无业务模型） | **部分适用**：依赖方向 + 配置规范；**豁免** E-5、G-1 业务相关规则（gateway 因纯路由豁免 E-5.3 应用层） |
| **CRUD 支撑域**（v4.5，D13） | 纯表结构增删改查、无状态机、无领域事件、无演进压力（字典、简单配置类数据） | **简化适用**：E-5.2 极简树 + 直连 Repository + 命名简化（豁免 E-5.3 完整应用层）；**仍守** R1 依赖方向 + R4 仓储收敛 + 多租户（E-4.4） |

**判定通用规则**：

1. **看 Controller**：有 `@RestController` 且暴露业务路径 → **应用模块**，全规范
2. **看依赖方向**：被业务模块 `import` → **SDK / 框架**，可豁免包结构
3. **混合情况**：含 SDK 子模块和 Studio 子模块（如 `bone-extension-engine`）→ **按子模块**分别判定，**Studio = 应用，SDK = 引擎**

**门禁**：新增豁免模块或扩大豁免范围须按 [E-0.4](#e-04-例外登记的统一形态) 写 ADR；任何「应用模块伪装成引擎以逃避规范」在 CR 阻断。

**判定示例（不容歧义）**：

- `bone-extension-engine/bone-extension-studio` = **应用**（暴露 `/api/v1/extension/*`）→ 完全适用 E-3 铁律；持久化端口命名 `*Repository`，包 `domain/repository/`。
- `bone-extension-engine/bone-extension-sdk` = **SDK**（被业务进程 import）→ 守 E-8 D0/D1 即可。
- `studio-generator` = **应用**（暴露 `/api/v1/generator/*`）→ 完全适用 E-3 铁律；不得存在自造 `@UseCase` / `UseCaseExecutor` 类型与 `*UseCase` 类。

### E-5.5 持久化端口命名（强制）

| 维度 | 规则 |
|------|------|
| 包 | `domain/repository/`（**唯一**）；**禁止** `domain/store/` 或任何同义包名 |
| 接口命名 | `*Repository`（如 `OrderRepository`）；**禁止** `*Store`、`*Dao`、`*Mapper` 作为领域端口名 |
| 实现位置 | `infrastructure/persistence/repository/`（或 SDK 代理生成） |
| 与 E-9.2 联动 | 仅允许聚合保存 / 删除 / 按 ID 加载（`save` / `saveAll` / `remove` / `removeAll` / `findById` / `findAllById` / `existsById`），加 E-9.2 单键白名单（`findBy*` / `existsBy*` 前缀） |

> 既有 `*Store` / `domain.store/` 视为不符合规范，按 [E-0.2](#e-02-存量不符合规范代码的处理) 一律迁移；CI 由 `BoneDddArchRules.noNewDomainStorePackage()` 拦截新增。

---

## E-6. 各层职责

| 层 | 职责 | 禁止 |
|----|------|------|
| **adapter** | 协议转换、入参校验、路由；**Controller 直接注入** `*CommandHandler` / `*QueryHandler` / `*Orchestrator`；满足 E-5.3.2 F1/F2 时可注入 `*Facade` | 业务规则；注入 `*UseCase`、`application/service/*Service`、`domain/service/*`（领域服务）、`domain/repository/*Repository`；直接操作持久化 |
| **application** | 用例编排、**写事务边界**（`@Transactional` 置于 CommandHandler 或等价边界）、调领域与端口 | 实现本属聚合内的业务不变量；直接拼写/执行 SQL；直接依赖 infrastructure 实现类；`application/usecase` 包或 `*UseCase` 类 |
| **domain** | 规则、聚合、事件、端口定义 | Spring/JPA/MyBatis/Jackson、查询构建器 |
| **infrastructure** | ACL 实现、SDK 配置、技术适配；持久化 `*PO` / `*Converter` / `*RepositoryImpl` | 领域业务规则 |

---

## E-7. bone-framework 要点

### E-7.1 模块职责（摘要）

| 模块 | 职责（实际类一览）|
|------|------|
| **bone-core** | 实体基类：`Entity`、`AbstractEntity`、`TenantAbstractEntity`、`AggregateRoot`；响应：`ApiResponse`、`PageResult`；上下文：`TenantContext`；异常根：`BizException`、`DomainException`、`SystemException` 等（E-7.3 三根收敛）；ID：`DistributedIdGenerator`；事件：`DomainEvent` |
| **bone-metadata-sdk** | `@EnableSqlRepositories`、`Repository<T,ID>`、`QueryBuilder` / `FluentQuery`、`@Table` / `@Id` / `@GeneratedValue` 等 |
| **bone-extension-sdk** | `@ExtensionPoint` / `@Extension` |
| **bone-security** | 认证、JWT、密码编码等 |

### E-7.2 实体继承（按代码实际）

bone-core 中实体基类构成**单根继承树**（`Entity<ID>` 为根，下分聚合根链与审计实体链），业务实体须**二选一**：

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
- **元数据注解**：仅 **bone-metadata-sdk**（见 E-8 D1），禁止 JPA `@Entity` 等。
- **D 等级**：上述基类均属 D2 例外。**现状澄清（2026-08-30 复核）**：`AggregateRoot` / `TenantAggregateRoot` 当前已仅使用 `@Getter` + 手写 `setId` / `setTenantId`（**未使用 `@Data`**）；业务实体属 D1；继承产生的 setter 不算违反 D0/D1，见 E-8 D2 注脚。**须区分两个独立问题**：① `setId` / `setTenantId` 的 **public 可见性是 SDK 反射回填与 `Tenantable` 契约所需，不可降级为 protected**（全平台大量依赖），改以调用点 ArchUnit 规则约束（见 E-8）；② `@Data` 注解收敛只是个别模块少量 domain 类的**有界替换任务**，勿与①混为一谈、勿高估其成本。

### E-7.3 异常与 API（规范）

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

- **三根收敛（v4.5，D7）**：一切业务/系统异常最终归于**三根**——`DomainException`（领域不变量被违反）、`BizException`（用例级业务失败，根类含错误码，与 API 规范台账一致）、`SystemException`（系统级故障，继承 `InfrastructureException`，ADR-0012，映射 5xx）。既有 `NotFoundException` / `IdempotentException` / `InvalidRequestException` / `InfrastructureException` / `ServiceException` / `DistributedLockException` / `LockAcquireFailedException` 保留为**三根的可识别子类**（按语义选择），但**禁止模块自建新异常类型**（ArchUnit `noCustomBusinessException` 等拦截）；新异常必须归于三根之一。
- 若规则属于「仅在该聚合内成立」，优先 **`DomainException`**；跨聚合编排失败或应用策略拒绝用 **`BizException`**（错误码与 API 规范台账一致）；基础设施 / 系统故障用 **`SystemException`**（映射 5xx）。
- 统一响应：`ApiResponse<T>` / `PageResult<T>`（bone-core）。
- **`ApiResponse.success(String)` 重载陷阱（2026-08 补充）**：`ApiResponse` 存在 `success(String message)` 与泛型 `success(T data)` 两个单参重载，**字符串实参会命中前者**（结果 `message=字符串`、`data=null`）。凡"返回字符串数据"（如状态、`taskId`、导出内容）必须用**双参** `success(String message, T data)`；仅当确需返回"纯提示消息"时才用单参 `success(String message)`。曾在 `masterdata` 导出、`studio-generator` 任务状态/数据源创建等 5 处发生数据劫持，修复时以此为戒。

---

## E-8. 领域模型与纯净度（D0 / D1 / D2）

| 级别 | 说明 |
|------|------|
| **D0** | 无持久化需求的纯逻辑类型：核心业务逻辑纯 Java + `java.util`；Lombok 仅 `@Getter` + 私有 `@NoArgsConstructor`；**禁止** `@Setter`/`@Data`/`@AllArgsConstructor`。**v4.5（D2-1）**：D0 **只留给**无表值对象 / 领域服务 / 纯策略等无持久化需求的类型 |
| **D1（可持久化充血模型，v4.5 更名）** | 在 D0 基础上，**额外允许** `bone-metadata-sdk` 的 `@Table`、`@Id`、`@GeneratedValue` 等元数据注解；**同样禁止** `@Setter`/`@Data`/Spring/Jackson/JPA 注解。若 SDK 升级带来新注解，须经架构评审纳入「D1 白名单」。**不再视为「向贫血妥协」**：业务行为与持久化映射并存，反贫血判据是**行为**（R8 纯单测）而非「是否有映射注解」；若未来需拆为「PO + 领域类」两套（PO 分离），须在评审中给出**退出条件**，不默认拆分 |
| **D2（基础设施基类例外）** | `bone-core` 的 `AbstractEntity`、`TenantAbstractEntity`、PO（`*PO.java`）等**框架基类与持久化对象**允许 `@Data`、`@AllArgsConstructor` 等便利注解。新增此类例外按 [E-0.4](#e-04-例外登记的统一形态) 在 `bone-framework` 模块 README + ADR 登记 |

> **D2 继承注脚**：业务实体继承 D2 基类（如 `AbstractEntity`、`TenantAbstractEntity`、`AggregateRoot` 的父类 `Entity`）后，通过 Lombok `@Data` 在**编译期获得** setter。这是工程折中，**不视为违反 D0/D1**；但业务代码**不得调用** setter，状态变更必须经领域行为。CR 审查为主；后续可酌情加 PMD/SpotBugs 自定义规则辅助（非强制）。

**聚合**：小聚合、工厂方法、领域行为、事件过去式命名。**扩展点**：跨场景的可插拔逻辑用 **bone-extension-sdk** 的 `@ExtensionPoint` / `@Extension`，禁止超长 `if-else`。

**反贫血红线（2026-08 补充，业界共识：领域逻辑必须落在领域层）**：

- 聚合根 / 实体的**状态迁移、不变量校验、派生计算**必须封装为领域行为方法（`account.enable()`、`order.confirm()`）；`Handler` 只做"加载 → 调领域方法 → 保存"的编排。
- **禁止**：外部（application / adapter）通过 getter 读出状态、setter 逐个修改、再 save——该形态使领域对象退化为数据袋（贫血模型），不变量散落外层且无法复用。
- D1 注解继承的 setter（E-8.2 注脚）**仅限框架/测试回填**使用，业务路径不得调用；CR 按此审查（后续可加 PMD/ArchUnit 辅助）。
- 判别自查：若某业务规则"只能从 Handler 里读出来，写进领域对象"——说明领域方法缺失，应下沉到聚合或领域服务。

**反贫血的判定口径（2026-08-30 补充，v4.5：弱约束代码形式、强约束行为）**：

反贫血治理应**强约束行为、弱约束代码形式**——代码形态（是否有 `reconstitute()`、是否用 `@Data`）不是可靠判据，**能否在纯单测里让不变量失败**才是。据此分层：

1. **主判据 ＝ R8 聚合纯单测**：每个聚合根须有可无容器运行的纯单测，覆盖主状态机与 ≥1 条拒绝路径。机器判据为 `AggregatePureUnitTestGuard.verify(...)` **五条**——① 同名 `*Test` 类存在、② 至少 1 个 `@Test` 方法（堵住空类绕过）、③ 无容器注解（`@SpringBootTest` / `@DataJpaTest` / `@ExtendWith(SpringExtension.class)` 等）、④ **调用了聚合的行为方法**（仅 getter 调用无法证明不变量生效）、⑤ **含断言或异常期望**（堵住 `assertEquals(1, 1)` 式空测试）；仅统计 `..domain..` 下的具体聚合根，`..domain.outbox..` 等基础设施持久化记录属技术对象不计入。内容质量由 CR 检查清单与测试模板保证。存量缺口用 `verifyAllowingPending(<待补简名集合>, ...)` 登记过渡（见 [附录 B.3](#b3-archunit-模板bone-architecture-test)）。
2. **ORM 恢复属基础设施细节，不要求业务层提供 `reconstitute()`**：SDK 通过**字段级反射**恢复对象（`SmartRowMapper` 的 `field.setAccessible(true); field.set(...)`；主键回填走 `ReflectionUtil.setFieldValue`），实例经无参构造器创建，**整个过程不经由 setter**。
3. **不强制 `reconstitute()` 静态工厂，也不把调用点规则扩展为「外层禁调所有 `set*`」**：该做法测不到「是否绕过行为」，只会逼出 `create()` + `reconstitute()` 双入口冗余。
4. **但聚合身份与租户归属的保护为硬门禁，不可弱化**：`outerLayersMustNotMutateAggregateIdentity()` 属 ArchUnit **A 类强制规则**（非可选风格约束），禁止外层篡改 `setId` / `setTenantId`——这是租户隔离与身份完整性的底线。
5. **「`@Data` 收敛」与「setter 可见性」是两个问题**：`AggregateRoot` / `TenantAggregateRoot` 已仅用 `@Getter`；残余 `@Data` 集中在个别模块的少量 domain 类，按模块逐步替换即可（见 E-7.2）。

### E-8.1 空值与 Optional 约定（与 `CLAUDE.md` 对齐）

| 场景 | 约定 |
|------|------|
| 仓储查询返回 | 以 `bone-metadata-sdk` 契约为准：`Repository.findById(ID)` 返回**裸类型 `T`**（未找到返回 `null`），调用方判空后抛 `NotFoundException` 或走业务分支；`findByIds` / `findAll` 返回集合（空集合而非 null）。**未来改造项**：SDK 升级为 `Optional<T>` 后按新契约统一迁移（见文末注） |
| Application/Adapter 内部返回 | 集合返回空集合（`List.of()`），单对象按业务语义返回 `Optional` 或显式 DTO；**禁止**用 `null` 表达「未找到」（边界处由仓储层 null 立即转 `Optional` 或抛错，不让 `null` 漂入） |
| 聚合 / 值对象 | 字段是否可空在工厂方法或值对象构造中显式校验；状态变更经领域行为而非 setter；JSON 序列化层不依赖 `null` 表达业务语义 |
| 三方/遗留 ACL | 在 `infrastructure.gateway` 适配器内**立即**把外部 `null` 转为 `Optional` 或抛错，不让 `null` 漂入应用/领域层 |

> **仓储 null 契约说明（2026-08 修订）**：SDK `Repository` 当前 `findById` 返回裸类型（未找到为 `null`），故仓储层可合法返回 `null`；应用/适配器**不得**让该 `null` 继续漂移，须在消费边界判空。`returnsOptionalOrCollection()` 类 ArchUnit 断言在 SDK 升级为 `Optional<T>` 后再启用（届时按「仓储层禁 null」执行）。

### E-8.2 持久化对象决策树

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

### E-8.3 D1 退出条件与 PO 分离迁移路径（v4.6 新增）

> **为何补这一节**：D1「可持久化充血模型」在 v4.5 被定为**长期正解**，但只说了「不默认拆分」，**没有定义什么情况下必须拆**。缺少退出条件的架构折中不会自然演进，只会沉淀为债——`bone-blueprint` 的订单明细就是实证：`Order` 聚合持有 `List<OrderItem>`，而 `t_order_item` **全库无任何写入方**（无 `OrderItemRepository`、SDK 无级联能力），明细**永不落库**；`OrderCreatedEventHandler` 仅留一条 `error` 日志兜底。这是「领域模型 = 数据模型」这一耦合造成的**真实数据丢失**，不是理论风险。

**D1 定位修正**：D1 是**过渡档**（适用于「聚合结构与表结构 1:1、无嵌套集合、无多存储需求」的简单域），**不是终态**。

**退出信号（出现任一即应评估 PO 分离）**：

| # | 信号 | 判据 | 后果 |
|---|------|------|------|
| S1 | 聚合持有**需要持久化的集合 / 嵌套实体** | 聚合内 `List<Entity>` 且该实体有独立表 | 明细无法落库（当前 `OrderItem` 情形） |
| S2 | 领域模型字段与表字段**出现语义分歧** | 表中存在仅供查询/索引、领域不需要的列，或领域字段需由多列计算 | 领域模型被表结构污染 |
| S3 | 需要**多存储 / 多形态** | 同一聚合需同时落库 + 缓存 + 索引，或需影子表 / 分库分表 | 注解无法表达多目标映射 |
| S4 | 需要**审计 / 加密 / 脱敏**等存储级横切 | 列级策略与领域模型无关 | 领域对象被迫承载存储细节 |
| S5 | 聚合需要**跨存储迁移** | 换库、拆库、异构存储 | 迁移需改动领域层 |

**迁移路径**（E-8.2 决策树已备好目标形态，此处给出步骤）：

1. 在 `infrastructure/persistence/entity/` 新建 `*PO`（D2，允许 `@Data`）；
2. 在 `infrastructure/persistence/converter/` 新建 `*Converter`，实现 PO ↔ 领域对象双向映射（**映射逻辑只在此处**）；
3. 领域对象移除 D1 注解，落回 **D0**（纯 Java）；
4. `infrastructure/persistence/repository/*RepositoryImpl` 改为操作 PO + Converter，对外仍实现 `domain/repository` 接口；
5. 领域层**零改动**（这是分层的价值验证点：若第 4 步需要改领域代码，说明映射没收敛干净）。

**存量债登记**：`bone-blueprint` `t_order_item` 明细不落库属 S1，修复**必须排在 ADR-0019 之后**——否则 `OrderItem.orderId` 外键会指向被 SDK 覆盖前的旧 id（见 [E-9.1](#e-91-id)）。修复前该缺陷按 [E-0.4](#e-04-例外登记的统一形态) 在 `bone-blueprint` README 显式登记为已知数据缺陷，不得静默。

---

## E-9. ID、仓储与 CQRS（Bone）

### E-9.1 ID

- **当前契约（2026-08 对齐 SDK）**：`bone-metadata-sdk` 的 `@GeneratedValue(strategy = DISTRIBUTED_ID)` 由 **SDK 统一生成主键**——`Repository.insert` / `save` 均会**重新生成并回填**实体 id（忽略应用层预分配值）。应用层**不预分配** id：聚合工厂的 `id` 参数传 `null`，持久化后以 `entity.getId()`（或 `insert` 返回值）为准。
- **机制与陷阱（2026-08-30 复核补充）**：`BaseRepository.insert()` 在非 `IDENTITY` 分支**无条件**调用 `generateId` + `setEntityId`（第 164-167 行），而 `batchInsert`（第 196 行）与 `save` 依赖的 `ensureIdInitialized`（第 658 行）均为 `if (id == null)` 才生成。
  - ⚠️ **后果**：应用层预分配并传入聚合构造的 id，会在 `insert` / `save` 时被**静默替换**为 SDK 新生成的值，即**内存态 id ≠ 持久化后 id**。
  - ⚠️ **禁止**依赖「构造期传入的 id」进行任何跨实体关联（外键、事件载荷中的 id 引用等）；一切以 `save` / `insert` 之后的 `entity.getId()` 为准。
  - `bone-blueprint` 现有 `DistributedIdGenerator.generateLongId()` + 传入 `Order.create(orderId, ...)` 的写法**超前于 SDK 能力**，属待对齐项（其 `OrderItem` 以该内存 id 作外键，存在错位隐患）。
- **领域纯净**：**领域不**依赖 ID 生成器（不 import `DistributedIdGenerator`）；ID 生成技术属基础设施（SDK 内部）。应用层即便预生成，也须在**持久化后**重新读取 id，不得假设其不变。
- **主键策略**：**默认禁止**以数据库 **`IDENTITY` / 自增列** 作为**领域主标识**（避免与分布式 ID、跨库迁移、合并冲突处理不一致）。
- **例外**：① 遗留表或强约束场景必须使用自增时，按 [E-0.4](#e-04-例外登记的统一形态) 在模块 README 登记（表名、字段、范围、退役计划）；② 业务需要**保留外部 id**（数据迁移/导入、外部系统 id 映射）时，须在模块 README 登记，并经 SDK 扩展支持（当前 `insert`/`save` 不支持保留入参 id，见 [ddd/07-supplements.md §18.1 备注](./ddd/07-supplements.md)）。**在 ADR-0019 落地前，该例外不得删除**——否则等于宣称「可保留外部 id」而 SDK 实际仍会覆盖。
- **演进方向（v4.5，待 ADR-0019）**：目标为「**身份在构造期确定**」——由应用层生成 id 并作为聚合构造参数传入，使聚合构造后即持有稳定身份、领域事件可安全携带 id。其**先决条件**是 **ADR-0019**：将 `insert()` 改为「仅 id 为空时才生成」，与 `batchInsert` / `ensureIdInitialized` 一致，**尊重调用方预置的非空 id**。ADR-0019 落地后，本条「应用层不预分配 id」的契约方可放宽，例外②方可删除。

### E-9.2 写侧 Repository

子接口 **仅继承** SDK 基 `Repository`，**不新增**带 **多个业务条件组合**、分页、排序、Join 的方法（此类一律走读侧 E-9.3）。

**判据（两条，主 / 辅分明）**：

| 层级 | 判据 | 说明 |
|------|------|------|
| **主判据（本质）** | 写侧仓储方法的**返回类型**只能是 **聚合根 / `Optional<聚合根>` / `boolean` / `void`** | 与 [E-3 R4](#e-3-铁律r1r9-必守) 一致。返回**投影 / DTO / `Page` / `List<VO>`** 即违规——那意味着仓储在做「查询」而非「聚合加载与持久化」。不受命名风格影响 |
| **辅助判据（当前机器实现）** | 方法名须落在白名单：`save` / `saveAll` / `remove` / `removeAll` / `findById` / `findAllById` / `existsById`，或单键 `findByXxx` / `existsByXxx`；**禁止** `findBy*And*` / `*Or*` 等多条件组合形式 | ArchUnit `domainRepositoriesShouldOnlyDeclareWhitelistedMethods`（[G-1 #5](#g-1-测试与-ciarchunit-规则集)）。属**启发式**，见下方说明 |

> **为何以返回类型为主判据**（业界共识：Repository 是「聚合根的集合抽象」）：方法名扫描是**脆弱启发式**——`findByStatusAndType` 被拦住，而 `queryByStatusAndType`、`search(...)` 等同样语义的命名可绕过；反之 `findByCode`（返回单个聚合）本是合法的单键加载，却可能被过严的命名规则误伤。**返回类型**才是「仓储是否越界做查询」的本质判据，且可机器判定、不误杀。
>
> **实现状态**：当前 ArchUnit 仅实现「辅助判据（方法名）」；「返回类型」主判据由 CR 按上表判定，**M2 阶段补齐到规则库**，届时方法名规则降为提示 / warn。

**单键辅助方法（可选）**：仅允许 **单一等值条件** 的 `existsByXxx` / `findByXxx`，语义须为业务外键或唯一码（如 `findByCode`、`existsByEmail`）。新增方法在 PR 评审中按上表**主判据（返回类型）**判定，不另设模块级二次白名单。

### E-9.3 读侧（v4.6：位置判据替代注解判据 + 目标态收敛）

**当前可用写法**：`QueryBuilder.from(Entity.class)` 或等价 `FluentQuery` / `Criteria`；读侧 DSL 类型**须标注** `@ReadSideOnly`（`bone-core`），ArchUnit 按注解依赖检测。复杂 SQL 放 `infrastructure.query`。读侧 DSL **禁止**出现在 `domain` 与 `application.command.handler`。

**v4.6 判据升级——以「位置白名单」为主判据**：

| 判据 | 方式 | 缺陷 |
|------|------|------|
| 注解判据（v4.5，当前实现） | 读侧 DSL 类型标注 `@ReadSideOnly`，检测「谁依赖了被标注的类型」 | **漏标即绕过**：新增一个 DSL 类型忘了标注，所有依赖它的类全部静默放行。这是典型的「标记坏人」式否定检测 |
| **位置白名单（v4.6 主判据）** | 读侧 DSL **只允许**出现在 `..infrastructure.query..` / `..infrastructure.persistence..`；出现在 `..application..` 即违规 | 无法漏标——按包位置判定，不依赖任何人工标记。误报由白名单显式豁免 |

新增规则 `readSideDslOnlyInQueryLayer()`（[G-1 #19](#g-1-测试与-ciarchunit-规则集)）。**过渡期双轨**：两条规则并存，注解判据保留（防回滚），位置判据以 `FreezingArchRule.freeze()` 登记存量（当前 `application/query/handler` 大量直用 DSL），按附录 B.3.1 台账逐批收敛。

**目标态：读侧 DSL 退出 `application` 层**（解一条 v4.5 遗留的内在矛盾）：

> **矛盾陈述**：[E-6](#e-6-各层职责) 规定 application「禁止直接拼写/执行 SQL」，而本条允许 `*QueryHandler` 直接使用 `QueryBuilder`——那是 SQL 的 DSL 皮肤，二者字面冲突。现状代码即为该矛盾产物：`AccountPageQueryHandler` 直接 `QueryBuilder.from(Account.class)`，把**领域聚合当作读模型表**使用。

**目标态**：`*QueryHandler` 只依赖**读侧端口**（`*ReadPort`，ADR-0013 已有形态），DSL 实现在 `infrastructure/query/*QueryImpl`。收益：① 读模型可独立演进（换存储 / 加投影不动 Handler）；② 聚合字段改名不再破坏全部查询；③ E-6 与本条不再冲突。

> **不强制立即迁移**：读侧是 Bone 存量代码量最大的部分，一次性收敛风险高于收益。按「**新增读路径必须走端口，存量读路径随功能重构逐步下沉**」推进，并在模块 README 登记存量清单。

### E-9.4 启动扫描（参考）

```java
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.{module}.domain.repository")
public class Application { }
```

### E-9.5 读侧端口（`*ReadPort`，ADR-0013）

当列表/搜索/统计等读操作**不宜**或**无法**用 `QueryBuilder`（E-9.3）表达时（如内存仓储、元数据混合持久化、专用读模型），在 **`domain/gateway/*ReadPort`** 声明读侧专用端口；`*QueryHandler` 依赖 `*ReadPort`，写侧仍用 `domain/repository/*Repository` 白名单方法（E-9.2）。实现类可在 `infrastructure/persistence/` **同一类**双接口实现（`implements XxxRepository, XxxReadPort`）。

> **v4.5（D5）**：**CommandHandler 亦可注入 `*ReadPort`**（组合读取 / 「读己之写」）——`*ReadPort` 属读侧端口（R5 允许），不违反 R6（R6 仅禁止 CommandHandler 内使用 `QueryBuilder`）；简单 CRUD 支撑域可直连 `Repository.findById`（E-5.4 CRUD 豁免档）。

**读路径决策树（两条 + `*ReadPort` 例外）**：

```text
需要读取领域数据？
├─ 聚合加载（主键 / 单业务键） → 写侧 Repository.findById / findByCode（E-9.2）；CRUD 支撑域可直连
├─ 查询 / 报表（多条件 / 分页 / 排序 / Join） → QueryBuilder + @ReadSideOnly（E-9.3）
└─ 例外：列表 / 搜索 / count 且 QueryBuilder 不适用 → domain/gateway/*ReadPort（本条）
    └─ 跨限界上下文 / 外部系统 → ACL Gateway（E-10）或集成事件投影
```

**`domain/gateway/` 包内职责区分**（命名后缀区分，勿混用）：

| 后缀 / 用途 | 示例 | 职责 |
|-------------|------|------|
| `*ReadPort` | `ExtPointReadPort` | **模块内**读侧：列表、搜索、统计 |
| `*Gateway` / `*Port`（出站） | `PaymentGateway`、`InventoryGateway` | **跨边界**出站 ACL（外部 HTTP/RPC/MQ） |

新增读方法**只加在 `*ReadPort`**，禁止回写到 `*Repository` 规避白名单。首版见 [ADR-0013](./adr/0013-extension-studio-repository-read-side.md)（`bone-extension-studio`）。

### E-9.6 并发控制与幂等原语（v4.6 新增）

> **为何必须补**：[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 要求「至少一次投递 + 消费端按 `eventId` 幂等」，[E-5.3](#e-53-应用层结构强制) 允许「Orchestrator 可重试编排」——这两条的落地**完全依赖**并发控制与幂等键。v4.5 未定义二者，导致上述规范在当前状态下**不可执行**：可以写出合规代码，却无法保证重试安全。

**1. 乐观并发**：

- 聚合根**必须**带 `version` 字段（或由 SDK 提供等价能力并在模块 README 登记）；**核心域（L2+）** 聚合表 DDL **强制**含 `version` 列（评审必检；`scripts/ci-check.sh` DDL 审查逐步纳入，与 HC-008 同类检查）；
- 仓储实现 UPDATE 时追加 `WHERE version = ?`，影响行数 0 → 抛并发冲突异常（映射 **HTTP 409**，见 [Bone-API-规范](./Bone-API-规范.md)）；
- **未实现 `version` 的聚合，禁止在文档中声明「支持并发写」**，也禁止依赖重试编排。

**2. 幂等键**：

| 维度 | 约定 |
|------|------|
| 来源优先级 | 外部业务键（`channelTradeNo` / `requestId`）> 事件 `eventId` > 客户端 `Idempotency-Key` 头；**禁止**用业务自增主键作幂等键 |
| 存储 | **唯一索引**兜底（防并发重复写），记录保留期 ≥ 业务最长重试窗口（默认 ≥ 7 天，可配置） |
| 位置 | 幂等**判断**在聚合内（[P-5.5](#p-55-回调幂等与跨聚合协作支付场景样板)）；幂等**存储**在基础设施。**二者缺一不可**——前者防业务重复，后者防并发竞态，只有其中之一都不足以抵御并发重复回调 |

**3. 事件身份**：领域事件**必须**携带全局唯一 `eventId`（发布侧在信封层生成），消费端按 `eventId` 去重。当前 `DomainEvent` 为空接口、无 id 字段，落地 ADR-0021 时须补齐。

> **依赖标注（重要）**：本节平台级实现依赖 [ADR-0021](./adr/0021-outbox-and-consumer-idempotency-platformization.md)。**ADR-0021 落地前，[P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 中「必须 Outbox」各行不具备可执行条件**；模块已自建的 Outbox（`bp_outbox`、`int_outbox`）须按 [E-0.4](#e-04-例外登记的统一形态) 登记为过渡实现并注明收敛计划。规范不应宣称一个尚未具备支撑能力的约束。

### E-9.7 强类型 ID（v4.6 新增，过渡期约定）

**问题**：聚合身份与跨聚合引用当前一律使用裸 `Long`（如 `Order.customerId`）。裸标量 ID 在编译期无法区分「订单 ID」与「客户 ID」，参数顺序写错即造成**静默数据错乱**——这是 DDD 项目最高频的低级缺陷，且常规单测难以发现。

**约定**：

- **新增聚合的跨聚合引用必须使用强类型 ID 值对象**（`OrderId`、`CustomerId`），禁止裸 `Long`；
- 强类型 ID 按 [P-3.2](#p-32-实体与值对象) 实现（`record` 或全 `final` 字段 + 构造期空值校验），无 setter；
- 与 SDK 共存：`Entity<ID>` 泛型可承载 `Entity<OrderId>`；持久化层由 Converter（[E-8.3](#e-83-d1-退出条件与-po-分离迁移路径) 第 2 步）负责 `OrderId` ↔ 列值转换；
- **存量裸 `Long` 不强制批量改造**（收益低于风险），但**改造后的聚合**必须使用强类型 ID。

---

## E-10. ACL（Bone）

出站端口在 **domain**（如 `PaymentGateway`），实现在 **infrastructure**；禁止在 `application` 直接依赖第三方 HTTP/RPC **实现类型**。

**ACL 模式指引（2026-08 补充，对齐 Evans / Vernon 防腐层模式）**：

| 层 | 职责 | 约束 |
|----|------|------|
| `domain/gateway/*Gateway`（端口） | 用**领域语言**声明出站能力（入参/出参均为领域类型） | 禁止暴露第三方类型（DTO/异常） |
| `infrastructure/gateway/*GatewayImpl`（适配器） | 协议转换：领域对象 → 外部请求 → 外部响应 → 领域对象；**立即**把外部 `null`/错误转为 `Optional` 或领域异常 | 禁止把外部异常原样抛给 application；禁止把外部 DTO 穿透到 domain |
| **Translator**（可选，置于 adapter 内） | 外部 DTO ↔ 领域对象的显式映射类（如 `XxxExternalTranslator`） | 映射逻辑不外散在 Controller/Handler |

**强制要求**：

- 每个跨上下文/外部系统集成必须有**契约测试**（打桩外部响应，验证翻译与错误语义隔离），见 G-1 测试金字塔。
- 外部错误语义（HTTP 状态、第三方错误码）**不得穿透**核心领域；在适配器边界转为 `BizException`（带稳定错误码）或领域异常。
- 禁止在 `application` / `domain` 出现 Feign 客户端、`RestTemplate`、MQ producer 的具体类型（ArchUnit R3 拦截）。

---

## E-11. Flow / AI 编排（可选）

若使用 Flow、`@Capability` 等：

- **能力声明**：在 `*CommandHandler` / `*QueryHandler` / `*Orchestrator` 上加 `@Capability` 元数据；**禁止**为「让 AI 发现」而新建 `*UseCase` 门面。
- **能力发现**：通过独立的 capability 注册表（如 `bone-core` 的能力扫描器）按注解汇总，**与 DDD 用例命名解耦**。
- **执行入口**：Flow / AI 调度统一通过注入 Handler / Orchestrator 调用，遵循 E-6 事务边界。
- **底线**：编排能力**不替代**聚合与 E-3 铁律；命名对齐通用语言。

---

## G-1. 测试与 CI（ArchUnit 规则集）

**共享规则库**：`bone-framework/bone-architecture-test`（`BoneDddArchRules`），各应用模块以 `test` scope 依赖引用，避免每模块复制规则。

**ArchUnit 最小规则集**（每应用模块 `src/test/java/.../architecture/ArchitectureTest.java`，**与 E-3 R1–R9 对应**）：

| # | 规则方法（`BoneDddArchRules.*`） | 对应规范 |
|---|----------------------------------|----------|
| 1 | `domainMustNotDependOnOuterLayers` | R1 |
| 2 | `applicationMustNotDependOnInfrastructure` | R1 |
| 3 | `domainMustNotUseQueryBuilder` | R5 |
| 4 | `commandHandlersMustNotUseQueryBuilder` | R6 |
| 5 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` | R4 + E-9.2 |
| 6 | `noUseCaseClassesInApplication` + `noApplicationUseCasePackage` | R7 + E-5.3 |
| 7 | `noBoneCoreUseCaseApiDependency`；`studio-generator` 加 `noStudioGeneratorUseCaseAnnotation` | R7 |
| 8 | `noNewDomainStorePackage` | E-5.5 |
| 9 | `noCustomBusinessException` | E-7.3 |
| 10 | `noBusinessExceptionSuffix` | E-7.3 |
| 11 | `adapterControllersMustNotDependOnApplicationService` | R7 + E-6（仅拦截 `..application.service..`；`..application.facade..` 不受此规则限制） |
| 12 | `adapterControllersMustNotDependOnDomainRepository` | E-6 |
| 13 | `commandHandlersShouldBeNamedCommandHandler` | E-13 |
| 14 | `queryHandlersShouldBeNamedQueryHandler` | E-13 |
| 15 | `commandHandlersShouldBeTransactional` | E-6 |
| 16 | `queryHandlersShouldBeReadOnlyTransactional` | R6（建议） |
| 17 | `adapterControllersMustNotDependOnDomainService` | R7 + E-6（Controller 禁直注 `domain/service` 领域服务） |
| 18 | `outerLayersMustNotMutateAggregateIdentity` | R2 + E-8（**A 类强制**：禁外层篡改 `setId` / `setTenantId`） |
| 19 | `readSideDslOnlyInQueryLayer` | E-9.3（读侧 DSL 只许在 `infrastructure/query`，application 层禁依赖；存量以 freeze 收敛） |
| — | `AggregatePureUnitTestGuard.verify("<根包>")`（**R8，独立测试类承载，非 `ArchRule`**） | R8 + E-8（每个聚合根须有纯单测） |
| 20 | `noCrossContextModelDependency(根包)` | E-4.1.1（跨上下文模型依赖禁令：含读侧 `QueryBuilder.from(其它上下文实体)`、跨上下文 JOIN） |
| 21 | `oneAggregatePerTransaction` | R9 + E-3.1.2（一事务一聚合；写事务内 `*Repository.save` 目标类型 ≤ 1；v4.7 扫描范围扩至 `command.handler` / `service` / `orchestration`，ApplicationService 同样受约束） |
| 22 | `businessLayersMustNotReadTenantContextDirectly` | E-4.4（租户取值收敛：`application` / `domain` / `adapter` 禁止直调 `TenantContext`，仅 `infrastructure` 访问；v4.7 补门禁，参考样板已启用） |

> **#18 与 R8 说明**：
> - **#18 `outerLayersMustNotMutateAggregateIdentity` 属 A 类强制、不 freeze**（守护租户隔离与身份完整性），**必须**出现在每个应用模块的 `ArchitectureTest` 中。⚠️ 该规则此前虽已在规则库实现，却**未登记进本表与附录 B.3 模板**，导致各模块实际未启用——属门禁缺口，本次补齐（v4.6：bone-blueprint + 全部 8 个应用模块已启用）。判据口径见 [E-8](#e-8-领域模型与纯净度d0--d1--d2) 反贫血红线补充。
> - **R8 不能写成普通 `ArchRule`**：ArchUnit 的 `@AnalyzeClasses` 默认带 `ImportOption.DoNotIncludeTests`，`ArchitectureTest` 看不到测试类，无法判定「某聚合是否存在对应纯单测」。故由 `AggregatePureUnitTestGuard` 用 `ClassFileImporter` **分别导入主代码**（`DoNotIncludeTests`）**与测试代码**（`OnlyIncludeTests`）**两个类集合交叉比对**，以模块内**独立测试类**承载（示例见 [附录 B.3](#b3-archunit-模板bone-architecture-test)）。
> - **R8 的存量接入＝待补清单而非 freeze**：无聚合模块（如 `bone-extension-studio`、`bone-metadata-server`）直接用严格模式 `verify(...)`——未来一旦引入聚合根立即要求纯单测；有存量缺口的模块用 `verifyAllowingPending(<待补简名集合>, ...)`：清单内聚合暂不判失败（但**新增聚合一旦不在清单即失败**，守住增量），清单内聚合若已存在合规纯单测则**报错要求移除**（清单只许收缩，不退化永久豁免），清空后即严格模式。

读侧检测：`domainMustNotUseQueryBuilder` / `commandHandlersMustNotUseQueryBuilder` 拦截对 `@ReadSideOnly` 类型的依赖（非写死类名）。`noBoneCoreUseCaseApiDependency` **不 freeze**（防回滚、无存量命中）。

**Freeze 建议**（2026-05-23）：`applicationMustNotDependOnInfrastructure`、仓储白名单、QueryBuilder 禁令、adapter/Handler 命名与事务规则在 **`bone-blueprint` 参考样板不 freeze**（须 0 违规）；其它应用模块对上述 #11–#17 规则 **freeze 存量**，迁移后 `allowStoreUpdate=true` 收缩基线。`noUseCase*` / `noNewDomainStore` / `noCustomBusinessException*` 继续 freeze 防回潮。**#18 不 freeze**（A 类强制，须 0 违规）。**#21 / #22 参考样板不 freeze**（须 0 违规）；存量模块接入时以 `FreezingArchRule` 登记、逐步收敛。**R8 无 freeze 基线**（新增即失败）：存量缺口用 `AggregatePureUnitTestGuard.verifyAllowingPending(...)` **待补清单**过渡（清单只许收缩，见上），不清零不封版；参考样板与无聚合模块用严格 `verify(...)`。当前待补清单状态登记于各模块 `AggregatePureUnitTestCoverageTest.PENDING`（IAM 9 / masterdata 7 / integration 5 / system 6 / generator 6 项，blueprint / notification / extension-studio / metadata-server 已清零为严格模式）。

**存量违规处理**：使用 `FreezingArchRule` 登记当前违规快照（基线 `archunit_store/`），仅拦截**新增**；迁移后基线收缩，**不允许扩张**。

```java
@ArchTest
static final ArchRule no_new_use_cases =
    FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());
```

> 首次集成或基线更新：`mvn test -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true`。详见 `bone-framework/bone-architecture-test/README.md`。

**单测**：聚合与值对象规则（无容器）；**集成测**：用例与端口（Testcontainers 等）；**跨服务**：契约测试（OpenAPI / Pact 等）。**覆盖率**：与仓库质量门禁对齐，核心域优先提高阈值。

**测试金字塔（2026-08 补充，业界共识 ~70/20/10）**：

- **目标比例**：单测 ≈ 70%（领域规则、值对象、Handler 编排逻辑）；集成测 ≈ 20%（仓储 + 用例 + 端口）；E2E/契约 ≈ 10%。
- **领域规则必须在无容器环境可测**（纯 JUnit + Mockito，不启动 Spring 上下文）：聚合状态机、不变量、值对象校验——这是可测试性的硬性要求（见 [G-1](#g-1-测试与-ciarchunit-规则集)）；若领域测试被迫依赖容器/DB，说明领域层混入了基础设施依赖（D0/D1 纯净度被破坏）。
- 跨上下文/外部集成必须有**契约测试**（E-10 ACL 强制项）：打桩外部响应验证翻译与错误语义隔离。

---

## E-12. 极简 / 轻量 / 低成本（Bone 默认心态）

| 做法 | 建议 |
|------|------|
| **CQRS** | 保留 `command/query` 分包；不默认独立读库、事件投影。 |
| **应用层** | 默认 Controller → Handler → Domain **三步**（adapter → application → domain）；**不引入** UseCase，**禁止** `application/service`（E-5.3.1 按性质分流）；满足 E-5.3.2 F1/F2 时按条件追加 `*Facade`；跨聚合编排按 E-5.3 模块 README 例外加 `*Orchestrator`。 |
| **扩展点 / ACL / MQ / RPC / 定时** | 无真实需求则不建。 |
| **战略文档** | 小模块一页纸术语表起步。 |
| **领域事件** | 无跨聚合协调时可少发。 |

**bone-blueprint** 为全特性参考样板；新建业务对齐 **E-5.2 极简包 + E-3 R1–R9 铁律 + E-5.3 应用层结构**，按需求再扩。

### E-12.1 新模块快速入门（5 步）

> 新建一个**应用 / 控制面 BFF** 模块时，按以下顺序操作即可满足本规范。复杂用例再按对应章节扩展。

1. **定边界**：在模块 `README.md` 写一页纸：上下文名称、职责一句、对外契约、数据所有权（[E-4.1](#e-41-限界上下文)）；核心术语 5–20 条对照表（[E-4.2](#e-42-通用语言)）。
2. **起包结构**：按 [E-5.2](#e-52-极简树小模块默认够用) 极简树创建 `adapter/web/`、`application/command/`、`application/query/`、`domain/{aggregate}/`、`domain/repository/`；持久化层按 [E-8.2](#e-82-持久化对象决策树) 决策树放 D1（元数据驱动）或 `infrastructure/persistence/entity/` 的 PO（遗留映射）。
3. **写第一个用例**：Controller → `CreateXxxCommandHandler` → 聚合根工厂方法 → `XxxRepository.save(...)`；命名遵守 [E-13](#e-13-命名约定)；异常按 [E-7.3](#e-73-异常与-api规范) 选择（聚合不变量用 `DomainException`，用例级失败用 `BizException`，资源不存在用 `NotFoundException`，**禁止**自建 `BusinessException`）。
4. **接 ArchUnit**：`pom.xml` 加 `bone-architecture-test` 测试依赖；复制 [附录 B.3 模板](#b3-archunit-模板bone-architecture-test) 到 `src/test/java/<module>/architecture/ArchitectureTest.java`，把包名改成本模块；首次跑 `-Darchunit.freeze.store.default.allowStoreCreation=true` 生成基线后提交 Git。
5. **持续守护**：日常 CI 不开启 `allowStoreCreation`；任何例外按 [E-0.4](#e-04-例外登记的统一形态) 按影响半径登记；每次重构后 freeze 基线**只收缩、不扩张**。

> 不在以上 5 步默认范围内的（事件发布、ACL、Orchestrator、Flow / AI）按需引入，并对应阅读 P-3.3 / E-10 / E-5.3 / E-11。

### E-12.2 AI / Agentic 生成守则

1. **先判断一致性边界，再生成结构**：任何跨聚合 / 跨进程 / 外部回调场景，先读 [P-5.4](#p-54-跨上下文一致性事件发布决策表v46按容忍度触发) 决策表确定事件与一致性方案（进程内 Spring Event / Outbox / 直接异步），再决定 Handler / Orchestrator 结构；禁止「先写结构、后补一致性」。
2. 禁止生成 `*UseCase`、`application/usecase/**`、模块自建 `*BusinessException`。
3. 领域规则放 domain：状态变更经聚合行为（反贫血，见 [E-8](#e-8-领域模型与纯净度d0--d1--d2)）；不在 Handler / Facade 内写业务规则。
4. 修改 `domain/**` 后 ArchUnit freeze 基线**只收缩、不扩张**（见 [G-1](#g-1-测试与-ciarchunit-规则集) / [附录 B.3](#b3-archunit-模板bone-architecture-test)）。
5. 跨 2+ 聚合编排须产出 `*Orchestrator` 放 `application/orchestration/`，模块 README 登记步骤 / 一致性 / 失败补偿（[E-5.3](#e-53-应用层结构强制) 模块 README 例外），不得新增 UseCase 门面。
6. 不得修改 `DomainException` / `BizException` / `InfrastructureException` 根类型语义（ADR 流程除外）。
7. 生成 `*Facade` 前须验证触发条件（[E-5.3.2](#e-532-applicationfacade-约束条件追加非默认) F1/F2）；生成时在类级 Javadoc 注明触发条件编号；禁止在 Facade 内写领域规则或直接依赖 Repository/Domain。

代码示例与适应度指标见 [ddd/07-supplements.md](./ddd/07-supplements.md)。

---

## E-13. 命名约定

### E-13.1 分层命名（应用层 vs adapter DTO）

| 层级 / 包 | 命令 | 查询 | 说明 |
|-----------|------|------|------|
| `application/command/cmd/` | `*Command` | — | **禁** `*Cmd` 类名；目录名 `cmd/` 可保留 |
| `application/query/qry/` | — | `*Query` | **禁** `*Qry` 类名；目录名 `qry/` 可保留 |
| `application/command/handler/` | — | — | 类名 **必须** `*CommandHandler` |
| `application/query/handler/` | — | — | 类名 **必须** `*QueryHandler` |
| `adapter/web/dto/request/` 或 `adapter/web/dto/` | `*Req` | `*Qry`（可选） | 与 [Bone-API-规范](./Bone-API-规范.md) §12 对齐；**仅 adapter 入参**，非 application 层 Query 对象 |
| `adapter/web/dto/response/` | — | `*Resp` | REST 出参 |

> **易混点**：`FlowPageQuery`（application）与 `ExtPointPageQry`（adapter 入参）可并存；禁止把 application 层查询类命名为 `*Qry`。

### E-13.2 其它命名

| 类型 | 示例 | 备注 |
|------|------|------|
| 领域事件 | `OrderPaidEvent` | 过去式 |
| 集成事件 | `OrderPaidIntegrationEvent` | 跨边界契约；后缀 `IntegrationEvent` |
| 编排器（例外） | `OrderRefundOrchestrator` | 仅 E-5.3 模块 README 例外允许；放 `application/orchestration/` |
| 入站门面（条件） | `OrderFacade` | 仅 E-5.3.2 F1/F2 条件下追加；放 `application/facade/`；禁止叫 `*AppService` / `*Service` |
| 写侧端口 | `OrderRepository` | E-5.5：禁 `*Store`/`*Dao`/`*Mapper` |
| 读侧端口 | `OrderReadPort` | E-9.5：列表/搜索/统计 |
| 出站 ACL | `PaymentGateway` | E-10 |
| 应用服务（受约束） | `OrderShippingService` | E-5.3.1：可承载用例级编排；禁 Controller 直注、禁承载聚合不变量、禁 `new` 领域对象、禁调聚合 setter；命名黑名单 `ApplicationService` / `OrderService` / `Common*` / `Base*` / `Business*` / `*Manager` |

---

## E-14. 修订与 Owner

- **唯一权威**：架构组；规则变更须 [E-0.3](#e-03-破坏性变更流程) 流程。
- **边界变更**：先更新术语表与第一部分相关认知，再改第二部分门禁与代码。

---

## 附录 A：与旧 Bone-Blueprint 版本号的关系

历史版本号（v7 / v9.5 / v16.3 / v24 等）仅表示过往迭代；**门禁以第二部分** E-3（铁律）、E-5～E-13 与 G-1 中与**结构、领域持久化、ACL、测试、极简、命名**相关的条文为准（**第二部分** E-1「目标」～E-2「与第一部分对齐」为 Bone 目标与对齐摘要，非逐条 CI）。产品文档中「Bone-Blueprint」可与本方案同义指称。

---

## 附录 B：模块适用性快照与不符合规范的处置

> 本附录**只描述客观分类与处置形态**，不规定时间、批次、Owner。落地排期由各模块 Maintainer 在内部跟踪。

### B.1 模块适用性快照

#### B.1.1 应用 / 控制面 BFF（完全适用 E-5.4）

| 模块 | 物理位置 |
|------|----------|
| `bone-platform/bone-iam` | bone-platform/ |
| `bone-platform/bone-masterdata` | bone-platform/ |
| `bone-platform/bone-integration` | bone-platform/ |
| `bone-platform/bone-system` | bone-platform/ |
| `bone-platform/bone-notification` | bone-platform/（含 `NotificationController` 业务 API，按 E-5.4「看 Controller」判定为应用；当前仅站内信 + Alert 告警通道，扩充中） |
| `bone-engine/bone-extension-engine/bone-extension-studio` | bone-engine/（按 E-5.4 性质判定为应用） |
| `bone-engine/studio-generator` | bone-engine/（按 E-5.4 性质判定为应用） |
| `bone-blueprint` | 根目录（参考样板） |

#### B.1.2 引擎 SDK / 框架库（按 E-3.1.1 豁免 + E-8 强制）

| 模块 | 备注 |
|------|------|
| `bone-framework/bone-core` | 提供 `com.bone.core.capability`（`@Capability`、`HandlerRegistry`）；**已删除** `UseCaseExecutor` / `@UseCase` |
| `bone-framework/bone-web` / `bone-security` / `bone-utils` / `bone-datasource` | 工具 / 基础设施类 |
| `bone-engine/bone-metadata-sdk` / `bone-metadata-server` / `bone-metadata-engine` | metadata SPI |
| `bone-engine/bone-extension-engine/bone-extension-sdk` | 业务进程内嵌 SDK |
| `bone-engine/bone-workflow`、`bone-procurement` | 引擎库 |
| `bone-sdk/*` | 客户端 SDK |

#### B.1.3 基础设施服务（依用例判定；默认豁免 E-5）

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
| **模块根包**下的 `controller/`（如 `com.bone.xxx.controller.*`，**不在** `adapter/web/controller/`） | 迁移到 `adapter/web/controller/`；按 E-5.1 重组 `dto/request\|response`、`assembler/` | `git mv` + StrReplace |
| 命名 `*Cmd` / `*Qry` | 类名重命名为 `*Command` / `*Query`；子包 `cmd/` / `qry/` 可保留作短目录名（E-5.1） | `scripts/ddd-rename-cmd-qry.py <module> --apply` |
| 模块自建 `BusinessException` / `*BusinessException` | 全部改用 `com.bone.core.exception.BizException` 或 `*BizException` 后缀（如 `MetadataEngineBizException`、`ExtensionBizException`）；删除自建 `BusinessException` 类 | 手工 + ArchUnit `noBusinessExceptionSuffix` |
| 存量 `application/service/*Service`（应用层共享逻辑） | 按 E-5.3.1 性质分流：领域规则→`domain/service`；技术横切→`domain/gateway` 端口 + `infrastructure` 实现，Handler 经端口注入；删除 `application/service` 包 | `scripts/migrate-extension-studio-service-to-application.py`（模板，改造后） |
| `Controller` 直接注入 `domain/service/*`（领域服务） | Controller 改注入对应 Handler；领域服务由 Handler 在应用层编排调用（ArchUnit #17 机器拦截） | 手工 + ArchUnit `adapterControllersMustNotDependOnDomainService` |
| 缺少 ArchUnit 守护（新模块或老模块） | 使用 `scripts/ddd-archtest-template.py <module> <root-package> [--extra-archunit ...]` 一行生成 `ArchitectureTest`，再 `allowStoreCreation=true` 生成基线 | `scripts/ddd-archtest-template.py` |

### B.3 ArchUnit 模板（`bone-architecture-test`）

> **使用说明**：
> 1. 将下方 `com.bone.iam` 替换为本模块根包（如 `com.bone.masterdata`、`com.bone.system` 等）。
> 2. 所有 `FreezingArchRule.freeze(...)` 包裹的规则需要先生成基线再提交：`mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true`。基线放在 `<module>/archunit_store/`，提交入库。
> 3. **Freeze 策略（2026-05-26）**：`application_no_infra`、`repository_methods_whitelist`、`noBoneCoreUseCaseApiDependency`、QueryBuilder 禁令 **不 freeze**（直接门禁）；`noUseCase*`、`noNewDomainStore`、`noCustomBusinessException*` **继续 freeze** 防回潮；**`adapter_no_*`（#11/#12/#17）、Handler 命名与事务规则（#13–#16）在非 `bone-blueprint` 模块首次接入时 freeze 存量**，迁移后收缩基线。见 `bone-architecture-test/README.md`。

#### B.3.1 Freeze 台账模板（违规数单调不增）

各模块在 `archunit_store/` 维护基线时，须在模块 `README.md`（或统一台账）登记每条 freeze 规则的状态：

| 规则（`BoneDddArchRules.*`） | 违规条目（现状） | 拆除条件 | 到期目标 | 上次违规数 |
|----------------------------|------------------|----------|----------|------------|
| 示例：`adapter_no_application_service` | `legacy/UserService` 2 处（直注 `application/service`） | 迁移到 Handler / `domain` 端口后清零 | 2026-12-31 | 2 |
| 示例：`repository_methods_whitelist` | 历史 `findByNameAndStatus` 复合查询 3 处 | 拆到 `*ReadPort` 或复合自然键后清零 | 2026-11-30 | 3 |

**CI 校验方式（v4.6 收紧）**：

1. **单调不增**：CI 以 `-Darchunit.freeze.store.default.allowStoreUpdate=false`（且 `refreeze=false`）跑 `mvn test`，基线违规数**只减不增**（新增违规即失败；存量清零后基线自动收缩）。
2. **到期硬失败**：台账 yaml 中**到期日 < 今天**且违规数 > 0 的条目 → **构建失败**。删除 v4.5 的「warn 或按模块排期」——不阻断就等于不存在。
3. **台账机器可读**：各模块 `archunit_store/` 下维护 `freeze-ledger.yaml`（CI 读取），README 表格作为人读视图由 yaml 生成或手工同步：

```yaml
# archunit_store/freeze-ledger.yaml
- rule: adapter_no_application_service
  violations: 2
  items: ["legacy/UserService", "legacy/OrderService"]
  removalCondition: "Controller 改注入 Handler 后清零"
  dueDate: 2026-12-31        # 到期未清零 → CI 失败
  lastRenewal: null          # 最多续期一次
```

4. **总量上限**：单模块 freeze 违规**总条目**上限 20（例外预算 5 条之外的技术债口径），超出须提交清理计划。
   > **为何设上限**：freeze 的本意是「只拦截新增」，但在无到期强制、无总量约束时，它把 N 条违规固化为永久合法状态——`bone-iam` 现存 6 条 freeze 即为活样本。freeze 应当是**有息贷款**，不是**债务赦免**。

```java
import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.iam", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // R1
    @ArchTest static final ArchRule domain_independent =
            BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest static final ArchRule application_no_infra =
            BoneDddArchRules.applicationMustNotDependOnInfrastructure();

    // R5
    @ArchTest static final ArchRule domain_no_query_builder =
            BoneDddArchRules.domainMustNotUseQueryBuilder();

    // R6
    @ArchTest static final ArchRule command_no_query_builder =
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

    // R2 + E-8（A 类强制，不 freeze：禁外层篡改聚合 setId / setTenantId）
    @ArchTest static final ArchRule aggregate_identity_immutable =
            BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity();

    // R4 + E-9.2（仓储方法名白名单）
    @ArchTest static final ArchRule repository_methods_whitelist =
            BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

    // R7 + E-5.3
    @ArchTest static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

    @ArchTest static final ArchRule no_bone_core_usecase =
            BoneDddArchRules.noBoneCoreUseCaseApiDependency();

    // E-5.5
    @ArchTest static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

    // E-7.3
    @ArchTest static final ArchRule no_custom_business_exception =
            FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

    @ArchTest static final ArchRule no_business_exception_suffix =
            FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

    // R7 + E-6 + E-13（参考样板 bone-blueprint：以下 7 条不 freeze；其它模块 freeze 存量）
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

**R8 聚合纯单测门禁（独立测试类，不可并入 `ArchitectureTest`）**：`ArchitectureTest` 的 `@AnalyzeClasses` 带 `ImportOption.DoNotIncludeTests`，看不到测试类，故 R8 另建一个测试类承载。两种形态：

```java
// 形态一：严格模式（参考样板 / 无聚合或已全覆盖的模块）
class AggregatePureUnitTestCoverageTest {

    @Test
    void everyAggregateRootHasAPureUnitTest() {
        AggregatePureUnitTestGuard.verify("com.bone.iam");
    }
}

// 形态二：存量待补清单过渡（清单只许收缩，补一个删一行；清空即改回形态一）
class AggregatePureUnitTestCoverageTest {

    private static final java.util.Set<String> PENDING =
        java.util.Set.of("Account", "Role", "Tenant");

    @Test
    void everyAggregateRootHasAPureUnitTest() {
        AggregatePureUnitTestGuard.verifyAllowingPending(PENDING, "com.bone.iam");
    }
}
```

判定五条全满足才算通过：**① 同名 `*Test` 类存在**；**② 至少 1 个 `@Test` 方法**（堵住空类绕过）；**③ 无容器注解**（`@SpringBootTest` / `@DataJpaTest` / `@ExtendWith(SpringExtension.class)` 等）；**④ 调用了聚合的行为方法**（getter 以外的方法调用）；**⑤ 含断言或异常期望**（堵住 `assertEquals(1, 1)` 式空测试）。仅统计 `..domain..` 下的**具体**聚合根——`..domain.outbox..` 等基础设施持久化记录（如 `OrderOutboxRecord`）虽继承 `AggregateRoot`，属技术对象不计入。清单内聚合若已存在合规纯单测会**报错要求移除**（Guard 内置，防止永久豁免）。参考实现见 `bone-blueprint`（`AggregatePureUnitTestCoverageTest`，严格模式，全绿）。

**Freezing 基线**：各模块根目录 `archunit_store/`，须提交 Git。

| 场景 | 命令 |
|------|------|
| 首次生成 | `mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreCreation=true` |
| **收缩基线**（存量违规已消除） | `mvn test -pl <module> -Dtest=ArchitectureTest -Darchunit.freeze.store.default.allowStoreUpdate=true` |
| 全量覆盖快照（慎用） | `-Darchunit.freeze.store.default.refreeze=true`（见 `bone-architecture-test/README.md`） |

详见 `bone-framework/bone-architecture-test/README.md`。

本文结束。
