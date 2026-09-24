# ADR-0028：Application Service First + Selective CQRS

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-09-14 |
| **决策者** | 架构师批准 |
| **关联** | [Bone-DDD v5.2.0](../Bone-DDD-最终实践方案.md)、[ADR-0025](./0025-ddd-v5-0-2-implementation-alignment.md)、[ADR-0026](./0026-ddd-single-document-consolidation.md) |
| **取代** | 主文档 [E-3 应用用例](#application-use-case-boundary) 与 [E-4.2 读侧](#cqrs-port-location) 中“默认一刀切 CQRS”的决策口径 |

---

## 背景

Bone Blueprint 是会被大量业务模块复制的参考实现与工程模板。其默认结构会按模块数量被放大。当前的默认口径（写用例默认 `*CommandHandler`、读用例默认 `*QueryHandler`）会把每个简单 CRUD 展开成 `Command / Query / Handler` 三件套，导致：

1. 简单业务生成 15～25 个类，架构复杂度大于业务复杂度。
2. 大量 `Command → Handler → 仅透传 ApplicationService` 的仪式化委派，属于 Ceremonial/仪式感架构。
3. 新人面对 `CreateXXXCommand / CreateXXXHandler / XXXApplicationService` 不知道该调用谁。
4. AI Coding 在大量重复抽象上更容易复制错误、消耗上下文。

CQRS 的本质是**读写职责分离**（Command 可改状态、Query 不改状态），而不是“Command/Query/Handler 三件套”。全模块机械生成三件套只是 CQRS 的一种实现风格，不是 CQRS 本身。

## 决策

Bone 的默认应用架构从“Everything CQRS”调整为：

> **Application Service First + Selective CQRS + Complexity-driven DDD**

### 1. Application Service 是默认应用入站边界

简单用例默认形态：

```text
Controller → ApplicationService → Aggregate/Rafactory → Repository
```

查询默认形态（简单读）：

```text
Controller → ApplicationService.get()/page() → Repository
```

没有 CommandHandler、QueryHandler，只要满足“写不改状态”以外的基本纪律（见下），即符合 CQRS 思想。

### 2. 复杂度驱动，而非套模板

| 层级 | 适用 | 读侧 | 写侧 |
|------|------|------|------|
| **L1 简单** | CRUD / 后台 / 配置 / 字典 / 简单主数据 | `ApplicationService` 直查 | `ApplicationService` |
| **L2 中复杂度** | 订单 / 状态流转 / 审批 / 库存 / 支付 | `QueryService`（读模型与 domain 分歧时） | `Command`（写意图需显式契约 / 命令数多 / 多入口） |
| **L3 高复杂度** | 分析 / 报表 / 看板 / 搜索 / 跨聚合流程 | `Query → QueryHandler → Read DB` 读模型 | `Command → CommandHandler`（独立路由 / 生命周期 / 多入口统一） |

目录按需生长，不预生成全套。

### 3. 六条规则（Rule 1–6）

1. **Application Service 是默认应用入口。**
2. **Command 在写意图需要显式契约时引入。**（写操作开始有明确 intent，或命令数量多）
3. **Command Handler 在命令需要独立路由、生命周期或执行策略时引入。**（e.g. `Command → Message`、异步/重试/DLQ、多个入口统一执行）
4. **Query Service 在读模型与 domain 模型分歧时引入。**
5. **专用 Read Model 仅在读查询的伸缩性或复杂度需要时才引入。**（e.g. 分析/报表/搜索）
6. **CQRS 是一个频谱，不是开关。**`Light CQRS → Selective CQRS → Full CQRS` 按需取用。

### 4. 命令异步化必须走 Outbox + 消费端幂等

当命令需要队列 / 异步 / 多入口（REST/Kafka/Scheduler）执行时，**不得**在 `@Transactional` 调用方内 inline 持有长事务。正确形态：

```text
同事务写 Outbox → 消费者/任务端执行 → 幂等消费
```

Handler 位于**消费端/任务执行端**，而非 REST 调用链。这与 [ADR-0021](./0021-outbox-and-consumer-idempotency-platformization.md) 保持一致：业务写与发布记录必须在同一事务，远程写不能出现在本地事务内。

### 5. 门禁口径：构件事项降为裁量（Advisory）

“用哪种用例构件”是复杂度裁量判断，ArchUnit/HC 无法机械判定。因此：

- **降为裁量（CR 可豁免）**：用例构件呈现（Handler / ApplicationService / QueryService 的选型）、`rule #19`（QueryHandler 仅依赖 QueryPort）的默认性。
- **保留硬门禁（不变量）**：分层依赖、domain 零框架依赖、租户/审计、`ApiResponse<T>` / `PageResult<T>`、`bone-metadata-sdk` 唯一持久化、事务边界（写事务在最外层写用例）、聚合根不变量。

已存在的 `*Handler` / `*ApplicationService` 只要满足不变量即可保留；新增代码优先参考本节判据，而非强制生成 Handler。

## 理由

- 简单问题保持简单，复杂问题再引入相应复杂度；架构让复杂度只在真正需要的地方出现。
- 降低认知负担：默认 `XXApplicationService` 即业务入口，比面对一整套 Command/Query/Handler 更易理解。
- 减少无意义代码：消除 `Command → Handler → 透传 ApplicationService` 的仪式化委派。
- 架构可演进：`ApplicationService → +Command → +Handler → +CommandBus` 顺时针，不一开始顶配。
- 适合 AI Coding / Blueprint 放大场景：默认减少重复抽象与上下文消耗。

## 后果

### 正面

- Blueprint 默认形态更收敛、更易复制与维护。
- CQRS 语义（读写职责分离）保留，但不再以类文件数量翻倍实现。
- 硬门禁聚焦不可违背的不变量，误报减少。

### 负面 / 风险

- “选择性”依赖裁量，需要 CR Reviewer 具备复杂度判断能力；文档必须给出明确判据，避免退化成“想用就用”。
- 现有模块大量已按 Handler/QueryPort 生成，存量不追溯改写；规则变化主要体现在新增代码与测试同步放宽。
- 读过 OLD 文档的工程师可能对“不再默认 Handler”产生惯性摩擦，需版本导引。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| Everything CQRS（全模块模板化三件套） | 复杂度 > 业务复杂度；默认被模块数量放大；仪式化委派 |
| 命令总线全局化（Always Command Bus） | L1/L2 场景过度设计，引入调度/序列化成本 |
| 完全取消 CQRS 概念 | 丢失复杂读写的结构化治理；不符合既有编译器与门禁体系 |

## 合规与迁移

1. 新增代码按本 ADR 判据选型；已存在 Handler/QueryPort 的存量代码保留，不一次性重构。
2. [Bone-DDD v5.2.0](../Bone-DDD-最终实践方案.md) E-3/E-4 同步本决策：E-3 决策流程翻转默认、E-4.2 读侧默认放宽、rule #19 降为裁量。
3. 共享 `BoneDddArchRules` 与各模块 `ArchitectureTest` 中“强制构件”的 `@ArchTest` 降为 warn/freeze，与“呈现降为裁量”保持一致；不变量硬规则不变。
4. `doc/agents/06-AI协作与编码准则.md` §11.2 默认入站边界描述同步更新（原位于 `AGENTS.md` §11.2，2026-09-17 拆分后随文档迁移）。
5. 本 ADR 进入 [ADR README](./README.md) 索引。