# 设计：单一仓储 + 外置 `.sql` 优先 + `@TenantScope` 自动租户注入

> 状态：**已并入 [ADR-0030](./adr/0030-domain-repository-read-merge.md)（唯一真源）**。本文只作机制论证留档，不再单独维护；后续改动一律落在 ADR-0030。
> 原文状态说明：设计稿（待审核），不动代码，仅给出方案、改动点与待拍板清单。
> 审核通过后，再按「实施分阶段」落地。

---

## 0. 背景与结论

### 0.1 当前真源事实（已核实代码）
- **两通道语义不同**：`Repository<Order,Long>` 的 Criteria DSL 方法**自动注入租户+软删**（ADR-0029，`TenantFilterInjector`）；`@Sql` / 外置 `.sql` 走 `SqlMethodHandler → SqlProcessor → SqlExecutor`，**完全不经过 `TenantFilterInjector`**——当前 `@Sql` 默认**不注入租户**（缺租户条件 = 跨租户数据泄漏风险）。
- **加载优先级已支持两种模式**：`SqlConfigProperties.template.loadPriority` 取 `annotation-first`（默认）或 `classpath-first`，配合 `fallbackEnabled=true`。即"外置 `.sql` 优先 + `@Sql` 兜底/覆盖"的能力**SDK 已具备**，只是默认值是 `annotation-first`。
- **外部 `.sql` 路径已固定**：`classpath:/sql/<package>/<RepoClass>/<methodName>.sql`（标准格式 `package.RepoClass.methodName`）。
- **`RepositoryFactoryBean` 缺陷**：`initializeHandlers()` 用 `doWithMethods` 遍历接口**全部**方法，对非 `default`、非 `BaseRepository` 的方法一律 `loadTemplate`。接口里写 `static` 辅助方法会触发 `TemplateNotFoundException` 致启动失败（已实机踩过）。
- **SQL 解析层级**：`MyBatisSqlProcessor` 的 AST 是 MyBatis 动态标签级（`IfNode/WhereNode/...` + `TextNode` 原始文本），**不是 SQL 语法树**——无法直接定位 WHERE/JOIN 做语法级注入。

### 0.2 设计目标
1. **合并**：`OrderRepository`（写）+ `OrderReadRepository`（读）折叠为**单一 `OrderRepository`**。
2. **外置优先**：自定义 SQL 默认放外置 `*.sql` 文件，`@Sql` 注解作为覆盖/兜底仍可用。
3. **租户安全**：`@Sql` / 外置 `.sql` 通道也能**自动注入租户**（对标 MP 的 `TenantLineInnerInterceptor`），消除"手动优先"的安全债。
4. **规范对齐**：E-4.1 / E-4.2 / CORE-05 / ADR-0029 相应放宽"读写物理分离"的硬约束。

### 0.3 核心结论
> 合并之所以此前不可行，根因是 **`@Sql` 通道不注入租户**。一旦 SDK 提供 `@TenantScope(AUTO)` 自动注入（fail-closed），"自动租户 vs 手动租户"的差别消失，**物理分离不再是安全护栏，可安全合并**。规范随之从"强制分离"改为"可选合并"。

---

## 1. SDK 优化设计

### 1.1 加载优先级：外置 `.sql` 优先（classpath-first）

| 项 | 现状 | 改后 |
|---|---|---|
| `loadPriority` 默认 | `annotation-first` | **`classpath-first`**（外置优先） |
| `fallbackEnabled` | `true` | `true`（保持） |

**共存规则（两种模式都支持）**：
- `classpath-first`：先找 `sql/<pkg>/<Repo>/<method>.sql`（外置）；找不到再回退 `@Sql` 内联。→ **外置优先、`@Sql` 覆盖**。
- `annotation-first`：先 `@Sql` 内联；找不到回退外置。→ **内联优先、外置兜底**（保留旧行为）。

**风险**：翻默认后，凡"同名外置文件存在"即覆盖 `@Sql` 内联——这是预期行为，但需排查现有 `@Sql` 方法是否误命中同名文件。当前 blueprint 无外置 `.sql`，影响面仅新增文件，**风险可控**。

**决策点**：是否翻默认（见 §6-1）。

### 1.2 `@TenantScope` 注解（新增，`domain.annotation`）

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScope {
    Mode value() default Mode.MANUAL;   // 见下
    enum Mode {
        AUTO,    // SDK 从 TenantContext 自动注入 tenant_id（fail-closed，无上下文即抛）
        MANUAL,  // 调用方自行在 SQL 里写 tenant_id（当前默认行为，向后兼容）
        ALL,     // 跨租户扫描（定时 Job / Outbox 中继），不注入不抛
        BYPASS   // 平台级运维操作逃生舱
    }
}
```

- **不设置隐式默认**：要求每个 `@Sql` / 外置 `.sql` 方法**必须显式标注** `@TenantScope`，缺省即启动期报错（`TemplateLoadException`）。强制显式决策，杜绝静默踩坑。
- 复用现有 `TenantFilterInjector` 的 `MissingTenantContextException` 与"可信上下文优先 / caller-EQ 兜底 / 失败关闭"语义，**与 Criteria 路径完全一致**。

### 1.3 自动注入实现：两种方案

#### 方案 A（推荐，对标 MP）：`TenantSqlRewriter` 语法级注入
- 引入 **`net.sf.jsqlparser`（Apache-2.0，轻量、业界标准）** 到 SDK（已确认 Bone 当前 SDK 未引，需新增；`bone-metadata-engine` 未直接依赖，独立引入无冲突）。
- `TenantSqlRewriter.rewrite(sql, tableMeta, tenantId)`：解析 AST → 在**最外层 SELECT 的 WHERE** 注入 `tenant_id = ?`（JOIN 场景下注入到主表条件 / WHERE，不污染子查询），绑定 `_sdk_tenant_id` 参数。
- 软删处理：`@TenantScope(autoDelete=true, table="t_order")` 显式给出表名时，额外注入 `deleted = 0`。**不自猜表名**（投影方法返回的是 DTO 而非实体，无实体元数据）——软删为**显式 opt-in**，避免误注入。
- 注入点：`SqlMethodHandler.invoke()` 在 `processor.process()` 之后、`createCompiledQuery()` 之前，若方法 `@TenantScope(AUTO)` 则对 `processed.getSql()` 重写并往 `params` 塞 `_sdk_tenant_id`。

#### 方案 B（MVP，零新依赖）：受控占位符 `<tenant-filter/>`
- 复用现有 MyBatis 标签 AST，新增 `TenantFilterNode`：作者在外置/内联 SQL 的 `<where>` 内写 `<tenant-filter/>`，SDK 展开为 `AND tenant_id = #{_sdk_tenant_id}` 并绑定上下文。
- 优点：不加依赖、实现简单、CI 可 grep 校验 `<tenant-filter/>` 存在性。
- 缺点：非"全自动"，作者需放占位符（但位置作者可控，比方案 A 更安全可读）。

**建议**：P1 用方案 B 快速闭环安全债；P2 视复杂度升级方案 A 实现 MP 式全自动。两者注解完全一致，可平滑切换。

### 1.4 `RepositoryFactoryBean` 静态方法缺陷修复
`shouldSkipMethod(Method)` 增加 `Modifier.isStatic(method.getModifiers())` 跳过——接口里的 `static` 辅助方法不再触发 `loadTemplate` 启动失败。
- 注：本就不应在 Repository 接口写 static 方法（SDK 用 `default` 承载逻辑），但加防御避免误用致启动崩溃。

### 1.5 新增治理检查（CI）
`scripts/ci/` 或 `check-ddd-*` 系列新增一条：**对租户表相关的 `@Sql` / 外置 `.sql` 方法，扫描是否标注 `@TenantScope`；缺失即红**。防止合并后读方法忘标租户作用域。

---

## 2. Blueprint 合并重构设计

### 2.1 目标结构（单一仓储）
```
domain/repository/OrderRepository.java        // 唯一仓储：extends Repository<Order,Long>
  ├─ 写方法：继承自 Repository（save/insert/update/findById…）  ← 聚合根写路径
  └─ 读方法：声明投影查询（@Sql / 外置 .sql / default Criteria），均 @TenantScope
domain/model/OrderWithItemsProjection.java    // 读模型（从 infrastructure/query 迁回 domain）
domain/model/OrderHeadProjection.java
application/query/port/OrderQueryPort.java    // 见 2.5 取舍
```

### 2.2 删除清单
- `infrastructure/query/OrderReadRepository.java`
- `infrastructure/query/PaymentQueryAdapter.java`（同模式，一并折）
- 原 `OrderQueryAdapter.java`（阶段二已删，确认无残留）
- 外置文件命名随新仓储：`sql/com/bone/blueprint/domain/repository/OrderRepository/findOrderWithItems.sql` 等。

### 2.3 投影 DTO 归属
原投影类在 `infrastructure/query` 或 `application/query/dto`。合并后 `OrderRepository`（domain）返回它们 → **必须迁到 `domain/model`（domain 读模型）**，保持 domain 自洽、不反向依赖 application/infrastructure。应用层如需出参 DTO 自行 map。

### 2.4 方法示例（改造后）
```java
public interface OrderRepository extends Repository<Order, Long> {

  // —— 读：外置 .sql（classpath-first 优先加载 findOrderWithItems.sql）——
  @TenantScope(AUTO)                                  // 自动注入 tenant_id（fail-closed）
  List<OrderWithItemsProjection> findOrderWithItems(@Param("orderId") long orderId);

  // —— 读：全租户扫描 Job（定时线程无上下文）——
  @TenantScope(ALL)                                   // 不注入、不抛
  List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(@Param("before") Timestamp before);

  // —— 读：Criteria 默认方法（自动租户，无需 @TenantScope，走既有注入器）——
  default Optional<OrderStatus> findStatusById(long tenantId, long orderId) {
    return findOneByCriteria(Criteria.of(Order.class).eq("id", orderId)).map(Order::getStatus);
  }
}
```
> 注：`@TenantScope(AUTO)` 方法**不**在签名里收 `tenantId` 参数（租户由 SDK 从上下文注入）；`MANUAL` 方法仍由调用方传 `tenantId`。

### 2.5 应用层取数（取舍，见 §6-4）
- **方案 X（推荐，最"合并"）**：删除 `OrderQueryPort`，应用服务直接注入 `OrderRepository` 调读方法。接口可被 mock，测试缝不丢。依赖方向 application→domain 合法。
- **方案 Y（保留端口）**：`OrderQueryPort` 迁为 **domain 自有端口**（`domain/port/OrderQueryPort`），`OrderRepository implements OrderQueryPort`，应用仍注入端口。多一层抽象、更"纯"，但 `OrderRepository` 同时是仓储又是端口，略重。

---

## 3. 规范调整（逐条改前/改后）

| 条目 | 改前 | 改后 |
|---|---|---|
| **E-4.1**（写侧仓储白名单） | 仓储只声明写方法，读方法走独立读仓储 | 允许 `domain/repository/XxxRepository` 同接口声明读投影方法；读方法须租户安全（`@TenantScope(AUTO)` 或 Criteria） |
| **E-4.2**（读侧投影物理分离） | 必须 `infrastructure/query/XxxReadRepository` 物理分离 | 改为**可选**：可同仓储声明（推荐），也可保留独立读仓储；不论哪种读方法必须租户安全。原"条件性合并（待 SDK 能力就绪）"→ **"已启用（SDK 提供 `@TenantScope`）"** |
| **CORE-05** | 读写仓储分离 | 允许单一仓储承载读写，前提是租户安全通道就绪 |
| **ADR-0029** | 租户注入仅限 Criteria DSL | 扩至 `@Sql`/外置 `.sql` 通道（`@TenantScope(AUTO)` + `TenantSqlRewriter`），关闭原缺口 |
| **新 ADR-0030** | — | 记录"单一仓储 + `@TenantScope`"决策：为何合并、为何此前不可、安全前提 |

> 同步更新：`doc/_generated/blueprint/compliance.json`（删除读仓储/适配器后重算）、`doc/agents/03-架构分层规范.md` 对应段落。

---

## 4. 风险与验证

### 4.1 风险清单
| 风险 | 级别 | 缓解 |
|---|---|---|
| 翻 `loadPriority` 默认致同名外置覆盖 `@Sql` | P2 | 排查现有 `@Sql` 方法；blueprint 当前无外置文件，影响面小 |
| 方案 A 引 `jsqlparser` 增加依赖体积/学习成本 | P2 | 先方案 B 闭环；A 为可选升级 |
| `@TenantScope` 遗漏标注 | P0→已防 | 启动期报错 + CI 扫描双重拦截 |
| `AUTO` 无 TenantContext 致查询失败 | 预期行为 | fail-closed 与 Criteria 一致；定时 Job 用 `ALL` |
| 软删自猜表名误注入 | P1 | 软删仅 `@TenantScope(autoDelete=true, table=)` 显式 opt-in |

### 4.2 验证步骤
1. **SDK 单测**：`@TenantScope` 四种模式各用例——AUTO 注入 tenant_id 且 fail-closed、ALL/BYPASS 不注入、MANUAL 原样；占位符/重写器行为。
2. **blueprint 重构**：`mvn -o -pl bone-blueprint test` 全绿；`mvn -o spotless:check` 通过。
3. **三道治理检查**：`check-ddd-doc-drift.py` / `check-ddd-doc-code-sync.py --strict` / `check-ddd-gate-state.py`(+generate --check) 全绿。
4. **外置加载实证**：放 `findOrderWithItems.sql`，确认 `classpath-first` 下被加载（日志 `成功加载模板 ... source=classpath:/sql/...`）。

---

## 5. 实施分阶段（审核通过后）

| 阶段 | 内容 | 产出 |
|---|---|---|
| **P1** | SDK：`@TenantScope` 注解 + 方案 B 占位符注入 + `RepositoryFactoryBean` static 修复 + 治理检查 | SDK 租户安全通道就绪 |
| **P2** | blueprint：删除 `OrderReadRepository`/`PaymentQueryAdapter`，合并入 `OrderRepository`，投影迁 domain，外置 `.sql` 落地 | 单一仓储试点 |
| **P3** | 规范落档：E-4.1/E-4.2/CORE-05/ADR-0029 改写 + 新 ADR-0030 + compliance.json 重算 | 文档与代码同批 |
| **P4** | （可选）方案 A `jsqlparser` 全自动注入；其他模块推广 | 全平台统一 |

---

## 6. 待你拍板（条目化）

1. **加载默认**：翻 `loadPriority` 默认 → `classpath-first`（外置优先）？还是**不改默认、仅把 `classpath-first` 作为推荐配置**？
2. **`@TenantScope` 默认模式**：要求**每个方法显式标注**（无隐式默认，最安全）？还是给个默认 `MANUAL`（向后兼容、少改现有）？
3. **自动注入方案**：先上 **方案 B 占位符**（零依赖、快）？还是直接 **方案 A `jsqlparser`**（对标 MP 全自动）？
4. **合并后应用层**：直接注入 `OrderRepository`（删 `OrderQueryPort`，方案 X）？还是保留 domain 端口（方案 Y）？
5. **推广范围**：仅 blueprint 试点（P1–P3）？还是同步规划其他模块（P4）？
6. **软删自动注入**：`@Sql` 通道的软删走 **显式 opt-in（`autoDelete=true, table=`）**？还是暂不自动（作者手写 `deleted=0`）？

> 以上 6 点确认后，我再出具体改动（SDK 代码 + blueprint 重构 + 规范修订），按 P1→P4 顺序落地并跑齐验证。
