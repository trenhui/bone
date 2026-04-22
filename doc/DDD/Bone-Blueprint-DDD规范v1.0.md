# 🏛️ Bone-Blueprint v9.5 工程规范（工业级最佳实践·完全自洽版）

## 企业级 DDD + 轻量 CQRS + 六边形架构 + Bone 元数据驱动框架

> **定位**：可执行架构协议，元数据零配置，通用能力下沉 bone-framework  
> **适用规模**：3–20 人团队 / 单体到模块化演进系统  
> **核心目标**：结构即规则、命名即语义、依赖即约束，开发者想犯错都难  
> **生效日期**：2026-04-22

---

## 0️⃣ bone-framework 核心能力概览

### 0.1 模块职责矩阵

| 模块 | 核心职责 | 关键类 |
|------|---------|--------|
| **bone-core** | 领域模型基类、实体基类、DTO基类、统一响应、分页模型、租户上下文、异常体系、ID生成策略、反射工具 | `AggregateRoot`、`AbstractEntity`、`TenantAbstractEntity`、`ApiResponse`、`PageResult`、`TenantContext`、`DomainException`、`DistributedIdGenerator` |
| **bone-metadata-sdk** | 元数据驱动的仓储实现、类型安全的 DSL 查询、批量操作、聚合查询、软删除、多租户支持 | `Repository<T, ID>`、`FluentQuery<T>`、`QueryBuilder`、`Criteria<T>`、`SqlExecutor`、`@EnableSqlRepositories` |
| **bone-extension-sdk** | 扩展点管理、多维度路由、插件化架构 | `@ExtensionPoint`、`@Extension`、`ExtensionPointExecutor` |
| **bone-security** | 认证授权、JWT令牌、密码加密 | `JwtTokenProvider`、`PasswordEncoder` |
| **bone-utils** | 通用工具（骨架） | — |

### 0.2 实体继承体系（必须严格遵循）

```
Entity<ID>                          # 主键基类（@Id + @GeneratedValue）
    ↑
AbstractEntity<ID>                 # 审计字段 + 软删除（Auditable + SoftDeletable + @Deleted）
    ↑
TenantAbstractEntity<ID>           # 多租户字段（Tenantable：tenantId）
```

**继承选择规则**：
- **聚合根**：继承 `AggregateRoot<ID>`（继承自 `Entity<ID>`），ID 类型为业务 ID 值对象（如 `OrderId`）
- **实体（非聚合根，单租户）**：继承 `AbstractEntity<ID>`
- **实体（非聚合根，多租户）**：继承 `TenantAbstractEntity<ID>`

### 0.3 bone-metadata-sdk 核心 API

#### Repository<T, ID> 接口主要方法（由 SDK 动态代理实现）
- **基础 CRUD**：`findById`, `insert`, `update`, `save`, `deleteById`, `batchInsert`, `batchSave`, `deleteByIds`
- **条件查询**：`findByCriteria`, `findOneByCriteria`, `pageByCriteria`, `countByCriteria`
- **聚合查询**：`aggregate`（支持 COUNT/SUM/AVG/MAX/MIN）
- **通用查询**：`query`, `queryPage`
- **DSL 入口**：`query()` 返回 `FluentQuery<T>`，`where(SFunction<T,F>)` 快捷条件

#### FluentQuery<T> 类型安全的 DSL
- **条件**：`where`, `and`, `or`, `and(Consumer<WhereBuilder>)`, `or(Consumer<WhereBuilder>)` 分组
- **排序**：`orderByAsc`, `orderByDesc`
- **分页**：`limit`, `offset`, `page(pageNum, pageSize)`
- **分组**：`groupBy`, `having`
- **关联**：`leftJoin`, `join`, `rightJoin`, `fullJoin`, `joinOn`, `leftJoinOn`
- **执行**：`list()`, `single()`, `first()`, `count()`, `exists()`, `page()`
- **投影**：`select(field, resultType)`, `map(mapper)`
- **聚合**：`aggregate(function, field, resultType)`
- **流式**：`stream()`, `forEach()`

#### QueryBuilder 静态工厂（查询唯一入口）
`QueryBuilder` 由 SDK 提供，通过 `@EnableSqlRepositories` 自动初始化 `SqlExecutor`，可直接使用。

```java
// 查询入口
FluentQuery<Order> query = QueryBuilder.from(Order.class);
FluentQuery<Order> queryWithAlias = QueryBuilder.from(Order.class, "o");
```

### 0.4 启动类配置

```java
@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.blueprint.domain.service")
public class BoneBlueprintApplication {
    public static void main(String[] args) {
        SpringApplication.run(BoneBlueprintApplication.class, args);
    }
}
```

> **说明**：`@EnableSqlRepositories` 会自动扫描并代理 `Repository` 接口，同时初始化 `SqlExecutor`，因此 `QueryBuilder` 无需手动初始化。

---

## 1️⃣ 架构第一性原则（不可违反）

### 1.1 四大铁律

**① 领域唯一性（Domain First）**
- `domain` 是唯一业务真相来源，不依赖任何框架（Spring/MyBatis/JPA ❌）
- **允许有限使用 Lombok**：
  - **domain 层**：仅限 `@Getter` 和私有构造器注解（`@NoArgsConstructor(access = AccessLevel.PRIVATE)`），严禁 `@Setter` 和 `@Data`
  - **application / infrastructure 层**：允许 `@RequiredArgsConstructor`
  - **值对象**：允许 `@Value` 或 `record`
  - **测试代码**：允许 `@Builder`

**② 依赖方向唯一性**
```
adapter → application → domain ← infrastructure
```
- ✔ 只能向内依赖，❌ 禁止任何反向 import

**③ 轻量 CQRS + 查询单一入口（核心升级）**

- **Command（写模型）**：必须经过 domain。仓储（`domain.repository`）**只允许**：
  - `save` / `remove` / `findById`（聚合重建）
  - **规则查询**：`existsByXxx`（返回 `boolean`）、`findByBusinessKey`（返回 `Optional<T>`）
  - **❌ 禁止任何带条件组合的查询**（如 `findByStatusAndType`）

- **Query（读模型）**：
  - 所有带 `where` 条件组合的查询（包括单表多条件、分页、排序、关联、聚合）**必须通过 `QueryBuilder`** 完成。
  - `QueryBuilder` 位于 `infrastructure.query.builder`（实际为 SDK 提供），由 `@EnableSqlRepositories` 自动初始化。
  - 查询结果必须映射到 `application.query.dto` 或 `application.query.projection`。
  - **❌ 禁止在 `domain.repository` 中定义任何返回集合、分页或复杂条件的查询方法**。

- **`QueryBuilder` 使用范围**：
  - 仅限 `application.query.handler` 和 `infrastructure.query.native`
  - **禁止在 domain 包内使用**
  - **禁止在 `application.command.handler` 中使用**

**④ 外部系统隔离（ACL）**
- 所有外部依赖通过 `infrastructure` 网关实现，domain 不感知任何外部系统

### 1.2 扩展点使用原则（新增）

对于**多租户、多业务域、多变规则**（如智能理赔中的规则引擎）的场景：

- **❌ 禁止在领域服务或应用层使用大量 `if-else` 或 `switch` 语句**根据租户/场景分支逻辑。
- **✅ 必须使用 `bone-extension-sdk` 的 `@ExtensionPoint` + `@Extension` 机制**，将不同租户或场景的实现解耦为独立的扩展点。
- **示例**：规则校验、价格计算、策略选择等天然适合扩展点。

### 1.3 bone-extension-sdk 详细使用指南

#### 1.3.1 扩展点定义

```java
// domain/service/order/OrderPriceCalculator.java
@ExtensionPoint(
    name = "订单价格计算扩展点",
    description = "不同租户和场景下的订单价格计算",
    version = "1.0.0",
    transactional = false,
    timeout = 10,
    singleton = true
)
public interface OrderPriceCalculator {
    BigDecimal calculate(OrderPriceRequest request);
}
```

#### 1.3.2 扩展实现

```java
// infrastructure/extension/DefaultOrderPriceExtension.java
@Extension(
    name = "默认订单价格计算",
    description = "标准价格计算逻辑",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "standard"
)
public class DefaultOrderPriceExtension implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        // 标准价格计算逻辑
        return request.getBaseAmount();
    }
}

// infrastructure/extension/VipOrderPriceExtension.java
@Extension(
    name = "VIP订单价格计算",
    description = "VIP客户享受9折优惠",
    tenant = "ALI",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip"
)
public class VipOrderPriceExtension implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        // VIP价格计算逻辑（9折）
        return request.getBaseAmount().multiply(new BigDecimal("0.9"));
    }
}
```

#### 1.3.3 业务上下文构建与调用

```java
// application/service/OrderApplicationService.java
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderPriceCalculator priceCalculator;
    private final OrderRepository orderRepository;
    
    public OrderId createOrder(CreateOrderCommand cmd) {
        // 构建业务上下文（自动线程绑定）
        BizContext<OrderPriceRequest> context = BizContext.builder()
            .tenant("ALI")
            .bizCode("ecommerce")
            .useCase("order")
            .scenario(cmd.isVip() ? "vip" : "standard")
            .build();
        
        // 调用扩展点（自动路由到匹配的实现）
        BigDecimal finalPrice = priceCalculator.calculate(
            OrderPriceRequest.builder().baseAmount(cmd.getTotalAmount()).build()
        );
        
        // 创建订单...
        return orderId;
    }
}
```

#### 1.3.4 扩展点路由规则

| 路由维度 | 说明 | 示例 | 优先级 |
|----------|------|------|--------|
| `tenant` | 租户代码，支持通配符 `*` | `"ALI"`, `"*"` | 最高 |
| `bizCode` | 业务域代码 | `"ecommerce"`, `"finance"` | 高 |
| `useCase` | 业务用例 | `"order"`, `"payment"` | 中 |
| `scenario` | 场景代码 | `"vip"`, `"promotion"` | 中 |
| `env` | 环境标识 | `"dev"`, `"prod"` | 低 |

**路由算法**：最具体匹配优先。例如：
- 上下文：`tenant=ALI, bizCode=ecommerce, scenario=vip`
- 扩展1：`tenant=ALI, bizCode=ecommerce, scenario=vip` → 完全匹配
- 扩展2：`tenant=ALI, bizCode=ecommerce, scenario=*` → 部分匹配
- 扩展3：`tenant=*, bizCode=ecommerce, scenario=*` → 通配符匹配

#### 1.3.5 扩展点高级特性

1. **事务支持**：`@ExtensionPoint(transactional = true)`
2. **超时控制**：`@ExtensionPoint(timeout = 5)`（秒）
3. **单例模式**：`@ExtensionPoint(singleton = true)`
4. **异步执行**：使用 `AsyncExtensionExecutor`
5. **权限控制**：通过 `ExtensionPermissionManager`
6. **性能监控**：集成 `ExtensionMetricsCollector`

---

## 2️⃣ 标准工程结构（以订单模块为例，含完整类名示例）

```text
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
│
├── adapter/                                    # 入站适配器层
│   ├── web/
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   │   ├── CreateOrderRequest.java
│   │   │   │   └── OrderPageRequest.java
│   │   │   └── response/
│   │   │       ├── OrderDetailResponse.java
│   │   │       └── OrderPageResponse.java
│   │   ├── assembler/
│   │   │   └── OrderAssembler.java
│   │   └── exception/
│   │       └── InvalidRequestException.java
│   ├── rpc/
│   │   ├── OrderRpcService.java
│   │   └── assembler/
│   │       └── OrderRpcAssembler.java
│   ├── mq/
│   │   ├── listener/
│   │   │   └── OrderPaidListener.java
│   │   └── assembler/
│   │       └── OrderMessageAssembler.java
│   └── schedule/
│       └── CancelExpiredOrderJob.java
│
├── application/                                # 应用层
│   ├── command/
│   │   ├── cmd/
│   │   │   ├── CreateOrderCommand.java
│   │   │   └── CancelOrderCommand.java
│   │   └── handler/
│   │       ├── CreateOrderCommandHandler.java
│   │       └── CancelOrderCommandHandler.java
│   ├── query/
│   │   ├── qry/
│   │   │   ├── OrderPageQuery.java
│   │   │   └── OrderDetailQuery.java
│   │   ├── handler/
│   │   │   ├── OrderPageQueryHandler.java
│   │   │   └── OrderDetailQueryHandler.java
│   │   ├── dto/                                 # 简单查询 DTO（字段与实体基本对应）
│   │   │   └── OrderDto.java
│   │   └── projection/                          # 复杂报表投影（聚合、联表、重命名）
│   │       └── OrderStatisticsProjection.java
│   ├── event/
│   │   ├── OrderPaidEventHandler.java
│   │   └── OrderCreatedEventHandler.java
│   ├── service/                                 # 应用服务（跨聚合协调、外部服务编排）
│   │   └── OrderApplicationService.java
│   └── exception/                               # 应用层异常
│       └── ApplicationException.java
│
├── domain/                                     # 领域层（零框架依赖）
│   ├── model/                                  # 按业务分组（限界上下文）
│   │   └── order/
│   │       ├── Order.java                      # 聚合根
│   │       ├── valueobject/                    # 值对象包
│   │       │   ├── OrderId.java
│   │       │   ├── OrderItem.java
│   │       │   ├── OrderStatus.java
│   │       │   └── ShippingAddress.java
│   │       └── event/                          # 领域事件
│   │           ├── OrderCreatedEvent.java
│   │           ├── OrderPaidEvent.java
│   │           └── OrderCancelledEvent.java
│   ├── repository/                             # 仓储端口（写 + 规则查询）
│   │   └── OrderRepository.java
│   ├── gateway/                                # 防腐层端口（ACL）
│   │   ├── InventoryGateway.java
│   │   └── PaymentGateway.java
│   ├── service/                                # 领域服务（无状态业务规则）
│   │   └── OrderAmountValidator.java
│   ├── specification/                          # 规约模式（可选，复杂业务规则）
│   │   └── ActiveOrderSpecification.java
│   └── exception/                              # 领域异常
│       └── OrderDomainException.java
│
├── infrastructure/                             # 基础设施层
│   ├── gateway/                                # 防腐层实现
│   │   ├── feign/
│   │   │   └── InventoryFeignGatewayImpl.java
│   │   └── wechat/
│   │       └── WechatPayGatewayImpl.java
│   ├── repository/                             # 仓储实现（由 SDK 动态代理，无需手写）
│   ├── query/                                  # 读模型扩展
│   │   ├── native/                             # 原生 SQL 查询接口
│   │   │   └── OrderNativeQueryRepository.java
│   │   └── executor/                           # （可选）复杂查询执行器封装
│   ├── extension/                              # 扩展点实现（插件化）
│   │   ├── DefaultOrderPriceExtension.java
│   │   ├── VipOrderPriceExtension.java
│   │   └── PromotionOrderPriceExtension.java
│   ├── security/                               # 安全组件
│   │   ├── JwtTokenProvider.java
│   │   └── PasswordEncoderImpl.java
│   ├── config/                                 # 配置类（按功能分组）
│   │   ├── metadata/
│   │   │   └── BoneMetadataConfiguration.java
│   │   ├── extension/
│   │   │   └── ExtensionConfiguration.java
│   │   ├── security/
│   │   │   └── SecurityConfiguration.java
│   │   └── web/
│   │       └── WebMvcConfiguration.java
│   ├── util/                                   # 基础设施工具类（与技术相关）
│   │   └── QueryUtils.java
│   └── exception/                              # 基础设施异常
│       └── InfrastructureException.java
│
└── resources/
    ├── sql/                                    # 原生 SQL 文件
    │   └── order/
    │       └── monthlyOrderStatistics.sql
    ├── config/                                 # 环境配置文件
    │   ├── application.yml
    │   └── application-prod.yml
    └── templates/                              # 模板文件（邮件、消息等）
        └── email/
            └── order-confirmation.html
```

### 测试目录结构（推荐，与主目录对应）

```text
src/test/java/com/bone/blueprint/
├── adapter/
│   └── web/
│       └── controller/
│           └── OrderControllerTest.java
├── application/
│   ├── command/
│   │   └── handler/
│   │       └── CreateOrderCommandHandlerTest.java
│   └── query/
│       └── handler/
│           └── OrderPageQueryHandlerTest.java
├── domain/
│   ├── model/
│   │   └── order/
│   │       └── OrderTest.java
│   └── service/
│       └── OrderAmountValidatorTest.java
└── infrastructure/
    └── gateway/
        └── InventoryFeignGatewayImplTest.java
```

### 2.1 已删除项（不可恢复）

- `infrastructure/persistence/` 整个目录（SDK 接管，不保留手写 PO/Mapper）
- `domain/shared/` 整个包（下沉 bone-core）
- 项目内任何 `common` 包
- `application/query/dto/OrderPageResult.java`（统一用 `PageResult<T>`）
- `infrastructure/query/builder/` 目录（QueryBuilder 由 SDK 提供，无需显式定义）

### 2.2 包分组原则

✅ **按业务分组（强制）**：`domain/model/order/`（聚合根、值对象、事件在一起）  
❌ **按类型分组（禁止）**：`domain/model/entities/`、`domain/model/valueobjects/`、`domain/model/events/`

### 2.3 数据库表设计规范（双 ID 模型 + JOIN 优化）

```sql
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据库自增主键，聚簇索引，用于内部 JOIN',
    biz_id VARCHAR(64) NOT NULL UNIQUE COMMENT '业务ID（UUID/雪花），对外暴露',
    tenant_id BIGINT COMMENT '租户ID（多租户系统必须）',
    customer_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT,
    deleted TINYINT DEFAULT 0,
    INDEX idx_biz_id (biz_id),
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status)
);

CREATE TABLE t_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_db_id BIGINT NOT NULL COMMENT '关联 t_order.id（Long），用于内部 JOIN',
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    INDEX idx_order_db_id (order_db_id)
);
```

- `id`（dbId）：数据库自增主键，用于内部关联和聚簇索引性能，**所有内部 JOIN 必须使用此字段**
- `biz_id`：业务 ID，领域聚合根使用，对外 API 暴露，全局唯一
- 聚合根中只持有 `biz_id` 对应的值对象（如 `OrderId`），`dbId` 由 SDK 回填，领域层不主动使用

---

## 3️⃣ 各层职责表

| 层级 | 职责 | 禁止 |
|------|------|------|
| **adapter** | 协议转换、DTO校验、请求路由 | 业务逻辑、调用repository、直接访问infrastructure |
| **application** | 用例编排、事务边界、领域调用协调 | 包含业务规则、直接操作数据库、抛出业务异常 |
| **domain** | 业务规则、聚合根、值对象、领域事件 | 框架依赖、SQL/MQ/RPC、DTO、持久化注解、setter、**QueryBuilder** |
| **infrastructure.query** | 读模型查询执行（通过 QueryBuilder） | 写操作、业务逻辑 |
| **infrastructure** (其他) | 外部实现、安全、SDK配置、原生查询 | 业务逻辑、手写持久化代码 |
| **config/** | 纯配置类 | 实现类、工具类 |

---

## 4️⃣ 写链路（Command Side）完整实现

### 4.1 聚合根设计原则（双 ID 模型 + JOIN 优化 + 序列化安全）

- **聚合根持有业务 ID**（UUID/雪花），在工厂方法中直接生成
- **数据库自增 ID（dbId）** 仅用于存储层和内部 JOIN，由 SDK 回填，领域层不主动使用
- **所有内部 JOIN 必须使用 dbId（Long）**，严禁使用 `biz_id`（UUID）以保证 JOIN 性能
- 严禁暴露 `setId` 方法
- 只允许 `@Getter` + `@NoArgsConstructor(access = AccessLevel.PRIVATE)`
- 确保序列化框架能正确保存/恢复 `dbId`（如使用 Jackson 的 `@JsonIgnore` 避免对外暴露）

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends AggregateRoot<OrderId> {
    private OrderId id;          // 业务 ID（对外暴露，UUID，对应 biz_id）
    private Long dbId;           // 数据库自增主键（用于内部 JOIN，由 SDK 回填）
    private Long customerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private OrderStatus status;

    public static Order create(Long customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单至少需要一个商品项");
        }
        Order order = new Order();
        order.id = OrderId.of(DistributedIdGenerator.generateUuid());
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.recalculateTotal();
        order.status = OrderStatus.CREATED;
        order.addDomainEvent(new OrderCreatedEvent(order));
        return order;
    }

    public void pay() {
        if (this.status != OrderStatus.CREATED) {
            throw new DomainException("只有新建状态的订单可以支付");
        }
        this.status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(this.id, this.totalAmount));
    }

    public void cancel() {
        if (this.status == OrderStatus.SHIPPED) {
            throw new DomainException("已发货订单无法取消");
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new DomainException("订单已取消");
        }
        this.status = OrderStatus.CANCELLED;
        addDomainEvent(new OrderCancelledEvent(this.id));
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

### 4.2 仓储接口（仅规则查询，禁止展示查询）

```java
package com.bone.blueprint.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.valueobject.OrderId;
import java.util.Optional;

public interface OrderRepository extends Repository<Order, OrderId> {
    // ✅ 允许：用于领域不变量校验的查询（返回 boolean 或 Optional）
    boolean existsByCustomerIdAndStatus(Long customerId, OrderStatus status);
    Optional<Order> findByBizId(OrderId id);
    
    // ❌ 禁止：展示类查询（分页、列表、条件组合）
    // Page<Order> findByStatus(OrderStatus status, int page, int size);
    // List<Order> findByCustomerId(Long customerId);
}
```

### 4.3 领域服务（纯 POJO，不依赖 Spring）

```java
// domain/service/order/OrderAmountValidator.java
package com.bone.blueprint.domain.service.order;

import com.bone.blueprint.domain.model.order.Order;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;

/**
 * 订单金额验证领域服务
 * 业务规则：订单总金额不能超过最大限制
 */
public class OrderAmountValidator {
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("1000000");

    public static void validate(Order order) {
        if (order.getTotalAmount().compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new DomainException("订单金额超过限制：" + MAX_ORDER_AMOUNT);
        }
    }
}
```

> **说明**：领域服务使用静态方法，无需 Spring 管理，保持 domain 完全纯净。

### 4.4 命令处理器

```java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.order.valueobject.OrderId;
import com.bone.blueprint.domain.model.order.valueobject.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.service.order.OrderAmountValidator;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler {
    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;

    @Transactional
    public OrderId handle(CreateOrderCommand cmd) {
        // 1. 校验库存（ACL 调用）
        for (OrderItemDto item : cmd.getItems()) {
            if (!inventoryGateway.checkStock(item.getProductId(), item.getQuantity())) {
                throw new DomainException("商品库存不足: " + item.getProductId());
            }
        }

        // 2. 创建订单聚合根
        List<OrderItem> items = cmd.getItems().stream()
                .map(dto -> OrderItem.create(dto.getProductId(), dto.getQuantity(), dto.getUnitPrice()))
                .collect(Collectors.toList());
        Order order = Order.create(cmd.getCustomerId(), items);

        // 3. 领域服务校验金额
        OrderAmountValidator.validate(order);

        // 4. 保存（SDK 自动处理 dbId 回填和领域事件发布）
        orderRepository.save(order);
        return order.getId();
    }
}
```

### 4.5 领域事件发布时机（重要规范）

为确保 **数据库事务与领域事件发布的原子性**，`bone-metadata-sdk` 的 `Repository.save` / `Repository.update` 等方法**必须内置“自动发布聚合根内暂存事件”的机制**，并遵循以下规则：

- **事件收集**：聚合根内通过 `addDomainEvent` 添加的事件暂存在聚合根中。
- **事件发布时机**：在 `save` / `update` 成功执行（数据库事务提交后）**自动**获取聚合根中暂存的事件，并通过 `ApplicationEventPublisher` 发布。
- **事务绑定**：使用 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 确保事件在事务成功提交后才被处理，避免因事务回滚导致错误的事件发送。
- **开发者无需手动调用事件发布**，SDK 自动处理。

```java
// 订阅者示例
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPaidEvent event) {
        // 事务已提交，安全发送通知
        notificationService.sendOrderPaidNotification(event.getOrderId());
    }
}
```

---

## 5️⃣ 读链路（Query Side）实现

### 5.1 基本规则

- **查询唯一入口**：`QueryBuilder`（由 SDK 提供，通过 `QueryBuilder.from(...)` 使用）
- **结果必须使用 DTO/Projection**，禁止返回 domain 实体
- **Query Handler 只做查询 + 映射，禁止业务逻辑**
- **`QueryBuilder` 禁止出现在 domain 包内**，只能用于 `application.query.handler` 和 `infrastructure.query.native`

### 5.2 简单分页查询（QueryBuilder）

```java
package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {

    @Transactional(readOnly = true)
    public PageResult<OrderDto> handle(OrderPageQuery query) {
        return QueryBuilder.from(Order.class)
            .where(Order::getCustomerId).eq(query.getCustomerId())
            .where(Order::getStatus).eq(query.getStatus())
            .orderByDesc(Order::getCreateTime)
            .page(query.getPageNum(), query.getPageSize())
            .map(order -> OrderDto.from(order));
    }
}
```

### 5.3 关联查询示例（JOIN 必须使用 dbId）

```java
public List<OrderWithItemsDto> getOrdersWithItems(Long customerId) {
    // 内部 JOIN 必须使用 dbId（Long），严禁使用 biz_id
    return QueryBuilder.from(Order.class, "o")
        .leftJoin(OrderItem.class, "i").on("o.dbId", "i.orderDbId")
        .where("o.customerId").eq(customerId)
        .orderByDesc("o.createTime")
        .list()
        .stream()
        .map(row -> toOrderWithItemsDto(row))
        .collect(Collectors.toList());
}
```

### 5.4 复杂查询下沉原则（强制）

为了避免 `FluentQuery` 链式代码因过度复杂而难以维护，**强制规定**：

- **单表查询、简单关联（≤2 张表）**：允许使用 `QueryBuilder`。
- **3 张表及以上的关联查询**：**必须使用原生 SQL（`@Sql`）并放置在 `infrastructure.query.native`**。
- **涉及特定数据库优化（如索引提示、窗口函数、存储过程）**：**必须使用原生 SQL**。
- 代码审查时，若发现超过 2 次 `join()` 调用或复杂的 `groupBy` / `having` 链，应拒绝并建议重构为原生 SQL。

### 5.5 复杂原生 SQL（逃生舱口）

**接口位置**：`infrastructure.query.native.OrderNativeQueryRepository`

```java
package com.bone.blueprint.infrastructure.query.native;

import com.bone.blueprint.application.query.projection.OrderStatisticsProjection;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import java.util.List;

public interface OrderNativeQueryRepository {
    @Sql("script:classpath:sql/order/monthlyOrderStatistics.sql")
    List<OrderStatisticsProjection> getMonthlyStatistics(@Param("yearMonth") String yearMonth);
}
```

**外部 SQL 文件**：`resources/sql/order/monthlyOrderStatistics.sql`

```sql
SELECT DATE_FORMAT(o.create_time, '%Y-%m') AS month,
       COUNT(o.id) AS orderCount,
       SUM(o.total_amount) AS totalAmount,
       AVG(o.total_amount) AS avgAmount
FROM t_order o
WHERE o.deleted = 0
  AND DATE_FORMAT(o.create_time, '%Y-%m') = #{yearMonth}
GROUP BY DATE_FORMAT(o.create_time, '%Y-%m')
```

### 5.6 CQRS 决策矩阵（快速判断）

| 功能类型 | 判断条件 | 放置位置 | 使用 API |
|----------|----------|----------|----------|
| 写操作 | 修改数据 | `application.command.handler` | `Repository.save/update` |
| 规则校验查询 | 存在性/唯一性，返回 boolean/Optional | `domain.repository` | `existsByXxx`, `findByBusinessKey` |
| **所有条件组合查询** | 任何带 where 条件的查询（包括单表多条件、分页、排序、关联） | `application.query.handler` | `QueryBuilder.from(...)` |
| 极端复杂查询 | ≥3 张表关联、窗口函数、存储过程 | `infrastructure.query.native` | `@Sql` 原生 SQL |

---

## 6️⃣ 命名体系（完整版）

### 6.1 包命名

| 包路径 | 说明 |
|--------|------|
| `adapter.web.controller` | HTTP 控制器 |
| `adapter.web.dto.request` | 请求 DTO |
| `adapter.web.dto.response` | 响应 DTO |
| `adapter.web.assembler` | DTO ↔ 命令/查询对象转换 |
| `adapter.rpc` | RPC 服务 |
| `adapter.mq.listener` | 消息监听器 |
| `adapter.schedule` | 定时任务 |
| `application.command.cmd` | 命令对象 |
| `application.command.handler` | 命令处理器 |
| `application.query.qry` | 查询对象 |
| `application.query.handler` | 查询处理器 |
| `application.query.dto` | 简单查询 DTO |
| `application.query.projection` | 复杂报表投影 |
| `application.event` | 事件处理器 |
| `application.service` | 应用服务（跨聚合协调） |
| `domain.model.{context}` | 领域模型按限界上下文分组 |
| `domain.model.{context}.valueobject` | 值对象 |
| `domain.model.{context}.event` | 领域事件 |
| `domain.repository` | 仓储端口 |
| `domain.gateway` | 防腐层端口 |
| `domain.service` | 领域服务 |
| `domain.specification` | 规约 |
| `infrastructure.gateway` | 防腐层实现 |
| `infrastructure.extension` | 扩展点实现 |
| `infrastructure.query.native` | 原生 SQL 查询接口 |
| `infrastructure.security` | 安全组件 |
| `infrastructure.config` | 配置类 |
| `infrastructure.util` | 基础设施工具类 |

### 6.2 类命名

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| 控制器 | `{Domain}Controller` | `OrderController` |
| 请求 DTO | `{Action}{Domain}Request` | `CreateOrderRequest` |
| 响应 DTO | `{Domain}{Action}Response` | `OrderDetailResponse` |
| Assembler | `{Domain}Assembler` | `OrderAssembler` |
| RPC 服务 | `{Domain}RpcService` | `OrderRpcService` |
| MQ 监听器 | `{Domain}{Event}Listener` | `OrderPaidListener` |
| 定时任务 | `{Domain}{Action}Job` | `CancelExpiredOrderJob` |
| 命令对象 | `{Action}{Domain}Command` | `CreateOrderCommand` |
| 命令处理器 | `{Action}{Domain}CommandHandler` | `CreateOrderCommandHandler` |
| 查询对象 | `{Domain}{Action}Query` | `OrderPageQuery` |
| 查询处理器 | `{Domain}{Action}QueryHandler` | `OrderPageQueryHandler` |
| 简单 DTO | `{Domain}Dto` | `OrderDto` |
| 投影 | `{Domain}{Suffix}Projection` | `OrderStatisticsProjection` |
| 聚合根 | `{Aggregate}` | `Order` |
| 值对象 | `{BusinessConcept}` | `OrderId`, `OrderItem`, `OrderStatus` |
| 领域事件 | `{Aggregate}{PastEvent}` | `OrderCreatedEvent` |
| 仓储接口 | `{Aggregate}Repository` | `OrderRepository` |
| 网关接口 | `{ExternalSystem}Gateway` | `InventoryGateway` |
| 领域服务 | `{Domain}{Function}Validator` | `OrderAmountValidator` |
| 规约 | `{Domain}{Condition}Specification` | `ActiveOrderSpecification` |
| 原生查询接口 | `{Domain}NativeQueryRepository` | `OrderNativeQueryRepository` |

### 6.3 方法命名

| 层级 | 方法类型 | 格式 | 示例 |
|------|----------|------|------|
| Controller | HTTP 映射 | `{action}{Domain}` | `createOrder`, `getOrder` |
| Assembler | 转换 | `toCommand`, `toQuery`, `toResponse` | `toCommand(CreateOrderRequest)` |
| CommandHandler | 处理方法 | `handle` | `handle(CreateOrderCommand)` |
| QueryHandler | 处理方法 | `handle` | `handle(OrderPageQuery)` |
| 聚合根 | 业务方法 | `{verb}{Noun}` | `pay`, `cancel` |
| 聚合根 | 工厂方法 | `create` | `Order.create(...)` |
| 值对象 | 工厂方法 | `of`, `create` | `OrderItem.create(...)` |
| 领域服务 | 验证方法 | `validate{BusinessRule}` | `validate(Order)` |

---

## 7️⃣ 依赖约束（ArchUnit 核心规则）

完整 ArchUnit 规则集（必须纳入 CI）：

```java
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "com.bone.blueprint")
public class ArchitectureTest {

    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
            .check(classes);
    }

    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "com.bone.core..", "lombok..")
            .check(classes);
    }

    @ArchTest
    static void repositoryShouldOnlyHaveRuleQueries(JavaClasses classes) {
        classes().that().resideInAPackage("..domain.repository..")
            .and().areAssignableTo(Repository.class)
            .forEach(repo -> {
                repo.getMethods().stream()
                    .filter(m -> !m.getOwner().isAssignableTo(Repository.class))
                    .forEach(m -> {
                        String name = m.getName();
                        Class<?> returnType = m.getReturnType().getRawType();
                        boolean allowed = (name.startsWith("exists") && returnType.equals(boolean.class))
                                        || (name.startsWith("findBy") && returnType.equals(Optional.class));
                        if (!allowed) {
                            fail("Repository 方法 " + name + " 不允许。仅允许 existsByXxx(boolean) 或 findByXxx(Optional)");
                        }
                    });
            });
    }

    @ArchTest
    static void domainLayerShouldNotUseQueryBuilder(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().haveSimpleName("QueryBuilder")
            .check(classes);
    }

    @ArchTest
    static void queryHandlerShouldNotUseRepository(JavaClasses classes) {
        noClasses().that().resideInAPackage("..application.query.handler..")
            .should().dependOnClassesThat().resideInAPackage("..domain.repository..")
            .check(classes);
    }

    @ArchTest
    static void queryHandlerShouldOnlyUseQueryBuilder(JavaClasses classes) {
        classes().that().resideInAPackage("..application.query.handler..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("com.bone.metadata.sdk.query.dsl..", "..dto..", "..projection..", "java..", "org.springframework..")
            .check(classes);
    }

    @ArchTest
    static void commandHandlerShouldNotUseQueryBuilder(JavaClasses classes) {
        noClasses().that().resideInAPackage("..application.command.handler..")
            .should().dependOnClassesThat().haveSimpleName("QueryBuilder")
            .check(classes);
    }

    @ArchTest
    static void nativeQueryRepositoryShouldNotBeInDomain(JavaClasses classes) {
        noClasses().that().haveNameMatching(".*NativeQueryRepository")
            .should().resideInAPackage("..domain..")
            .check(classes);
    }

    @ArchTest
    static void queryHandlerShouldNotReturnDomainEntity(JavaClasses classes) {
        classes().that().resideInAPackage("..application.query.handler..")
            .should().onlyHaveRawReturnTypes(that().resideInAnyPackage("..dto..", "..projection..", "..PageResult", "java.lang", "java.util"))
            .check(classes);
    }

    @ArchTest
    static void domainServicesShouldNotHaveSpringAnnotations(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain.service..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
            .check(classes);
    }
}
```

---

## 8️⃣ 事务模型

| 位置 | 规则 |
|------|------|
| `application.command.handler` | `@Transactional`（必须） |
| `application.query.handler` | `@Transactional(readOnly = true)` |
| `application.event` | `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` |
| domain / infrastructure | 严禁事务注解 |

---

## 9️⃣ 异常处理分层

| 异常类型 | 抛出层 | HTTP 状态 |
|----------|--------|------------|
| `DomainException` | domain | 400 |
| `NotFoundException` | application / adapter | 404 |
| `SystemException` | infrastructure | 500 |

全局异常处理器统一转换为 `ApiResponse<T>`。

---

## 🔟 强制红线（CI 阻断级）

| ❌ 禁止 | ✅ 正确 |
|--------|--------|
| 手写 `RepositoryImpl`、`PO`、`Mapper` | 继承 `Repository`，SDK 动态代理 |
| `domain` 对象使用 `@Setter` 或 `@Data` | 仅 `@Getter` + 私有无参构造 |
| 数据库自增 ID 作为领域 ID | 双 ID 模型（业务 ID + 自增主键） |
| `domain.repository` 中定义展示查询 | 仅 `existsByXxx`、`findByBusinessKey` |
| Query 返回 domain 实体 | 返回 DTO 或 Projection |
| 在 `domain` 包内使用 `QueryBuilder` | `QueryBuilder` 仅用于 `application.query.handler` 和 `infrastructure.query.native` |
| 在 `application.command.handler` 中使用 `QueryBuilder` | 必须通过仓储规则查询获取数据 |
| JOIN 使用 `biz_id`（UUID） | 内部 JOIN 必须使用 `dbId`（Long） |
| Query Handler 中包含业务决策（如 `if (order.getStatus() == PAID)`） | Query Handler 仅做查询和映射，复杂逻辑放 domain |
| 在 `QueryBuilder` 返回的结果上执行 `map()` 时进行复杂计算 | 计算应在 domain 或 application 层完成 |
| 在领域服务或应用层使用 `if-else` 根据租户/场景分支业务逻辑 | 使用 `@ExtensionPoint` + `@Extension` 解耦 |
| 在 Query Handler 中使用超过 2 次的 `join()` | 复杂关联下沉到 `infrastructure.query.native` 原生 SQL |
| 依赖 SDK 自动发布事件却忽略事务边界 | 使用 `@TransactionalEventListener` 确保事务提交后才发布 |
| 聚合根序列化后丢失 `dbId` | 确保序列化框架能正确处理 `dbId`，或使用基类提供的存储区域 |
| 项目内创建 `common` 包 | 下沉 `bone-core` |
| SQL 字符串拼接 | 参数化查询 `#{param}` |
| `adapter.web.converter` 包名 | `adapter.web.assembler` |

---

## 1️⃣1️⃣ 测试体系

| 层级 | 工具 | 覆盖率要求 |
|------|------|------------|
| Domain 单元测试 | JUnit5 + AssertJ | ≥90% |
| Application 集成测试 | `@SpringBootTest` + TestContainers | 核心用例 100% |
| Architecture 测试 | ArchUnit | CI 必须通过 |

### 领域服务单元测试示例

```java
@ExtendWith(MockitoExtension.class)
class OrderAmountValidatorTest {

    @Test
    void shouldThrowExceptionWhenAmountExceedsLimit() {
        Order order = mock(Order.class);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("2000000"));

        assertThatThrownBy(() -> OrderAmountValidator.validate(order))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("订单金额超过限制");
    }

    @Test
    void shouldPassWhenAmountWithinLimit() {
        Order order = mock(Order.class);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("500000"));

        assertThatCode(() -> OrderAmountValidator.validate(order)).doesNotThrowAnyException();
    }
}
```

---

## 1️⃣2️⃣ 演进路径

- **v1 单体**：四层架构 + SDK 动态代理
- **v2 模块化**：多限界上下文分包
- **v3 事件化**：Outbox + MQ
- **v4 分布式**：微服务拆分

---

## 1️⃣3️⃣ 团队落地 Checklist

### 项目初始化
- [ ] 引入 `bone-core`、`bone-metadata-sdk`、`bone-extension-sdk` 依赖
- [ ] 启动类添加 `@EnableSqlRepositories` 和 `@EnableExtensionPoints`
- [ ] 创建完整包结构（无 `persistence`、无 `common`）
- [ ] 配置 ArchUnit 规则并集成到 CI
- [ ] 数据库表采用双 ID 模型（自增主键 + biz_id），内部 JOIN 使用 dbId

### 每个领域模块开发
- [ ] 聚合根使用业务 ID（UUID），工厂方法中直接生成
- [ ] 聚合根中包含 `dbId` 字段（Long），仅用于内部 JOIN，由 SDK 回填
- [ ] 仓储接口只允许规则查询（`existsByXxx`、`findByBusinessKey`）
- [ ] 领域服务使用静态方法或纯 POJO，不依赖 Spring
- [ ] 查询结果使用 DTO 或 Projection
- [ ] 所有读查询使用 `QueryBuilder.from(...)` 作为唯一入口
- [ ] 复杂 SQL 放在 `infrastructure.query.native` + 外部文件
- [ ] 编写领域层单元测试（≥90%）

### Code Review 核心关注点
- [ ] 是否有 `RepositoryImpl`、`PO`、`Mapper`、`common` 包？
- [ ] `domain` 包是否有 Spring/MyBatis 注解？是否有 `@Setter`/`@Data`？
- [ ] 仓储接口是否有禁止的查询方法（分页、列表、条件组合）？
- [ ] `domain` 包内是否使用了 `QueryBuilder`？
- [ ] 领域服务是否合理封装了跨聚合规则？命名是否清晰？
- [ ] Query 处理器是否返回 DTO/Projection？
- [ ] JOIN 是否使用了 `dbId`（Long）而非 `biz_id`？
- [ ] 原生查询是否只读且参数化？
- [ ] 事务是否在 `application.command.handler`？
- [ ] Assembler 命名是否为 `{Domain}Assembler`？

---

## 1️⃣4️⃣ 核心口诀

> **架构层面**：六边形定边界，整洁控依赖，CQRS 分读写，查询单一入口（QueryBuilder）。  
> **编码层面**：值对象自验证，聚合根充血，命令动词化，查询用 Builder，事务在应用，异常在领域，ID 双模型，JOIN 用长整。  
> **复杂查询**：两张表内用 DSL，三张以上写 SQL，扩展点替 if-else，事件发布事务捆。

---

**本规范即日起作为 Bone-Blueprint 工程落地的唯一标准。**  
**所有新项目必须遵循，存量项目应在迭代中逐步对齐。**

*文档版本：v9.5 | 生效日期：2026-04-22 | 维护团队：架构组*  
*核心修正：基于实际 SDK API 使用 QueryBuilder 作为查询唯一入口；明确双 ID 模型中 JOIN 必须使用 dbId；强化复杂查询下沉原则；补充序列化安全、领域事件发布事务绑定、扩展点替代 if-else 等最佳实践；新增 bone-extension-sdk 详细使用指南；全面优化目录结构，增加 application/service、domain/specification、独立 exception 包等；测试目录与主目录一一对应。*