# 🏛️ Bone-Blueprint v22.0 工程规范（AI可执行架构操作系统版）

## 企业级 DDD + 分级 CQRS + 双模式 UseCase + 六边形架构 + Bone 元数据驱动框架

> **定位**：从“架构规范”升级为“可演进的企业级业务操作系统”。以 4 条铁律守住架构底线，以双模式策略平衡效率与扩展性，以原子化 Handler 为 AI 可调度的能力积木，以 Flow 为流程编排载体。  
> **适用规模**：10–100 人团队 / 单体到分布式演进系统 / 企业级 AI 原生应用  
> **核心目标**：结构即规则、命名即语义、依赖即约束、能力即资产、流程可编排、演进有红线。  
> **生效日期**：2026-04-23

---

## 0️⃣ 设计哲学

本规范是 Bone-Blueprint 系列的终局融合版本，融合了 **工业级落地规范** 与 **双模式演进策略**，实现“架构正确性”与“组织可落地性”的终极平衡。

| 原则 | 说明 |
|------|------|
| **最小必要约束** | 只有 4 条铁律 CI 阻断，其余均为推荐或可选 |
| **Repository 空接口** | 子接口仅继承基类，不添加任何自定义方法 |
| **查询统一入口** | 所有自定义查询使用 `Criteria` 或 `QueryBuilder`，复杂查询提供逃生机制 |
| **ID 生成上移** | ID 由应用层生成后传入聚合根 |
| **实体 @Table 显式绑定** | 所有实体类使用 `@Table` 注解显式指定数据库表名 |
| **聚合根包直接化** | 聚合根直接放在 `domain.{aggregate}` 下，同包放实体、值对象、事件 |
| **双模式 UseCase** | Mode A 直写（快速），Mode B 编排（可复用、AI 就绪），由机器规则判定 |
| **Handler 粒度上限** | 单 UseCase 内 Handler 数量 ≤ 5，超过必须合并或引入 Flow 编排 |
| **AI 可见性** | 需要暴露给 AI 的能力必须封装为 Handler 并添加 `@Capability` 完整元数据 |
| **扩展点替代 if-else** | 多租户/多场景通过扩展点实现，提供决策可解释层 |
| **工具化落地** | 脚手架、ArchUnit、Flow Studio 可视化编排 |
| **渐进演进** | 五阶段路线图，从单体到 AI-Native 平台 |

**核心理念**：
> 先写 A（快），再长成 B（稳），最终进 Flow（智能）。  
> 硬约束保底线（防腐化），软能力促生长（提效率）。  
> 简单场景走简单路，复杂场景给复杂方案。  
> **规则可判定，决策可解释，复杂度可治理。**

---

## 1️⃣ 架构第一性原则（4 条铁律，CI 阻断）

### 🔴 铁律 1：依赖方向必须正确（唯一拓扑约束）

```
adapter → application → domain ← infrastructure
```

- ✔ 只能向内依赖
- ❌ 禁止任何反向 import（如 domain 引用 adapter 或 infrastructure）
- ✅ **ArchUnit 在 CI 中强制阻断**

### 🔴 铁律 2：Domain 必须纯净（分级纯净度）

| 层级 | 允许 | 禁止 |
|------|------|------|
| **Level 0（核心 Domain）** | 纯 Java + java.util | 任何框架注解、数据库、RPC |
| **Level 1（领域服务/值对象）** | lombok, bone-metadata-sdk 注解 | Spring, Jackson, JPA |
| **Level 2（DTO/Assembler）** | Jackson, MapStruct | 业务逻辑 |

- ✅ 允许 `bone-metadata-sdk` 的 `@Table`、`@Id`、`@GeneratedValue` 等元数据注解
- ✅ 领域服务类本身无 Spring 注解，允许在 `infrastructure.config` 中通过 `@Bean` 注册

> **为什么**：Domain 是业务真相来源，一旦沾染框架，技术债务将直接污染业务规则。

### 🔴 铁律 3：业务逻辑必须在 Domain（反贫血模型）

```java
// ❌ 贫血模型——业务逻辑泄漏到应用层
order.setStatus(OrderStatus.PAID);
if (order.getStatus() == OrderStatus.PAID) { ... }

// ✅ 充血模型——业务规则封装在聚合根
order.pay();  // 内部校验状态、计算、发布事件
```

- ❌ 禁止在 UseCase/Handler 中写 `if (order.getStatus() == X)` 等业务判断
- ✅ 所有业务规则、状态流转、不变量校验必须在聚合根或领域服务中

### 🔴 铁律 4：外部系统必须通过 ACL（防腐层）

- ❌ 禁止在 application / domain 中直接调用 Feign / HttpClient / MQ Producer
- ✅ 所有外部依赖通过 `domain.gateway` 定义端口，`infrastructure.gateway` 实现

> **为什么**：防止外部系统 API 变更、限流、宕机直接击穿业务核心。

**其他一切均为“推荐”或“可选”，团队可根据项目复杂度灵活选择。**

---

## 2️⃣ ID 生成策略（应用层生成，领域层纯接收）

### 2.1 核心原则

- **ID 生成属于基础设施行为，必须在应用层完成**
- 领域层聚合根工厂方法接收 ID 参数，不依赖任何 ID 生成工具
- 统一使用 `bone-core` 提供的 `DistributedIdGenerator`（雪花算法）在应用层直接调用生成 `Long` 型 ID
- 禁止使用数据库自增主键（`IDENTITY`）作为领域 ID
- 禁止创建多余的包装类，直接使用 `DistributedIdGenerator.generateLongId()`

### 2.2 数据库主键设计（订单示例）

```sql
-- 订单主表
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    tenant_id BIGINT COMMENT '租户ID',
    customer_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status)
);

-- 订单明细表（实体，有独立主键，由聚合根管理）
CREATE TABLE t_order_item (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    order_id BIGINT NOT NULL COMMENT '关联 t_order.id',
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
);
```

### 2.3 聚合根与实体定义（优化后包结构）

#### 订单明细实体（`OrderItem`）

```java
// domain/order/OrderItem.java
package com.bone.blueprint.domain.order;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order_item")
public class OrderItem extends AbstractEntity<Long> {

    private Long id;
    private Long orderId;          // 所属订单ID
    private Long productId;
    private String productName;    // 快照
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    private OrderItem(Long id, Long orderId, Long productId, String productName,
                      Integer quantity, BigDecimal unitPrice) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static OrderItem create(Long id, Long orderId, Long productId, String productName,
                                   Integer quantity, BigDecimal unitPrice) {
        if (productId == null || productId <= 0) {
            throw new DomainException("商品ID无效");
        }
        if (quantity == null || quantity <= 0) {
            throw new DomainException("商品数量必须大于0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("商品单价必须大于0");
        }
        return new OrderItem(id, orderId, productId, productName, quantity, unitPrice);
    }

    public void updateQuantity(Integer newQuantity) {
        if (newQuantity == null || newQuantity <= 0) {
            throw new DomainException("商品数量必须大于0");
        }
        this.quantity = newQuantity;
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(newQuantity));
    }
}
```

#### 订单聚合根（`Order`）

```java
// domain/order/Order.java
package com.bone.blueprint.domain.order;

import com.bone.core.domain.AggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.blueprint.domain.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order")
public class Order extends AggregateRoot<Long> {

    private Long id;
    private Long customerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private OrderStatus status;

    public static Order create(long id, Long customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单至少需要一个商品项");
        }
        Order order = new Order();
        order.id = id;
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.recalculateTotal();
        order.status = OrderStatus.CREATED;
        order.addDomainEvent(new OrderCreatedEvent(order));
        return order;
    }

    public void addItem(OrderItem item) {
        this.items.add(item);
        recalculateTotal();
    }

    public void removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            throw new DomainException("商品项索引无效");
        }
        this.items.remove(index);
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
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

    public void updateTotalAmount(BigDecimal newTotal) {
        if (newTotal == null || newTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("订单金额无效");
        }
        this.totalAmount = newTotal;
    }
}
```

---

## 3️⃣ Repository 规范（空接口 + 基类提供完整 CRUD + 逃生机制）

### 3.1 核心原则

- **Repository 子接口只需继承 `Repository<T, ID>`，不添加任何额外方法**
- 基类 `Repository<T, ID>` 已提供完整的 CRUD 能力：
  - `findById`, `findByIds`, `findByIdIncludingDeleted`
  - `save`, `insert`, `batchInsert`, `batchSave`
  - `update`, `updateByCriteria`
  - `deleteById`, `deleteByIds`
  - `findByCriteria`, `findOneByCriteria`, `pageByCriteria`, `countByCriteria`
  - `query`, `queryPage`
  - `aggregate` 系列（聚合查询）
  - `query()` 返回 `FluentQuery`（DSL 查询）
  - `where` 快捷条件入口
- **所有自定义查询（包括存在性校验、按业务键查询、复杂条件）一律使用 `Criteria` 或 `QueryBuilder`**
- 这样保证 Repository 职责单一：只作为聚合根的存储端口，不承担查询语义膨胀

### 3.2 Repository 子接口示例

```java
// domain/repository/OrderRepository.java
package com.bone.blueprint.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.blueprint.domain.order.Order;

public interface OrderRepository extends Repository<Order, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
```

### 3.3 强制逃生机制

当 Criteria/QueryBuilder 无法满足复杂查询时（如窗口函数、存储过程、复杂报表），允许使用原生 SQL，但必须标记 `@EscapedQuery` 并提供说明：

```java
// infrastructure/query/native/OrderNativeQueryRepository.java
package com.bone.blueprint.infrastructure.query.native;

import com.bone.blueprint.application.query.projection.OrderStatisticsProjection;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.Sql;
import com.bone.metadata.sdk.domain.annotation.EscapedQuery;
import java.util.List;

@NativeQueryRepository
public interface OrderNativeQueryRepository {
    
    @EscapedQuery(
        description = "月度订单统计报表",
        reason = "涉及窗口函数和复杂聚合，QueryBuilder无法表达",
        performanceWarning = "全表扫描，建议在低峰期执行"
    )
    @Sql("script:classpath:sql/order/monthlyOrderStatistics.sql")
    List<OrderStatisticsProjection> getMonthlyStatistics(@Param("yearMonth") String yearMonth);
}
```

### 3.4 使用 Criteria 进行存在性校验和业务键查询

```java
// 在 UseCase 或 Handler 中
Criteria<Order> criteria = Criteria.<Order>builder()
    .eq(Order::getOrderNo, orderNo);
boolean exists = orderRepository.countByCriteria(criteria) > 0;

Optional<Order> order = Optional.ofNullable(
    orderRepository.findOneByCriteria(Criteria.<Order>builder().eq(Order::getOrderNo, orderNo).build())
);
```

### 3.5 使用 QueryBuilder 进行列表/分页查询

```java
// 在 UseCase 或 QueryHandler 中
List<OrderDto> list = QueryBuilder.from(Order.class)
    .where(Order::getCustomerId).eq(customerId)
    .orderByDesc(Order::getCreateTime)
    .list()
    .map(OrderDto::from);
```

### 3.6 ArchUnit 约束

```java
@ArchTest
static void repositoryShouldNotHaveAnyCustomMethods(JavaClasses classes) {
    classes().that().resideInAPackage("..domain.repository..")
        .and().areAssignableTo(Repository.class)
        .forEach(repo -> {
            long customMethodCount = repo.getMethods().stream()
                .filter(m -> !m.getOwner().isAssignableTo(Repository.class))
                .count();
            if (customMethodCount > 0) {
                fail("Repository 子接口 " + repo.getName() + " 不应定义任何自定义方法，请使用 Criteria 或 QueryBuilder");
            }
        });
}
```

---

## 4️⃣ 双模式 UseCase 策略（机器可判定）

### 4.1 双模式定义

| 模式 | 名称 | 适用场景 | 代码形态 |
|------|------|----------|----------|
| 🟢 **Mode A** | 简单模式 | 简单 CRUD、边缘功能、MVP 快速验证 | UseCase 直写，逻辑内联 |
| 🔵 **Mode B** | 企业模式 | 核心能力、复用需求、AI 编排、多租户 | UseCase 编排 + Handler 原子化 |

### 4.2 机器可判定规则（强制进入 Mode B）

```
Mode B = 必须满足以下任意一条（结构信号判定）：
1. @Capability 标注（需要暴露给 AI）
2. @ExtensionPoint 参与（多租户/多场景）
3. ≥1 个 Gateway 调用（外部系统交互）
4. ≥1 个 Handler 编排（非单一步骤）
```

### 4.3 UseCase 接口定义

```java
// bone-core/usecase/UseCaseExecutor.java
package com.bone.core.usecase;

public interface UseCaseExecutor<C, R> {
    R execute(C command);
}
```

### 4.4 UseCase 元数据注解

```java
// bone-core/usecase/UseCase.java
package com.bone.core.usecase;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UseCase {
    String name();
    String description() default "";
    boolean transactional() default true;
    boolean idempotent() default false;
}
```

### 4.5 订单场景模式选择矩阵

| 场景 | 推荐模式 | 判定依据 |
|------|----------|----------|
| **订单创建** | 🔵 **Mode B** | 涉及 Gateway 调用 + 多个 Handler 编排 |
| **订单支付** | 🔵 **Mode B** | 涉及 Gateway 调用 + 扩展点 |
| **订单取消** | 🟢 **Mode A** | 无 Gateway 调用，单一逻辑 |
| **查询订单列表** | 🟢 **Mode A** | 纯读操作 |
| **查询订单详情** | 🟢 **Mode A** | 纯读操作 |

### 4.6 Handler 粒度上限（强制）

```java
// 🔴 强制规则：单 UseCase 内 Handler 数量 > 5 → 必须合并或引入 Flow 编排
@ArchTest
static void useCaseShouldNotHaveTooManyHandlers(JavaClasses classes) {
    classes().that().haveSimpleNameEndingWith("UseCase")
        .forEach(useCase -> {
            long handlerCount = useCase.getFields().stream()
                .filter(f -> f.getRawType().getName().endsWith("Handler"))
                .count();
            if (handlerCount > 5) {
                System.err.println("[WARN] UseCase " + useCase.getName() + 
                    " has " + handlerCount + " handlers, consider merging or using Flow");
            }
        });
}
```

### 4.7 Mode A：UseCase 直写（简单模式）

```java
// application/usecase/simple/CancelOrderUseCase.java
package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CancelOrder",
    description = "简单取消订单，适用于内部管理后台",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase implements UseCaseExecutor<CancelOrderCommand, Void> {

    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;

    @Override
    public Void execute(CancelOrderCommand cmd) {
        Order order = orderRepository.findById(cmd.getOrderId())
            .orElseThrow(() -> new NotFoundException("订单不存在"));
        
        order.cancel();
        orderRepository.save(order);
        inventoryGateway.releaseStock(order.getId());
        
        return null;
    }
}
```

### 4.8 Mode B：UseCase + Handler（企业模式）

#### Handler（原子能力积木）

```java
// application/command/handler/CheckStockHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CheckStockHandler {
    private final InventoryGateway inventoryGateway;
    
    public void handle(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (!inventoryGateway.checkStock(item.getProductId(), item.getQuantity())) {
                throw new DomainException("商品库存不足: " + item.getProductId());
            }
        }
    }
}

// application/command/handler/CreateOrderHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreateOrderHandler {
    private final OrderRepository orderRepository;
    
    public Order handle(Long orderId, Long customerId, List<OrderItem> items) {
        Order order = Order.create(orderId, customerId, items);
        orderRepository.save(order);
        return order;
    }
}
```

#### UseCase（编排层）

```java
// application/usecase/standard/CreateOrderUseCase.java
package com.bone.blueprint.application.usecase.standard;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.handler.CheckStockHandler;
import com.bone.blueprint.application.command.handler.CreateOrderHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@UseCase(
    name = "CreateOrder",
    description = "标准订单创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase implements UseCaseExecutor<CreateOrderCommand, Long> {

    private final CheckStockHandler checkStockHandler;
    private final CreateOrderHandler createOrderHandler;
    private final OrderRepository orderRepository;
    private final OrderPriceCalculator priceCalculator;  // 扩展点

    @Override
    public Long execute(CreateOrderCommand cmd) {
        // 1. 生成订单ID
        long orderId = DistributedIdGenerator.generateLongId();
        
        // 2. 构建订单明细
        List<OrderItem> items = cmd.getItems().stream()
            .map(dto -> OrderItem.create(
                DistributedIdGenerator.generateLongId(),
                orderId,
                dto.getProductId(),
                dto.getProductName(),
                dto.getQuantity(),
                dto.getUnitPrice()))
            .collect(Collectors.toList());
        
        // 3. 校验库存
        checkStockHandler.handle(items);
        
        // 4. 创建订单
        Order order = createOrderHandler.handle(orderId, cmd.getCustomerId(), items);
        
        // 5. 计算最终价格（扩展点）
        BigDecimal finalPrice = priceCalculator.calculate(
            OrderPriceRequest.builder()
                .baseAmount(order.getTotalAmount())
                .shippingFee(BigDecimal.ZERO)
                .build()
        );
        order.updateTotalAmount(finalPrice);
        orderRepository.save(order);
        
        return orderId;
    }
}
```

### 4.9 Mode A 查询 UseCase（纯读）

```java
// application/usecase/simple/GetOrderListUseCase.java
package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.order.Order;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "GetOrderList",
    description = "查询订单列表",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class GetOrderListUseCase implements UseCaseExecutor<OrderPageQuery, PageResult<OrderDto>> {

    @Override
    public PageResult<OrderDto> execute(OrderPageQuery query) {
        return QueryBuilder.from(Order.class)
            .where(Order::getCustomerId).eq(query.getCustomerId())
            .where(Order::getStatus).eq(query.getStatus())
            .orderByDesc(Order::getCreateTime)
            .page(query.getPageNum(), query.getPageSize())
            .map(OrderDto::from);
    }
}
```

---

## 5️⃣ AI 可见性规范（@Capability 完整元数据）

### 5.1 能力注解定义

```java
// infrastructure/annotation/Capability.java
package com.bone.blueprint.infrastructure.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Capability {
    String name();
    String description();
    String inputSchema();
    String outputSchema();
    boolean idempotent() default false;
    int cost() default 1;           // 1-10，越高成本越大
    boolean retryable() default true;
    int timeout() default 30;       // 秒
}
```

### 5.2 使用示例

```java
// application/command/handler/ReleaseStockHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.infrastructure.annotation.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Capability(
    name = "ReleaseStock",
    description = "释放订单占用的库存",
    inputSchema = "{\"orderId\": \"long\"}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 10
)
@Component
@RequiredArgsConstructor
public class ReleaseStockHandler {
    private final InventoryGateway inventoryGateway;
    
    public boolean handle(Long orderId) {
        inventoryGateway.releaseStock(orderId);
        return true;
    }
}
```

### 5.3 Handler Registry（能力注册表）

```java
// infrastructure/handler/HandlerRegistry.java
package com.bone.blueprint.infrastructure.handler;

import com.bone.blueprint.infrastructure.annotation.Capability;
import lombok.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HandlerRegistry {
    private final Map<String, CapabilityMetadata> capabilities = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    
    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @PostConstruct
    public void init() {
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(Capability.class);
        handlers.forEach((name, handler) -> {
            Capability annotation = handler.getClass().getAnnotation(Capability.class);
            if (annotation != null) {
                CapabilityMetadata metadata = CapabilityMetadata.builder()
                    .name(annotation.name())
                    .description(annotation.description())
                    .inputSchema(annotation.inputSchema())
                    .outputSchema(annotation.outputSchema())
                    .idempotent(annotation.idempotent())
                    .cost(annotation.cost())
                    .retryable(annotation.retryable())
                    .timeout(annotation.timeout())
                    .build();
                capabilities.put(annotation.name(), metadata);
            }
        });
    }
    
    public List<CapabilityMetadata> listCapabilities() {
        return new ArrayList<>(capabilities.values());
    }
    
    @Value
    @lombok.Builder
    public static class CapabilityMetadata {
        String name;
        String description;
        String inputSchema;
        String outputSchema;
        boolean idempotent;
        int cost;
        boolean retryable;
        int timeout;
    }
}
```

---

## 6️⃣ 扩展点规范（决策可解释层）

### 6.1 包路径与命名

| 角色 | 包路径 | 命名格式 | 示例 |
|------|--------|----------|------|
| 扩展点接口 | `domain.extension.{context}` | `{Domain}{Function}Calculator` | `OrderPriceCalculator` |
| 扩展实现 | `infrastructure.extension.{context}` | `{Modifier}{Domain}{Function}Calculator` | `DefaultOrderPriceCalculator` |

### 6.2 扩展点接口定义

```java
// domain/extension/order/OrderPriceCalculator.java
package com.bone.blueprint.domain.extension.order;

import com.bone.extension.sdk.annotation.ExtensionPoint;
import java.math.BigDecimal;

@ExtensionPoint(
    name = "订单价格计算扩展点",
    description = "支持租户差异化定价、VIP折扣、促销活动等场景",
    version = "1.0.0"
)
public interface OrderPriceCalculator {
    BigDecimal calculate(OrderPriceRequest request);
}
```

### 6.3 扩展请求值对象

```java
// domain/extension/order/OrderPriceRequest.java
package com.bone.blueprint.domain.extension.order;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;

@Value
@Builder
public class OrderPriceRequest {
    BigDecimal baseAmount;
    BigDecimal shippingFee;
    String couponCode;
}
```

### 6.4 扩展实现

**默认实现（必须存在）**：
```java
// infrastructure/extension/order/DefaultOrderPriceCalculator.java
package com.bone.blueprint.infrastructure.extension.order;

import com.bone.extension.sdk.annotation.Extension;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import java.math.BigDecimal;

@Extension(
    name = "默认订单价格计算",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "*",
    priority = 100
)
public class DefaultOrderPriceCalculator implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        return request.getBaseAmount().add(request.getShippingFee());
    }
}
```

**租户定制**：
```java
// infrastructure/extension/order/AliOrderPriceCalculator.java
@Extension(tenant = "ALI", bizCode = "ecommerce", useCase = "order", scenario = "*", priority = 20)
public class AliOrderPriceCalculator implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        BigDecimal amount = request.getBaseAmount();
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            amount = amount.subtract(new BigDecimal("200"));
        }
        return amount.add(request.getShippingFee());
    }
}
```

**场景定制**：
```java
// infrastructure/extension/order/VipOrderPriceCalculator.java
@Extension(tenant = "*", bizCode = "ecommerce", useCase = "order", scenario = "vip", priority = 10)
public class VipOrderPriceCalculator implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        return request.getBaseAmount().multiply(new BigDecimal("0.9"));
    }
}
```

### 6.5 扩展点决策记录器

```java
// infrastructure/extension/ExtensionDecisionRecorder.java
package com.bone.blueprint.infrastructure.extension;

import lombok.Builder;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ExtensionDecisionRecorder {
    
    private final List<ExtensionDecision> decisions = new ArrayList<>();
    private final ConcurrentHashMap<String, List<ExtensionDecision>> decisionsByPoint = new ConcurrentHashMap<>();
    
    public ExtensionDecision record(String extensionPoint, Object input, 
                                    List<ExtensionCandidate> candidates,
                                    ExtensionCandidate selected) {
        ExtensionDecision decision = ExtensionDecision.builder()
            .extensionPoint(extensionPoint)
            .timestamp(Instant.now())
            .input(input)
            .candidates(candidates.stream()
                .map(c -> c.getExtensionClass().getSimpleName())
                .collect(Collectors.toList()))
            .selected(selected.getExtensionClass().getSimpleName())
            .reason(selected.getMatchReason())
            .build();
        
        decisions.add(decision);
        decisionsByPoint.computeIfAbsent(extensionPoint, k -> new ArrayList<>()).add(decision);
        
        return decision;
    }
    
    public List<ExtensionDecision> getDecisions(String extensionPoint) {
        return decisionsByPoint.getOrDefault(extensionPoint, new ArrayList<>());
    }
    
    @Value
    @Builder
    public static class ExtensionDecision {
        String extensionPoint;
        Instant timestamp;
        Object input;
        List<String> candidates;
        String selected;
        String reason;
    }
    
    @Value
    @Builder
    public static class ExtensionCandidate {
        Class<?> extensionClass;
        String matchReason;
    }
}
```

### 6.6 业务上下文全入口覆盖

**强制规范**：所有入站适配器（Web、RPC、MQ、Schedule）在进入应用层前必须绑定 `BizContext`，并在 finally 中解绑。

#### Web 拦截器

```java
// adapter/web/interceptor/TenantInterceptor.java
package com.bone.blueprint.adapter.web.interceptor;

import com.bone.extension.sdk.BizContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        BizContext.builder()
            .tenant(request.getHeader("X-Tenant-Id"))
            .bizCode("ecommerce")
            .useCase("order")
            .scenario(request.getHeader("X-Scenario"))
            .build()
            .bind();
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        BizContext.unbind();
    }
}
```

#### MQ 监听器

```java
// adapter/mq/listener/OrderPaidListener.java
package com.bone.blueprint.adapter.mq.listener;

import com.bone.extension.sdk.BizContext;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaidListener {
    
    @KafkaListener(topics = "order-paid")
    public void onMessage(String message) {
        BizContext.builder()
            .tenant(extractTenant(message))
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("standard")
            .build()
            .bind();
        try {
            // 业务逻辑
        } finally {
            BizContext.unbind();
        }
    }
    
    private String extractTenant(String message) {
        // 从消息中提取租户ID
        return "DEFAULT";
    }
}
```

#### Schedule 定时任务

```java
// adapter/schedule/OrderTimeoutJob.java
package com.bone.blueprint.adapter.schedule;

import com.bone.extension.sdk.BizContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {
    
    @Scheduled(cron = "0 0/5 * * * ?")
    public void execute() {
        BizContext.builder()
            .tenant("SYSTEM")
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("system")
            .build()
            .bind();
        try {
            // 业务逻辑
        } finally {
            BizContext.unbind();
        }
    }
}
```

### 6.7 ArchUnit 约束

```java
@ArchTest
static void everyExtensionPointShouldHaveDefaultImplementation(JavaClasses classes) {
    classes().that().areAnnotatedWith(ExtensionPoint.class).forEach(extPoint -> {
        long defaultImplCount = classes().that().areAnnotatedWith(Extension.class)
            .filter(c -> c.isAssignableTo(extPoint))
            .filter(c -> {
                Extension ann = c.getAnnotation(Extension.class);
                return ann != null && "*".equals(ann.tenant()) && "*".equals(ann.scenario());
            })
            .count();
        if (defaultImplCount == 0) {
            fail("ExtensionPoint " + extPoint.getName() + " 缺少默认实现");
        }
    });
}

@ArchTest
static void extensionImplementationShouldBeNamedCalculator(JavaClasses classes) {
    classes().that().areAnnotatedWith(Extension.class)
        .should().haveSimpleNameEndingWith("Calculator")
        .check(classes);
}
```

---

## 7️⃣ 标准工程结构（完整版）

```text
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
│
├── adapter/                                    # 入站适配器层
│   ├── web/
│   │   ├── controller/
│   │   │   ├── OrderController.java
│   │   │   └── ExtensionDiagnosticController.java
│   │   ├── dto/
│   │   │   ├── request/CreateOrderRequest.java
│   │   │   └── response/OrderDetailResponse.java
│   │   ├── assembler/OrderAssembler.java
│   │   └── interceptor/TenantInterceptor.java
│   ├── rpc/OrderRpcService.java
│   ├── mq/listener/OrderPaidListener.java
│   └── schedule/CancelExpiredOrderJob.java
│
├── application/                                # 应用层
│   ├── usecase/                                # 用例层
│   │   ├── UseCaseExecutor.java
│   │   ├── simple/                             # 🟢 Mode A
│   │   │   ├── CancelOrderUseCase.java
│   │   │   └── GetOrderListUseCase.java
│   │   └── standard/                           # 🔵 Mode B
│   │       ├── CreateOrderUseCase.java
│   │       └── PayOrderUseCase.java
│   ├── command/                                # 命令处理器（Mode B）
│   │   └── handler/
│   │       ├── CheckStockHandler.java
│   │       ├── CreateOrderHandler.java
│   │       ├── DeductStockHandler.java
│   │       └── ReleaseStockHandler.java
│   ├── command/cmd/
│   │   ├── CreateOrderCommand.java
│   │   └── CancelOrderCommand.java
│   ├── query/
│   │   ├── qry/OrderPageQuery.java
│   │   ├── handler/OrderPageQueryHandler.java
│   │   └── dto/OrderDto.java
│   ├── event/OrderPaidEventHandler.java
│   └── assembler/OrderAssembler.java
│
├── domain/                                     # 领域层
│   ├── order/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   ├── valueobject/OrderStatus.java
│   │   └── event/
│   │       ├── OrderCreatedEvent.java
│   │       ├── OrderPaidEvent.java
│   │       └── OrderCancelledEvent.java
│   ├── repository/OrderRepository.java
│   ├── gateway/
│   │   ├── InventoryGateway.java
│   │   ├── MessageGateway.java
│   │   └── PaymentGateway.java
│   ├── service/order/OrderAmountValidator.java
│   ├── extension/order/OrderPriceCalculator.java
│   └── exception/OrderDomainException.java
│
└── infrastructure/                             # 基础设施层
    ├── gateway/
    │   ├── feign/InventoryFeignGatewayImpl.java
    │   ├── rocketmq/MessageGatewayImpl.java
    │   └── wechat/WechatPayGatewayImpl.java
    ├── extension/
    │   ├── order/
    │   │   ├── DefaultOrderPriceCalculator.java
    │   │   ├── AliOrderPriceCalculator.java
    │   │   └── VipOrderPriceCalculator.java
    │   └── ExtensionDecisionRecorder.java
    ├── handler/
    │   ├── HandlerRegistry.java
    │   └── annotation/Capability.java
    ├── query/native/
    │   └── OrderNativeQueryRepository.java
    ├── security/
    │   ├── JwtTokenProvider.java
    │   └── PasswordEncoderProvider.java
    ├── config/
    │   ├── DomainServiceConfig.java
    │   ├── BoneMetadataConfiguration.java
    │   ├── ExtensionConfiguration.java
    │   └── WebMvcConfiguration.java
    └── exception/InfrastructureException.java
```

---

## 8️⃣ 命名体系

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| UseCase 接口 | `UseCaseExecutor<C, R>` | 框架提供 |
| UseCase 实现（Mode A） | `{Action}{Domain}UseCase` | `CancelOrderUseCase` |
| UseCase 实现（Mode B） | `{Action}{Domain}UseCase` | `CreateOrderUseCase` |
| Handler | `{Action}{Domain}Handler` | `CheckStockHandler` |
| 命令对象 | `{Action}{Domain}Command` | `CreateOrderCommand` |
| 查询对象 | `{Domain}{Action}Query` | `OrderPageQuery` |
| 扩展点接口 | `{Domain}{Function}Calculator` | `OrderPriceCalculator` |
| 扩展实现 | `{Modifier}{Domain}{Function}Calculator` | `DefaultOrderPriceCalculator` |
| 聚合根 | `{Aggregate}` | `Order` |
| 实体 | `{Aggregate}{Component}` | `OrderItem` |
| 值对象 | `{BusinessConcept}` | `OrderStatus` |
| 仓储接口 | `{Aggregate}Repository` | `OrderRepository` |
| 网关接口 | `{ExternalSystem}Gateway` | `InventoryGateway` |
| 原生查询接口 | `{Domain}NativeQueryRepository` | `OrderNativeQueryRepository` |

---

## 9️⃣ 事务与异常模型

| 位置 | 规则 |
|------|------|
| `application.usecase`（Mode A） | `@Transactional`（按需） |
| `application.usecase`（Mode B） | `@Transactional`（必须） |
| `application.query.handler` | `@Transactional(readOnly = true)` |
| `application.event` | `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` |
| domain / infrastructure | 严禁事务注解 |

| 异常类型 | 抛出层 | HTTP 状态 |
|----------|--------|------------|
| `DomainException` | domain | 400 |
| `NotFoundException` | application / adapter | 404 |
| `SystemException` | infrastructure | 500 |

全局异常处理器统一转换为 `ApiResponse<T>`。

---

## 🔟 测试体系

| 层级 | 工具 | 覆盖率要求 |
|------|------|------------|
| Domain 单元测试 | JUnit5 + AssertJ | ≥90% |
| Application 集成测试 | `@SpringBootTest` + TestContainers | 核心用例 100% |
| Architecture 测试 | ArchUnit | CI 必须通过 |

**领域服务测试示例**：
```java
@ExtendWith(MockitoExtension.class)
class OrderAmountValidatorTest {
    @Test
    void shouldThrowExceptionWhenAmountExceedsLimit() {
        Order order = mock(Order.class);
        when(order.getTotalAmount()).thenReturn(new BigDecimal("2000000"));
        assertThatThrownBy(() -> OrderAmountValidator.validate(order))
            .isInstanceOf(DomainException.class);
    }
}
```

**Handler 单元测试示例**：
```java
@ExtendWith(MockitoExtension.class)
class CreateOrderHandlerTest {
    @Mock
    private OrderRepository orderRepository;
    @InjectMocks
    private CreateOrderHandler createOrderHandler;

    @Test
    void shouldCreateOrder() {
        List<OrderItem> items = new ArrayList<>();
        Order order = createOrderHandler.handle(1L, 100L, items);
        verify(orderRepository).save(any(Order.class));
    }
}
```

**扩展点集成测试示例**：
```java
@SpringBootTest
class OrderPriceExtensionTest {
    @Autowired
    private OrderPriceCalculator priceCalculator;
    
    @Test
    @WithMockTenant(tenant = "ALI", scenario = "vip")
    void shouldRouteToAliVipExtension() {
        // 验证路由正确
    }
}
```

**UseCase 集成测试示例**：
```java
@SpringBootTest
@Transactional
@Rollback
class CreateOrderUseCaseTest {
    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Test
    void shouldCreateOrderSuccessfully() {
        CreateOrderCommand command = CreateOrderCommand.builder()
            .customerId(100L)
            .items(List.of(
                OrderItemDto.builder()
                    .productId(1L)
                    .productName("测试商品")
                    .quantity(1)
                    .unitPrice(new BigDecimal("100"))
                    .build()
            ))
            .build();
        Long orderId = createOrderUseCase.execute(command);
        assertThat(orderId).isNotNull();
    }
}
```

---

## 1️⃣1️⃣ 渐进式演进路线图

### 阶段 1（第 1 个月）：建立底线
- ✅ 建立 adapter / application / domain / infrastructure 包结构
- ✅ 核心业务逻辑下沉到 domain（充血模型）
- ✅ 配置 ArchUnit（4 条铁律 + 核心约束）
- ✅ 统一使用雪花 ID（`DistributedIdGenerator.generateLongId()`），数据库主键为 `BIGINT`
- ✅ 实体类全部添加 `@Table` 注解，显式指定表名
- ✅ 聚合根与聚合内实体放在同一包下（`domain.order`）
- ✅ Repository 子接口为空，不添加任何自定义方法
- ✅ 所有自定义查询使用 `Criteria` 或 `QueryBuilder`
- ❌ 不强制 CQRS 分级，L1 模式允许 ApplicationService 内直接使用 Criteria/QueryBuilder
- ❌ 不修改存量代码，新模块按规范开发

### 阶段 2（第 2–3 个月）：引入双模式 UseCase
- ✅ 默认使用 Mode A（UseCase 直写）
- ✅ 执行机器规则判定，自动识别 Mode B
- ✅ 抽取 Handler，升级 Mode B
- ✅ 引入 `bone-cli` 脚手架

### 阶段 3（第 4–6 个月）：引入扩展点 + 决策可解释层
- ✅ 识别多租户/多场景差异
- ✅ 定义扩展点接口
- ✅ 实现默认扩展和定制扩展
- ✅ 接入 ExtensionDecisionRecorder

### 阶段 4（第 7–9 个月）：Handler Registry + AI 就绪
- ✅ 扫描 `@Capability` 注解
- ✅ 生成能力清单（含完整元数据）
- ✅ 接入 AI 调度

### 阶段 5（第 10–12 个月）：Flow Runtime
- ✅ 分布式执行
- ✅ Saga 补偿
- ✅ AI 动态编排

---

## 1️⃣2️⃣ 团队落地 Checklist

### 项目初始化
- [ ] 引入 `bone-core`、`bone-metadata-sdk`、`bone-extension-sdk`
- [ ] 启动类添加 `@EnableSqlRepositories` 和 `@EnableExtensionPoints`
- [ ] 配置 `bone.blueprint.cqrs.default-level: L1`
- [ ] 创建包结构（见第 7 章）
- [ ] 配置 ArchUnit 规则并集成 CI
- [ ] 确认 `DistributedIdGenerator` 可用（环境变量配置）
- [ ] 数据库主键使用 `BIGINT`，无 `biz_id`
- [ ] 所有实体类已添加 `@Table("table_name")` 注解
- [ ] 聚合根与聚合内实体放在同一包下

### 每个模块开发
- [ ] 根据场景选择 Mode A 或 Mode B（机器规则自动判定）
- [ ] 聚合根充血，包含业务方法
- [ ] 聚合根工厂方法接收 ID 参数，不依赖外部生成器
- [ ] 聚合根、实体类添加 `@Table` 注解
- [ ] Repository 子接口为空（仅继承 `Repository<T, ID>`）
- [ ] 所有自定义查询（存在性、业务键、列表、分页）使用 `Criteria` 或 `QueryBuilder`
- [ ] UseCase 带 `@UseCase` 注解
- [ ] 代码量控制：简单 CRUD ≤ 5 个类
- [ ] Assembler 使用 `@Mapper(componentModel = "spring")`，注入使用
- [ ] 单 UseCase 内 Handler 数量 ≤ 5

### 扩展点使用
- [ ] 扩展点接口定义在 `domain.extension.{context}`
- [ ] 命名格式 `{Domain}{Function}Calculator`
- [ ] 扩展实现放在 `infrastructure.extension.{context}`，命名 `{Modifier}{Domain}{Function}Calculator`
- [ ] 默认实现（`tenant="*", scenario="*"`）必须存在
- [ ] 路由维度完整覆盖业务场景
- [ ] 编写扩展点单元测试和集成测试

### AI 能力暴露
- [ ] 需要暴露给 AI 的 Handler 添加 `@Capability` 注解
- [ ] 完整填写 inputSchema、outputSchema、cost、timeout 等元数据
- [ ] 通过 HandlerRegistry 暴露能力清单

### 升级 L2/L3
- [ ] 识别复杂查询信号（≥3 条件、分页、关联）
- [ ] 创建 QueryHandler + DTO/Projection
- [ ] 使用 QueryBuilder 或 `@Sql`
- [ ] 保持 Repository 空接口，不添加任何方法

### Code Review 重点
- [ ] 依赖方向是否正确？（ArchUnit 阻断）
- [ ] Domain 是否纯净（无 Spring 注解，只允许 bone-metadata-sdk 元数据注解）？
- [ ] 业务逻辑是否在聚合根/领域服务中？
- [ ] 是否贫血模型？
- [ ] 外部调用是否通过 Gateway？
- [ ] Repository 子接口是否为空？
- [ ] 是否使用 Criteria/QueryBuilder 代替 Repository 自定义方法？
- [ ] 实体类是否添加了 `@Table` 注解？
- [ ] 聚合根与实体是否同包（`domain.{aggregate}`）？
- [ ] 扩展点接口是否在 `domain.extension`，实现是否在 `infrastructure.extension`？
- [ ] 扩展实现命名是否以 `Calculator` 结尾？
- [ ] 单 UseCase 内 Handler 数量是否 ≤ 5？
- [ ] AI 能力是否添加了完整的 `@Capability` 元数据？
- [ ] Assembler 是否使用 Spring 注入方式？

---

## 1️⃣2️⃣ 基础设施组件规范

### 12.1 租户拦截器（TenantInterceptor）

#### 位置与职责
- **位置**：`bone-web` 模块中的 `com.bone.core.web.interceptor.TenantInterceptor`
- **职责**：从请求头提取租户信息，设置到租户上下文和业务上下文中

#### 核心功能
```java
@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String BIZ_CODE_HEADER = "X-Biz-Code";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从请求头获取租户ID
        String tenantIdStr = request.getHeader(TENANT_ID_HEADER);
        Long tenantId = parseTenantId(tenantIdStr);
        
        // 2. 设置到租户上下文（支持线程池传递）
        TenantContext.setTenantId(tenantId);
        
        // 3. 同步到业务上下文（支持扩展点路由）
        if (BIZ_CONTEXT_AVAILABLE) {
            setBizContext(tenantId, bizCode);
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        // 清理租户上下文，防止内存泄漏
        TenantContext.clear();
    }
}
```

#### 使用方式
```java
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor())
                .addPathPatterns("/**");
    }
}
```

#### 最佳实践
- **请求头约定**：租户ID使用 `X-Tenant-Id`，业务码使用 `X-Biz-Code`
- **默认值处理**：未提供租户ID时使用默认租户（0L）
- **上下文清理**：在 `afterCompletion` 中清理上下文，防止内存泄漏
- **扩展点支持**：自动同步到 BizContext，支持扩展点路由

### 12.2 全局异常处理器（GlobalExceptionHandler）

#### 位置与职责
- **位置**：`bone-web` 模块中的 `com.bone.core.exception.GlobalExceptionHandler`
- **职责**：统一处理所有异常，将异常转换为标准的 API 响应格式

#### 核心功能
```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ServiceException.class)
    public ApiResponse<?> serviceExceptionHandler(ServiceException ex) {
        log.info("[serviceExceptionHandler]", ex);
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
    
    @ExceptionHandler(BizException.class)
    public ApiResponse<?> bizExceptionHandler(BizException ex) {
        log.info("[bizExceptionHandler]", ex);
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> defaultExceptionHandler(HttpServletRequest req, Throwable ex) {
        log.error("[defaultExceptionHandler]", ex);
        return ApiResponse.error(INTERNAL_SERVER_ERROR.getCode(), INTERNAL_SERVER_ERROR.getMsg());
    }
}
```

#### 异常分类处理
| 异常类型 | HTTP状态码 | 说明 |
|---------|-----------|------|
| `MissingServletRequestParameterException` | 400 | 请求参数缺失 |
| `MethodArgumentTypeMismatchException` | 400 | 请求参数类型错误 |
| `MethodArgumentNotValidException` | 400 | 参数校验不通过 |
| `BindException` | 400 | 参数绑定错误 |
| `ConstraintViolationException` | 400 | Validator 校验失败 |
| `NoHandlerFoundException` | 404 | 请求地址不存在 |
| `HttpRequestMethodNotSupportedException` | 405 | 请求方法不正确 |
| `ServiceException` | 200 | 服务异常（业务异常） |
| `BizException` | 200 | 业务异常 |
| `Exception` | 500 | 系统异常 |

#### 最佳实践
- **异常分类**：根据异常类型返回不同的 HTTP 状态码和错误信息
- **日志记录**：业务异常使用 info 级别，系统异常使用 error 级别
- **统一响应**：所有异常都返回 `ApiResponse` 格式
- **异常日志**：系统异常会自动记录到数据库（可选）

### 12.3 模块依赖建议

#### bone-web 模块
- **定位**：框架级 Web 基础设施组件
- **包含内容**：
  - `TenantInterceptor`：租户拦截器
  - `GlobalExceptionHandler`：全局异常处理器
  - 其他 Web 相关基础设施组件

#### 业务模块依赖
```xml
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 使用建议
- **不要重复实现**：业务模块不应重复实现 `TenantInterceptor` 和 `GlobalExceptionHandler`
- **直接使用**：通过依赖 `bone-web` 模块直接使用框架提供的实现
- **定制化**：如需定制化，可通过扩展点机制或继承方式实现

---

## 1️⃣3️⃣ 核心口诀（最终版）

> **四铁律：依赖向内不反向，Domain 纯净有分级，业务逻辑要充血，外部系统走 ACL。**  
> **ID 策略：雪花 Long 主键，应用层生成传入，不包装不依赖，领域只收不生产。**  
> **实体注解：@Table 必须加，表名显式蛇形写，聚合根 t_order，明细同包不搞 entity 子包。**  
> **Repository：子接口空，基类方法够用，Criteria 和 QueryBuilder 查一切，复杂查询逃生标记。**  
> **双模式：Mode A 直写快，Mode B 判定机器定，Handler 不超 5，超则合并或 Flow。**  
> **AI 可见：@Capability 完整元数据，Handler Registry 能力清。**  
> **扩展点：接口 Calculator，实现也用 Calculator，默认实现必须有，决策可解释层要记录。**  
> **CQRS：L1 直接用工具类，L2 独立 Handler，L3 读写分离。**  
> **落地法：阶段一建底线，阶段二拆查询，阶段三做分离，架构陪团队成长。**

---

**本规范即日起作为 Bone-Blueprint 工程落地的终局融合标准。**  
*文档版本：v22.0 | 生效日期：2026-04-23*  
*核心能力：4 条铁律、分级 Domain 纯净度、机器可判定 Mode B、Handler 粒度上限、AI 完整元数据、扩展点决策可解释层、逃生机制、五阶段演进、完整测试体系。*