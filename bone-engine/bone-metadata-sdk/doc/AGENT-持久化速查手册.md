# Bone Metadata SDK — AI Agent 持久化速查手册

> **给谁用**：改造 bone 仓库、需要落持久化代码的 AI Agent / 开发。
> **怎么用**：先按 §1 决策树选通道，再按 §3 复制骨架，最后用 §5 坑位清单自检。
> **事实来源**：`src/main/java`（本文档所有行为均已对照实现，标注了类名）。
> 体系性说明见 [最佳实践方案](./Bone-Metadata-SDK-最佳实践方案.md)，日常 API 见 [使用指南](./使用指南.md)。

---

## 1. 决策树：这段代码该走哪条通道

```text
要持久化？
├─ 写侧（INSERT/UPDATE/DELETE）
│   └─ Repository 内置方法（insert/update/save/deleteById/...）        → §3.1
│      └─ 例外：跨行 UPDATE/DELETE 用 updateByCriteria/deleteByCriteria
│
└─ 读侧（SELECT）
    ├─ 单表 + 条件/排序/分页 + 要 total
    │   ├─ 类型安全优先 → Criteria + pageByCriteria                    → §3.2
    │   └─ 链式优先     → repository.where(...).page(n, size)          → §3.3
    │
    ├─ 单表 + 聚合/GROUP BY → aggregate(...)
    │
    ├─ 多表 JOIN / 扁平投影 / 报表
    │   └─ @Sql 自定义仓储方法（MyBatis 风格动态 SQL）                 → §3.4  ★首选
    │       └─ 租户策略必须用 @TenantScope 逐方法显式声明              → §3.5
    │
    └─ 跨租户全表扫描（Outbox 中继 / 定时任务）
        ├─ 走 Criteria/SqlBuilder → 必须显式 disableTenantFilter()      → §4.2
        └─ 走 @Sql               → 必须标注 @TenantScope(ALL)          → §4.2
```

**一句话判据**：能用内置方法就别写 SQL；要 JOIN/投影就用 `@Sql`；**不要手写 `JdbcTemplate`**（违反平台持久化 P0）。

**两条铁律**：

1. 仓储必须 `extends Repository<T, ID>`，**不要**继承 `BaseRepository`。
2. 只加 `@EnableSqlRepositories` 不会自动扫描——`basePackages` 必须覆盖你的接口所在包（接口**简单名以 `Repository` 结尾**才会被扫描）。

---

## 2. 三条通道对照

| 维度 | Criteria / 内置方法 | DSL `FluentQuery` | `@Sql` 自定义方法 |
|---|---|---|---|
| 入口 | `findByCriteria` / `pageByCriteria` | `repo.where(...)` / `QueryBuilder.from(X)` | 接口方法 + `@Sql("...")` |
| 底层 | `SqlBuilder` → `SqlExecutor` | `SqlExecutor` | `SqlTemplateLoader` → `SqlProcessor` → `SqlExecutor` |
| 租户注入 | **自动**（ADR-0029） | 同左（走 SqlExecutor/Criteria 编译） | 由 `@TenantScope` 决定：默认 `MANUAL` **不注入**；`AUTO` 自动注入且失败关闭；`ALL` 全租户（§3.5） |
| 软删过滤 | **自动** `deleted=false` | 自动 | **不注入**，自己写；`@TenantScope(softDelete = true)` 可追加（仅单表，§3.5） |
| 多表 JOIN | 不适合 | 不适合 | **支持** |
| 返回 DTO/投影 | 不支持（只能实体） | 不支持（只能实体） | **支持** `List<DTO>` |
| 分页带 total | `pageByCriteria` → `PageResult` | `page(n,size)` → `PageResult` | **不支持**，见 §5 坑 4 |
| SQL 位置 | Java（Lambda） | Java（Lambda） | 注解内 或 `resources/sql/<包路径>/<接口简名>/<方法名>.sql`（默认**外置优先**） |

---

## 3. 可直接复制的骨架

### 3.1 写侧

```java
public interface OrderRepository extends Repository<Order, Long> { }

// 使用（实体继承 TenantAbstractEntity 时，tenantId 由 SDK 从 TenantContext 解析并覆盖实体值）
Long id = orderRepository.insert(order);          // INSERT，返回主键
boolean ok = orderRepository.update(order);       // 只更新非 null 字段
orderRepository.batchInsert(list);
```

### 3.2 读侧 · Criteria（要 total 就用这个）

```java
Criteria<Order> c = Criteria.<Order>create()
    .eq(Order::getStatus, OrderStatus.PAID)      // 优先 SFunction，别用裸字符串
    .eq(status != null, Order::getStatus, status) // 首个 boolean 是条件开关
    .in(Order::getId, idList)
    .orderByDesc(Order::getCreatedAt)
    .page(pageNum, pageSize);                    // 必须显式分页
PageResult<Order> page = orderRepository.pageByCriteria(c);
```

可用条件（均有 `(boolean, SFunction, v)`、`(SFunction, v)`、`(String, v)` 三套重载）：
`eq` `ne` `gt` `lt` `like` `in` `notIn` `between` `isNull` `isNotNull`；排序 `orderByAsc/orderByDesc`；分组 `or(Consumer<Criteria<T>>)`。

### 3.3 读侧 · DSL

```java
List<Order> list = orderRepository.where(Order::getStatus).eq(OrderStatus.PAID)
    .and(Order::getCreatedAt).gt(start)
    .orderByDesc(Order::getCreatedAt)
    .limit(100)
    .list();

PageResult<Order> page = orderRepository.where(Order::getStatus).eq(OrderStatus.PAID)
    .page(pageNum, pageSize);
long n = orderRepository.where(Order::getStatus).eq(OrderStatus.PAID).count();
```

`FluentQuery` 终端方法只有：`list()` / `count()` / `page(int,int)` / `exists()` / `singleOpt()` / `first()`。
**没有** `findOne()`、没有无参 `page()`、没有 `list(Class)` 投影重载。

### 3.4 读侧 · `@Sql` 自定义方法（JOIN / 投影 / 全租户首选）

```java
@SqlFragment(id = "orderCols", value = "t.id, t.tenant_id, t.status, t.created_at")
public interface OrderRepository extends Repository<Order, Long> {

  // AUTO：租户条件由 SDK 注入（调用方不再传 tenantId）；缺上下文即失败关闭
  @TenantScope(TenantScopeMode.AUTO)
  @Sql("""
       SELECT <include refid="orderCols"/>
       FROM t_order t
       WHERE t.status = #{status}
       ORDER BY t.created_at DESC
       """)
  List<OrderHeadProjection> findRecentByStatus(@Param("status") String status);

  // AUTO + JOIN：必须放锚点 /*bone:tenant*/，否则 SDK 拒绝执行（JOIN 别名歧义）
  @TenantScope(value = TenantScopeMode.AUTO, column = "t.tenant_id")
  @Sql("""
       SELECT t.id AS order_id, i.id AS item_id, i.product_name, i.quantity
       FROM t_order t
       LEFT JOIN t_order_item i ON i.order_id = t.id AND i.deleted = 0
       WHERE t.deleted = 0 /*bone:tenant*/
         <if test="orderId != null"> AND t.id = #{orderId} </if>
       """)
  List<OrderWithItemsProjection> findOrderWithItems(@Param("orderId") Long orderId);

  // ALL：全租户扫描，仅限已登记的运维型定时任务 / Outbox 中继
  @TenantScope(TenantScopeMode.ALL)
  @Sql("""
       SELECT <include refid="orderCols"/>
       FROM t_order t
       WHERE t.deleted = 0 AND t.status = 'CREATED' AND t.created_at < #{before}
       """)
  List<OrderHeadProjection> findExpiredUnpaidOrdersAllTenantsBySql(@Param("before") Timestamp before);
}
```

要点：

- 接口放**读侧包**（如 `infrastructure/query`）时，务必把该包加进 `@EnableSqlRepositories(basePackages = {...})`。
- 参数用 `@Param("name")`；不写则用编译期参数名（需 `-parameters`），否则退化为 `arg0`。
- 列必须**别名成 DTO 字段名**（`t.id AS order_id` → 字段 `orderId`）。
- DTO 必须有**无参构造器**（见 §5 坑 3）。
- **每个 `@Sql` 方法都应显式标注 `@TenantScope`**（见 §3.5）：漏标不会报错，但等同于 `MANUAL` 裸奔。

### 3.5 `@Sql` 的租户策略：`@TenantScope`（必看）

`@Sql` 通道**不经过** `TenantFilterInjector`（ADR-0029），所以租户策略只能靠方法上的 `@TenantScope` 声明：

| 模式 | 行为 | 用在哪 |
|---|---|---|
| `AUTO` | 从可信 `TenantContext` 注入；**无上下文即抛 `MissingTenantContextException`**（失败关闭） | 绝大多数"当前租户"查询 |
| `MANUAL` | 不注入，租户条件自己写。**默认值**（向后兼容）；未标注的 `@Sql` 即此模式 | 需自己控制租户谓词位置的老代码 |
| `ALL` | 不注入、不限租户 | 已登记的运维型定时任务 / Outbox 中继（定时线程无请求上下文，按"当前租户"扫只会落到平台租户 0） |
| `BYPASS` | 跳过滤且**不要求 `TenantContext`** 的平台逃生舱 | 确实要越租户的基础设施链路，须 `platform:*` 授权 + 审计 |

```java
@TenantScope(value = TenantScopeMode.AUTO,  // 默认 MANUAL
             column = "t.tenant_id",        // 默认 "tenant_id"；JOIN 必须带别名
             softDelete = true)             // 默认 false；true 追加 AND deleted = 0（裸列名，仅单表）
```

`AUTO` 的注入位置（`TenantSqlRewriter`）：

1. **优先锚点**：SQL 里放 `/*bone:tenant*/`，SDK 替换为 `<column> = :__boneTenantId__`。**JOIN / 子查询必须用锚点**（否则别名歧义）。
2. **无锚点的简单查询**：单表、无 JOIN/子查询时启发式注入（已有 `WHERE` 就在其后追加 `AND ...`；没有就在 `GROUP BY` / `ORDER BY` 等顶层边界前补 `WHERE`）。
3. **无锚点的复杂查询**：直接抛 `IllegalStateException` 要求作者补锚点 —— **失败关闭，绝不静默漏注**。

> 实现位置：`RepositoryFactoryBean#invoke` 仅在 `AUTO` 时调用 `TenantSqlRewriter`，
> 租户值取自 `TenantContext.getTenantIdAsLong()`；因此 `@Sql` 注解与外置 `.sql` 模板**同等生效**。

---

## 4. 租户与软删（最容易踩）

### 4.1 自动注入（Criteria / DSL / 内置方法路径）

`TenantFilterInjector`（ADR-0029）对 `tenant_scoped` 表强制单租户隔离，取值优先级：

1. `TenantContext`（JWT Filter 写入）— 可信值；此时 caller 自带的 `tenantId` EQ 被忽略并 WARN；
2. 上下文为空 + caller 已显式 `eq(tenantId, x)` 限定单租户 → 用 caller 值；
3. 两者都无 → **失败关闭**，抛 `MissingTenantContextException`。

INSERT 时 `tenantId` 以 `TenantContext` 为准（与实体值不一致会覆盖并 WARN）。

> `@Sql` 通道**不走**这条注入器，其租户策略由 `@TenantScope` 声明 —— 见 §3.5。

### 4.2 逃生舱

- Criteria / DSL 路径：`Criteria.create().disableTenantFilter()`（跨租户扫描，如 Outbox 中继）。会打 WARN，必须在应用层保证 `platform:*` 授权 + 审计。
- `@Sql` 路径：显式标注 `@TenantScope(TenantScopeMode.ALL)`（全租户）或 `BYPASS`（不要求上下文）——**不再靠"SQL 里没写 `tenant_id`"隐式达成**，必须登记授权与审计（见 §3.5）。

### 4.3 软删

`findById` / `findByCriteria` / `pageByCriteria` / `countByCriteria` 自动追加 `deleted = false`；
要含已删数据用 `findByIdIncludingDeleted` / `findByIdsIncludingDeleted`。
**`@Sql` 默认不注入软删**，自己写 `AND t.deleted = 0`；或标注 `@TenantScope(softDelete = true)` 让 SDK 在租户条件后追加（生成的是裸列名 `deleted = 0`，仅适用单表，见 §3.5）。

---

## 5. 坑位清单（写完自检）

| # | 坑 | 现象 | 对策 |
|---|---|---|---|
| 1 | `#{x}` 求值为 **null** | `SqlProcessingException: #{x} is null` | 可选参数一律用 `<if test="x != null">` 包住 |
| 2 | `${x}` 直接拼接 | 值必须匹配 `^[a-zA-Z0-9_]+$`，否则 `SqlInjectionRiskException` | 仅用于白名单化的表名/列名；参数名恰为 `tableName` 时必须登记 `metadata.sdk.sql.security.allowed-tables` |
| 3 | DTO 无**无参构造器** | `Failed to instantiate` | `SmartRowMapper` 用 `BeanUtils.instantiateClass`；补无参构造器即可（字段由反射填充，**不需要 setter**） |
| 4 | `@Sql` 返回 `PageResult` | 代理**无 PageResult 分支**，结果被误映射 | 改为 `List<DTO>` + 独立 COUNT 方法，在调用方 `PageResult.of(...)` 组装 |
| 5 | `<include refid>` 找不到片段 | 不报错，原样保留 `<include .../>` 文本 → SQL 语法错 | 确认 `@SqlFragment(id=)` 与 refid 一致，且片段已缓存（同一接口） |
| 6 | 用了不支持的标签 | 标签被当普通文本原样输出 | 只支持 `if/where/foreach/choose/when/otherwise/set/trim/bind` |
| 7 | `<foreach>` 传数组 | 静默跳过（只认 `Iterable`） | 传 `List` |
| 8 | SQL 里出现 `;` 或 `DROP/ALTER/TRUNCATE/EXEC` | `SqlProcessingException` | 单语句，禁止多语句 |
| 9 | 字符串参数值含 `;` / `SELECT` 等 | `SqlProcessingException` | 参数化，别拼值 |
| 10 | `LIKE '%xxx%'` | 可能被 `validateSql` 判为注入 | 用 `LIKE CONCAT('%', #{name}, '%')`（已白名单） |
| 11 | `@Sql` 方法漏标 `@TenantScope` / 漏写租户条件 | 跨租户越权：漏标等同 `MANUAL`，SDK 不注入 | 逐方法显式标注：租户内用 `@TenantScope(AUTO)`（JOIN 记得放 `/*bone:tenant*/` 锚点）；全租户用 `@TenantScope(ALL)` 并登记授权（§3.5） |
| 12 | 只看 `Repository` 接口文档猜方法名 | `findOne()` / `findByCriteriaWithPage` / `list(Class)` **都不存在** | 以 `Repository.java`、`FluentQuery.java` 为准 |
| 13 | 两个 `SqlBuilder` 混 import | 编译过但行为错 | `query.SqlBuilder` 与 `query.dsl.QueryBuilder` 不同类 |
| 14 | 改了 D1 注解不生效 | 元数据走 Caffeine 缓存 | 重启或失效缓存 |

---

## 6. 配置前缀（写错不报错，静默用默认值）

```yaml
metadata:
  sdk:
    deploymentMode: EMBEDDED        # MetadataSdkProperties
    sql:                            # SqlConfigProperties
      database:
        type: MYSQL
        batch-size: 1000
      template:
        load-priority: classpath-first    # annotation-first | classpath-first（默认 classpath-first：外置 .sql 优先、注解兜底）
        fallback-enabled: true
        base-path: classpath:/sql/
        yaml-path: classpath:/sql-templates/
        max-template-size: 1048576
      security:
        allowed-tables: []          # ${tableName} 白名单
      monitor:
        slow-query-threshold-ms: 500
```

---

## 7. 自检命令

改 SDK 后：

```bash
mvn -pl bone-engine/bone-metadata-sdk spotless:apply
mvn -pl bone-engine/bone-metadata-sdk test
```

SDK 被业务模块依赖时还需 `mvn -pl bone-engine/bone-metadata-sdk install`（会触发 `spotless:check`）。
