# ADR-0024：DDD v5.0 规则语义校准与文档分册

| 项 | 内容 |
|----|------|
| **状态** | 已被 [ADR-0026](./0026-ddd-single-document-consolidation.md) 部分取代（规则语义保留，分册结构废止） |
| **日期** | 2026-09-10 |
| **决策者** | 架构师批准（本次评审） |
| **关联** | [Bone-DDD v5.0](../Bone-DDD-最终实践方案.md)、[ADR-0023](./0023-core-domain-smart-metadata.md) |

---

## 背景

Bone-DDD v4.9 在战略设计、聚合、依赖倒置、ACL、CQRS 和架构门禁方面方向正确，但存在四类问题：

1. Context Map 混入 framework、SDK、Gateway 等技术模块，并误用 Shared Kernel、Conformist 等上下文关系术语。
2. 一些 Bone 工程策略被表述为 DDD 行业铁律，例如限制核心域数量、用 Repository 类型近似事务边界、把 Handler 设为唯一应用入口。
3. ApplicationService、ReadPort、D0/D1 和 Outbox 条文存在内部矛盾或锁死具体实现。
4. 1500 多行主文档混合原则、规范、门禁、迁移台账和版本历史，重复条文容易漂移。

## 决策

### 1. 文档结构

- 主文档重写为原则、Bone 决策和门禁口径的规范入口。
- Context Map、应用与一致性、门禁、迁移台账和版本历史分别进入 `doc/architecture/ddd/` 分册。
- 主文档保留仓内关键旧标题作为兼容入口。

### 2. 战略与边界

- Bone 当前仍只认定 Metadata 为核心域。
- 删除“DDD 通常只能有一个核心域”的行业断言。
- 一个模块只能属于一个上下文；一个上下文可以包含多个内聚模块。
- Context Map 与技术模块依赖图分离。

### 3. 应用用例

- `CommandHandler`、`QueryHandler`、语义化 `ApplicationService` 都可以直接作为应用用例边界。
- 一个用例只能选择一个边界构件，禁止 Handler/ApplicationService 一对一套娃。
- Orchestrator 用于跨步骤、重试或补偿流程。
- Facade 只用于多个入站适配器复用或稳定 Client SDK 契约。

### 4. CQRS、事务与持久化

- 新查询端口位于 application，QueryBuilder 位于 infrastructure。
- “一事务一聚合”修订为“默认一个事务修改一个聚合实例”。
- 现有按 Repository 类型扫描降为 Advisory，不再声称证明事务语义。
- 删除“跨聚合 ID 引用必须 D0+PO”；PO 分离由模型与存储的真实分歧触发。

### 5. 可靠性、并发与错误

- 可靠事件发布先定义原子性、投递、幂等、顺序和恢复保证；Outbox 是默认实现，不是唯一实现。
- 并发写必须声明策略；乐观锁是默认候选，不是唯一合法机制。
- 允许模块定义项目异常根下的语义子类。
- 领域事件与 Integration Event Envelope 分离。

### 6. 门禁

- 规则分为 Hard gate、Semantic review、Advisory 和 Planned。
- `oneAggregatePerTransaction()` 与 `AggregatePureUnitTestGuard` 明确为启发式/卫生检查。
- 删除固定测试类型配比。
- Java ArchUnit 规则本 ADR 不修改；不符合新语义的规则先标记“待调整”。

## 理由

- DDD 的价值在语言、边界和不变量，不在固定后缀或目录。
- 静态分析适合证明依赖关系，不适合证明聚合边界、领域行为和事务必要性。
- 能力契约比锁定单一技术实现更能支持平台演进。
- 一个用例一个边界比 Handler/Service 套层更简单，也更符合 Application Service 的经典职责。
- 分册降低主文档认知负担，并给每类规则建立唯一真源。

## 后果

### 正面

- Context Map 与 DDD 术语更准确。
- 新代码不再被错误触发条件引导到多余结构。
- 门禁声明与真实证明能力一致。
- 主文档可在较短时间内阅读和评审。
- 可靠性与并发方案可以在保证不变的前提下演进。

### 负面 / 风险

- 当前 ArchUnit 中禁止 Controller 注入 ApplicationService 的规则与 v5.0 不一致。
- 存量 `domain/gateway/*ReadPort` 需要渐进迁移。
- 一些模块 README 的命名例外需重新表述为风格差异。
- 团队需要适应“工程策略不等于 DDD 原则”的分级。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| 保留 v4.9，仅追加勘误 | 无法消除重复、内部矛盾和概念混图 |
| 完全取消 ArchUnit DDD 门禁 | 依赖方向等结构属性仍适合机器守护 |
| 强制统一为 Handler，删除 ApplicationService | 把合法应用用例构件变成隐藏实现层，制造套娃 |
| 保留 Outbox/乐观锁唯一实现 | 把业务保证错误绑定到当前技术 |

## 合规与迁移

1. 本次同步主文档、分册、ADR-0023、glossary、AGENTS 和直接引用。
2. Java 业务代码、ArchUnit 实现、DDL、OpenAPI 和 CI 本轮不修改。
3. `adapterControllersMustNotDependOnApplicationService`、R9 分级、QueryPort 新位置和异常规则进入迁移台账。
4. 新规则必须先有正反 fixture，再从 blueprint 推广到存量模块。
5. 历史 `.specstory`、memory 与已归档 OpenSpec 不追溯修改。
