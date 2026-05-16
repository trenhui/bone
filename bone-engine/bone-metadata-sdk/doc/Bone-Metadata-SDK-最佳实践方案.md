# Bone Metadata SDK 最佳实践方案

> **唯一权威**：`bone-metadata-sdk/doc/` 目录仅维护本文档。  
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
6. [SQL 模板与自定义仓储方法](#6-sql-模板与自定义仓储方法)
7. [元数据服务](#7-元数据服务)
8. [多租户与动态数据源](#8-多租户与动态数据源)
9. [扩展字段（EAV / JSON / 预留列）](#9-扩展字段eav--json--预留列)
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
- 业务模块同时依赖两者时，以 **`AGENTS.md` §5.1.1** 为准

---

## 2. 设计原则（业界 + Bone）

| 原则 | 业界参照 | Bone 落地 |
|------|----------|-----------|
| **元数据为骨架** | Salesforce Custom Object / Field | `TableMetadataResolver` + D1 注解；动态字段走 EAV/预留列 |
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
List<OrderSummary> list = QueryBuilder.from(Order.class)
    .where(Order::getStatus).eq(OrderStatus.PAID)
    .orderByDesc(Order::getCreatedAt)
    .page(pageNo, pageSize)
    .list(OrderSummary.class);
```

DSL 包路径：`com.bone.metadata.sdk.query.dsl`。与 `com.bone.metadata.sdk.query.SqlBuilder` **不同类**，禁止混 import。

### 5.4 双 API 收敛方向（目标）

原升级方案 1/2/3 共识：

```text
Criteria API ──adapter──┐
                        ├──► UnifiedQueryContext ──► UnifiedSqlBuilder ──► CompiledQuery
DSL API      ──adapter──┘
```

**原则**：对外保持双 API；对内只维护一套编译与方言逻辑（见 §13 阶段一）。

---

## 6. SQL 模板与自定义仓储方法

### 6.1 模板 ID 约定

```text
{Repository接口全限定名}.{方法名}
例：com.bone.masterdata.domain.repository.MasterDataRecordRepository.searchPage
```

### 6.2 模板来源（优先级）

1. Classpath：`resources/sql-templates/**/*.yaml` 或 `.sql`  
2. 方法注解：`@Query` / `@SqlFragment`  
3. MyBatis 风格片段（经 `MyBatisTemplateParser`）

### 6.3 模板规范

- 仅使用 **命名参数**（`:param`），禁止字符串拼接用户输入  
- 动态片段用 XML/YAML 标签控制（`<if>`），而非 Java 拼接 SQL  
- 上线前经 `TemplateSecurityValidator` + Code Review  
- 复杂 SQL 放 `infrastructure` 模块的 `resources`，不散落在 `domain`

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

### 8.1 行级租户

- 实体继承 `TenantAbstractEntity` 时，写操作由 `BaseRepository` / 拦截器填充 `tenantId`  
- 读操作：`Criteria` 中显式租户条件，或依赖全局 SQL 拦截器（若已启用）

### 8.2 动态数据源

- `@DS("slave")` / `DataSourceContextHolder`：读走从库（与 `bone-datasource` 配置一致）  
- 配置前缀：`bone.metadata.datasource`  
- **最佳实践**：事务内不切换数据源；只读查询再路由从库

### 8.3 分库分表

- 当前 SDK 提供路由钩子与文档化实践（原「极简侵入式多数据源」方案）  
- **分片键、全局 ID、跨片查询** 需在业务与 `bone-datasource` 层显式设计，SDK 不自动分片

---

## 9. 扩展字段（EAV / JSON / 预留列）

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

- 短中期：IAM + 应用层校验 + 审计字段（`createBy` / `updateBy`）  
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

- **已对齐**：`bone-masterdata`、`bone-system`、`bone-integration`（`@EnableSqlRepositories`）  
- **待对齐**：部分模块仍手写 `JdbcTemplate`（如 IAM），应迁移或提交 ADR 说明例外

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

---

## 15. 附录：配置要点

```yaml
# 元数据 SDK
metadata:
  sdk:
    deploymentMode: EMBEDDED   # 或 REMOTE

bone:
  metadata:
    datasource:
      enabled: true

# SQL 模板
bone:
  metadata:
    sql:
      template-locations: classpath:sql-templates/
      max-template-size: 65536
```

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
- 数据库表设计：[`doc/architecture/数据库开发规范.md`](../../../doc/architecture/数据库开发规范.md)

---

## 文档历史

| 日期 | 说明 |
|------|------|
| 2026-05 | 合并 `doc/` 下 5 份方案为本文档；废止多份重复升级/DSL 草案 |

**已废止（已从仓库删除，见 Git 历史）**：`元数据整体升级方案.md`、`元数据sdk升级方案1/2/3.md`、`DSL查询方案.md`、`README_DSL_QUERY.md`、`SDK优化方案详细版.md` 及 `src/main/` 下各类方案草稿。
