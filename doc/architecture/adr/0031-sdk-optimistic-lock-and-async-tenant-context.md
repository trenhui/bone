# ADR-0031：写路径租户护栏补全 + SDK 原生乐观锁（`@Version`）+ 异步入口租户声明

| 项 | 内容 |
|----|------|
| **状态** | 提议 · 待架构组批准（**D0、D1、D2 已实现**，D3 待开工；D1、D2 均经独立代码审查修正后入库） |
| **日期** | 2026-09-19 |
| **决策者** | 架构师 |
| **关联** | [ADR-0029](./0029-sdk-auto-tenant-filter.md)（租户过滤失败关闭）、[ADR-0030](./0030-domain-repository-read-merge.md)（单一仓储 / 外置 `.sql` / `@TenantScope`）、E-2（异步入口须显式声明租户）、E-5.3 / CORE-07（乐观锁）、[06-AI协作与编码准则](../../agents/06-AI协作与编码准则.md) §12（**L3 须架构师审批**） |
| **下游同步** | `bone-engine/bone-metadata-sdk`（doc + 实现）、`Bone-DDD-最终实践方案.md` E-4.4 / E-5.3、`bone-blueprint/README.md`、`doc/_generated/*` 重算 |

---

## 背景

### 1. 读/写租户护栏不对称，已造成两个现存缺陷（本轮核实）

`TenantFilterInjector` 的取值优先级是「可信上下文 → caller 显式 EQ → 失败关闭」，且认 `Criteria#disableTenantFilter()` 逃生舱（[TenantFilterInjector.java:48-88](../../../bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/query/builder/TenantFilterInjector.java)）。但**写路径不遵守同一契约**：

- `DynamicUpdateBuilder`（`save` / `update` 的唯一写路径）**只认 `TenantContext`**，既无 caller-EQ 兜底，也不认逃生舱 → 缺上下文一律抛 `MissingTenantContextException`；
- 对照：`insert` 宽容（`TenantFilterInjector.resolveInsertTenantValue` 会回落到实体字段）。

于是「全租户扫描 → 逐行写回」这一在 ADR-0029 §6 中**被明确许可**的后台链路（读侧 `disableTenantFilter()`）在写侧必然失败。已核实的两条实际路径：

| # | 路径 | 结果 |
|---|------|------|
| 缺陷 1 | `OrderOutboxRelayJob`（`@Scheduled`）→ `OrderOutboxRelayPortAdapter.relayPending` → `outboxRepository.update(record)`；`bp_outbox` 含 `tenant_id` = 租户表 | 抛 `MissingTenantContextException`；异常在 catch 内第二次 `update` 再抛 → 逃出 `@Transactional relayPending` → **整轮回滚、retryCount 也不落库 → 每 5 秒重复失败** |
| 缺陷 2 | `OrderPaymentInconsistencyJob`（`@Scheduled`）→ `appendPaymentInconsistent` → `outboxRepository.save(record)`，而 `save` 先 `findByIdIncludingDeleted(id)` 探测存在性 | 同上抛异常；**安全网检出的「钱货不一致」事件全部丢失**（对账静默失效，比不扫更危险） |

两条都被单测掩盖：仓储是 mock，`MissingTenantContextException` 只可能出现在 `BaseRepository` 真身上；且 `@Scheduled` 线程不继承请求上下文（框架内没有 TTL 包装的 `TaskScheduler`）。

### 2. `@Version` 是死元数据：写路径无乐观锁

`TableMetadataResolver` 确实解析了 `@Version`，但产物 `TableMetadata.version` 是 `@Getter(AccessLevel.NONE)` 且**全仓零消费者**；`DynamicUpdateBuilder` 的 WHERE 只有「主键 + 租户」，version 既不进 WHERE 也不自增 → `save` / `update` 是**后写覆盖**，并发下静默丢更新。

### 3. 乐观锁只能由各模块各写一遍，且被 ADR-0030 逼向 domain 仓储

blueprint 因此在两个域仓储里各写一份 `saveWithVersionCheck`（`updateByCriteria` + 自拼 `version - 1` 条件），生产调用点 **11 处**（`PaymentApplicationService` 6、`OrderApplicationService` 3、`PaymentSucceededEventHandler` 1、`PaymentRefundedEventHandler` 1）。这属"框架缺位、业务补位"：每个新模块都要重新推一遍「期望值怎么算、冲突怎么抛、租户条件要不要带」。

### 4. 异步入口没有统一的租户声明点

现有异步写路径靠「命令显式携带 `tenantId` → 应用服务显式下发 → caller-EQ 兜底」侥幸成立。已核实的四处（**当前不炸，但都不是因为声明了租户**）：

| 入口 | 为何当前成立 | 何时会炸 |
|------|--------------|----------|
| `CancelExpiredOrderJob`（`@Scheduled`） | **D2 已修复**：调用处用 `TenantContextRunner.runAs(row.getTenantId(), …)` 显式声明租户，写路径 `update(entity)` 因此拿到 `TenantContext`（D2 前靠 `saveWithVersionCheck` 的 caller-EQ 兜底，D2 后该兜底随手写版本管理一起退役） | 若移除 `runAs` 且调度线程无上下文，写路径 ADR-0029 失败关闭，异常被任务 `catch(Exception)` 静默吞掉 ⇒ 超时单永不清理 |
| `CloseExpiredPaymentJob`（`@Scheduled`） | 同上 | 同上 |
| `OrderPaidIntegrationListener`（RocketMQ 消费者线程） | 只做 `insert`，而 insert 宽容（`resolveInsertTenantValue` 回落实体字段） | 幂等抢占改 `save()`，或下游动作写租户表，立即失败关闭 |
| `PaymentSucceeded/RefundedEventHandler`（AFTER_COMMIT） | 发布事务源自请求线程 ⇒ 监听器同线程、上下文仍在 | 改为 `@Async` / MQ 消费即失效；`OrderCancelledEventHandler` 已经跑在调度线程上（目前只做远程调用） |

一旦某处忘了带，或某条路径改走无兜底的写方法，结果就是**静默丢数据**或**整批响亮失败**，两种都不是好状态。ADR-0029 §6 其实已给出标准做法（"先 `TenantContext.setTenantId(...)` 再查"），缺的是**统一封装与执行纪律**。

---

## 决策

分四批，**顺序不可调换**（先 SDK 能力、再业务切换、最后入口纪律）：

### D0（已实现 · L2）：补全读/写护栏不对称，消除缺陷 1 / 2

1. `bone-core` 新增 `TenantContextRunner`：`runAs(Long, Runnable)` / `callAs(Long, Supplier<T>)`，进入前记录、退出 `finally` 恢复（异步线程原值通常为 `null` ⇒ 等价清空，不残留、不串租户；嵌套与请求线程内调用同样安全）；`tenantId == null` **快速失败**（`NullPointerException`）——"没传租户"与"以某租户执行"不是一回事，不应静默退化成无上下文。
2. `OrderOutboxRelayPortAdapter`：扫描保持 `disableTenantFilter()`（跨租户），**逐条** `TenantContextRunner.callAs(record.getTenantId(), () -> relayOne(record))`。
3. `OrderPaymentInconsistencyJob`：**逐行**以 `row.getTenantId()` 包裹 Outbox 落库。
4. 契约测试（锁住不变量，防止回退）：`TenantContextRunnerTest`（作用域内可见 / 退出恢复前值 / 异常路径恢复 / 拒绝 `null`）、`DynamicUpdateBuilderTest`（**写路径无兜底**：缺上下文必抛；非数值租户编码同样必抛；**上下文与实体字段不同值时以上下文为准**）、`OrderOutboxRelayTest`（逐条上下文可见 + 退出无残留 + **一批多租户各自声明** + 写失败仍恢复）、`OrderPaymentInconsistencyJobTest`（同理）。
5. 说明：写路径"无 caller-EQ 兜底、不认 `disableTenantFilter()`"是**签名层的结构事实**（`DynamicUpdateContext` 只有 `table` + `entity`，`SqlBuilder.buildDynamicUpdate` 无 `Criteria` 形参），无法用行为断言直接表达；测试改为断言"**实体字段不是授权来源**"这一等价且可判别的性质。

### D1（P1 · L3 · **已实现**）：SDK 原生支持 `@Version`

1. `TableMetadata` 暴露 `getVersion()` / `isVersioned()`（去掉 `@Getter(AccessLevel.NONE)`）；未标 `@Version` 的表**不启用**加锁（零向后兼容风险）。`@Version` 字段**必须是 `Number` 子类型**，否则解析期即抛 `MetadataException`。
2. `DynamicUpdateBuilder`：
   - SET 子句**排除** version 列，改写为 `version = version + 1`（DB 端自增，不引入"实体携带新值"的隐式约定）；
   - WHERE 追加 `AND version = :__bone_version_old__`，旧值取实体字段当前值（= 加载时值）；
   - **回写职责在 `BaseRepository.update()`，不在构建器**：成功后按字段声明类型反射回写 `entity.version = old + 1`（兼容 `Integer`/`int`/`BigInteger`，非只 `Long`）——不回写则同一实体连续两次写必然假冲突（最易漏的一步）；若实体 `@Version` 为 `null`（未加载），抛出清晰的 `IllegalStateException` 而非被误报成并发冲突。
3. `insert`（`BatchInsertBuilder`）：`@Version` 字段为 `null` 时参数置 `0`（DDL 有 `DEFAULT 0`，但 insert 显式给列，`null` 会违反 `NOT NULL`）；单条与批量 `insert` 后都把实体 `version` 回写为 `0`，与库一致。
4. 新增 SDK 异常 `OptimisticLockingFailureException`；**仅对 version 表**：`update()` 现有「0 行 → warn + 返回 false」语义改为**抛异常**（冲突 / 不存在 / 越租户统一按冲突）；**非 version 表保留 `warn + false`**（零破坏性——当前 D2 未开工、全仓尚无 `@Version` 实体，该行为变更**零实际影响面**）。
5. **`ConditionalUpdateBuilder` / `BatchUpdateBuilder`（条件更新 / 批量更新）同样**排除** version 列**——避免把它们当普通列覆盖成实体内存值（无 bump、无护栏），即「条件/批量路径不支持乐观锁」在构建器层已落实，而非仅口头声明。
6. **批量路径**（`saveAll` / `batchUpdate`）拿不到逐行影响行数 → 明确不支持乐观锁（version 既不 bump 也不护栏，仅原样略过）；调用方如需乐观锁须走单条 `update`。
7. **必须写明的歧义**：MySQL affected-rows=0 无法区分「版本冲突」与「行不存在 / 租户不匹配」。要区分只能 `SELECT ... FOR UPDATE` 或多一次往返。决策：维持统一抛冲突异常（与现状 `saveWithVersionCheck` 语义一致）。
8. 记账不改：`DynamicUpdateBuilder` 是全列覆盖（null 也写）、`ConditionalUpdateBuilder` 是 null 跳过——两条写路径的空值语义分歧另案处理，D1 不顺手改。

### D2（P2 · L3）：blueprint 从「业务自拼」切到「SDK 原生」

`Order` / `Payment` 补 `@Version`；删除 `incrementVersion()`（Order 6 处 / Payment 5 处调用）；`saveWithVersionCheck` 退役为 SDK `update(entity)`；SDK 异常在应用层翻译回现有 `OptimisticLockConflictException` 语义。

> **D2 实现（2026-09-19）**：已按上述实施并通过独立代码审查。要点：
> - `PaymentApplicationService#processCallback` 成功路径并发拦截改为 `catch (OptimisticLockingFailureException | DuplicateKeyException)` 统一按幂等跳过，保持 ADR 要求的"保持有效"语义（避免 re-throw 逃逸成 500）。
> - 其余 10 处调用点将 SDK `OptimisticLockingFailureException` 翻译为领域 `OptimisticLockConflictException` 后按硬错误上抛。
> - 写路径改走 SDK `update(entity)` 后租户由 `TenantContext` 提供，两个 `@Scheduled` 任务（`CancelExpiredOrderJob` / `CloseExpiredPaymentJob`）已在调用处用 `TenantContextRunner.runAs(row.getTenantId(), …)` 显式声明租户，否则会因 ADR-0029 失败关闭被任务 `catch(Exception)` 静默吞掉。

### D3（P3 · L2）：异步入口租户声明常态化（B）

1. 入口清单（逐条 `runAs`）：① `@Scheduled` 全租户扫描 4 个（CancelExpired / CloseExpired / Inconsistency / OutboxRelay）；② RocketMQ 消费者 `OrderPaidIntegrationListener`；③ `AFTER_COMMIT` 事务事件订阅（**当前同线程、上下文仍在，可不动**，但须在规范中写明"改为 `@Async` / MQ 消费时必须 `runAs`"）。
2. 命令中的 `tenantId` **保留但降级**：从"数据来源"变为"授权声明 + 审计字段"（E-2 登记依据、日志、死信核对），并在 runner 内断言「上下文 == 命令声明」，不一致即拒绝 → E-2 授权链不因 B 而断。
3. 域仓储删除 `findByIdInTenant` / `findStatusById` 的 tenantId 形参，改回 SDK `findById` / Criteria（租户由上下文保证）。

### D4：门禁

R3 / R4 之外补 **R7「异步入口必须显式声明租户」**：`@Scheduled` / MQ 监听方法体内出现租户表读写时，必须处于 `TenantContextRunner` 之内（ArchUnit 对此判定能力弱，建议 **lint + 约定 + 集成测试矩阵** 三重兜底，先落 lint 白名单）。

> 状态说明：R3 / R4 目前仍是 **Planned（P3）**——见 ADR-0030 §4 门禁表与 §10.3、`doc/architecture/Bone-DDD-最终实践方案.md` 门禁③/④，以及 `scripts/check-ddd-gate-state.py` 的 `KNOWN_MISSING` 登记（`sqlTemplateSourceMustNotDuplicate` / `sqlMethodsMustDeclareTenantScope`）。本 ADR 不改变其状态，仅新增 R7 待立。

---

## 理由

1. **D0 是缺陷修复，不是重构**：两条路径今天就在失败，与是否采纳 D1~D3 无关。
2. **C 先行、B 后置**：C 只动 SDK 即可独立上线并被 SDK 单测覆盖；B 若不配 C，会让"租户来源收敛到上下文"与"乐观锁仍靠 domain 手写"两套心智并存，白改一轮。反过来 C 不动 B 也能拿到统一乐观锁。
3. **把"漏传租户"从静默错误变成响亮失败**：这是 B 的核心价值——现在是"命令里忘了 tenantId → 落到平台租户 0"或"写路径无兜底 → 整轮回滚"，前者静默、后者粗暴；统一 `runAs` 后语义单一且可审计。
4. **乐观锁属框架能力**：让每个模块自行推导「期望值 = version − 1」是重复且易错的（本次已发现一处漏带租户条件的实例），应由 SDK 统一提供。

---

## 后果

### 正面

- 两条现存缺陷消除，且用契约测试锁住（mock 仓储无法再掩盖此类问题）。
- 写路径与读路径的租户语义一致：都从可信上下文取值、都失败关闭。
- 新模块不必再写 `saveWithVersionCheck`，也不必在仓储签名里透传 `tenantId`。

### 负面 / 风险

- **B 的唯一实质代价**：租户从「编译期可见的形参」变成「运行期上下文」，读代码看不出隔离；漏 `runAs` 只有集成测试能抓（单测 mock 仓储一律绿）。因此 D3 必须配真库/Testcontainers 集成测试矩阵。
- **单测的固有局限（已实测确认）**：仓储被 mock 时不会抛 `MissingTenantContextException`，新测试锁的是"逐条声明了上下文"，**不能复现失败关闭本身**；后者只能靠集成测试 + SDK 侧 `DynamicUpdateBuilderTest` 共同覆盖。
- **中继的"毒丸"锐边（评审查出，未在本轮修改）**：`relayPending` 整批一个事务，`relayOne` 的 catch 分支再抛会使整批回滚（本改动把"必炸"变成"正常"，但单条脏数据仍可演变为永久毒丸，例如 `record.getTenantId()` 为 `null`）。`TenantContextRunner` 现对 `null` 快速失败，故该情形会**响亮失败**而非静默；彻底隔离需逐条 `REQUIRES_NEW`，与 MQ 至少一次语义的交互需单独评估，留 D3。
- **线程继承语义**：`TenantContext` 底层是 `InheritableThreadLocal`，`runAs` 作用域内**新建**的线程会继承该租户，而退出时的恢复只作用于当前线程——需向工作线程传递上下文时应显式包装线程池（已在 `TenantContextRunner` javadoc 写明）。
- **D1 的破坏性（实际为零）**：`update()` 0 行语义仅对 **version 表**改为抛异常；非 version 表保留 `false`。D2 已于 2026-09-19 实现（`Order` / `Payment` 标注 `@Version`），该变更随之生效；新模块都不再需要手写 `saveWithVersionCheck`。
- **`affected=0` 歧义不可消除**（见 D1.7），调用方需按"冲突或不存在"统一处理。
- D1/D2 属 **L3**（改 SDK 行为 + 删除既有代码），须架构师审批并走 PR 双人 Review。

---

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A：保持现状（domain 自写 `findByIdInTenant` + `saveWithVersionCheck`） | 保留上面两个缺陷；且"框架缺位、业务补位"会让每个新模块重复推导乐观锁 |
| 只做 B（上下文驱动租户），不做 C | 收益减半：租户来源统一了，乐观锁仍是各写一遍；且 B 单独会打散现有 `saveWithVersionCheck` 的租户条件护栏 |
| 只做 C，不做 B | 可行（D1+D2 可独立上线），但异步入口纪律仍缺统一封装，未来仍会出现本次这类问题 |
| 让 `DynamicUpdateBuilder` 也支持 caller-EQ 兜底 / 逃生舱 | 会削弱写路径失败关闭强度（写比读危险），且与 ADR-0029「可信上下文优先」冲突；正解是入口显式声明 |
| 在 `TenantContextRunner` 内无条件 `clear()` 而非恢复 | 恢复语义对嵌套与请求线程内调用更安全，异步线程原值为 `null` 时二者等价 |

---

## 合规与迁移

1. **批次与可上线性**：D0（可独立上线，已实现）；D1（可独立上线，仅 SDK + SDK 测试）；D2（依赖 D1）；D3（依赖 D2，且顺带修复缺陷 1/2 的根因）。
2. **验证矩阵（D1/D2/D3 各自的验收）**：
   - D1：未标 `@Version` 不启用 / 冲突抛异常 / 成功回写实体 / `null` 初始化 0 / 批量声明不支持。
   - D2：`Order`/`Payment` 状态迁移后实体 `version` 与库内一致；并发两次写第二次冲突；`OptimisticLockConflictException` 语义不变。
   - D3：`runAs` 后写成功且退出无残留；漏 `runAs` 的路径必须失败关闭（集成测试）；E-2 声明与上下文不一致时拒绝执行。
3. **回归面**：D0 已跑 `bone-metadata-sdk` **250**、`bone-blueprint` **205**（含既有门禁测试 `SqlTemplateGovernanceTest` 9）、`TenantContextRunnerTest` **4**，全绿。
4. **回滚**：D0 可整体回滚（`TenantContextRunner` 无其他调用方）；D1 以"是否标注 `@Version`"为开关，回滚即去掉注解。
5. **遗留待办（本轮发现，不属本 ADR 决策范围，均需单独收敛；也是当前工作区 `mvn -am` 全 reactor 跑不通的原因）**：
   - `bone-framework/bone-architecture-test/.../BoneDddArchRules.java`（未提交 WIP）**spotless 违规**，导致 `mvn install` 在该模块即失败（`bone-core` 之后的模块拿不到最新 jar，本轮改为单独 install `bone-core` 规避）。
   - `bone-blueprint/src/test/java/com/bone/blueprint/SqlTemplateGovernanceTest.java`（**未跟踪**新文件，ADR-0030 门禁①③④⑥ 的载体，9 个用例全绿）同样 spotless 违规 → `mvn -pl bone-blueprint test` 会在 `spotless:check` 阶段失败。该文件归属待确认后统一 `spotless:apply`。
   - `bone-core` 的 `DomainRepositoryReturnTypeRuleTest#projectionAndPersistenceVocabularyAreRejected` **失败**：未提交的 `BoneDddArchRules` ADR-0030 R2 放宽（允许 `List` / `PageResult` 承载领域读模型）与旧 fixture 期望不一致，需同步更新期望或 fixture。
