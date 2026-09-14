# 设计：Bone DDD 规范 v4.4 → v4.5 收敛

## Context

动机见 `proposal.md`。本节只记录** shaping the approach 的现状与约束**。

**现状快照（实测）**

| 项 | 值 |
|---|---|
| 规范主文档 | `doc/architecture/Bone-DDD-最终实践方案.md`，1192 行，v4.4 |
| 现状铁律 | §12.1 P0 七条（CI/ArchUnit 硬门禁） |
| 共享规则库 | `bone-framework/bone-architecture-test/.../BoneDddArchRules.java`，390 行，**20 个规则方法** |
| 适用模块 | 8 个应用 / 控制面 BFF 模块（附录 B.1.1） |
| 存量治理 | `FreezingArchRule` + `archunit_store/` 基线，只收缩不扩张 |
| 参考样板 | `bone-blueprint`，136 测试全绿 |

**平台级硬约束（不可绕过，决定了若干取舍）**

1. 持久化唯一方案为 `bone-metadata-sdk`（AGENTS.md §11.10），禁止 JPA/Hibernate/MyBatis。
2. `AggregateRoot.setId` 与 `TenantAggregateRoot.setTenantId` 为 **public**，属 SDK 反射回填与 `Tenantable` 契约，全平台 167+ 处依赖，**不可降级为 protected**。
3. SDK `Repository.findById` 当前返回**裸类型**（未找到为 `null`），非 `Optional`。
4. `@GeneratedValue(strategy = DISTRIBUTED_ID)` 由 SDK 在 `insert`/`save` 时生成并回填，应用层不预分配。
5. Java 17（非 21）；`mvn spotless:apply`（Google Java Format）绑定 validate 阶段。

## Goals / Non-Goals

**Goals**

- 给出 v4.5 铁律 8 条的**判定方式映射**（哪条由 ArchUnit 判定、哪条只能 CR）。
- 给出规范文档的**三层重构方案**与统一编号方案。
- 给出 `BoneDddArchRules` 20 条规则的**分类处置**（保留 / 改造 / 降级）。
- 给出 M1 / M2 / M3 三批落地顺序、每批验收口径与回滚方式。
- 给出 M3 所需的 **ADR 清单**（本阶段只出草稿）。

**Non-Goals**

- 不修改任何业务模块源码、不改 `bone-core` 基类签名、不改 SDK 运行时行为。
- 不迁移存量违规代码（本阶段为 design-only）。
- 不引入新框架、不升级 Java 版本。
- 不重做 `bone-blueprint` 已通过的 136 个测试。

## Decisions

### D1 — 适应度函数换轨：从「命名合规」到「聚合行为可测」

**决定**：新增铁律 P2「每个聚合根必须有一个纯单测（主状态机 + ≥1 条拒绝路径，无容器/无 DB/无 MQ）」，并把现有命名类规则降为 Checkstyle warn。

**备选方案**

| 方案 | 取舍 |
|---|---|
| A. 保持现状（靠 CR 反贫血） | 已被证伪：类型系统生成 setter，CR 挡不住进度压力 |
| B. PMD / SpotBugs 自定义规则 | 可判定 setter 调用，但**测不到「Handler 里写领域规则」**这类语义问题 |
| **C. ArchUnit 存在性检查 + 纯单测约定** ✅ | 可机器判定「有/没有测试」，配合测试内容约定即可覆盖主状态机与拒绝路径；成本低、无新依赖 |

**落地形态（2026-08-30 已实现）**：新增 `AggregatePureUnitTestGuard.verify("<根包>")`（`bone-architecture-test`）——扫描 `..domain..` 下继承 `AggregateRoot`/`TenantAggregateRoot` 的**具体**类，要求存在同名 `*Test`，且该类**至少 1 个 `@Test` 方法**、**无容器注解**。
> **实现形态修正**：原设计写为 `BoneDddArchRules.aggregateRootsShouldHavePureUnitTest()` 这一 `ArchRule`，**不可行**——ArchUnit 的 `@AnalyzeClasses` 默认带 `ImportOption.DoNotIncludeTests`，`ArchitectureTest` 看不到测试类。故改为独立守卫类，用 `ClassFileImporter` 分别导入主代码（`DoNotIncludeTests`）与测试代码（`OnlyIncludeTests`）两个类集合交叉比对，由模块内独立测试类承载。

### D2 — 三处公开改口（规范语义变更）

| 现在 | 改为 | 理由 |
|---|---|---|
| §17 称 D1 为「领域纯净度分级」 | **「可持久化充血模型」（Active Record 风格聚合）**；D0 只留给无表的值对象与领域服务 | 打 `@Table`/`@Id` 就是表映射。承认它，才能诚实定义退出条件（出现第二消费者 / schema 与模型分叉 → 上 PO + Converter） |
| §10.2 称 framework + metadata-sdk 为「共享内核 / Conformist」 | **「厚共享内核的模块化单体」**，优化目标是平台内一致开发体验 | 所有上下文共享同一套基类/异常/查询构建器/元数据注解，本就不是可独立演进的 BC |
| §14.4 把 `bone-gateway` 归为「应用 / 控制面 BFF」 | **基础设施服务，豁免 §14** | BFF 天然含聚合编排，与 §10.2「纯路由、无业务语义」直接矛盾 |

### D3 — 仓储按「返回类型」判定，而非扫方法名 `And/Or`

**决定**：写侧仓储方法返回值只能是 **聚合根 / `Optional<聚合根>` / `boolean` / `void`**；允许 `findByTenantIdAndCode` 这类复合自然键；方法名禁持久化词汇（`Sql`/`Criteria`/`Dynamic`/`Query`）；参数 ≤3 作为 CR 指南（不做门禁）。

**备选方案**：(A) 维持方法名白名单 → 已证伪，逼出假 `*ReadPort`、逼人把租户藏进 `ThreadLocal`；(B) 完全放开 → 仓储会重新长成报表层。**返回类型是唯一既能挡住投影/分页、又能机器判定的维度**。

**落地形态**：改造 `domainRepositoriesShouldOnlyDeclareWhitelistedMethods()`，判定从「方法名匹配」改为「返回类型不在允许集合内即违规」。

### D4 — 应用层收敛：补齐编排真空，删掉魔法数

| 项 | v4.4 | v4.5 | 理由 |
|---|---|---|---|
| `*Orchestrator` | ADR 例外（跨 2+ 聚合） | **模块 README 三级例外**（写清步骤 / 一致性 / 失败补偿） | ADR 门槛过高会逼人把跨聚合写回单 Handler + 同一事务，正好违反 §5.3 |
| 复杂流程命名 | 无合法命名 | 允许 `{语义}ApplicationService`（如 `CheckoutApplicationService`）放 `application/orchestration/` | DDD 的 Application Service 是合法概念；真正该禁的是无语义 Service |
| Facade F3「>7 个 Handler」 | 硬阈值 | **删除**，只保留 F1（多入站适配器）/ F2（对外 Client SDK） | 数字无架构意义，且鼓励用 Facade 给 God Controller 遮丑 |
| 禁用清单 | 禁 `application/service` 包 | 保留禁包 + 新增**命名黑名单**（`*Manager`、`Common*`、`Base*`、`Business*`）+ **行为规则**（不得操作仓储、不得改聚合状态） | 位置可机器判定，语义质量交 CR |

### D5 — 读路径收敛为「两条 + 一个例外」

```
写模型加载 → Repository（身份 / 自然键，可复合）
查询       → QueryHandler（QueryBuilder 或直接 SQL）
例外       → *ReadPort，仅当 QueryBuilder 物理做不到（内存仓储 / 混合存储 / 专用读库）
```

同时明确：**CommandHandler 允许注入 `*ReadPort` 做写前唯一性校验**（补上「仓储不能多条件 + Handler 禁 QueryBuilder」造成的查询真空）；简单 CRUD 的 QueryHandler 允许直接 `findById`，不必为「没走 QueryBuilder」产生违规感。

**边界**：`*ReadPort` 不得作为仓储白名单的泄压阀——若只是为了绕过 D3，应改回 Repository 加复合自然键方法。

### D6 — 事件与一致性：补齐失败语义，Outbox 按可靠性触发

| 场景 | 方案 |
|---|---|
| 进程内领域事件订阅 | Spring Event（`AFTER_COMMIT`） |
| **跨进程 / 跨服务发布** | **必须 Outbox**（与业务数据同本地事务落库 + 至少一次投递 + 消费端按 `eventId` 幂等） |
| 非关键通知 | 直接异步发送 |
| 本地事件 + 消费端幂等 | **平台能力**，禁止每个 Handler 各自实现 |

回答 v4.4 未定义的问题：`save` 成功而 `publishFrom` 失败 → 事件须在 Outbox 中（同事务）而非依赖发布调用成功；`AFTER_COMMIT` 订阅失败 → 重试 + 死信；无 Outbox 的双写 → 跨进程场景禁止。

### D7 — 异常收敛为 3 个根 + 修掉 API 陷阱

`DomainException`（聚合不变量）/ `BizException`（用例级）/ `InfrastructureException`（技术故障）为唯三根类；`NotFoundException`、`IdempotentException`、`InvalidRequestException`、`SystemException`、`ServiceException`、两把锁异常降级为**子类或错误码**，不再作为平级根类。

**同时**：`ApiResponse.success(String)` 单参重载与泛型 `success(T)` 冲突（已有 5 处数据劫持事故）→ **改 API**（删除或改名 `successMessage`），不再靠注释当门禁。

### D8 — 多租户参数化，异步路径显式传参

- 租户作为**显式参数**进入仓储方法（沿用样板 `findByIdInTenant` 的正确方向）。
- `TenantContext` 只在适配层解析一次；**Job、MQ 消费、`AFTER_COMMIT` 订阅等异步路径禁止依赖 ThreadLocal 传递租户**（经典串租户事故源）。
- 超管跨租户查询用**独立端口**（如 `findByIdIgnoringTenant`），而非同一 `findById` 的隐式旁路。
- 判定规则：租户是**领域概念**（计费、隔离规则）→ 进聚合；只是**数据隔离**→ 留基础设施层。

### D9 — 跨上下文规则改用 FQN 正则，去掉空命中

现状 §10.4 用 `resideInAPackage("com.bone.iam.domain..")` + `allowEmptyShould(true)`：对方类不在 classpath 时规则空命中即通过，**等于没有守护**。

改为按**全限定名匹配**（`com\.bone\.(?!<self>)[a-z0-9]+\.domain\..*`），匹配的是字节码中记录的引用，与 classpath 无关；并**去掉 `allowEmptyShould(true)`**，空匹配视为配置错误。

### D10 — 文档三层重构与统一编号

```
P-  原则（战略 + 战术 + 架构，不含任何 Bone 实现词）
E-  工程规范（包结构、命名、应用层形态、持久化折中、多租户、横向治理）
G-  门禁（ArchUnit 规则集、Checkstyle、freeze 台账模板）
```

- 消除「第一部分 §10 上下文映射」与「第二部分 §10 目标」的双编号歧义。
- **外置**：支付样板 → `doc/architecture/ddd/samples/payment.md`；ArchUnit 模板 → `bone-architecture-test/README.md`（已有）；迁移剧本 → `scripts/`。
- 新增文首**一页纸速览**（8 条铁律 + 能力开关表 L0–L3 + 按角色阅读路径），解决「1192 行读不完」。
- freeze 台账字段扩展：**违规条目 + 拆除条件 + 到期目标 + 上次违规数**，CI 校验违规数单调不增。
- 删除 COLA 术语对照（规范不该与 COLA 抢术语）。

### D11 — ID 契约：身份在构造期确定（固化既有模式 + ADR-0019）

**评审修订（v4.5 收敛第二论）**：原「第一步新增 `domain/gateway/*IdGenerator` 端口」经核对属单一实现的纯仪式，违反 D4「应用层收敛」精神，删除。现有设施已满足「身份在构造期确定」：

- `DistributedIdGenerator`（`bone-core/util` 静态工具，含 `generateLongId()`）已被 blueprint 应用层使用：`long orderId = DistributedIdGenerator.generateLongId(); Order.create(orderId, ...)`——**id 作为聚合构造参数传入，构造后即有稳定身份**，领域事件可安全携带 id；
- `IdGenerator`（`bone-core/domain/id`）+ `DefaultIdGenerator`（SDK `sql/executor`）是 SDK 保存时生成 id 的 **SPI**，**不是**给应用层的端口，不应在应用层再包一层 `domain/gateway/*IdGenerator`。

据此 D11 收敛为两步：

- **第一步（不新增端口，固化 + 测试锁定）**：确认并固化「工厂/Handler 调用 `DistributedIdGenerator.generateLongId()` 生成 id、作为聚合构造参数传入」的既有模式；在 `bone-blueprint` 增加断言测试，锁定「聚合构造后即刻持有非空稳定 id」不变量（既有 136 测试仍全绿）。
- **第二步（ADR-0019，SDK 行为修正）**：`BaseRepository.insert()` 当前在非 IDENTITY 分支**无条件** `generateId + setEntityId`（第 164-167 行），会覆盖调用方已传入的非空 id；而 `batchInsert`（第 196 行）与 `save` 依赖的 `ensureIdInitialized`（第 658 行）均已 `if (id == null)`。**改为一致地尊重非空 id——仅 id 为空时才生成。**

> **⚠️ 二次复核修正（依赖关系写反，必须纠正）**：经 `CreateOrderCommandHandler` 与 `BaseRepository` 联合取证，上述两步**并非独立**——「第一步不依赖 SDK 改造」的说法**不成立**。
>
> 1. 因 `insert()` 无条件覆盖，应用层预分配并传入聚合构造的 id 会在 `insert`/`save` 时被 **SDK 静默替换为新值**。故「聚合构造后即持有**稳定** id」在当前 SDK 下并不成立——该 id 仅内存态有效，持久化后即被改写（原表述「非空稳定 id」属过誉，已修正）。
> 2. **`ADR-0019` 不是可选的第二步，而是「身份在构造期确定」的先决条件。** 正确顺序是：**先**落地 ADR-0019，**后**固化「构造期传入 id」的模式与测试锁定（此时才可能断言「构造期 id == 持久化后 id」）。
> 3. 在 ADR-0019 落地前，主规范 §18.1「应用层不预分配 id、工厂 `id` 参数传 `null`」才是与当前 SDK 行为一致的契约；blueprint 现有 `DistributedIdGenerator.generateLongId()` + 传 id 的写法**超前于 SDK 能力**，属待对齐项（ADR-0019 落地后即转为合规样板）。
> 4. **连带隐患（复核新发现）**：`CreateOrderCommandHandler` 以预分配 `orderId` 构造 `Order`，并以同一 `orderId` 作为 `OrderItem` 的 `orderId` 外键；而 `insert` 会改写 `Order` 的实际落库 id。若明细持久化上线而 ADR-0019 未落地，明细外键将指向不存在的订单。**这是 ADR-0019 的真实价值——不只是风格一致，而是数据正确性前提。**

**ADR-0019 影响面（实测，低风险）**：全平台 `insert()` 直接调用点仅 **20 处**（masterdata 3 / extension-studio 9 / studio-generator 2 / metadata-server 3），主体写路径走 `save`（132 处）/ `batchInsert`（4 处）；**未发现任何「先 set 非空 id 再 insert 依赖覆盖」的反模式**；受影响实体主键策略全部为 `DISTRIBUTED_ID`（IDENTITY 仅 SDK 内部 1 处，UUID/SEQUENCE 生产 0 处）。故该修复对现有 null-id 调用方**零行为变化**、仅新增对预置 id 的尊重，可安全落地。

**收益（须待 ADR-0019 落地后）**：可删除 §18.1 中「保留外部 id 需在模块 README 登记」的例外（导入场景自然成立）。**ADR-0019 落地前该例外必须保留**——否则等于在规范中宣称「可保留外部 id」，而 SDK 实际仍会覆盖。

### D12 — 反贫血机制：弱约束代码形式、强约束行为（对照 Evans / Vernon / IDDD / Spring DDD 指南）

**评审修订（v4.5 收敛第二论）**：原 D12 含两处绝对化表述（强制 `reconstitute()` + 调用点 ArchUnit 作为硬门禁），已按业界共识「**弱约束代码形式、强约束行为**」收敛。实测影响面见下。

受约束 2 限制（public setter 是 SDK 契约，167+ 依赖），**不能**通过「禁用 setter 声明」实现；但实测 `AggregateRoot` / `TenantAggregateRoot` **已仅用 `@Getter`（非 `@Data`）**，6/8 模块 domain 实体已改用 `@Getter`，且 SDK 字段回填走 `ReflectionUtil.setFieldValue` 字段级反射（`BaseRepository.java:674`，不依赖 setter）——去 `@Data` 不影响持久化。

按杠杆排序落实（高杠杆优先、可机器判定者为主判据）：

1. **（主判据·存在性门禁）行为方法驱动状态变更 + 不变量在构造/行为时校验**：由 **P2 聚合纯单测**（R1，独立 `DomainModelTest`）做机器门禁。**措辞校正**：P2 当前只判定「同名 `*Test` 类是否存在」，空测试类即可绕过，故称其为「真门禁」属过誉——它实为**存在性门禁**，内容质量靠 CR 检查清单与测试模板。**低成本强化（建议采纳）**：`beCoveredByPureUnitTest` 增加「该测试类至少含 1 个 `@Test` 方法」的判定，堵住空类绕过（仍不保证断言质量，但消除最廉价的绕过路径）。这是反贫血的**主判据，不依赖代码形态**。
2. **（强约束）身份早期确定**：id 是构造参数、非 setter 后补——由 D11 固化（blueprint 已满足），与 P2 联动保证聚合构造后即有稳定身份。
3. **（弱约束·基础设施细节）ORM 恢复不经由 setter**：重建由受保护无参构造器 + SDK `ReflectionUtil.setFieldValue` 字段级映射完成（已有事实），属基础设施细节，**不要求业务层暴露 `reconstitute()` 入口**。
4. **（去掉）强制 `reconstitute()`，以及把现有调用点规则「扩展为外层禁调所有 `set*`」的提案**：前者是纯仪式；后者**测不到「是否绕过行为」**，只会逼出 `create()` + `reconstitute()` 双入口冗余。**但既有 `outerLayersMustNotMutateAggregateIdentity()` 本身必须保留为硬门禁**——实测它在 M2 分类中属 **A 类「保留强制（不 freeze）」**（被降为可选的仅为 4 条命名/事务规则），且它守护聚合身份与租户归属（隔离安全），不可弱化。本次放弃的仅是其「扩展为禁所有 `set*`」的提案。
5. **（弱约束·有界小任务）收敛 `@Data`**：实测聚合根基类 `AggregateRoot` / `TenantAggregateRoot` **已仅用 `@Getter`**（从未使用 `@Data`），故「基类去 `@Data`」本身是**已完成项**；残余仅 `studio-generator` 约 5 个 domain 类。**须与「167+ public setter 依赖」严格区分**：后者是 `setId` / `setTenantId` 的**可见性契约**（SDK 反射回填 + `Tenantable` 所需，不可降级），与 `@Data` 无关；前者只是 5 个类的有界替换任务。列为后续专项（本 change 不推进），但**不得再以「167+ 依赖的工程改造」为由高估其成本**。

**M3 落地形态**：D12 不再产出「强制 `reconstitute()`」类 ADR；`outerLayersMustNotMutateAggregateIdentity()` 维持现状（风格约束），`studio-generator` 的 `@Data` 收敛列入后续专项任务。

### D13 — 增加「何时不要 DDD」的出口

纯 CRUD、无演进压力的支撑域（System 配置、Notification 记录、字典表等）**允许 Transaction Script + 表实体**，不强制 Handler / 聚合 / ReadPort 三件套。§1 已有此意，但第二部分门禁实际取消了它——在 §14.4 显式增加「CRUD 支撑域豁免档」。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| 放宽仓储方法名 → 仓储重新长成报表层 | 返回类型门禁 + 参数 ≤3 CR 指南 + 禁持久化词汇入方法名 |
| 允许 `{语义}ApplicationService` → 形态泛滥 | 位置约束（`application/orchestration/`）+ 命名黑名单 + 行为规则（禁操作仓储 / 禁改聚合状态） |
| 铁律从「全量命名门禁」收缩 → 治理松弛 | P2 聚合纯单测补位 + freeze 台账到期日 + 违规数单调不增 |
| D3/D9 规则改造误伤存量 | 先在 `bone-blueprint`（0 违规）验证，再在其它模块 freeze 存量后切换 |
| D11/SK ADR-0019（insert 尊重非空 id）→ 原评估「破坏存量写入」经实测降级为**低风险**：insert 调用点仅 20 处且零反模式，修复对 null-id 调用方零行为变化；按「blueprint 先行验证 + 全平台 `mvn clean install` 回归」即可，无需灰度模块清单 |
| **【复核新发现】blueprint 明细从未落库**：全库检索 `t_order_item` 仅命中实体注解 / DDL / 读 SQL / README，**无任何写入方**（无 `OrderItemRepository`、SDK 无级联、`t_order` 无 `items` 列）；叠加 `insert` 改写 id，明细外键会指向不存在的订单 | 超出本 change（design-only）范围，**本次不修复**；已在 D11 登记。建议另立 change：补 `OrderItemRepository` + 明细持久化，且**必须排在 ADR-0019 之后**（否则外键错位） |
| D10 重编编号 → 交叉引用断裂 | 脚本批量替换 + 引用清单核对（AGENTS.md / `ddd/` 分册 / ADR-0011~13 / 各模块 README / `bone-architecture-test/README.md`） |
| AI 生成器仍优先满足后缀与包路径 | 命名规则降级为 warn + 更新 §22.2 生成守则（先问一致性边界，再生成结构） |

## Migration Plan

| 批次 | 内容 | 验收 | 回滚 |
|---|---|---|---|
| **M1** 文档重构 | D2 三处改口、D4 应用层收敛、D10 文档三层与编号、D13 CRUD 出口、freeze 台账字段、文首一页纸速览 | 文档内部零矛盾（含 §14.3.1 ↔ §23.2）；全部交叉引用可解析 | `git revert`（纯文档） |
| **M2** 门禁重配 | D1 新增聚合纯单测规则、D3 返回类型判定、D9 FQN 正则、D5 读路径收敛、命名/事务规则降级为可选 | `mvn clean install` 全绿；`bone-blueprint` 0 违规；规则处置为 **19 条强制 + 4 条可选**（详见 Design Doc 第 2 节） | 规则逐个 freeze 开关；`archunit_store/` 基线回退 |
| **M3** 平台能力（仅出 ADR 草稿） | D11 ID 契约、D12 反贫血机制、D6 Outbox 平台化、外部回调验签端口化 | 每项有独立 ADR（动机 / 影响模块 / 迁移路径 / 回滚） | 按 ADR 灰度；blueprint 先行 |

**ADR 清单（M3 前置）**

1. `ADR-0019` ID 生成契约：SDK 尊重非空 id
2. `ADR-0020` 反贫血机制：弱约束代码形式、强约束行为的影响面与分批方案（P2 纯单测为主判据；`outerLayersMustNotMutateAggregateIdentity` 维持风格约束；`studio-generator` `@Data` 收敛专项）
3. `ADR-0021` Outbox 与消费端幂等平台化
4. `ADR-0022` 外部回调验签端口化（推广自支付样板）

## Open Questions

1. SDK 改为「尊重非空 id」可行性——**已实测确认可行（低风险）**：insert 调用点全平台仅 20 处、零「预置 id 依赖覆盖」反模式、受影响实体全部 `DISTRIBUTED_ID`，修复对 null-id 调用方零行为变化。无需独立 spike，ADR-0019 可直接起草（SDK 代码改动延后至执行该 ADR 的后续 change）。
2. ArchUnit 按返回类型判定仓储方法是否存在边界 case（如 `saveAll`/`removeAll`）——需 POC。
3. 聚合纯单测规则在存量模块如何分批启用（建议 blueprint 先行，其余 freeze 后切换）——不影响本设计。
4. Java 17 是否升级 21——独立技术决策，不在本 change 范围。
