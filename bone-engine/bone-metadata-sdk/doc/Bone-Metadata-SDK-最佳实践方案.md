# Bone Metadata SDK 最佳实践方案

> **唯一权威**：架构、分层与演进以本文档为准；日常 API 见 [`使用指南.md`](./使用指南.md)，
> AI / 新同学上手速查见 [`AGENT-持久化速查手册.md`](./AGENT-持久化速查手册.md)。  
> **版本**：v1.0（合并版） | **对齐代码**：`bone-engine/bone-metadata-sdk` 当前主分支实现  
> **平台约束**：[`doc/architecture/Bone-DDD-最终实践方案.md`](../../../doc/architecture/Bone-DDD-最终实践方案.md) §16～§18（D1 元数据注解、仓储空接口、读写分离）

本文档合并并去重原 `元数据整体升级方案`、`元数据sdk升级方案 1/2/3`、`DSL查询方案` 及模块内相关草案，结合 **Salesforce 元数据驱动**、**Spring Data 仓储抽象**、**MyBatis 模板 SQL**、**QueryDSL/JOOQ 类型安全查询** 等业界实践，给出**可落地**的目标架构、当前实现说明与分阶段演进路线。

---

## 目录

1. [定位与边界](#1-定位与边界)
2. [设计原则（业界 + Bone）](#2-设计原则业界--bone)
3. [当前架构（As-Is）](#3-当前架构as-is)
4. [数据访问模型](#4-数据访问模型)
5. [查询体系：Criteria 与 DSL](#5-查询体系criteria-与-dsl)
6. [`@Sql` 自定义仓储方法](#6-sql-自定义仓储方法)
7. [元数据服务](#7-元数据服务)
8. [多租户与动态数据源](#8-多租户与动态数据源)
9. [扩展字段（预留列 / JSON / EAV）](#9-扩展字段预留列--json--eav)
10. [安全与合规](#10-安全与合规)
11. [性能与可观测性](#11-性能与可观测性)
12. [业务模块接入规范](#12-业务模块接入规范)
13. [目标架构与演进路线](#13-目标架构与演进路线)
14. [反模式与禁止事项](#14-反模式与禁止事项)
15. [附录：配置要点](#15-附录配置要点)

---

## 1. 定位与边界

### 1.1 是什么

Bone Metadata SDK 是 Bone 平台**默认数据访问层**，为业务/平台模块提供：

- **写侧**：`Repository<T,ID>` 空接口 + `@EnableSqlRepositories` 动态代理 + `BaseRepository` 通用 CRUD
- **读侧**：`Criteria` 条件对象 + `query.SqlBuilder` 编译 SQL；可选 `query.dsl` 流式查询
- **复杂 SQL**：YAML / 注解 / Classpath 模板 + `Repository` 自定义方法
- **元数据**：`@Table` / `@Column` 等 D1 注解解析、`MetadataService`（嵌入式或远程）
- **横切**：动态数据源、`@DS` 路由、租户上下文联动、扩展列分配

### 1.2 不是什么

- **不是** JPA/Hibernate 或 MyBatis-Plus 的替代声明式 ORM（平台 P0 禁止业务模块以 JPA 实体 / MyBatis-Plus `BaseMapper` 为默认栈）
- **不是** 已拆分的 `bone-metadata-orm/*` 多模块工程（见 §13，属中长期目标）
- **不负责** 领域不变式与用例编排（归属 `application` 层 Handler）

### 1.3 与 `bone-datasource` 的关系

- **bone-datasource**：连接池、多数据源路由、与 Bone 租户配置联动  
- **bone-metadata-sdk**：在已选数据源上完成 SQL 构建、执行与元数据映射  
- 业务模块同时依赖两者时，以 **`doc/agents/03-架构分层规范.md` §5.4** 为准

---

## 2. 设计原则（业界 + Bone）

| 原则 | 业界参照 | Bone 落地 |
|------|----------|-----------|
| **元数据为骨架** | Salesforce Custom Object / Field | `TableMetadataResolver` + D1 注解；动态字段走扩展字段三模式（预留列/JSON/EAV） |
| **仓储抽象** | Spring Data `Repository` | 领域 `XxxRepository extends Repository<Entity, Id>`，禁止手写 JDBC 作为主路径 |
| **类型安全查询** | QueryDSL Lambda、JOOQ DSL | `Criteria` + `SFunction`；`query.dsl.FluentQuery` |
| **SQL 显式可控** | MyBatis 模板 | `UnifiedSqlTemplateLoader` + 多解析器（SQL/YAML/MyBatis 片段） |
| **参数化执行** | OWASP JDBC 指南 | 值一律绑定参数；列名/表名白名单校验 |
| **租户内置** | SaaS Row-Level Security | `TenantContext` + 拦截器/SQL 守卫注入 `tenant_id` |
| **存储方言** | jOOQ / Hibernate Dialect | `sql.dialect.*`、`DatabaseDialect`、按库类型 LIKE/分页差异 |
| **渐进演进** | 绞杀者模式 | Criteria 与 DSL 双轨并存，底层逐步收敛到统一查询引擎 |

---

## 3. 当前架构（As-Is）

### 3.1 运行时主链路

```text
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.{module}.domain.repository")
        │
        ▼
RepositoryRegistrar / ClassPathRepositoryScanner
        │
        ▼
RepositoryFactoryBean → JDK Proxy
        ├── 接口方法 → SqlMethodHandler（模板/注解 SQL）
        └── BaseRepository 方法 → BaseRepository 委托（Criteria + SqlBuilder + SqlExecutor）
        │
        ▼
NamedParameterJdbcOperations（SqlExecutor）
```

### 3.2 包结构（与仓库一致）

```text
com.bone.metadata.sdk
├── Repository.java / BaseRepository.java      # 仓储契约与默认实现
├── domain/           # 注解、TableMetadata、Criteria 编译模型、异常
├── query/
│   ├── criteria/     # Criteria / Condition（读侧主路径）
│   ├── builder/      # SelectBuilder、CountBuilder 等
│   ├── dsl/          # FluentQuery、DSL SqlBuilder（勿与 query.SqlBuilder 混淆）
│   └── SqlBuilder.java
├── sql/
│   ├── proxy/        # EnableSqlRepositories、RepositoryFactoryBean
│   ├── executor/     # SqlExecutor、SmartRowMapper
│   ├── template/     # 模板加载与安全校验
│   ├── processor/    # SQL 处理、SqlSecurityGuard
│   └── dialect/
├── metadata/         # MetadataService 嵌入式/远程/委托
├── extension/        # 扩展列、EAV、Json、Plugin
└── support/
    ├── config/       # AutoConfiguration、SqlConfigProperties
    ├── dataSource/   # DynamicDataSource、@DS
    ├── tenant/ / security/ / interceptor/
    └── util/
```

### 3.3 Spring Boot 自动配置

| 配置类 | 作用 |
|--------|------|
| `MetadataAutoConfiguration` | `MetadataService`、健康检查、`MetadataSdkContext` |
| `SqlRepositoryAutoConfiguration` | `SqlExecutor`、`SqlTemplateLoader`、`SqlBuilder` Bean |
| `UnifiedDataSourceAutoConfiguration` | 动态数据源（`bone.metadata.datasource.enabled`） |
| `InterceptorAutoConfiguration` | 审计/租户等拦截 |

启用仓储扫描：**必须**在应用类或 `@Configuration` 上使用 `@EnableSqlRepositories(basePackages = "...domain.repository")`。仅依赖自动配置**不会**扫描仓储接口。

---

## 4. 数据访问模型

### 4.1 领域仓储（推荐）

```java
// domain/repository/OrderRepository.java
public interface OrderRepository extends Repository<Order, Long> {
    // 仅声明非通用方法；CRUD 由 BaseRepository 提供
    List<Order> findByStatus(@Param("status") String status);
}
```

```java
// 启动类
@EnableSqlRepositories(basePackages = "com.bone.xxx.domain.repository")
@SpringBootApplication
public class XxxApplication { }
```

**不要**让业务模块继承 `BaseRepository`；由 `RepositoryFactoryBean` 内聚实现。

### 4.2 实体 D1 注解（与 Bone-DDD 一致）

```java
@Table("biz_order")
public class Order extends TenantAbstractEntity<Long> {
    @Id
    @GeneratedValue(strategy = GenerationStrategy.SNOWFLAKE)
    private Long id;
    // ...
}
```

- 领域类型：**仅** bone-core + bone-metadata-sdk D1 白名单注解  
- **禁止** JPA `@Entity`、`@MappedSuperclass` 等映射注解

### 4.3 写侧事务

- `BaseRepository` 的 `insert` / `update` / `delete` 等已标注 `@Transactional`  
- 应用层 `*CommandHandler` 作为用例事务边界时，避免嵌套传播冲突（默认 `REQUIRED` 即可）

---

## 5. 查询体系：Criteria 与 DSL

### 5.1 选型建议

| 场景 | 推荐 API | 原因 |
|------|----------|------|
| 平台模块标准列表/详情 | **Criteria** + `findByCriteria` / `pageByCriteria` | 与 `BaseRepository` 一体、团队已熟悉 |
| 动态条件较多、单测友好 | **Criteria** + Lambda（`SFunction`） | 字段名可重构、减少字符串列名 |
| 多表 JOIN、投影、聚合 | **DSL**（`FluentQuery`）或 **SQL 模板** | Criteria 不适合复杂 JOIN |
| 报表/原生 SQL | 仓储自定义方法 + YAML/SQL 模板 | 可读性、可评审 |

### 5.2 Criteria 示例

```java
Criteria<Order> c = Criteria.<Order>create()
    .eq(Order::getTenantId, TenantContext.getTenantId())
    .eq(Order::getStatus, OrderStatus.PAID)
    .page(pageNo, pageSize)
    .orderByDesc(Order::getCreatedAt);
List<Order> rows = orderRepository.findByCriteria(c);
```

**最佳实践**：

- 优先 `SFunction` 形式，避免裸字符串字段名（防拼 SQL 注入与拼写错误）
- **必须**显式 `page(pageNo, pageSize)`，不要依赖默认 `pageSize`（当前实现默认偏大，见 §14）
- 列表查询默认过滤软删（`deleted`）；需含已删数据用 `*IncludingDeleted` 系列 API

### 5.3 DSL 示例

```java
List<Order> list = QueryBuilder.from(Order.class)
    .where(Order::getStatus).eq(OrderStatus.PAID)
    .orderByDesc(Order::getCreatedAt)
    .limit(100)
    .list();

PageResult<Order> page = QueryBuilder.from(Order.class)
    .where(Order::getStatus).eq(OrderStatus.PAID)
    .page(pageNo, pageSize);
```

DSL 包路径：`com.bone.metadata.sdk.query.dsl`。与 `com.bone.metadata.sdk.query.SqlBuilder` **不同类**，禁止混 import。

> **DSL / Criteria 只能返回实体，没有 DTO 投影重载**（不存在 `list(XxxSummary.class)`）。
> 需要多表 JOIN、扁平投影或聚合报表，请用 §6 的 `@Sql` 自定义仓储方法。

### 5.4 双 API 收敛方向（目标）

原升级方案 1/2/3 共识：

```text
Criteria API ──adapter──┐
                        ├──► UnifiedQueryContext ──► UnifiedSqlBuilder ──► CompiledQuery
DSL API      ──adapter──┘
```

**原则**：对外保持双 API；对内只维护一套编译与方言逻辑（见 §13 阶段一）。

---

## 6. `@Sql` 自定义仓储方法

> **适用**：多表 JOIN / 扁平投影 / 聚合报表 / 全租户扫描。
> 本节是 SDK 的**第三类数据访问通道**：既不是 Criteria，也不是裸 JDBC。
> 它是「MyBatis 风格模板 SQL」在本平台的落地形态，走 `sql/template` + `sql/proxy`，最终仍由 `SqlExecutor` 执行。

### 6.1 执行链（As-Is）

```text
@EnableSqlRepositories(basePackages = "...")
        │
        ▼
RepositoryRegistrar / ClassPathRepositoryScanner     ← 只认「接口 且 简单名以 Repository 结尾」
        │
        ▼
RepositoryFactoryBean → JDK Proxy
        ├── BaseRepository 方法        → 委托 BaseRepository（Criteria + SqlBuilder + SqlExecutor）
        └── 其余方法（@Sql / 模板文件） → SqlMethodHandler
                                          ├─ SqlTemplateLoader.loadTemplate(方法, id)
                                          ├─ SqlProcessor.process(...)  解析动态标签 + 绑定参数
                                          └─ SqlExecutor.query/update    （过 SqlSecurityGuard）
```

`RepositoryFactoryBean#SqlMethodHandler.invoke` 的核心三步：

```java
SqlProcessor processor = sqlProcessorFactory.getProcessor(sqlTemplate.getSqlTemplateType());
ProcessedSql processed = processor.process(sqlTemplate.getId(), sqlTemplate.getSql(), params);
CompiledQuery cq = createCompiledQuery(processed);   // processed.getSql() + 有效参数
return executeQuery(sqlTemplate.getSqlType(), cq);   // SELECT/CTE→查询；INSERT→insert；UPDATE/DELETE→update
```

**结论**：`@Sql` 路径与写侧共用 `SqlExecutor`，因此同样受 `SqlSecurityGuard.scanForInjectionKeywords`
（每处入口均调用）约束，不存在"绕过 SDK 直连 JDBC"的问题。

### 6.2 启用与扫描

- 接口必须 `extends Repository<T, ID>`，且两个泛型参数是具体类型。
- 接口**简单名必须以 `Repository` 结尾**才会被 `ClassPathRepositoryScanner` 命中。
- 仓储接口的包必须出现在 `@EnableSqlRepositories(basePackages = {...})` 里。
  读侧仓储若放在 `infrastructure/query`（符合 DDD 读侧分层），**必须显式把该包加进扫描清单**，
  否则 Bean 不注册（表现为注入失败，而不是"SQL 找不到"）。

### 6.3 模板来源与优先级

模板 ID 固定为 `{Repository 接口全限定名}.{方法名}`。

| 优先级（默认） | 来源 | 位置 / 写法 | 模板类型 |
|---|---|---|---|
| 1 | 方法注解 `@Sql("...")` | 接口方法上 | MYBATIS |
| 2（回退） | `classpath:/sql/<包路径>/<接口简名>/<方法名>.sql` | `resources/sql/` | MYBATIS |
| 3（回退） | `classpath:/sql-templates/<接口简名><方法名>.yaml` | `resources/sql-templates/` | YAML_SQL |

- 由 `metadata.sdk.sql.template.load-priority` 切换 `annotation-first` / `classpath-first`（默认 `annotation-first`）；
  `fallback-enabled` 控制是否回退。
- 模板按 ID 走 Caffeine 缓存（`cache-size` / `expire-hours`），改文件后需 `refreshTemplate(id)` 或重启。
- 处理器映射：MYBATIS → `MyBatisSqlProcessor`，YAML_SQL → `DynamicSqlProcessor`（同时是默认），SQL → `PassThroughSqlProcessor`。
- SQL 操作类型由**首关键字自动判定**（`SELECT/INSERT/UPDATE/DELETE/WITH` → `CTE`）。

### 6.4 动态 SQL 语法（MyBatis 兼容子集）

支持标签：`if` `where` `foreach` `choose` `when` `otherwise` `set` `trim` `bind`。

| 标签 | 语义要点 |
|------|----------|
| `<if test="SpEL">` | `test` 为 SpEL，根对象是参数 Map；支持 `request.name` 嵌套、可用 `isEmpty(...)` / `isNotEmpty(...)` / `contains(...)` |
| `<where>` | 自动去掉首个 `AND`/`OR` 并补 `WHERE` |
| `<foreach collection item index open separator close>` | `collection` 必须是 **`Iterable`**（数组会被静默跳过）；`item` 默认 `item`、`separator` 默认 `,` |
| `<set>` | 去掉尾随逗号并补 `SET` |
| `<choose>/<when>/<otherwise>` | 互斥分支 |
| `<trim prefix suffix prefixOverrides suffixOverrides>` | 通用前后缀裁剪 |
| `<bind name value>` | 把 SpEL 结果写入参数 |

**其它标签会被当成普通文本原样输出**（不报错）——这是最容易埋雷的一点，写完必须核对生成的 SQL。

**占位符**：

| 写法 | 行为 |
|------|------|
| `#{expr}` | SpEL 求值 → 绑定为命名参数（嵌套 `a.b` 会扁平化为参数名 `a_b`） |
| `${expr}` | 直接字符串拼接；值必须匹配 `^[a-zA-Z0-9_]+$`，否则 `SqlInjectionRiskException` |

- **`#{...}` 求值为 `null` 会抛 `SqlProcessingException`**：可选参数必须配合 `<if test="x != null">`。
- 参数名为 `tableName` 时，值必须在 `metadata.sdk.sql.security.allowed-tables` 白名单内。
- `LIKE CONCAT('%', #{name}, '%')` 是被明确放行的写法；手写 `LIKE '%...%'` 可能被 `validateSql` 判为注入。

### 6.5 SQL 片段复用

- 接口级注解：`@SqlFragment(id = "cols", value = "...")`，缓存键 `{接口全限定名}.{id}`。
- 模板内定义：`<sql id="cols">...</sql>`。
- 引用：`<include refid="cols"/>`（递归展开，深度上限 10）。
- 文件片段：`classpath:sql/{接口全限定名转路径}/sqlFragment/*.sql`，以文件名作为片段 id。

> `<include>` 找不到片段时**不报错**，而是把 `<include refid="..."/>` 原样留在 SQL 里，最终表现为语法错误。

### 6.6 返回类型与结果映射

| 返回类型 | 处理 |
|----------|------|
| `List<T>` | `sqlExecutor.query(...)` 逐行映射 |
| `Optional<T>` | 0 行 → `empty`；>1 行 → `IncorrectResultSizeDataAccessException` |
| 简单类型 | `sqlExecutor.queryForObject(...)` |
| 其它 | 按单行处理（0 行 `null`，>1 行抛异常） |

- **没有 `PageResult` 分支**：`@Sql` 方法不要把返回类型写成 `PageResult<T>`，代理不会自动 count。
  需要分页请写 `countXxx` + `selectXxx ... limit/offset` 两个方法，在调用方 `PageResult.of(...)` 组装。
- 结果映射走 `SmartRowMapper`：`BeanUtils.instantiateClass` 创建实例后**反射填充字段**。
  ⇒ **DTO 必须有可访问的无参构造器**（不需要 setter）；`final` 字段可写但建议配无参构造器。
  ⇒ 列标签需能映射到字段名：优先按列名（下划线转驼峰 / `@Column`），再按字段名直接匹配。
  ⇒ JOIN 扁平投影请在 SQL 里写**显式别名**（`t.id AS order_id` → 字段 `orderId`）。

### 6.7 租户与软删：本通道不注入

`@Sql` 按模板原样执行，SDK **不会**追加 `tenant_id`，也**不会**追加 `deleted = 0`：

- 租户内查询：自己写 `AND t.tenant_id = #{tenantId}`。
- 跨租户扫描（Outbox 中继、定时任务全量扫描）：不写即为全租户 —— 这是本通道的主要用途之一，
  但必须在应用层登记授权（`platform:*`）与审计，不能"因为 SQL 里没写就当作默认安全"。
- 软删：自己写 `AND t.deleted = 0`（对比：Criteria/内置方法路径会自动追加）。

### 6.8 规范与检查

- 复杂 SQL 放 `infrastructure` 的 `resources/sql/`，不散落在 `domain`；读侧仓储接口可放 `infrastructure/query`。
- 只用命名参数，禁止 Java 字符串拼接用户输入。
- 动态条件一律用标签表达，不要在 Java 里拼 SQL。
- 上线前经 Code Review + 模板安全校验（`TemplateSecurityValidator`），执行前还有 `SqlSecurityGuard` 二次检查。
- 单页 ≤ 200 行；`LIMIT/OFFSET` 由调用方传入，禁止无界查询。

---

## 7. 元数据服务

### 7.1 部署模式

| 模式 | 配置 | 适用 |
|------|------|------|
| **EMBEDDED**（默认） | `metadata.sdk.deploymentMode=EMBEDDED` | 单体能访问元数据表、列分配库 |
| **REMOTE** | `deploymentMode=REMOTE` + Feign Client | 元数据由独立 `bone-metadata-server` 服务提供 |

### 7.2 职责

- 表/字段元数据、扩展列分配（`ColumnAllocator`）  
- 与 SmartMeta / 控制台建模联动（平台级）  
- **不**替代领域模型中的业务规则

---

## 8. 多租户与动态数据源

### 8.1 行级租户（ADR-0029 自动注入）

**统一入口是 `TenantFilterInjector`**，对 `tenant_scoped` 表的 SELECT / COUNT / 条件 UPDATE / DELETE
强制单租户隔离（写入 `tenant_id = :_sdk_tenant_id`）。取值优先级：

1. **可信 `TenantContext`**（JWT Filter 写入请求线程）—— 此时 caller 自带的 `tenantId` EQ 被视为不可信，
   被忽略并打 WARN；
2. 上下文为空，但 caller 已在主表显式 `eq(tenantId, x)` 限定到**单一**租户 → 用 caller 值兜底
   （跨租户 admin / 后台定时任务链路的既有行为，隔离未被破坏，不抛异常）；
3. 两者皆无 → **失败关闭**，抛 `MissingTenantContextException`。

写侧：`insert` 时 `tenantId` 以 `TenantContext` 为准；实体自带值与上下文不一致时以上下文覆盖并 WARN。

**逃生舱**：`Criteria.create().disableTenantFilter()` —— 完全退出租户注入（打 WARN）。
仅限跨租户基础设施扫描（如 Outbox 中继），且必须在应用层保证 `platform:*` 授权 + 审计。

> **例外通道**：`@Sql` 自定义仓储方法**不走** `TenantFilterInjector`，SQL 所见即所得（见 §6.7）。
> 全租户能力只能来自"SQL 里没写 `tenant_id`"，因此必须显式登记，不能默认安全。

### 8.2 动态数据源

- `@DS("slave")` / `DataSourceContextHolder`：读走从库（与 `bone-datasource` 配置一致）  
- 配置前缀：`bone.metadata.datasource`  
- **最佳实践**：事务内不切换数据源；只读查询再路由从库

### 8.3 分库分表

- 当前 SDK 提供路由钩子与文档化实践（原「极简侵入式多数据源」方案）  
- **分片键、全局 ID、跨片查询** 需在业务与 `bone-datasource` 层显式设计，SDK 不自动分片

---

## 9. 扩展字段（预留列 / JSON / EAV）

| 机制 | 类 | 适用 |
|------|-----|------|
| 预留列 | `ReservedColumnsHandler` + `ColumnAllocator` | 高频扩展、列数可控 |
| JSON 列 | `JsonHandler` | 半结构化、Schema 松散 |
| EAV | `EavHandler` | 极低频、极强扩展 |

**最佳实践**：

- 主路径字段仍建模为 D1 注解列；扩展字段通过 `Extensible` + `ExtensionCoordinator` 加载  
- 扩展列分配走元数据表，避免手工 `ALTER` 与线上锁表

---

## 10. 安全与合规

### 10.1 SQL 注入防护（分层）

1. **值**：仅 `NamedParameterJdbcTemplate` 绑定  
2. **列/表名**：`SqlSafeUtils` / `SqlInjectionPreventer` 白名单（DSL 已部分使用；Criteria 字符串字段名需统一校验——见演进项）  
3. **模板**：`TemplateSecurityValidator` + 禁止远程模板主机未白名单  
4. **执行前**：`SqlSecurityGuard` 二次检查

### 10.2 权限与审计（目标能力）

原「整体升级方案」中的字段级权限、脱敏、行级安全属 **平台能力**，建议：

- 短中期：IAM + 应用层校验 + 审计字段（`createdBy` / `updatedBy`）  
- 中长期：在 `sql.processor` 链插入 `FieldPermissionChecker` SPI

### 10.3 合规

- 敏感字段：加密/脱敏在 **infrastructure** 或独立 security 组件实现，不散落在 SQL 字符串中  
- 日志：禁止打印完整 SQL 参数（含 PII）

---

## 11. 性能与可观测性

| 项 | 实践 |
|----|------|
| 批量写 | `batchInsert` / `batchSave`，控制批次（`jdbc.batch.size`，避免无效 `@Value` 配置） |
| 分页 | 强制上限（建议单页 ≤ 200，与 `PageResult` 一致） |
| 元数据缓存 | `TableMetadataResolver` Caffeine 缓存；变更注解需重启或提供失效 API |
| N+1 | 关联查询用 JOIN 模板或 DSL，避免循环 `findById` |
| 慢 SQL | 结合 Micrometer / 日志采样；模板 ID 打入 span tag |

---

## 12. 业务模块接入规范

### 12.1 标准步骤

1. `pom.xml` 引入 `bone-metadata-sdk`、`bone-datasource`  
2. 配置 `spring.datasource` 与 `bone.metadata.*`  
3. `@EnableSqlRepositories(basePackages = "...domain.repository")`  
4. 领域实体 D1 注解 + `domain.repository` 空接口  
5. 复杂查询：`infrastructure` 放 SQL 模板；Handler 只调仓储  
6. ArchUnit：禁止 `domain` 依赖 JDBC/MyBatis/JPA

### 12.2 参考模块

- **已对齐**：`bone-masterdata`、`bone-system`、`bone-integration`、`bone-iam`、`bone-file`、
  `bone-blueprint`、`bone-metadata-server`、`bone-extension-studio`（均在启动类或 `@Configuration` 上标注
  `@EnableSqlRepositories`）
- **待对齐**：仍在 `infrastructure/query` 里手写 `NamedParameterJdbcTemplate` 的读侧适配器
  —— 这类应迁到 §6 的 `@Sql` 读侧仓储（注意 §6.6 的 DTO 无参构造器与 `PageResult` 约束），
  或提交 ADR 说明例外

### 12.3 读模型

- 简单列表：`Criteria` + 仓储  
- 复杂报表：独立 `infrastructure.query` + 模板 SQL（CQRS 读侧），不经过聚合根

---

## 13. 目标架构与演进路线

### 13.1 模块形态（中长期愿景）

原「元数据整体升级方案」建议的 `bone-metadata-orm` 多模块拆分**合理**，但作为 **Phase 3+** 目标，避免与当前单 JAR 文档冲突：

```text
bone-metadata-sdk-core          # 无 Spring Web/Redis/Feign 的最小内核
bone-metadata-sdk-starter       # AutoConfiguration、可选远程元数据
bone-metadata-sdk-test          # 测试 fixtures
```

### 13.2 分阶段路线

| 阶段 | 周期 | 交付 | 说明 |
|------|------|------|------|
| **P0 稳定** | 2～3 周 | 测试可绿、README 与启用方式修正、Criteria 分页默认值/字段名校验 | 阻塞生产信心 |
| **P1 查询统一** | 3～4 周 | `core.query` 抽象 + Criteria/DSL Adapter + 单测覆盖方言 | 合并升级方案 1/2/3 核心诉求 |
| **P2 依赖瘦身** | 2 周 | core/starter 拆分，Redis/Feign 可选 | 降低传递依赖 |
| **P3 能力增强** | 持续 | 字段权限 SPI、查询缓存、可观测性 | 对齐 Salesforce 级元数据平台 |
| **P4 模块化** | 按需 | `orm-api` / `orm-core` 物理拆分 | 仅当多团队并行且 API 稳定 |

### 13.3 明确不采纳或暂缓

- **BoneQuery 静态单例入口**（原「核心优化方案」）：与 Spring DI 冲突，改用仓储 + 可选 `FluentQuery` 即可  
- **在 SDK 内引入完整 SOQL 解析器**：复杂度高，优先模板 SQL + DSL  
- **文档中的重复包结构图**：以 §3.2 仓库实际包为准

---

## 14. 反模式与禁止事项

| 反模式 | 原因 |
|--------|------|
| 业务 `domain` 使用 JPA `@Entity` | 违反平台 P0，与 Metadata SDK 双栈 |
| 手写 `JdbcTemplate` 作为主 CRUD | 审计/租户/软删不一致 |
| `Criteria.eq("rawColumn", userInput)` 且列名来自请求 | SQL 注入风险 |
| 不分页大列表 | 默认 pageSize 过大导致 OOM/慢查询 |
| 两个 `SqlBuilder` 混 import | 编译通过但运行逻辑错误 |
| 仅依赖 AutoConfiguration 不标 `@EnableSqlRepositories` | 仓储 Bean 未注册 |
| 在 `generated/` 改代码 | CI 红线 |
| `@Sql` 方法返回 `PageResult<T>` | 代理无分页分支，结果被误映射；应返回 `List<DTO>` + 独立 COUNT，再 `PageResult.of` |
| `@Sql` 里漏写 `tenant_id` | 本通道不注入租户 → 跨租户越权；仅登记过的跨租户场景可省略（§6.7） |
| `@Sql` 的 DTO 没有无参构造器 | `SmartRowMapper` 用 `BeanUtils.instantiateClass`，抛 `Failed to instantiate` |
| `#{x}` 传入 `null` | 抛 `SqlProcessingException`；可选条件必须用 `<if test="x != null">` 包住 |
| `<foreach>` 传数组 | 只认 `Iterable`，数组被静默跳过 |
| 读侧 `@Sql` 仓储放在未被 `basePackages` 覆盖的包 | Bean 不注册，表现为注入失败而非"SQL 找不到" |
| 用 `@Sql` 做单表简单 CRUD | 绕过了租户/软删/审计自动化，应优先内置方法（§5.1） |

---

## 15. 附录：配置要点

```yaml
# 元数据 SDK（MetadataSdkProperties：prefix = metadata.sdk）
metadata:
  sdk:
    deploymentMode: EMBEDDED        # EMBEDDED | REMOTE

# SQL / 模板（SqlConfigProperties：prefix = metadata.sdk.sql）
    sql:
      database:
        type: MYSQL                 # MYSQL|H2|POSTGRESQL|ORACLE|SQLSERVER|SQLITE
        batch-size: 1000
      template:
        load-priority: annotation-first   # annotation-first | classpath-first
        fallback-enabled: true
        base-path: classpath:/sql/
        yaml-path: classpath:/sql-templates/
        max-template-size: 1048576
        cache-size: 2000
        expire-hours: 24
      security:
        allowed-tables: []          # ${tableName} 取值白名单
      monitor:
        slow-query-threshold-ms: 500
```

> 配置前缀以代码为准：`MetadataSdkProperties` = `metadata.sdk`，`SqlConfigProperties` = `metadata.sdk.sql`。
> 写错前缀**不会报错**，只会静默使用默认值。

**依赖（业务模块）**：

```xml
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-metadata-sdk</artifactId>
</dependency>
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-datasource</artifactId>
</dependency>
```

**相关文档**：

- 快速开始：[`../README.md`](../README.md)  
- API 与 DSL 用法：[`使用指南.md`](./使用指南.md)  
- **AI Agent 速查（决策树 / 骨架 / 坑位清单）**：[`AGENT-持久化速查手册.md`](./AGENT-持久化速查手册.md)  
- 数据库表设计：[`doc/architecture/数据库开发规范.md`](../../../doc/architecture/数据库开发规范.md)

---

## 文档历史

| 日期 | 说明 |
|------|------|
| 2026-05 | 合并 `doc/` 下 5 份方案为本文档；废止多份重复升级/DSL 草案 |
| 2026-09-18 | 对照 `src/main/java` 校正：新增 §6「`@Sql` 自定义仓储方法」（执行链、动态标签、占位符、片段、返回映射、**PageResult 缺口**、DTO 无参构造器约束、租户不注入）；§8.1 按 ADR-0029 重写租户注入与逃生舱；§5.3 修正 DSL 无 DTO 投影重载；§15 修正配置前缀为 `metadata.sdk` / `metadata.sdk.sql`；§14 补 9 条反模式；新增 [`AGENT-持久化速查手册.md`](./AGENT-持久化速查手册.md) |

**已废止（已从仓库删除，见 Git 历史）**：`元数据整体升级方案.md`、`元数据sdk升级方案1/2/3.md`、`DSL查询方案.md`、`README_DSL_QUERY.md`、`SDK优化方案详细版.md` 及 `src/main/` 下各类方案草稿。
