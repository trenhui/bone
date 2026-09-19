# ADR-0021：Outbox 与消费端 `eventId` 幂等平台化（平台级公共组件，替代模块复制）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-08-30 |
| **决策者** | 平台架构组（待评审） |
| **关联** | [Bone-DDD-最终实践方案](../Bone-DDD-最终实践方案.md) §5.4 / §5.5 / D6；`bone-blueprint`、`bone-integration`；change `ddd-spec-v4-5-convergence`（D6 / M3） |

---

## 背景

### 现状：跨进程发布已有两套模块级 Outbox 复制，且无共享公共组件

- **bone-blueprint**：`bp_outbox` 表（`schema.sql:53-70`，含 `UNIQUE KEY uk_bp_outbox_event_id (event_id)`）+ `OrderOutboxRecord` / `OrderOutboxWriter` / `OrderOutboxEnvelopeFactory` / `OrderOutboxRelay` / `OrderOutboxRelayJob` 全链路。记录模型注释已自述：**「多模块都需要 Outbox 时应上抽为平台级公共组件，而非各模块复制本类」**（`OrderOutboxRecord.java:20`）。
- **bone-integration**：`int_outbox` 表（`bone-init.sql:493-512`）+ `IntegrationOutboxRecord` / `IntegrationOutboxRelay`，结构高度雷同、逻辑独立。

两套实现字段集几乎一致（id / tenant_id / event_id / event_type / topic / partition_key / envelope_json / status / retry_count / sent_at），但**代码、表、中继互不相通**。

### 现状缺口（复核实证）

1. **消费端无消息级幂等**：跨进程消费的唯一入口 `OrderPaidIntegrationMqListener`（`bone-blueprint/.../adapter/mq/listener/OrderPaidIntegrationMqListener.java:23-42`）仅反序列化 + `log.info`，**无 eventId/messageId 去重、无幂等表**。中继注释自身承认「at-least-once 投递，消费方须幂等」（`OrderOutboxRelay.java:15-16`），但该幂等**没有落地**。现有幂等仅在业务聚合内（状态机级，如 `Order.confirmPaid()` 重复回调返回 false），非消息级。

> **2026-09-18 收敛注记**：本节是决策时点的事实快照，其中类名与路径已随后续收敛变化——`OrderPaidIntegrationMqListener` → `adapter/messaging/listener/OrderPaidIntegrationListener`（包名 `adapter/mq` → `adapter/messaging`，E-13.5 已记为已收敛）、`OrderOutboxRelay` → `OrderOutboxRelayPortAdapter`。上文"缺口 1"已闭环：消费端消息级幂等落地为 `ConsumedEventPort` + `bp_processed_event` 原子抢占（见 E-13.3 与 blueprint README）。
2. **事件 id 仅在信封层生成**：`DomainEvent` 为空标记接口（`DomainEvent.java:4`），领域事件对象（如 `OrderCreatedEvent`）无全局 id；eventId 是落 Outbox 时由 `OrderOutboxEnvelopeFactory.newEventId()` 生成 UUID（`OrderOutboxEnvelopeFactory.java:19-21`）。
3. **死信机制不完整**：`int_dead_letter` 表 + 积压指标（`DeadLetterMetricsRefresher.java:19-36`）存在，但**未发现写入/消费 int_dead_letter 的完整逻辑**；blueprint 侧无死信概念，仅 FAILED 状态标记。
4. **模块间语义不一致**：bone-iam / bone-masterdata 目前无跨进程投递；blueprint 走「Spring Event + AFTER_COMMIT 订阅器」，integration 走「独立 `IntegrationDomainEventPublisher`（publish 即写 outbox + 同步 dispatch）」，机制不同。

主规范 D6 已定义目标语义：「**跨进程 / 跨服务发布必须 Outbox**（与业务数据同本地事务落库 + 至少一次投递 + 消费端按 `eventId` 幂等）；本地事件 + 消费端幂等为**平台能力，禁止每个 Handler 各自实现**」。现状与目标之间的差距即本 ADR 要解决的。

## 决策

将 Outbox 收敛为**平台级公共组件**（建议落点 `bone-framework` 下独立模块，如 `bone-message-sdk`，不污染 `bone-core` 共享内核），提供以下平台原语：

### 1. 统一 Outbox 记录模型（表结构可配）

- 合并 `bp_outbox` / `int_outbox` 的字段集为一套统一模型：`id`、`tenant_id`、`event_id`、`event_type`、`topic`、`partition_key`、`envelope_json`、`status`、`retry_count`、`sent_at`、`created_at`、`deleted`，`UNIQUE(event_id)`。
- **迁移友好**：表名按模块可配置（组件提供记录模型工厂，按模块表名生成 DDL），**存量数据不迁移、表不合并**——先统一代码，再渐进收敛表名；不强制一次性大迁移。

### 2. 统一写入端口与信封

- 应用层端口（如 `OutboxWriter.append(集成事件, topic, partitionKey)`），**必须在业务写事务内调用**（与业务数据同库同事务，保证「保存成功则事件必在 Outbox」）。
- 信封统一由组件生成：**eventId 在信封层生成（UUID）作为幂等键**，贯穿投递与消费去重；领域事件对象不强制携带 id（避免与 D11「身份在构造期确定」混淆——那是聚合身份，这是事件身份）。

### 3. 统一中继（投递重试）

- 轮询 `PENDING` → 投递 → `markSent`；失败 `incrementRetry`，超 `maxRetries`（默认 5，可配）转 `FAILED` / 进入死信。逻辑从两套中继收敛为一份。

### 4. 死信作为平台原语

- 收编 `int_dead_letter` 模式为组件能力：`FAILED` 记录可落入死信队列表（status PENDING/RETRYING/RESOLVED/FAILED、max_retries、next_retry_at），提供人工重放 / 指标观测。blueprint 侧由「仅 FAILED 标记」升级为完整死信。

### 5. 消费端幂等原语（补齐最大缺口）

- 组件提供消费幂等守卫（如 `ConsumeIdempotencyGuard`，`outbox_consume_log` 表按 `(topic, event_id)` 唯一）：消费方在**业务事务内**先注册 `event_id`、成功提交后才算已消费；重复 `event_id` 直接跳过。**禁止各 Handler 各自实现幂等表**。
- 现有 `OrderPaidIntegrationMqListener` 接入该守卫（当前仅 log，重复投递存在重复执行副作用的风险）。

### 6. 模块接入方式

| 模块 | 动作 |
|---|---|
| bone-blueprint | 现有 `bp_outbox` 链路迁移到组件（保留表名/数据）；`OrderPaidIntegrationMqListener` 接幂等守卫 |
| bone-integration | 现有 `int_outbox` 链路迁移到组件（保留表名/数据）；死信接组件能力 |
| bone-iam / bone-masterdata | 未来跨进程发布时直接接入组件（当前无跨进程投递，不强制回填） |

## 理由

- **两套复制已出现且代码自述应上抽**（`OrderOutboxRecord.java:20`）：继续复制将带来维护成本与行为漂移（重试上限、死信、幂等语义各自为政）。
- **消费端幂等缺失是真实缺陷**：at-least-once 重复投递在无幂等守卫时会对同一事件执行多次副作用；这是 D6「消费端按 eventId 幂等」的落地缺口。
- **D6 已定义语义，落地为平台原语**可将「禁止每个 Handler 各自实现」从文档约定变为可用组件。
- **迁移成本可控**：表名可配 + 保留存量数据，模块接入是「换实现、不换表」的适配改造。

## 后果

### 正面

- Outbox 代码收敛为一份：重试/死信/幂等语义跨模块一致。
- 消费端幂等成为平台原语，堵住重复消费副作用。
- 新模块（iam / masterdata）跨进程发布零成本接入。
- 死信完整化（blueprint 由 FAILED 升级为死信队列 + 人工重放）。

### 负面 / 风险

- **组件归属决策未定**：落 `bone-framework` 下的模块名与包名需评审（`bone-message-sdk` 仅为建议）。
- **两套现有实现迁移需要回归**：blueprint（136 测试）与 integration 需在迁移后全量验证；表名可配但代码路径变更仍有风险。
- **幂等守卫要求消费方改造配合**：仅平台端做不够，`OrderPaidIntegrationMqListener` 必须在业务事务内注册 eventId。
- **RocketMQ 事务消息未选用**（见备选 D）：本地 Outbox 是既定现状，组件化保留该模型。
- 生产默认 `mq-enabled=false`：平台化后需定义启用约定（哪些事件必须跨进程、topic 命名规范）。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 维持两套模块级实现 | 复制蔓延、语义漂移；消费幂等、死信缺口无人补；与 D6「平台能力，禁止各自实现」直接冲突 |
| B. 仅收编 bone-blueprint 一套，integration 不动 | 双轨继续存在，组件价值减半；integration 死信与幂等缺口仍无人补 |
| C. 只出约定文档、不建组件 | 无机器强制与可复用代码，容易绕开；两套实现继续漂移 |
| D. 改用 RocketMQ 原生事务消息替代本地 Outbox | 平台已有两套本地 Outbox 且工作正常；迁移到事务消息需引入半消息补偿语义，且无法解决消费端幂等（幂等原语仍必须）；本地 Outbox 与业务同库事务的一致性更直观。可保留为组件内部投递实现的备选（发送端不变、投递用事务消息），不作为整体替换 |

## 合规与迁移

1. **组件边界**：`bone-framework` 下新增（或并入既有 message 能力）模块，提供 Outbox 记录模型 / 写入端口 / 信封 / 中继 / 死信 / 消费幂等守卫，全套自测（正向 + 反向）。
2. **迁移顺序**：bone-blueprint 先行（样板模块，验证组件正确性）→ bone-integration 跟进；两模块表名可配、存量数据保留。
3. **消费端改造**：`OrderPaidIntegrationMqListener` 接入 `ConsumeIdempotencyGuard`；新增消费端必须接入（规范硬约束）。
4. **规范同步**：主规范 §5.4 由「Outbox（可选）」改为 D6 决策表表述（已随 change `ddd-spec-v4-5-convergence` 规划）；补「跨进程发布必须 Outbox」「消费端幂等必须用平台原语」。
5. **启用约定**：定义跨进程事件白名单 / topic 命名规范 / `mq-enabled` 启用流程。

**回滚**：组件提供灰度开关（relay 停用/启用）；两模块可回退到各自原实现直至迁移验收完成；无数据结构强制变更（表名保留）。
