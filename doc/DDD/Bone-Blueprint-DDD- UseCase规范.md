# 🏛️ Bone-Blueprint v24.0 工程规范（终局融合·AI-Native 企业级架构）

## 企业级 DDD + 分级 CQRS + 双模式 UseCase + 六边形架构 + Bone 元数据驱动框架

> **定位**：从"架构规范"升级为"可演进的企业级业务操作系统"。以 5 条铁律守住架构底线，以双模式策略平衡效率与扩展性，以原子化 Handler 为 AI 可调度的能力积木，以 Flow 为流程编排载体，以 @Capability 为 AI 可发现的能力资产。  
> **适用规模**：10–100 人团队 / 单体到分布式演进系统 / 企业级 AI 原生应用  
> **核心目标**：结构即规则、命名即语义、依赖即约束、能力即资产、流程可编排、演进有红线。  
> **生效日期**：2026-04-24

---

## 0️⃣ 设计哲学

本规范是 Bone-Blueprint 系列的终局融合版本，融合了 **工业级落地规范**、**双模式演进策略** 与 **AI 原生架构**，实现"架构正确性"、"组织可落地性"与"AI 可编排性"的终极平衡。

| 原则 | 说明 |
|------|------|
| **最小必要约束** | 5 条铁律 CI 阻断，其余均为推荐或可选 |
| **统一入口原则** | Controller 只依赖 UseCase，不直接依赖 Handler 或 QueryHandler |
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
> **Controller 永远只认 UseCase，Handler 只给 UseCase 用。**  
> **@Capability 是 AI 发现能力的唯一入口。**  
> **规则可判定，决策可解释，复杂度可治理。**

---

## 1️⃣ 架构第一性原则（5 条铁律，CI 阻断）

### 🔴 铁律 1：依赖方向必须正确（唯一拓扑约束）

```
adapter → application → domain ← infrastructure
```

- ✔ 只能向内依赖
- ❌ 禁止任何反向 import（如 domain 引用 adapter 或 infrastructure）
- ✅ **ArchUnit 在 CI 中强制阻断**

### 🔴 铁律 2：Controller 统一入口原则

> **Controller 只能依赖 UseCase，禁止直接依赖 Handler 或 QueryHandler**

```java
// ✅ 正确：Controller 依赖 UseCase
private final GetOrderDetailUseCase getOrderDetailUseCase;

// ❌ 错误：Controller 直接依赖 QueryHandler
private final OrderDetailQueryHandler orderDetailQueryHandler;
```

**理由**：
- 保持调用入口的唯一性
- 便于统一日志、权限、多租户拦截
- AI/Flow 编排只需要认识 UseCase
- 查询侧简单场景用 Mode A，复杂场景演进为 Mode B

### 🔴 铁律 3：Domain 必须纯净（分级纯净度）

| 层级 | 允许 | 禁止 |
|------|------|------|
| **Level 0（核心 Domain）** | 纯 Java + java.util | 任何框架注解、数据库、RPC |
| **Level 1（领域服务/值对象）** | lombok, bone-metadata-sdk 注解 | Spring, Jackson, JPA |
| **Level 2（DTO/Assembler）** | Jackson, MapStruct | 业务逻辑 |

### 🔴 铁律 4：业务逻辑必须在 Domain（反贫血模型）

```java
// ❌ 贫血模型——业务逻辑泄漏到应用层
order.setStatus(OrderStatus.PAID);

// ✅ 充血模型——业务规则封装在聚合根
order.pay();  // 内部校验状态、计算、发布事件
```

- ❌ 禁止在 UseCase/Handler 中写 `if (order.getStatus() == X)` 等业务判断
- ✅ 所有业务规则、状态流转、不变量校验必须在聚合根或领域服务中

### 🔴 铁律 5：外部系统必须通过 ACL（防腐层）

- ❌ 禁止在 application/domain 中直接调用 Feign/HttpClient/MQ Producer
- ✅ 所有外部依赖通过 `domain.gateway` 定义端口，`infrastructure.gateway` 实现

> **为什么**：防止外部系统 API 变更、限流、宕机直接击穿业务核心。

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
        addDomainEvent(new OrderPaidEvent(this));
    }

    public void cancel() {
        if (this.status == OrderStatus.SHIPPED) {
            throw new DomainException("已发货订单无法取消");
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new DomainException("订单已取消");
        }
        this.status = OrderStatus.CANCELLED;
        addDomainEvent(new OrderCancelledEvent(this));
    }

    public void updateTotalAmount(BigDecimal newTotal) {
        if (newTotal == null || newTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("订单金额无效");
        }
        this.totalAmount = newTotal;
    }
}
```

#### 订单状态值对象（`OrderStatus`）

```java
// domain/order/valueobject/OrderStatus.java
package com.bone.blueprint.domain.order.valueobject;

public enum OrderStatus {
    CREATED("新建"),
    PAID("已支付"),
    SHIPPED("已发货"),
    DELIVERED("已送达"),
    CANCELLED("已取消");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
```

#### 领域事件定义

##### 订单创建事件（`OrderCreatedEvent`）

```java
// domain/order/event/OrderCreatedEvent.java
package com.bone.blueprint.domain.order.event;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.domain.DomainEvent;

public class OrderCreatedEvent implements DomainEvent {
    private final Order order;
    
    public OrderCreatedEvent(Order order) {
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public Long getOrderId() {
        return order.getId();
    }
}
```

##### 订单支付事件（`OrderPaidEvent`）

```java
// domain/order/event/OrderPaidEvent.java
package com.bone.blueprint.domain.order.event;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.domain.DomainEvent;

public class OrderPaidEvent implements DomainEvent {
    private final Order order;
    
    public OrderPaidEvent(Order order) {
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public Long getOrderId() {
        return order.getId();
    }
}
```

##### 订单取消事件（`OrderCancelledEvent`）

```java
// domain/order/event/OrderCancelledEvent.java
package com.bone.blueprint.domain.order.event;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.domain.DomainEvent;

public class OrderCancelledEvent implements DomainEvent {
    private final Order order;
    
    public OrderCancelledEvent(Order order) {
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public Long getOrderId() {
        return order.getId();
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

---

## 4️⃣ 双模式 UseCase 策略（核心升级）

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
| **查询订单详情** | 🟢 **Mode A** | 纯读操作（但封装为 UseCase） |

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

#### 订单取消 UseCase

```java
// application/usecase/simple/CancelOrderUseCase.java
package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CancelOrder",
    description = "取消订单",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase implements UseCaseExecutor<CancelOrderCommand, Boolean> {

    private final CancelOrderCommandHandler cancelOrderCommandHandler;

    @Override
    public Boolean execute(CancelOrderCommand command) {
        return cancelOrderCommandHandler.handle(command);
    }
}
```

#### 订单列表查询 UseCase

```java
// application/usecase/simple/GetOrderListUseCase.java
package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "GetOrderList",
    description = "获取订单列表",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class GetOrderListUseCase implements UseCaseExecutor<OrderPageQuery, PageResult<OrderDto>> {

    private final OrderPageQueryHandler orderPageQueryHandler;

    @Override
    public PageResult<OrderDto> execute(OrderPageQuery query) {
        return orderPageQueryHandler.handle(query);
    }
}
```

#### 查询对象

```java
// application/query/qry/OrderPageQuery.java
package com.bone.blueprint.application.query.qry;

public class OrderPageQuery {
    private Long customerId;
    private String status;
    private Integer pageSize = 10;
    private Integer pageNum = 1;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }
}
```

#### 查询结果 DTO

```java
// application/query/dto/OrderDto.java
package com.bone.blueprint.application.query.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createTime;
    private List<OrderItemDto> items;

    private OrderDto() {
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long customerId;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createTime;
        private List<OrderItemDto> items;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder createTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder items(List<OrderItemDto> items) {
            this.items = items;
            return this;
        }

        public OrderDto build() {
            OrderDto dto = new OrderDto();
            dto.id = this.id;
            dto.customerId = this.customerId;
            dto.totalAmount = this.totalAmount;
            dto.status = this.status;
            dto.createTime = this.createTime;
            dto.items = this.items;
            return dto;
        }
    }

    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;

        private OrderItemDto() {
        }

        public Long getId() {
            return id;
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public BigDecimal getSubtotal() {
            return subtotal;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private Long productId;
            private String productName;
            private Integer quantity;
            private BigDecimal unitPrice;
            private BigDecimal subtotal;

            public Builder id(Long id) {
                this.id = id;
                return this;
            }

            public Builder productId(Long productId) {
                this.productId = productId;
                return this;
            }

            public Builder productName(String productName) {
                this.productName = productName;
                return this;
            }

            public Builder quantity(Integer quantity) {
                this.quantity = quantity;
                return this;
            }

            public Builder unitPrice(BigDecimal unitPrice) {
                this.unitPrice = unitPrice;
                return this;
            }

            public Builder subtotal(BigDecimal subtotal) {
                this.subtotal = subtotal;
                return this;
            }

            public OrderItemDto build() {
                OrderItemDto dto = new OrderItemDto();
                dto.id = this.id;
                dto.productId = this.productId;
                dto.productName = this.productName;
                dto.quantity = this.quantity;
                dto.unitPrice = this.unitPrice;
                dto.subtotal = this.subtotal;
                return dto;
            }
        }
    }
}
```

#### 查询处理器

```java
// application/query/handler/OrderPageQueryHandler.java
package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {
    
    private final OrderRepository orderRepository;
    
    @Transactional(readOnly = true)
    public PageResult<OrderDto> handle(OrderPageQuery query) {
        // 这里应该使用查询构建器或SQL执行查询
        // 简化实现，实际应该使用分页查询
        List<Order> orders = getOrdersByQuery(query);
        
        List<OrderDto> orderDtos = orders.stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
        
        // 简化实现，返回空的PageResult
        return PageResult.of(orderDtos, (long) orderDtos.size(), query.getPageNum(), query.getPageSize());
    }
    
    private List<Order> getOrdersByQuery(OrderPageQuery query) {
        // 模拟实现，实际应该从数据库查询
        return List.of();
    }
    
    private OrderDto toOrderDto(Order order) {
        List<OrderDto.OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toOrderItemDto)
                .collect(Collectors.toList());
        
        return OrderDto.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .items(itemDtos)
                .build();
    }
    
    private OrderDto.OrderItemDto toOrderItemDto(OrderItem item) {
        return OrderDto.OrderItemDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }
}
```

### 4.8 Mode B：UseCase + Handler（企业模式）

#### 命令对象

```java
// application/command/cmd/CreateOrderCommand.java
package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderCommand {
    private Long customerId;
    private List<OrderItemDto> items;

    private CreateOrderCommand() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long customerId;
        private List<OrderItemDto> items;

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder items(List<OrderItemDto> items) {
            this.items = items;
            return this;
        }

        public CreateOrderCommand build() {
            CreateOrderCommand command = new CreateOrderCommand();
            command.customerId = this.customerId;
            command.items = this.items;
            return command;
        }
    }

    public static class OrderItemDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;

        private OrderItemDto() {
        }

        public Long getProductId() {
            return productId;
        }

        public String getProductName() {
            return productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long productId;
            private String productName;
            private Integer quantity;
            private BigDecimal unitPrice;

            public Builder productId(Long productId) {
                this.productId = productId;
                return this;
            }

            public Builder productName(String productName) {
                this.productName = productName;
                return this;
            }

            public Builder quantity(Integer quantity) {
                this.quantity = quantity;
                return this;
            }

            public Builder unitPrice(BigDecimal unitPrice) {
                this.unitPrice = unitPrice;
                return this;
            }

            public OrderItemDto build() {
                OrderItemDto dto = new OrderItemDto();
                dto.productId = this.productId;
                dto.productName = this.productName;
                dto.quantity = this.quantity;
                dto.unitPrice = this.unitPrice;
                return dto;
            }
        }
    }
}
```

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

// application/command/handler/CancelOrderCommandHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CancelOrderCommandHandler {

    private final OrderRepository orderRepository;

    @Transactional
    public Boolean handle(CancelOrderCommand command) {
        Order order = orderRepository.findById(command.getOrderId());
        if (order == null) {
            throw new DomainException("订单不存在");
        }
        order.cancel();
        orderRepository.save(order);
        return true;
    }
}
```

#### UseCase（编排层）

```java
// application/usecase/standard/CreateOrderUseCase.java
package com.bone.blueprint.application.usecase.standard;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.dto.OrderItemDto;
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
    private final OrderPriceCalculator priceCalculator;

    @Override
    public Long execute(CreateOrderCommand cmd) {
        long orderId = DistributedIdGenerator.generateLongId();
        
        List<OrderItem> items = cmd.getItems().stream()
            .map(dto -> OrderItem.create(
                DistributedIdGenerator.generateLongId(),
                orderId,
                dto.getProductId(),
                dto.getProductName(),
                dto.getQuantity(),
                dto.getUnitPrice()))
            .collect(Collectors.toList());
        
        checkStockHandler.handle(items);
        Order order = createOrderHandler.handle(orderId, cmd.getCustomerId(), items);
        
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

---

## 5️⃣ AI 可见性规范（@Capability 完整元数据）

### 5.1 能力注解定义

`@Capability` 注解定义在 **`bone-core`** 模块的 `com.bone.core.usecase` 包下，作为 AI 可见能力的元数据注解。

```java
// bone-core/src/main/java/com/bone/core/usecase/Capability.java
package com.bone.core.usecase;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Capability {
    
    String name();
    String description();
    String inputSchema();
    String outputSchema();
    boolean idempotent() default false;
    int cost() default 1;
    boolean retryable() default true;
    int timeout() default 30;
}
```

### 5.2 使用示例

```java
// application/command/handler/ReleaseStockHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.core.usecase.Capability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Capability(
    name = "ReleaseStock",
    description = "释放订单占用的库存，回滚已扣减的库存数量",
    inputSchema = """
        {
            "type": "object",
            "properties": {
                "orderId": {"type": "integer", "description": "订单ID"}
            },
            "required": ["orderId"]
        }
        """,
    outputSchema = """
        {
            "type": "object",
            "properties": {
                "success": {"type": "boolean", "description": "是否成功"},
                "releasedCount": {"type": "integer", "description": "释放的库存数量"}
            }
        }
        """,
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 10
)
@Component
@RequiredArgsConstructor
public class ReleaseStockHandler {
    private final InventoryGateway inventoryGateway;
    
    public ReleaseStockResult handle(Long orderId) {
        int releasedCount = inventoryGateway.releaseStock(orderId);
        return ReleaseStockResult.success(releasedCount);
    }
}
```

### 5.3 Handler Registry（能力注册表）

```java
// bone-core/usecase/HandlerRegistry.java
package com.bone.core.usecase;

import com.bone.core.usecase.Capability;
import lombok.Builder;
import lombok.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HandlerRegistry {
    
    private final Map<String, CapabilityRegistration> capabilityIndex = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    
    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(Capability.class);
        handlers.forEach((beanName, handler) -> {
            Capability annotation = handler.getClass().getAnnotation(Capability.class);
            if (annotation != null) {
                CapabilityRegistration registration = CapabilityRegistration.builder()
                    .name(annotation.name())
                    .description(annotation.description())
                    .inputSchema(annotation.inputSchema())
                    .outputSchema(annotation.outputSchema())
                    .idempotent(annotation.idempotent())
                    .cost(annotation.cost())
                    .retryable(annotation.retryable())
                    .timeout(annotation.timeout())
                    .handlerInstance(handler)
                    .build();
                capabilityIndex.put(annotation.name(), registration);
            }
        });
    }
    
    public CapabilityRegistration getCapability(String name) {
        CapabilityRegistration registration = capabilityIndex.get(name);
        if (registration == null) {
            throw new CapabilityNotFoundException("Capability not found: " + name);
        }
        return registration;
    }
    
    public List<CapabilityRegistration> getAllCapabilities() {
        return new ArrayList<>(capabilityIndex.values());
    }
    
    @Value
    @Builder
    public static class CapabilityRegistration {
        String name;
        String description;
        String inputSchema;
        String outputSchema;
        boolean idempotent;
        int cost;
        boolean retryable;
        int timeout;
        Object handlerInstance;
    }
    
    public static class CapabilityNotFoundException extends RuntimeException {
        public CapabilityNotFoundException(String message) {
            super(message);
        }
    }
}
```

### 5.4 能力查询 API

```java
// bone-web/controller/CapabilityController.java
package com.bone.web.controller;

import com.bone.core.usecase.HandlerRegistry;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "能力管理", description = "AI/Flow 能力发现接口")
@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityController {
    
    private final HandlerRegistry handlerRegistry;
    
    @Operation(summary = "获取所有能力", description = "返回所有可被 AI/Flow 调用的能力清单")
    @GetMapping
    public ApiResponse<List<HandlerRegistry.CapabilityRegistration>> getAllCapabilities() {
        return ApiResponse.success(handlerRegistry.getAllCapabilities());
    }
    
    @Operation(summary = "获取指定能力", description = "根据能力名称获取详细元数据")
    @GetMapping("/{name}")
    public ApiResponse<HandlerRegistry.CapabilityRegistration> getCapability(@PathVariable String name) {
        return ApiResponse.success(handlerRegistry.getCapability(name));
    }
}
```

### 5.5 基础设施异常类

```java
// bone-core/exception/InfrastructureException.java
package com.bone.core.exception;

public class InfrastructureException extends RuntimeException {
    
    public InfrastructureException(String message) {
        super(message);
    }
    
    public InfrastructureException(String message, Throwable cause) {
        super(message, cause);
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
}
```

### 6.3 扩展实现

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

// infrastructure/extension/order/VipOrderPriceCalculator.java
package com.bone.blueprint.infrastructure.extension.order;

import com.bone.extension.sdk.annotation.Extension;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import java.math.BigDecimal;

@Extension(
    name = "VIP订单价格计算",
    tenant = "*",
    bizCode = "ecommerce",
    useCase = "order",
    scenario = "vip",
    priority = 50
)
public class VipOrderPriceCalculator implements OrderPriceCalculator {
    @Override
    public BigDecimal calculate(OrderPriceRequest request) {
        BigDecimal total = request.getBaseAmount().add(request.getShippingFee());
        // VIP 9折优惠
        return total.multiply(BigDecimal.valueOf(0.9));
    }
}
```

### 6.4 业务上下文全入口覆盖

所有入站适配器（Web、RPC、MQ、Schedule）必须绑定 `BizContext`：

```java
BizContext.builder()
    .tenant(request.getHeader("X-Tenant-Id"))
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
```

### 6.5 扩展点决策记录器

```java
// bone-extension-sdk/infrastructure/ExtensionDecisionRecorder.java
package com.bone.engine.extension.infrastructure;

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
            .candidates(candidates.stream().map(c -> c.getExtensionClass().getSimpleName()).collect(Collectors.toList()))
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

---

## 7️⃣ 适配器层实现

适配器层是系统与外部世界交互的入口，包括 Web、RPC、MQ 和定时任务等不同类型的入站适配器。

### 7.1 Web 适配器

#### 订单控制器（`OrderController`）

```java
// adapter/web/controller/OrderController.java
package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.usecase.simple.CancelOrderUseCase;
import com.bone.blueprint.application.usecase.standard.CreateOrderUseCase;
import com.bone.blueprint.application.usecase.standard.PayOrderUseCase;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final PayOrderUseCase payOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    private final OrderAssembler orderAssembler;

    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<Long> create(@Parameter(description = "订单创建请求") @Valid @RequestBody CreateOrderRequest request) {
        try {
            log.info("收到创建订单请求: customerId={}, itemsCount={}", 
                    request.getCustomerId(), request.getItems().size());
            
            CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
            Long orderId = createOrderUseCase.execute(command);
            
            log.info("创建订单成功: orderId={}", orderId);
            return ApiResponse.success(orderId);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            throw e;
        }
    }

    @Operation(summary = "支付订单", description = "支付指定的订单")
    @PostMapping("/{id}/pay")
    public ApiResponse<Void> pay(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到支付订单请求: orderId={}", id);
            
            PayOrderCommand command = orderAssembler.toPayOrderCommand(id);
            payOrderUseCase.execute(command);
            
            log.info("支付订单成功: orderId={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("支付订单失败: orderId={}", id, e);
            throw e;
        }
    }

    @Operation(summary = "取消订单", description = "取消指定的订单")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到取消订单请求: orderId={}", id);
            
            CancelOrderCommand command = orderAssembler.toCancelOrderCommand(id);
            cancelOrderUseCase.execute(command);
            
            log.info("取消订单成功: orderId={}", id);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("取消订单失败: orderId={}", id, e);
            throw e;
        }
    }

    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@Parameter(description = "订单ID") @PathVariable Long id) {
        try {
            log.info("收到查询订单详情请求: orderId={}", id);
            
            OrderDetailQuery query = orderAssembler.toOrderDetailQuery(id);
            var orderDto = orderDetailQueryHandler.handle(query);
            var response = orderAssembler.toOrderDetailResponse(orderDto);
            
            log.info("查询订单详情成功: orderId={}", id);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("查询订单详情失败: orderId={}", id, e);
            throw e;
        }
    }
}
```

#### 订单装配器（`OrderAssembler`）

```java
// adapter/web/assembler/OrderAssembler.java
package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderAssembler {
    
    CreateOrderCommand toCreateOrderCommand(CreateOrderRequest request);
    
    default PayOrderCommand toPayOrderCommand(Long orderId) {
        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(orderId);
        return command;
    }
    
    default CancelOrderCommand toCancelOrderCommand(Long orderId) {
        CancelOrderCommand command = new CancelOrderCommand();
        command.setOrderId(orderId);
        return command;
    }
    
    default OrderDetailQuery toOrderDetailQuery(Long orderId) {
        OrderDetailQuery query = new OrderDetailQuery();
        query.setOrderId(orderId);
        return query;
    }
    
    OrderDetailResponse toOrderDetailResponse(OrderDto orderDto);
}
```

#### Web 请求 DTO（`CreateOrderRequest`）

```java
// adapter/web/dto/request/CreateOrderRequest.java
package com.bone.blueprint.adapter.web.dto.request;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull(message = "客户ID不能为空")
    private Long customerId;
    
    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        @NotNull(message = "商品ID不能为空")
        private Long productId;
        
        @NotNull(message = "商品名称不能为空")
        private String productName;
        
        @NotNull(message = "数量不能为空")
        @Positive(message = "数量必须大于0")
        private Integer quantity;
        
        @NotNull(message = "单价不能为空")
        @Positive(message = "单价必须大于0")
        private BigDecimal unitPrice;
    }
}
```

#### Web 响应 DTO（`OrderDetailResponse`）

```java
// adapter/web/dto/response/OrderDetailResponse.java
package com.bone.blueprint.adapter.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {
    private Long id;
    private Long customerId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createTime;
    private List<OrderItemResponse> items;
    
    @Data
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
```

### 7.2 RPC 适配器

#### 订单 RPC 服务（`OrderRpcService`）

```java
// adapter/rpc/OrderRpcService.java
package com.bone.blueprint.adapter.rpc;

import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcRequest;
import com.bone.blueprint.adapter.rpc.dto.CreateOrderRpcResponse;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.usecase.standard.CreateOrderUseCase;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "订单RPC服务", description = "提供订单相关的RPC接口，供其他服务调用")
@RestController
@RequestMapping("/api/rpc/orders")
@RequiredArgsConstructor
public class OrderRpcService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderRpcService.class);
    
    private final CreateOrderUseCase createOrderUseCase;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    
    @Operation(summary = "创建订单", description = "创建新的订单")
    @PostMapping
    public ApiResponse<CreateOrderRpcResponse> createOrder(@Parameter(description = "订单创建请求") @RequestBody CreateOrderRpcRequest request) {
        try {
            log.info("收到创建订单RPC请求: customerId={}, itemsCount={}", 
                    request.getCustomerId(), request.getItems().size());
            
            List<CreateOrderCommand.OrderItemDto> items = request.getItems().stream()
                    .map(item -> CreateOrderCommand.OrderItemDto.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .build())
                    .collect(Collectors.toList());
            
            CreateOrderCommand command = CreateOrderCommand.builder()
                    .customerId(request.getCustomerId())
                    .items(items)
                    .build();
            
            Long orderId = createOrderUseCase.execute(command);
            
            CreateOrderRpcResponse response = new CreateOrderRpcResponse();
            response.setOrderId(orderId);
            response.setSuccess(true);
            response.setStatus("SUCCESS");
            
            log.info("创建订单成功: orderId={}", orderId);
            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            CreateOrderRpcResponse response = new CreateOrderRpcResponse();
            response.setSuccess(false);
            response.setErrorMsg(e.getMessage());
            response.setStatus("FAILED");
            return ApiResponse.success(response);
        }
    }
    
    @Operation(summary = "根据ID查询订单", description = "根据订单ID查询订单详情")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto> getOrderById(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        try {
            log.info("收到查询订单RPC请求: orderId={}", orderId);
            
            OrderDetailQuery query = new OrderDetailQuery();
            query.setOrderId(orderId);
            OrderDto orderDto = orderDetailQueryHandler.handle(query);
            
            if (orderDto != null) {
                log.info("查询订单成功: orderId={}", orderId);
            } else {
                log.warn("订单不存在: orderId={}", orderId);
            }
            
            return ApiResponse.success(orderDto);
        } catch (Exception e) {
            log.error("查询订单失败: orderId={}", orderId, e);
            return ApiResponse.success(null);
        }
    }
}
```

#### RPC 请求 DTO（`CreateOrderRpcRequest`）

```java
// adapter/rpc/dto/CreateOrderRpcRequest.java
package com.bone.blueprint.adapter.rpc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRpcRequest {
    private String tenantId;
    private Long customerId;
    private List<OrderItemRpcRequest> items;
    
    @Data
    public static class OrderItemRpcRequest {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
```

#### RPC 响应 DTO（`CreateOrderRpcResponse`）

```java
// adapter/rpc/dto/CreateOrderRpcResponse.java
package com.bone.blueprint.adapter.rpc.dto;

import lombok.Data;

@Data
public class CreateOrderRpcResponse {
    private Long orderId;
    private String status;
    private boolean success;
    private String errorMsg;
}
```

### 7.3 MQ 适配器

#### 订单支付监听器（`OrderPaidListener`）

```java
// adapter/mq/listener/OrderPaidListener.java
package com.bone.blueprint.adapter.mq.listener;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.application.command.handler.PayOrderCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidListener {
    
    private final PayOrderCommandHandler payOrderCommandHandler;
    
    public void onOrderPaid(String message) {
        try {
            log.info("收到订单支付消息: {}", message);
            
            Long orderId = Long.parseLong(message);
            
            log.info("处理订单支付: orderId={}", orderId);
            
            PayOrderCommand command = new PayOrderCommand();
            command.setOrderId(orderId);
            
            payOrderCommandHandler.handle(command);
            
            log.info("订单支付处理成功: orderId={}", orderId);
        } catch (NumberFormatException e) {
            log.error("消息格式错误，无法解析订单ID: {}", message, e);
        } catch (Exception e) {
            log.error("处理订单支付消息失败: {}", message, e);
        }
    }
}
```

### 7.4 定时任务适配器

#### 取消过期订单任务（`CancelExpiredOrderJob`）

```java
// adapter/schedule/CancelExpiredOrderJob.java
package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelExpiredOrderJob {
    
    private final CancelOrderCommandHandler cancelOrderCommandHandler;
    
    @Scheduled(cron = "0 0/30 * * * ?")
    public void cancelExpiredOrders() {
        try {
            log.info("开始执行取消过期订单任务");
            
            List<Long> expiredOrderIds = getExpiredOrderIds();
            
            log.info("发现过期订单: {} 个", expiredOrderIds.size());
            
            for (Long orderId : expiredOrderIds) {
                try {
                    log.info("取消过期订单: orderId={}", orderId);
                    
                    CancelOrderCommand command = new CancelOrderCommand();
                    command.setOrderId(orderId);
                    cancelOrderCommandHandler.handle(command);
                    
                    log.info("取消过期订单成功: orderId={}", orderId);
                } catch (Exception e) {
                    log.error("取消过期订单失败: orderId={}", orderId, e);
                }
            }
            
            log.info("取消过期订单任务执行完成");
        } catch (Exception e) {
            log.error("执行取消过期订单任务失败", e);
        }
    }
    
    private List<Long> getExpiredOrderIds() {
        return List.of();
    }
}
```

## 8️⃣ 标准工程结构（完整版）

```text
src/main/java/com/bone/
├── blueprint/                                 # 业务模块
│   ├── BoneBlueprintApplication.java
│   │
│   ├── adapter/                               # 入站适配器层
│   │   ├── web/
│   │   │   ├── controller/                    # REST 控制器
│   │   │   │   ├── OrderController.java       # 订单管理接口
│   │   │   │   └── CapabilityController.java  # 能力管理接口
│   │   │   ├── dto/                           # 数据传输对象
│   │   │   │   ├── request/CreateOrderRequest.java
│   │   │   │   └── response/OrderDetailResponse.java
│   │   │   ├── assembler/OrderAssembler.java  # DTO 转换
│   │   │   └── interceptor/TenantInterceptor.java
│   │   ├── rpc/                               # 远程服务接口
│   │   │   ├── dto/
│   │   │   │   ├── CreateOrderRpcRequest.java
│   │   │   │   └── CreateOrderRpcResponse.java
│   │   │   └── OrderRpcService.java
│   │   ├── mq/listener/OrderPaidListener.java # 消息队列监听器
│   │   └── schedule/CancelExpiredOrderJob.java # 定时任务
│   │
│   ├── application/                           # 应用层
│   │   ├── usecase/                           # 用例层
│   │   │   ├── simple/                        # 🟢 Mode A（简单模式）
│   │   │   │   ├── CancelOrderUseCase.java
│   │   │   │   └── GetOrderListUseCase.java
│   │   │   └── standard/                      # 🔵 Mode B（企业模式）
│   │   │       ├── CreateOrderUseCase.java
│   │   │       └── PayOrderUseCase.java
│   │   ├── command/                           # 命令处理器
│   │   │   ├── cmd/                           # 命令对象
│   │   │   │   ├── CancelOrderCommand.java
│   │   │   │   ├── CreateOrderCommand.java
│   │   │   │   └── PayOrderCommand.java
│   │   │   ├── dto/                           # 命令 DTO
│   │   │   │   └── OrderItemDto.java
│   │   │   └── handler/                       # 命令处理器
│   │   │       ├── CancelOrderCommandHandler.java
│   │   │       ├── CheckStockHandler.java
│   │   │       ├── CreateOrderCommandHandler.java
│   │   │       ├── CreateOrderHandler.java
│   │   │       └── PayOrderCommandHandler.java
│   │   ├── query/                             # 查询处理器
│   │   │   ├── dto/                           # 查询结果 DTO
│   │   │   │   └── OrderDto.java
│   │   │   ├── handler/                       # 查询处理器
│   │   │   │   ├── OrderDetailQueryHandler.java
│   │   │   │   └── OrderPageQueryHandler.java
│   │   │   ├── projection/                    # 投影对象
│   │   │   │   └── OrderWithItemsProjection.java
│   │   │   └── qry/                           # 查询对象
│   │   │       ├── OrderDetailQuery.java
│   │   │       └── OrderPageQuery.java
│   │   ├── event/OrderPaidEventHandler.java   # 事件处理器
│   │   └── assembler/OrderAssembler.java      # 应用层装配器
│   │
│   ├── domain/                                # 领域层
│   │   ├── order/                             # 订单聚合
│   │   │   ├── Order.java                     # 聚合根
│   │   │   ├── OrderItem.java                 # 实体
│   │   │   ├── valueobject/OrderStatus.java   # 值对象
│   │   │   └── event/                         # 领域事件
│   │   │       ├── OrderCancelledEvent.java
│   │   │       ├── OrderCreatedEvent.java
│   │   │       └── OrderPaidEvent.java
│   │   ├── repository/OrderRepository.java    # 仓储接口
│   │   ├── gateway/                           # 防腐层接口
│   │   │   └── InventoryGateway.java
│   │   ├── extension/order/                   # 扩展点
│   │   │   ├── OrderPriceCalculator.java
│   │   │   └── OrderPriceRequest.java
│   │   ├── exception/OrderDomainException.java # 领域异常
│   │   └── security/                          # 安全相关
│   │       ├── PasswordEncoder.java
│   │       └── TokenProvider.java
│   │
│   └── infrastructure/                        # 基础设施层
│       ├── gateway/                           # 网关实现
│       │   └── feign/InventoryFeignGatewayImpl.java
│       ├── extension/                         # 扩展实现
│       │   └── order/
│       │       ├── DefaultOrderPriceCalculator.java
│       │       ├── EnterpriseOrderPriceCalculator.java
│       │       ├── MemberOrderPriceCalculator.java
│       │       ├── PromotionOrderPriceCalculator.java
│       │       └── VipOrderPriceCalculator.java
│       ├── config/                            # 配置
│       │   ├── extension/ExtensionConfiguration.java
│       │   ├── metadata/BoneMetadataConfiguration.java
│       │   ├── security/SecurityConfiguration.java
│       │   └── web/WebMvcConfiguration.java
│       ├── security/                          # 安全实现
│       │   ├── JwtTokenProvider.java
│       │   └── PasswordEncoderImpl.java
│       └── exception/InfrastructureException.java # 基础设施异常
│
└── core/                                      # bone-core 模块
    └── src/main/java/com/bone/core/
        ├── usecase/                           # UseCase 相关
        │   ├── Capability.java                # AI 能力注解
        │   ├── UseCase.java                   # UseCase 元数据注解
        │   ├── UseCaseExecutor.java           # UseCase 执行接口
        │   └── HandlerRegistry.java           # 能力注册表
        ├── domain/                            # 领域基础
        │   ├── entity/                        # 实体基类
        │   │   ├── AbstractEntity.java
        │   │   └── TenantAbstractEntity.java
        │   ├── AggregateRoot.java             # 聚合根基类
        │   └── DomainEvent.java               # 领域事件基类
        ├── exception/                         # 异常体系
        │   ├── DomainException.java
        │   ├── InfrastructureException.java
        │   ├── NotFoundException.java
        │   └── SystemException.java
        ├── model/                             # 通用模型
        │   ├── ApiResponse.java
        │   └── PageResult.java
        ├── tenant/                            # 租户相关
        │   └── context/TenantContext.java
        └── util/                              # 工具类
            └── DistributedIdGenerator.java
```

---

## 8️⃣ 命名体系（完整版）

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| UseCase 接口 | `UseCaseExecutor<C, R>` | 框架提供 |
| UseCase 实现（Mode A） | `{Action}{Domain}UseCase` | `GetOrderListUseCase` |
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

## 9️⃣ ArchUnit 约束（完整版）

```java
@AnalyzeClasses(packages = "com.bone.blueprint")
public class ArchitectureTest {

    // 铁律 1：依赖方向
    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
            .check(classes);
    }

    // 铁律 2：Controller 统一入口
    @ArchTest
    static void controllerShouldOnlyDependOnUseCase(JavaClasses classes) {
        classes().that().resideInAPackage("..adapter.web.controller..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..adapter..",
                "..application.usecase..",
                "..common..",
                "com.bone.core..",
                "lombok..",
                "java..",
                "org.springframework..",
                "org.slf4j.."
            )
            .check(classes);
    }

    // 铁律 3：Domain 纯净度
    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "..domain..",
                "java..",
                "com.bone.core..",
                "lombok..",
                "org.springframework.lang.."
            )
            .check(classes);
    }

    // 铁律 4：业务逻辑在 Domain
    @ArchTest
    static void businessLogicShouldBeInDomain(JavaClasses classes) {
        classes().that().resideInAPackage("..application..")
            .should().notDependOnClassesThat()
            .resideInAPackage("..domain..")
            .as("Application layer should not contain business logic, only orchestration")
            .check(classes);
    }

    // 铁律 5：外部系统通过 ACL
    @ArchTest
    static void externalSystemAccessShouldBeThroughGateway(JavaClasses classes) {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "feign..",
                "httpclient..",
                "rocketmq..",
                "kafka.."
            )
            .check(classes);
    }

    // UseCase 相关约束
    @ArchTest
    static void useCaseShouldImplementUseCaseExecutor(JavaClasses classes) {
        classes().that().haveSimpleNameEndingWith("UseCase")
            .should().implement(UseCaseExecutor.class)
            .check(classes);
    }

    @ArchTest
    static void useCaseShouldBeAnnotatedWithUseCase(JavaClasses classes) {
        classes().that().haveSimpleNameEndingWith("UseCase")
            .should().beAnnotatedWith(UseCase.class)
            .check(classes);
    }

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

    // Repository 约束
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

    // 扩展点约束
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

    // AI 可见性约束
    @ArchTest
    static void capabilityAnnotatedClassesShouldBeHandlers(JavaClasses classes) {
        classes().that().areAnnotatedWith(Capability.class)
            .should().haveSimpleNameEndingWith("Handler")
            .check(classes);
    }
}
```

---

## 🔟 事务模型

| 位置 | 规则 | 理由 |
|------|------|------|
| `application.command.handler` | **必须** `@Transactional` | 一个命令一个事务，事务边界在这里 |
| `application.query.handler` | `@Transactional(readOnly = true)` | 优化数据库连接，提升性能 |
| `application.usecase` | **可选** `@Transactional` | 复杂编排场景可在 UseCase 层控制事务 |
| `domain` / `infrastructure` | **严禁**事务注解 | 领域模型不感知事务，事务由应用层控制 |

**最佳实践：**
- 简单场景：在 Handler 层加 `@Transactional`
- 复杂场景：在 UseCase 层加 `@Transactional`，统一控制事务边界
- 聚合根修改完成后保存，整个操作在一个事务内原子性完成

---

## 1️⃣1️⃣ 异常处理分层规范

| 异常类型 | 抛出层级 | 使用场景 | HTTP 状态 |
|----------|----------|----------|-----------|
| `DomainException` | **domain** | **所有**业务规则冲突（值对象校验、业务规则违反） | 400 |
| `NotFoundException` | `application` / `adapter` | 请求的资源不存在 | 404 |
| `InfrastructureException` | `infrastructure` | 数据库连接失败、外部服务不可用、IO错误 | 500 |
| `SystemException` | `infrastructure` | 系统级错误 | 500 |
| `CapabilityNotFoundException` | `core.usecase` | AI 能力不存在 | 404 |

> **设计原则**：所有业务规则检查都应该在领域层完成，异常也应该在领域层抛出。application 只做编排，不包含业务规则。

全局异常处理器统一转换为 `ApiResponse<T>` 格式。

---

## 1️⃣2️⃣ 强制红线（CI 阻断级）

| ❌ 绝对禁止 | ✅ 正确做法 |
|------------|------------|
| 项目内定义 `AggregateRoot`、`DomainEvent` | 统一使用 `bone-core` |
| 手动编写 `RepositoryImpl` 或 `PO` | 接口继承 `Repository`，SDK 动态代理 |
| 创建 `persistence` 目录及其子包 | 完全信任 SDK 元数据映射 |
| `domain` 对象使用 `@Data` 或 `@Setter` | 仅允许 `@Getter` 和私有构造器注解 |
| `domain` 对象加持久化注解 | 领域对象保持纯净 |
| 查询操作调用 `domain` 仓储 | 读模型使用 QueryBuilder 或独立 Mapper |
| Controller 直接依赖 Handler/QueryHandler | Controller 只依赖 UseCase |
| UseCase 内 Handler 数量 > 5 | 合并 Handler 或引入 Flow 编排 |
| 暴露给 AI 的能力未加 `@Capability` | 所有 AI 能力必须添加完整元数据 |
| 多租户/多场景使用 if-else | 使用扩展点实现，提供决策可解释层 |

---

## 1️⃣3️⃣ 测试体系

| 层级 | 目标 | 工具 | 覆盖率要求 |
|------|------|------|------------|
| Domain 单元测试 | 规则验证 | JUnit5 + AssertJ | ≥90% |
| Application 集成测试 | 用例验证 | `@SpringBootTest` + TestContainers | 核心用例 100% |
| Architecture 测试 | 依赖规则 | ArchUnit | CI 必须通过 |
| Handler 单元测试 | 原子能力验证 | Mockito + JUnit5 | ≥80% |
| Extension 测试 | 扩展点决策验证 | TestContainers + JUnit5 | 100% 覆盖默认实现 |

### 领域层单元测试示例

```java
// domain/order/OrderTest.java
package com.bone.blueprint.domain.order;

import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void order_should_reject_empty_items() {
        assertThatThrownBy(() -> Order.create(1L, 1001L, Collections.emptyList()))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("订单至少需要一个商品项");
    }

    @Test
    void order_should_calculate_total_amount_correctly() {
        OrderItem item1 = OrderItem.create(1L, 1L, 101L, "商品1", 2, BigDecimal.valueOf(100));
        OrderItem item2 = OrderItem.create(2L, 1L, 102L, "商品2", 1, BigDecimal.valueOf(200));
        Order order = Order.create(1L, 1001L, List.of(item1, item2));
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(400));
    }

    @Test
    void order_should_allow_pay_only_when_created() {
        OrderItem item = OrderItem.create(1L, 1L, 101L, "商品1", 1, BigDecimal.valueOf(100));
        Order order = Order.create(1L, 1001L, List.of(item));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        
        order.pay();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        
        assertThatThrownBy(order::pay)
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("只有新建状态的订单可以支付");
    }
}
```

### 应用层 Mock 测试示例

```java
// application/command/handler/CreateOrderCommandHandlerTest.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.dto.OrderItemDto;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.util.DistributedIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CheckStockHandler checkStockHandler;

    @InjectMocks
    private CreateOrderCommandHandler createOrderCommandHandler;

    @Test
    void should_create_order_successfully() {
        // 模拟数据
        CreateOrderCommand command = CreateOrderCommand.builder()
            .customerId(1001L)
            .items(List.of(
                OrderItemDto.builder()
                    .productId(101L)
                    .productName("商品1")
                    .quantity(2)
                    .unitPrice(BigDecimal.valueOf(100))
                    .build()
            ))
            .build();

        // 执行
        Long orderId = createOrderCommandHandler.handle(command);

        // 验证
        assertThat(orderId).isNotNull();
        verify(checkStockHandler).handle(anyList());
        verify(orderRepository).save(any(Order.class));
    }
}
```

---

## 1️⃣4️⃣ 演进路线图

| 阶段 | 触发条件 | 演进动作 |
|------|----------|----------|
| **v1 单体** | 项目启动 | 四层架构 + SDK 动态代理，零持久化代码 |
| **v2 模块化** | 多限界上下文 | 按 `modules/{context}` 分包，SDK 分别扫描 |
| **v3 事件化** | 最终一致性需求 | Outbox + MQ + Projection |
| **v4 分布式** | 微服务拆分 | 独立部署，事件网格 |
| **v5 AI-Native** | 智能编排需求 | Flow Studio + AI 能力目录 + 扩展点生态 |

---

## 🧠 最终架构本质

> **Domain 定义规则，bone-core 提供骨架，SDK 负责持久化，业务代码只写接口。**
> **UseCase 统一入口，Handler 原子化，@Capability 资产化，Flow 智能化。**

---

## 📋 团队落地 Checklist

### 项目初始化
- [ ] 引入 `bone-core`、`bone-web` 和 `bone-metadata-sdk` 依赖
- [ ] 启动类添加 `@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")`
- [ ] 创建四层包结构（无 `persistence` 目录）
- [ ] 配置 ArchUnit 规则（允许 Lombok @Getter，禁止 @Setter/@Data）
- [ ] 配置全局异常处理器和 `ApiResponse<T>`
- [ ] 配置扩展点扫描（如有需要）

### 每个领域模块开发
- [ ] 定义值对象替代原始类型
- [ ] 聚合根充血，继承 `bone-core` 的 `AggregateRoot`，使用 `@Getter` 和私有构造器
- [ ] 仓储接口定义在 `domain.repository`，继承 SDK `Repository`
- [ ] 防腐层接口定义在 `domain.gateway`
- [ ] 命令/查询对象与处理器按命名规范创建
- [ ] 读操作优先使用 SDK `QueryBuilder`
- [ ] 需要暴露给 AI 的能力添加 `@Capability` 注解
- [ ] 多租户/多场景使用扩展点实现
- [ ] 编写领域层单元测试
- [ ] 编写应用层集成测试

### Code Review 核心关注点
- [ ] 是否还有 `RepositoryImpl`、`PO`、`Converter` 等冗余类？
- [ ] `domain` 包是否无 Spring/MyBatis 注解？
- [ ] `domain` 是否误用了 `@Setter` 或 `@Data`？
- [ ] 读写是否分离？
- [ ] 事务是否在 `application.command.handler` 或 `application.usecase`？
- [ ] Controller 是否只依赖 UseCase？
- [ ] Handler 数量是否超过 5 个？
- [ ] AI 能力是否添加了 `@Capability` 元数据？
- [ ] 多租户/多场景是否使用了扩展点？

---

## 核心口诀

> **架构层面**：六边形定边界，整洁控依赖，CQRS 分读写，SDK 代持久，bone-core 垫基石。  
> **编码层面**：值对象自验证，聚合根充血，命令动词化，查询扁平化，事务在应用。  
> **命名层面**：Adapter 适协议，Handler 管用例，Repository 仅接口，SDK 自动成。  
> **AI 层面**：@Capability 资产化，Handler 原子化，Flow 编排化，决策可解释。

---

**本规范即日起作为团队 Bone-Blueprint