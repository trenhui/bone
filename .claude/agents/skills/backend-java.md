---
name: backend-java
description: Java 17 + Spring Boot 3.2 后端开发技能包（Bone DDD + bone-metadata-sdk）
---

> 规范真源：[`doc/agents/03-架构分层规范.md`](../../../doc/agents/03-架构分层规范.md) 与
> [`doc/architecture/Bone-DDD-最终实践方案.md`](../../../doc/architecture/Bone-DDD-最终实践方案.md)（门禁唯一权威）。
> 本文件是**操作摘要**，冲突一律以真源为准；门禁状态只在 §G-1.7 查，不在此处复制。

## 技术栈约束
- Java 17，Spring Boot 3.2.5（版本真源：`bone-parent/pom.xml`）。
- **持久化唯一方案：`bone-metadata-sdk`**（`@EnableSqlRepositories`）——禁 MyBatis-Plus / JPA / Hibernate / MyBatis（HC-001 · HC-006）。
- DDD 四层：`adapter → application → domain ← infrastructure`；`domain` 零框架依赖，`application` 不直连 `infrastructure`。
- 参数校验：Jakarta Validation；测试：JUnit 5 + Mockito + AssertJ。
- 格式化：改 Java 后执行 `mvn spotless:apply`（google-java-format，100 列）。

## 分层与包结构（两种合规形态，选一种并在模块内一致）
通用树（按「层」分包）：
- `domain/model/{aggregate,entity,valueobject,event}`、`domain/service`、`domain/repository`（**写侧**仓储接口）。
- `application/command/`（用例编排）、`application/query/{qry,dto}`、`application/query/port/`（读侧端口）、`application/event/`。
- `adapter/{协议}/{controller,assembler,dto/{request,response}}`。
- `infrastructure/{persistence,config,gateway,...}`。

`bone-blueprint` 采用「按限界上下文分包」（`domain/{order,payment}/{,event/,valueobject/}`，聚合根直接置于上下文包）——同样合规。
目录**按需创建，不要为新模块预生成空目录**。

## 核心基础类（`bone-core`，直接用，勿自造）
| 类 | 包 | 说明 |
|---|---|---|
| `AbstractEntity<ID>` | `com.bone.core.domain.entity` | 审计 `createdAt/createdBy/updatedAt/updatedBy` + 软删 `deleted`（`Boolean`，默认 `false`） |
| `TenantAbstractEntity<ID>` | `com.bone.core.tenant` | 上面 + `tenantId`（**新实体默认继承它**） |
| `AggregateRoot<ID>` / `TenantAggregateRoot<ID>` / `AuditableAggregateRoot` | `com.bone.core.domain` | 聚合根基类；`TenantAggregateRoot` 带 `tenantId` 且**不**继承 `TenantAbstractEntity` |
| `TenantContext` | `com.bone.core` | 线程级租户上下文 |
| `ApiResponse<T>` / `PageResult<T>` | `com.bone.core.model` | 统一 REST 响应 / 分页结果 |
| `BizException` / `DomainException` | `com.bone.core.exception` | 业务异常（**首参 code 是 HTTP 状态**）/ 领域不变量异常 |
| `DomainEventPublisher` | `com.bone.core.domain.event` | `publishFrom(aggregate)` 发布聚合事件 |

## 入站边界（ADR-0028 Application Service First）
- 默认语义化 `*ApplicationService`；**一个用例只选一种构件**，禁 Handler 与 ApplicationService 套娃。
- Controller **禁**直注 `domain/service`、`domain/repository`、infrastructure，只依赖 ApplicationService。
- 仅当写意图需显式契约**且**多入口时才拆 `*CommandHandler`。

## 适配器与 DTO
- 位置：`adapter/<协议>/dto/request/`（`*Req` / `*Qry`）、`adapter/<协议>/dto/response/`（`*Resp`）。
- 组装器：`adapter/**` 用 `*Assembler`；`infrastructure/**` 用 `*Converter`——**不混用**。
- 协议由**包路径**承载，**类名不带协议标记**（`web` / `rpc` 的 `*Controller` 同名合法）；标记下沉到 DI 标识
  （`@RestController("rpcOrderController")`、MapStruct `implementationName`）。

## 统一响应（禁 `Result<T>`）
```java
return ApiResponse.success(data);                       // 成功
return ApiResponse.page(records, total, page, size);    // 分页
throw BizException.of(404, "订单不存在");                // 失败（勿裸抛 RuntimeException）
```
- 领域层抛 `DomainException`；对外错误用 `BizException` 静态工厂 `of(...)`。
- **禁止返回 `null`**：用 `Optional<T>` 或空集合。

## 持久化（bone-metadata-sdk）
- 写侧仓储接口在 `domain/repository/*Repository`，实现在 `infrastructure/persistence`。
- 读侧端口在 `application/query/port/*QueryPort` + `application/query/dto/`。
- 本聚合读可合并进域仓储（ADR-0030）；**跨聚合 / 报表 / Join** 走 `*QueryPort`。

### 三条取数通道（按能力选，不要混）
1. **Criteria DSL**（默认）：SDK 自动注入租户（ADR-0029）+ 软删。
2. **SDK 继承的简单单表分页**：仅限单表、返回聚合行。
3. **`@Sql` / 外置 `.sql`**：多表 JOIN 扁平投影、聚合统计、全租户扫描。

### `@Sql` 通道要点（ADR-0030）
- 模板源**二选一，禁止并存**：注解 `@Sql(...)` 或
  `src/main/resources/sql/<接口包路径>/<接口简名>/<方法名>.sql`（默认 `load-priority=classpath-first`，外置优先；同名会形成「影子 SQL」）。
- 必须显式标 `@TenantScope(TenantScopeMode.AUTO|MANUAL|ALL|BYPASS)`：
  - `AUTO`：SDK 注入 `tenant_id`，SQL 内**不得再手写** `tenant_id`；含 JOIN 的复杂查询须留锚点 `/*bone:tenant*/`，漏锚点=**失败关闭**（抛异常）。
  - `MANUAL`：自己写 `AND x.tenant_id = #{tenantId}`；`ALL`：全租户扫描（须登记授权）。
- 软删自己写 `AND x.deleted = 0`，**JOIN 的每个子表都要带**。

## 领域事件
- 聚合内 `registerEvent(...)`；应用层保存后 `domainEventPublisher.publishFrom(aggregate)`——**保存与发布必须成对**
  （门禁 `applicationSaveMustPairWithPublishOrExempt`，类级判定）。
- 集成事件入参用**散装标量**，避免契约依赖 `domain`。

## 编码规范
- 依赖注入：构造器注入，依赖 `final`（`@RequiredArgsConstructor`）。
- 日志：`@Slf4j`，禁 `System.out.println`；命名驼峰，接口不加 `I` 前缀。
- 并发写聚合：聚合字段标 `@Version`（Number 子类，如 `Long`），并发写直接 `repository.update(entity)`——SDK 原生乐观锁（ADR-0031 D1）
  自动 `SET version = version + 1`、`WHERE version = :old` 并回写实体；冲突时 SDK 抛 `OptimisticLockingFailureException`，
  应用层翻译为领域异常 `OptimisticLockConflictException`（自带 HTTP 409）。**不再手写** `incrementVersion()` / `saveWithVersionCheck`。
- 测试命名：`should_<behavior>_when_<condition>`。

## 构建 / 校验命令
```bash
MVN=${MVN:-mvn}   # 或 ./mvnw；需要 JDK 17
$MVN -o -pl <module> test-compile        # 编译
$MVN -o -pl <module> test                # 单测
$MVN -o -pl <module> spotless:apply      # 格式化
./scripts/check.sh                       # 本地门禁（与 pre-commit 同源）
```
- 覆盖率门槛以 `bone-parent/pom.xml` 的 `jacoco.minimum.coverage` 为准（**勿在本文件写死数字**）。
