# ADR-0029：SDK 查询/更新/删除自动注入 tenant_id

| 项 | 内容 |
|----|------|
| **状态** | 已接受 / 已实现（SDK 落地；待各业务模块回归） |
| **日期** | 2026-09-18 |
| **决策者** | 架构师 |
| **关联** | [Bone-多租户规范.md](../Bone-多租户规范.md) §3/§4、[ADR-0016](./0016-metadata-catalog-abstract-entity-tenant.md)、[ADR-0019](./0019-id-generation-contract-respect-non-null-id.md)、[ADR-0006](./0006-iam-tenant-isolation-modes.md) |
| **下游同步** | 接受后同步 `Bone-DDD-最终实践方案.md` E-0.3 所列文档 + `Bone-多租户规范.md` §3 落地态 |

---

## 背景

1. **规范已要求、SDK 未落地**：`Bone-多租户规范.md` §3 明确"Infrastructure：SQL 自动附加 `tenant_id = ?`（拦截器 / SDK）"，§4 要求"所有业务查询带 `tenant_id`"。但 SDK 当前**只对扩展表 `ext_data_reserved` 的 JOIN/UPSERT 自动注入 `tenant_id`**（`SelectBuilder.java:64`、`CountBuilder.java:51`、`BaseRepository.java:790`）；**主表查询/更新/删除的 WHERE 从不自动注入**。
2. **现状代价**：业务仓储必须手写 `eq("tenantId", tenantId)`（如 `OrderRepository#findByIdInTenant`），重复且易漏写；漏写即退化为"按 id 跨租户读取"，违反"失败关闭"。
3. **id 全局唯一**：平台 id 生成契约（ADR-0019）保证 `id` 全局唯一，`WHERE id=?` 本身可唯一定位行。因此 `tenant_id` 进 WHERE 是**防御纵深**（防越权 + 失败关闭），而非唯一性依赖——自动注入让该纵深"零成本"获得。
4. **`TenantContext` 已就绪**：`com.bone.core.tenant.context.TenantContext.getTenantIdAsLong()` 在 SDK 内已用于扩展表与 `AllocationContext`，请求线程由 JWT Filter 写入、结束 `clear()`。注入的来源与生命周期均已具备。

---

## 决策

在 `bone-metadata-sdk` 的 SQL 构建阶段，**对"租户表"自动注入 `m.tenant_id = :_sdk_tenant_id`**（来源 `TenantContext`），覆盖主表的 SELECT / COUNT / UPDATE / DELETE 四类语句。具体：

1. **覆盖构建器与删除入口**：`SelectBuilder`、`CountBuilder`、`ConditionalUpdateBuilder`、`DeleteBuilder` 在拼接 WHERE 时（软删条件之后）追加租户条件；**另含 `BaseRepository#deleteById` / `deleteByIds` 的内联按 pk 删除 SQL（当前无租户护栏、属跨租户删除路径，须同步追加 `AND tenant_id = :_sdk_tenant_id`）**。
2. **租户表识别**：`TableMetadata` 新增 `isTenantScoped`，由 `TableMetadataResolver` 在解析列时判定——实体含名为 `tenant_id` 的列（即继承 `TenantAbstractEntity` 或字段 `@Column(name="tenant_id")`）即视为租户表。非租户表（如平台级无租户实体）**不注入**。
3. **租户来源**：构建时调用 `TenantContext.getTenantIdAsLong()`（SDK 已依赖 `bone-core`，新增 import 即可）。
4. **参数命名防撞**：使用保留参数名 `_sdk_tenant_id`，与调用方可能手写的 `tenantId` 参数隔离。
5. **租户只从可信 Context 注入，caller `Criteria` 的 `tenantId` 不可信**：tenant 由 `BaseRepository` 在入口从 `TenantContext.getTenantIdAsLong()` 填入各 `*Context`（`tenantId` 字段），构建器统一追加 `m.tenant_id = :_sdk_tenant_id`——与 `SelectBuilder.java:64` 扩展表 JOIN 自动带 `tenant_id` 同源。SDK **不读取 caller `Criteria` 里的 `tenantId` 条件**（违反多租户规范 §2「下游只读、禁止请求体覆盖已认证租户」）；若 caller 仍传 `eq("tenantId", X)`，SDK 忽略该条件并打 WARN。由此存量手写 `eq("tenantId",…)` 代码变为「无害冗余」（被忽略），无需改动即向后兼容。
6. **逃生舱（必含）**：提供显式关闭开关 `Criteria#disableTenantFilter()`（或在 `SelectContext`/`UpdateContext`/`DeleteContext` 置 `tenantFilterDisabled=true`），用于：
   - 跨租户平台管理查询（须 `platform:*` Scope + 审计）；
   - 后台作业 / Outbox 中继 / 定时任务等 `TenantContext` 为空的上下文——这些路径须**先 `TenantContext.setTenantId(...)` 再查**，或显式 `disableTenantFilter()`。
7. **缺失上下文默认失败关闭**：租户表 + `TenantContext` 为空 + 未显式关闭 → 抛 `MissingTenantContextException`（**不静默忽略**），杜绝"自动注入变 `tenant_id = NULL` 静默返回空"打挂后台链路。
8. **`findByIdIncludingDeleted` / `findByIdsIncludingDeleted`**：仅绕过**软删**（加载含已删行），**租户过滤保持开启**（与 `findById` 一致）。软删绕过与租户隔离是正交关注点，不可一并默认关闭；跨租户加载须调用方 `disableTenantFilter()` 显式声明意图（且须 `platform:*` 授权 + 审计）。

---

## 细化设计（评审补充）

### 1. 租户表识别（细化）

**判定规则（单一、可锁）**：以"实体含租户字段"为准——**同时按 Java 字段名 `tenantId` 与物理列名 `tenant_id`/`tenantId` 识别**，降低漏判。

```java
// TableMetadataResolver.load(cls) 解析 columns 后
this.tenantIdColumn = columns.stream()
    .filter(c -> "tenantId".equals(c.getFieldName())
             || "tenant_id".equals(c.getName())
             || "tenantId".equals(c.getName()))
    .findFirst().orElse(null);
this.tenantScoped = this.tenantIdColumn != null;
```

> 仅按列名 `tenant_id` 会漏判 camelCase 列名或 ADR-0016 中"自有 `tenantId` 字段但列名非 snake_case"的实体；加 `fieldName` 兜底覆盖两者。

与既有 `isVersion` / `isSoftDeleted` 的识别写法完全一致（`TableMetadataResolver.java:110` 同款 `field.isAnnotationPresent(...)` → `ColumnMetadata` 标记），新增一个 `isTenantScoped()` 即可。

**为什么不靠"继承 `TenantAbstractEntity`"判定**：ADR-0016 的 catalog 实体（`MetaEntity`/`MetaField`/`MetaRelation`）`extends AbstractEntity<Long>` 但**自有声明 `tenantId` 字段**，继承判定会漏掉它们；列名判定两者通吃。

**为什么不靠新增 `@Tenant` 注解（MVP 不做）**：标注需改 `bone-core` 注解包 + 全实体补注解，成本高；而 `Bone-多租户规范.md` §1 已强制"所有业务表 `tenant_id` 列名强制"，列名即规范事实源，无需额外注解。

**列类型校验**：`tenant_id` 应为 `Long`，与 `TenantContext.getTenantIdAsLong()` 类型一致。若解析到同名但非 `Long` 列（异常配置），记录 WARN 但仍按 `tenantScoped` 处理——误判为"注入"比误判为"不注入"更安全（后者是跨租户读漏洞）。

**误判处置（漏判风险）**：若某实体租户列名非 `tenant_id` → 不注入 → 跨租户读漏洞。缓解双保险：① 规范强制列名；② CI 既有"业务表必须有 `tenant_id` 列"检查（多租户规范 §7 检查清单）兜底。

**锁入单测**：`TableMetadataResolverTest` 断言 `Order`/`Payment`（继承 `TenantAbstractEntity`）→ `isTenantScoped()==true`；SDK 内无 `tenant_id` 列的实体 → `false`。

### 2. 逃生舱（细化）

**注入决策谓词（统一，四个构建器 + `deleteById`/`deleteByIds` 共用）**：

```text
// ctx.tenantId 由 BaseRepository 在入口从 TenantContext.getTenantIdAsLong() 填充（可信源）
// caller Criteria 里的 tenantId 在 HTTP 路径被忽略（见决策 5），不参与此谓词
inject = tbl.isTenantScoped()
      && !ctx.tenantFilterDisabled          // 显式关闭（跨租户基础设施扫描，须 platform:* 授权 + 审计）

if (tbl.isTenantScoped() && !ctx.tenantFilterDisabled) {
  // 取值优先级：
  //  ① 可信 TenantContext（请求线程由 JWT Filter 写入）→ 注入，caller tenantId 被忽略并 WARN
  //  ② 上下文为空 且 caller 主表显式 EQ 限定单租户 → 以 caller 值兜底（后台/跨租户 admin 既有行为，仍单租户隔离）
  //  ③ 两者皆无 → 失败关闭
}
```

即：tenant 优先从可信 `TenantContext` 注入，与 caller `Criteria` 解耦；HTTP 路径下 caller 传入的 tenantId 被视为不可信，被忽略并 WARN。仅当"**应注入却既无可信上下文、caller 又未显式限定到单一租户**"时才抛 `MissingTenantContextException`（真正无范围的跨租户/静默空查询）。`tenantFilterDisabled` 时 SDK 退出责任、不注入不打断（但打 WARN 审计线索：实体类 + 调用栈）。

**适用范围精确表**（明确 MVP 边界，避免广谱改 `save` 语义）：

| 方法 | 是否自动注入 tenant | 说明 |
|------|------------------|------|
| `findById(id)` | 注入 | 内部构建 `id` criteria |
| `findOneByCriteria` / `findByCriteria` / `pageByCriteria` / `countByCriteria` | 注入 | caller criteria |
| `updateByCriteria(entity, c)` / `deleteByCriteria(c)` | 注入 | caller criteria（仍提供租户护栏，须 `TenantContext`）；原 `saveWithVersionCheck` 走此路径，D2 已退役为 SDK `update(entity)` |
| `save(entity)` / `update(entity)`（`DynamicUpdateBuilder` 按 pk） | **不注入（MVP）** | 无 caller criteria，广谱改 `save` 语义风险大；未来加固再评估 |
| `findByIdIncludingDeleted` / `findByIdsIncludingDeleted` | 注入（与 `findById` 一致） | 仅绕过软删；租户隔离保持开启；跨租户须 `disableTenantFilter()` |
| `deleteById(id)` / `deleteByIds(List)`（内联 SQL） | 注入 | 入口在 `BaseRepository` 追加 `AND tenant_id = :_sdk_tenant_id`；当前无护栏，必须补 |

**API 形态**：
- `Criteria#disableTenantFilter()` → 置 `tenantFilterDisabled=true`，返回 `this`（fluent）；配 `isTenantFilterDisabled()`。
- `findByIdIncludingDeleted` / `findByIdsIncludingDeleted` 系列只置 `includeDeleted=true`，**不置** `tenantFilterDisabled`；租户过滤与 `findById` 完全一致（仍注入 `tenant_id`）。跨租户加载已删行须调用方显式 `disableTenantFilter()`。

**授权边界（关键）**：`disableTenantFilter()` 是**框架级开关，不是授权**。跨租户查询必须在 application/adapter 层先校验 `platform:*` Scope + 写审计；SDK 在"租户过滤被关闭"时打 **WARN 日志**（审计线索：实体类 + 调用栈）。

**后台链路标准做法**：优先 `TenantContext.setTenantId(tenantId)` 再查（保留隔离）；仅当查询本质全局（如 Outbox 中继按消息携带的 `tenantId` 扫描 `bp_outbox`）才用 `disableTenantFilter()`。

**异常**：新增 `MissingTenantContextException extends RuntimeException`，message 含 `entityClass` + `tableName`，便于定位"哪个租户表在无上下文时被查"。

**逃生舱单测矩阵**（`TenantFilterBuilderTest`）：
1. 租户表 + 上下文已设 → SQL 含 `m.tenant_id = ?`；
2. 租户表 + 上下文为 null + 未关 → 抛 `MissingTenantContextException`；
3. 租户表 + `disableTenantFilter()` → 无 `tenant_id` 条件、无异常；
4. 租户表 + caller 已 `eq("tenantId", X)` → HTTP 路径以上下文为准（caller 被忽略 + WARN）；后台 / 上下文为空时以 caller 值兜底（仍单租户、无异常、无重复）；
5. 非租户表 → 永不注入（即便上下文已设）；
6. `findByIdIncludingDeleted` → 默认**含** `tenant_id` 条件（仅绕过软删、不绕过租户）；`disableTenantFilter()` 后无此条件。

---

## 理由

- **与既有规范一致**：把 §3 已声称的"SDK 自动附加"真正落地，消除规范与实现漂移。
- **消除重复与漏写**：业务仓储不再手写 `eq("tenantId",…)`；租户隔离下沉到 SDK，是"失败关闭"而非"内存校验/靠人"。
- **id 全局唯一下的纵深**：不依赖租户做唯一性，仅作安全护栏，自动注入零语义负担。
- **向后兼容**：规则 5（调用方已带则不重复）+ 逃生舱保证 7 模块现有代码与后台链路可平滑迁移，不一次性破坏。

---

## 后果

### 正面

- 租户隔离在 SQL 层统一兜底，业务仓储代码简化、漏写风险归零。
- `findById(id)` 对租户表自动等价于 `findByIdInTenant(id, ctxTenant)`，`findByIdInTenant` 等样板方法后续可简化/回收。
- 更新/删除路径也获租户护栏，缩小"跨租户误改"攻击面。

### 负面 / 风险

- **多模块影响**：改共享 SDK，7 个后端模块全部受影响，需各模块回归（尤其后台/Outbox 链路，见规则 6/7）。
- **后台链路需改造（具体影响点，已定位并大部分已处理）**：
  - **Outbox 中继（必改）**：`OrderOutboxRelayPortAdapter.relayPending()` / `IntegrationOutboxRelay.relayBatch()` 在后台线程 `findByCriteria(eq(status, PENDING))` 扫描 `OrderOutboxRecord` / `IntegrationOutboxRecord`（均含 `tenant_id` 列 → 判定为租户表）。中继本质跨租户全量扫描，已加 `disableTenantFilter()`（每条消息自带 tenantId 进入消费逻辑）。
  - **定时任务扫描**：`CancelExpiredOrderJob` / `CloseExpiredPaymentJob` / `OrderPaymentInconsistencyJob` 走 `*AllTenants` 原生读端口（`findExpiredUnpaidOrdersAllTenants` 等）+ 显式 `findStatusById(tenantId, id)`，不触发 SDK 注入；其中 `findStatusById` 经 caller `eq("tenant_id")` 走"caller-EQ 兜底"分支，无需改造。
  - **消息消费幂等**：`ConsumedEventPortAdapter.tryClaim` 仅 `repository.insert`（唯一键抢占），tenant 来自消息体；INSERT 自动补值逻辑在上下文为空时回退实体值，安全，不触发异常。
  - **跨租户 admin 查询**：存量 `findByIdInTenant(tenantId, id)` / `eq("tenantId", X)` 在 HTTP 线程有 `TenantContext` → 以上下文为准（caller 被忽略 + WARN）；在后台线程无上下文 → 以 caller EQ 兜底（仍单租户）。无需逐一改调用点。
  - 其余后台查询若既无 `TenantContext` 又未显式 `eq("tenant_id")` 且非 `disableTenantFilter` → 触发 `MissingTenantContextException`（预期失败关闭，须补 `setTenantId` / `disableTenantFilter`）。
- **误判租户表**：依赖 `tenant_id` 列名识别；若某实体租户列名非 `tenant_id` 会漏注入——需在 `TableMetadataResolver` 单测锁定识别规则。
- **测试需更新**：`bone-blueprint` 的 `RepositoryTenantIsolationTest` 当前桩 `findOneByCriteria` 并断言"调用方传入 tenantId EQ"；SDK 接管后该断言对象转移为 SDK 内部构建器测试（应在 SDK 内新增 `TenantFilterBuilderTest`）。

---

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 仅在 blueprint 局部封装（仓储基类从 `TenantContext` 取 tenantId 拼入 Criteria） | 只覆盖单模块，不解决其余 6 模块重复/漏写；且仍依赖每调用点使用封装，非框架级兜底 |
| B. 用 MyBatis/JPA 拦截器统一注入 | 违反 `AGENTS.md` HC-001/HC-006（禁 MyBatis/JPA/MP），持久化唯一方案是 bone-metadata-sdk |
| C. 不改 SDK，维持手写 `eq("tenantId")` | 规范 §3 长期漂移；漏写即跨租户读，不满足失败关闭 |

---

## 实现方案（落地清单，供评审）

| 文件 | 改动 |
|------|------|
| `domain/model/TableMetadata.java` | 新增字段 `ColumnMetadata tenantIdColumn` + `boolean isTenantScoped()`；构造时由 `columns` 中 `name=="tenant_id"` 推导 |
| `domain/model/TableMetadataResolver.java` | 解析列后设置 `tenantIdColumn` + `isTenantScoped()`（按 `fieldName=="tenantId"` 或 `name∈{tenant_id,tenantId}`；现有 `isVersion`/`isSoftDeleted` 同款写法） |
| `domain/model/ColumnMetadata.java` | 无需改（复用 `name`） |
| `query/criteria/Criteria.java` | 新增 `boolean tenantFilterDisabled` + `disableTenantFilter()` / `isTenantFilterDisabled()` |
| `query/context/*Context.java`（`SelectContext`/`ConditionalUpdateContext`/`DeleteContext`/`CountContext` 如有） | 透传 `tenantFilterDisabled`（默认 false）；`includeDeleted` 路径默认置 true |
| `query/builder/SelectBuilder.java` | WHERE 段（软删之后）追加：`if (tbl.isTenantScoped() && !ctx.isTenantFilterDisabled()) injectTenantWhere(...)` |
| `query/builder/CountBuilder.java` | 同上 |
| `query/builder/ConditionalUpdateBuilder.java` | WHERE 段追加租户条件（来源 `TenantContext`） |
| `query/builder/DeleteBuilder.java` | WHERE 段追加租户条件 |
| 新增 `support/TenantFilterInjector.java` | 统一工具：`inject(ctx, params, sql)` 读 `ctx.tenantId`（入口由 `BaseRepository` 从 `TenantContext` 填充，可信源）；`isTenantScoped && !disabled && tenantId!=null` 时追加 `m.tenant_id = :_sdk_tenant_id`；应隔离却 `tenantId==null` → 抛 `MissingTenantContextException`；**忽略 caller `Criteria` 里的 `tenantId` 条件并 WARN**（决策 5） |
| 新增 `domain/exception/MissingTenantContextException.java` | 失败关闭异常（含 `entityClass` + `tableName`） |
| `BaseRepository#findByIdIncludingDeleted` / `findByIdsIncludingDeleted` | 仅置 `includeDeleted=true`，租户过滤保持开启（与 `findById` 一致）；不动 `tenantFilterDisabled` |
| `BaseRepository#deleteById` / `deleteByIds` | 内联删除 SQL 追加 `AND tenant_id = :_sdk_tenant_id`（值来自 `TenantContext`）；跨租户删除须 `disableTenantFilter()` 重载或显式 context |
| `query/builder/BatchInsertBuilder.java` / `UpsertBuilder.java` | **【建议补充 #1】** 对 `tenantIdColumn` 优先用 `TenantContext` 值（实体为 null 时补、非 null 不符则覆盖 + WARN） |
| SDK 单测 | 新增 `TenantFilterBuilderTest`：租户表自动注入、非租户表不注入、调用方已带不重复、上下文为空抛异常、逃生舱生效 |

**参数占位**：占位符 `*_sdk_tenant_id`；注入前若 `crit.getMainConditions()` 已存在 `tenantId` EQ 则跳过（规则 5）。

**业务侧后续（本 ADR 接受后单独提交，不阻塞）**：`bone-blueprint` 的 `OrderRepository#findByIdInTenant` / `PaymentRepository#findByIdInTenant` 可移除手写 `eq("tenantId",…)`，改由 SDK 注入；同步改写 `RepositoryTenantIsolationTest` 为断言 SDK 行为 + 调用方不再传 tenantId。

---

## 建议补充（第 1、2 项已在 SDK 落地；第 3 项可选，未实现）

> 以下两项是"读/删侧护栏"之外的**写侧完整性**增强；核心方案（SELECT/COUNT/UPDATE/DELETE 四类 WHERE 注入）已自洽，这两项可单独排期。

### 1. INSERT 自动从上下文填充 tenant_id（写侧更优先）

- **现状缺口**：`BatchInsertBuilder` / `UpsertBuilder` 直接用实体字段（`ReflectionUtil.getFieldValue`），`TenantContext` 只用于扩展表；主表 insert **不读上下文补 tenant_id**（`Bone-多租户规范.md` §4 要求"插入时从上下文填充"）。若实体 `tenantId == null` → 插入 **NULL tenant 行**：所有租户过滤查询都读不到（数据黑洞），且 `disableTenantFilter()` 时泄漏——比读过滤缺失更严重的**写侧数据损坏**。
- **建议**：insert 时若实体 `tenantId == null` 从 `TenantContext` 补；若非 null 但与 context 不符 → 以 context 为准（context 是唯一可信源），并打 WARN。
- **落地**：`BatchInsertBuilder` / `UpsertBuilder` 在拼参数时，对 `tenantIdColumn` 特殊处理（优先 context 值）。

### 2. `save(entity)` / `update(entity)`（DynamicUpdateBuilder 按 pk）租户护栏

- **现状缺口**：`DynamicUpdateBuilder` WHERE 仅主键，无租户条件（`DynamicUpdateBuilder.java:46-54`），可被误用跨租户改。
- **建议**：对租户表追加 `AND tenant_id = :_sdk_tenant_id`（来源 context）；跨租户 admin 保存须 `disableTenantFilter()`。权衡：`save` 调用极广，纳入后后台/跨租户保存须先 `setTenantId` 或显式关闭。
- **说明**：原 `saveWithVersionCheck` 走 `updateByCriteria`（已在核心范围），D2 已退役为 SDK `update(entity)`（由 `TenantContext` 提供租户护栏）；本项仅补 `save`/`update` 直写路径的 tenant_id 追加。

### 3. 后台链路便捷包装（降低逃生舱误用）

- 提供 `TenantContext.runWith(tenantId, () -> repo.xxx())` 或 `Repository.withTenant(tenantId, ...)`，让 Outbox 中继 / 定时任务"设上下文→操作→clear"成原子，降低漏设 `TenantContext` 触发 `MissingTenantContextException` 的概率（逃生舱 `disableTenantFilter()` 作为例外而非常规）。

---

## 合规与迁移

- **门禁**：本 ADR 改变 SDK 运行时行为，属 L3 框架级改动，须架构师审批 + 本 ADR 接受后方可实现。
- **回归范围**：7 个后端模块全量 `mvn test`；重点核对各模块 Outbox 中继、定时任务、`platform:*` 跨租户管理查询。
- **迁移顺序**：先合 SDK（含逃生舱 + 单测）→ 各模块冒烟（后台链路补 `setTenantId`/`disableTenantFilter`）→ 再简化业务仓储手写 `eq("tenantId")`。
- **文档同步**：接受后更新 `Bone-DDD-最终实践方案.md` E-0.3 下游清单、`Bone-多租户规范.md` §3 标记"已落地（SDK 自动注入）"、ADR README 索引加 0029。

### 实现状态（2026-09-18）

- **SDK 已实现并合入**：`TableMetadata`/`TableMetadataResolver` 租户识别（按 `fieldName==tenantId` 或列名 `tenant_id`/`tenantId`）、`Criteria#disableTenantFilter()`、`MissingTenantContextException`、`TenantFilterInjector`、`SelectBuilder`/`CountBuilder`/`ConditionalUpdateBuilder`/`DeleteBuilder`/`DynamicUpdateBuilder` 注入、`BaseRepository#deleteById`/`deleteByIds` 护栏、`BatchInsertBuilder`/`UpsertBuilder` 插入时从 `TenantContext` 补/校正 `tenant_id`。
- **租户取值精炼（复核后采纳）**：`TenantFilterInjector` 取值优先级 = ① 可信 `TenantContext`（HTTP 路径，caller tenantId 被忽略 + WARN）→ ② 上下文为空且 caller 主表显式 `EQ` 限定单租户则以 caller 值兜底（后台 / 跨租户 admin 既有行为，仍单租户隔离）→ ③ 两者皆无才失败关闭。该精炼消除了"后台/跨租户 admin 调 `eq("tenantId")` 而上下文为空"的级联断点，同时不破 Outbox 中继（其无租户条件，仍须 `disableTenantFilter()`）。`disableTenantFilter()` 分支打 WARN 审计线索。
- **SDK 测试**：232 项全过（`TenantFilterInjectorTest` 锁定租户识别 / 失败关闭 / 逃生舱 / caller-EQ 兜底 / 上下文优先 / 插入补值契约）。
- **业务模块已修**：`OrderOutboxRelayPortAdapter.relayPending()` 与 `IntegrationOutboxRelay.relayBatch()` 已加 `disableTenantFilter()`（跨租户全量扫描 PENDING）。
- **待办（各业务模块）**：① 7 个模块全量 `mvn test` 回归；② 其余后台查询若既无 `TenantContext` 又未显式 `eq("tenant_id")` 且非 `disableTenantFilter` → 触发 `MissingTenantContextException`（预期失败关闭，须补 `setTenantId`/`disableTenantFilter`）；③ 业务仓储手写 `eq("tenantId",…)` 在 HTTP 路径被 SDK 忽略并 WARN、后台路径作兜底，可逐步移除。

### @Sql / 外置 `.sql` 通道扩展（ADR-0030，2026-09-19）

- **原缺口已关闭**：本 ADR 原先只覆盖 Criteria 通道（SELECT/COUNT/UPDATE/DELETE 构建器自动注入）。`@Sql` / 外置 `.sql` 通道此前完全不经过 `TenantFilterInjector`，是租户隔离的盲区——这正是 ADR-0030「单一仓储合并」决策的**根因**。
- **新增 `@TenantScope` + `TenantSqlRewriter`**：`bone-metadata-sdk` 在 `@Sql` 通道最终 SQL 上做 fail-closed 注入，复用本 ADR"可信上下文优先 / 失败关闭 / `MissingTenantContextException`"同一不变量。四模式 `AUTO/MANUAL/ALL/BYPASS`；`AUTO` 用锚点标记 `/*bone:tenant*/`（联表须带别名），JOIN 无锚点失败关闭；默认 `MANUAL`（向后兼容）。详见 [ADR-0030](./0030-domain-repository-read-merge.md) §1.3 与 §10.2。
- **影响与边界**：`@Sql` 读侧仓储（E-4.4）现在可与 Criteria 通道同享租户护栏；但既存 `@Sql` 方法默认 `MANUAL`（不注入），作者须显式选 `AUTO`（放锚点）或 `ALL`/`BYPASS`（登记授权）方获自动注入——避免一次性击碎存量。MANUAL 下"漏写租户条件"由 ADR-0030 §4 的 R4 lint 兜底。
