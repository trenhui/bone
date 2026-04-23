# 🏛️ Bone-Blueprint v16.3 工程规范（终局完整版·工业级可落地）

## 企业级 DDD + 分级 CQRS + 六边形架构 + Bone 元数据驱动框架

> **定位**：架构正确性与组织可落地性的终极平衡。用 4 条铁律守住架构不腐化，用统一查询规范（Criteria/QueryBuilder）替代 Repository 自定义方法，用扩展点替代 if-else，用脚手架消灭落地阻力，用演进路线图保证渐进成功。  
> **适用规模**：10–100 人团队 / 单体到分布式演进系统  
> **核心目标**：结构即规则、命名即语义、依赖即约束、复杂度即选择、扩展可治理。  
> **生效日期**：2026-04-23

---

## 0️⃣ 设计哲学

本规范融合了 **架构正确性** 与 **工程可持续性**，遵循以下核心原则：

| 原则 | 说明 |
|------|------|
| **最小必要约束** | 只有 4 条铁律 CI 阻断，其余均为推荐或可选 |
| **Repository 空接口** | 子接口仅继承基类，不添加任何自定义方法，避免语义膨胀 |
| **查询统一入口** | 所有自定义查询（存在性校验、业务键、复杂条件）统一使用 `Criteria` 或 `QueryBuilder` |
| **ID 生成上移** | 领域层不感知 ID 生成技术，ID 由应用层生成后传入聚合根 |
| **实体 @Table 显式绑定** | 所有实体类必须使用 `@Table` 注解显式指定数据库表名 |
| **聚合根包直接化** | 聚合根直接放在 `domain.{aggregate}` 下，同包放实体、值对象、事件 |
| **领域服务纯净** | 领域服务类无 Spring 注解，允许通过 `@Bean` 注册但禁止使用 Spring 特性 |
| **实体与值对象区分** | 有独立标识的为实体（如 `OrderItem`），无标识的为值对象（如 `Address`） |
| **扩展点替代 if-else** | 多租户/多场景业务通过扩展点实现，禁止硬编码分支，扩展点实现类命名以 `Calculator` 结尾 |
| **工具化落地** | 脚手架、ArchUnit 自动检查、复杂度分析 CLI |
| **渐进演进** | 三阶段路线图，不推倒重来，存量系统逐步对齐 |
| **开发者体验优先** | L1 默认路径极简，简单 CRUD ≤ 5 个类 |

**核心理念**：  
> 架构的确定性兜住业务的不确定性，但架构本身必须给团队留确定性。  
> 硬约束保底线（防腐化），软能力促生长（提效率）。  
> 简单场景走简单路，复杂场景给复杂方案，不要为了架构而架构。

---

## 1️⃣ 架构第一性原则（4 条铁律，CI 阻断）

### 🔴 铁律 1：依赖方向必须正确（唯一拓扑约束）

```
adapter → application → domain ← infrastructure
```

- ✔ 只能向内依赖
- ❌ 禁止任何反向 import（如 domain 引用 adapter 或 infrastructure）
- ✅ **ArchUnit 在 CI 中强制阻断**

### 🔴 铁律 2：Domain 必须绝对纯净

- ❌ 禁止 Spring 注解（`@Service`, `@Component`, `@Autowired`, `@Transactional` 等）
- ❌ 禁止 MyBatis / JPA / Jackson 等框架注解（`@Entity`, `@JsonIgnore` 等）—— **注意**：`@Table` 注解来自 `bone-metadata-sdk`，属于元数据注解，不违反纯净性，但其他框架的注解禁止。
- ❌ 禁止数据库连接、SQL、MQ、RPC
- ✅ 仅允许：纯 Java + `java.util` + `lombok`（`@Getter`, `@NoArgsConstructor(access = AccessLevel.PRIVATE)`）
- ✅ 值对象允许 `@Value` 或 `record`
- ✅ 允许使用 `bone-metadata-sdk` 的 `@Table`、`@Id`、`@GeneratedValue` 等元数据注解（这些属于框架定义，不引入具体数据库依赖）
- ✅ 领域服务类本身不得有任何 Spring 注解；允许在 `infrastructure.config` 中通过 `@Bean` 注册（见第 5 章）

> **为什么**：Domain 是业务真相来源，一旦沾染框架，技术债务将直接污染业务规则。

### 🔴 铁律 3：业务逻辑必须在 Domain（反贫血模型）

```java
// ❌ 贫血模型——业务逻辑泄漏到应用层
order.setStatus(OrderStatus.PAID);
if (order.getStatus() == OrderStatus.PAID) { ... }

// ✅ 充血模型——业务规则封装在聚合根
order.pay();  // 内部校验状态、计算、发布事件
```

- ❌ 禁止在 `application.command.handler` 中写 `if (order.getStatus() == X)` 等业务判断
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

### 2.4 命令处理器（扩展点调用在应用层）

```java
// application/command/handler/CreateOrderCommandHandler.java
package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.extension.order.OrderPriceRequest;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler {

    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;
    private final OrderPriceCalculator priceCalculator;   // 扩展点接口

    @Transactional
    public Long handle(CreateOrderCommand cmd) {
        // 1. 校验库存（ACL）
        for (CreateOrderCommand.OrderItemDto dto : cmd.getItems()) {
            if (!inventoryGateway.checkStock(dto.getProductId(), dto.getQuantity())) {
                throw new DomainException("商品库存不足: " + dto.getProductId());
            }
        }

        // 2. 生成订单ID
        long orderId = DistributedIdGenerator.generateLongId();

        // 3. 构建订单明细实体（每个明细生成独立ID）
        List<OrderItem> items = cmd.getItems().stream()
                .map(dto -> OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        orderId,
                        dto.getProductId(),
                        dto.getProductName(),
                        dto.getQuantity(),
                        dto.getUnitPrice()))
                .collect(Collectors.toList());

        // 4. 创建订单聚合根
        Order order = Order.create(orderId, cmd.getCustomerId(), items);

        // 5. 调用扩展点计算最终价格（替代 if-else）
        BigDecimal finalPrice = priceCalculator.calculate(
                OrderPriceRequest.builder()
                        .baseAmount(order.getTotalAmount())
                        .shippingFee(BigDecimal.ZERO)
                        .build()
        );
        order.updateTotalAmount(finalPrice);

        // 6. 保存订单
        orderRepository.save(order);
        return order.getId();
    }
}
```

---

## 3️⃣ Repository 规范（空接口 + 基类提供完整 CRUD）

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

### 3.3 使用 Criteria 进行存在性校验和业务键查询

```java
// 在 CommandHandler 或 ApplicationService 中
Criteria<Order> criteria = Criteria.<Order>builder()
    .eq(Order::getOrderNo, orderNo);
boolean exists = orderRepository.countByCriteria(criteria) > 0;

Optional<Order> order = Optional.ofNullable(
    orderRepository.findOneByCriteria(Criteria.<Order>builder().eq(Order::getOrderNo, orderNo).build())
);
```

### 3.4 使用 QueryBuilder 进行列表/分页查询

```java
// 在 ApplicationService 或 QueryHandler 中
List<OrderDto> list = QueryBuilder.from(Order.class)
    .where(Order::getCustomerId).eq(customerId)
    .orderByDesc(Order::getCreateTime)
    .list()
    .map(OrderDto::from);
```

### 3.5 ArchUnit 约束

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

## 4️⃣ 领域服务管理（纯 POJO + 可选的 Spring 托管）

### 4.1 原则

- 领域服务类本身**禁止**添加 Spring 注解（`@Service`, `@Component`）
- 允许在 `infrastructure.config` 中通过 `@Bean` 注册，以便注入依赖（如扩展点接口）
- **禁止**在领域服务中使用任何 Spring 特性（`@Transactional`, `@Cacheable`, `@Async` 等）
- 若领域服务无外部依赖，推荐在 CommandHandler 中直接 `new` 实例化（更纯净）
- 若领域服务有外部依赖（如扩展点接口），推荐通过 `@Bean` 注册，但确保无 AOP 切面意外匹配

### 4.2 示例

```java
// domain/service/order/OrderAmountValidator.java（纯 POJO）
package com.bone.blueprint.domain.service.order;

import com.bone.blueprint.domain.order.Order;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;

public class OrderAmountValidator {
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("1000000");

    public static void validate(Order order) {
        if (order.getTotalAmount().compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new DomainException("订单金额超过限制：" + MAX_ORDER_AMOUNT);
        }
    }
}

// infrastructure/config/DomainServiceConfig.java
package com.bone.blueprint.infrastructure.config;

import com.bone.blueprint.domain.service.order.OrderAmountValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainServiceConfig {
    @Bean
    public OrderAmountValidator orderAmountValidator() {
        return new OrderAmountValidator();
    }
}
```

### 4.3 ArchUnit 约束

```java
@ArchTest
static void domainServiceShouldNotHaveSpringAnnotations(JavaClasses classes) {
    noClasses().that().resideInAPackage("..domain.service..")
        .should().beAnnotatedWith("org.springframework.stereotype.Service")
        .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
        .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
        .orShould().beAnnotatedWith("org.springframework.cache.annotation.Cacheable")
        .check(classes);
}
```

---

## 5️⃣ 实体与值对象的区分

| 类型 | 特征 | 数据库主键 | 示例 |
|------|------|------------|------|
| **聚合根** | 独立生命周期，全局标识 | 独立主键 | `Order` |
| **实体（非聚合根）** | 生命周期依附于聚合根，但有本地标识 | 独立主键（但由聚合根管理） | `OrderItem` |
| **值对象** | 无独立标识，不可变 | 无独立主键，使用联合主键或嵌入 | `Address`, `Money` |

**OrderItem 为实体**，因此有独立主键 `id`，但外部不能直接访问 `OrderItemRepository`，所有操作通过 `Order` 聚合根进行。

---

## 6️⃣ CQRS 分级策略

由于所有自定义查询都使用 `Criteria` 或 `QueryBuilder`，CQRS 等级主要影响**查询的封装位置**和**数据源**：

| 等级 | 名称 | 查询封装位置 | 数据源 | 自定义查询方式 |
|------|------|--------------|--------|----------------|
| **L1（默认）** | 标准模式 | ApplicationService 内直接使用 Criteria/QueryBuilder | 单一 | Criteria / QueryBuilder |
| **L2（推荐）** | 局部 CQRS | 独立 QueryHandler + DTO | 单一 | Criteria / QueryBuilder |
| **L3（高级）** | 完整 CQRS | 独立 QueryHandler + 读写分离 | 读写分离 | Criteria / QueryBuilder |

### 6.1 升级信号

| 信号 | 当前等级 | 建议升级 |
|------|----------|----------|
| 同一模块有多个复杂查询（≥3 条件、分页、关联） | L1 | **L2**（引入独立 QueryHandler） |
| 读 QPS > 1000 且写 QPS < 100 | L2 | **L3**（读写数据源分离） |

### 6.2 配置示例

```yaml
bone:
  blueprint:
    cqrs:
      default-level: L1
      modules:
        order: L2
        report: L3
```

---

## 7️⃣ 扩展点规范（替代 if-else）

### 7.1 包路径与命名

| 角色 | 包路径 | 命名格式 | 示例 |
|------|--------|----------|------|
| 扩展点接口 | `domain.extension.{context}` | `{Domain}{Function}Calculator` | `OrderPriceCalculator` |
| 扩展实现 | `infrastructure.extension.{context}` | `{Modifier}{Domain}{Function}Calculator` | `DefaultOrderPriceCalculator`, `AliOrderPriceCalculator` |

**命名原则**：接口与实现均以 `Calculator` 结尾，实现类通过前缀（`Default`、`Ali`、`Vip` 等）区分不同策略。

### 7.2 接口定义

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

### 7.3 扩展请求值对象

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

### 7.4 扩展实现

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

### 7.5 业务上下文全入口覆盖

**强制规范**：所有入站适配器（Web、RPC、MQ、Schedule）在进入应用层前必须绑定 `BizContext`，并在 finally 中解绑。

#### Web 拦截器

```java
// adapter/web/interceptor/TenantInterceptor.java
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
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BizContext.unbind();
    }
}
```

#### MQ 监听器

```java
// adapter/mq/listener/OrderPaidListener.java
@KafkaListener(topics = "order-paid")
public void onMessage(ConsumerRecord<String, String> record) {
    BizContext.builder()
        .tenant(extractTenant(record))
        .bizCode("ecommerce")
        .useCase("order")
        .scenario("standard")
        .build()
        .bind();
    try {
        handler.handle(record.value());
    } finally {
        BizContext.unbind();
    }
}
```

#### Schedule 定时任务

```java
// adapter/schedule/OrderTimeoutJob.java
@Component
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

### 7.6 ArchUnit 兜底检查

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

## 8️⃣ 标准工程结构（完整版）

```text
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
│
├── adapter/                                    # 入站适配器层
│   ├── web/
│   │   ├── controller/OrderController.java
│   │   ├── dto/
│   │   │   ├── request/CreateOrderRequest.java
│   │   │   └── response/OrderDetailResponse.java
│   │   └── assembler/OrderAssembler.java
│   ├── rpc/OrderRpcService.java
│   ├── mq/listener/OrderPaidListener.java
│   └── schedule/CancelExpiredOrderJob.java
│
├── application/                                # 应用层
│   ├── command/
│   │   ├── cmd/CreateOrderCommand.java
│   │   └── handler/CreateOrderCommandHandler.java
│   ├── query/                                  # L2/L3 时使用
│   │   ├── qry/OrderPageQuery.java
│   │   ├── handler/OrderPageQueryHandler.java
│   │   ├── dto/OrderDto.java
│   │   └── projection/OrderWithItemsProjection.java
│   ├── event/OrderPaidEventHandler.java
│   └── service/OrderApplicationService.java     # L1 时使用
│
├── domain/                                     # 领域层（零框架依赖，允许 bone-metadata-sdk 元数据）
│   ├── order/                                  # 订单聚合根包
│   │   ├── Order.java                          # 聚合根（@Table("t_order")）
│   │   ├── OrderItem.java                      # 聚合内实体（@Table("t_order_item")）
│   │   ├── valueobject/
│   │   │   └── OrderStatus.java
│   │   └── event/
│   │       ├── OrderCreatedEvent.java
│   │       ├── OrderPaidEvent.java
│   │       └── OrderCancelledEvent.java
│   ├── repository/                             # 仓储端口
│   │   └── OrderRepository.java
│   ├── gateway/                                # 防腐层端口
│   │   └── InventoryGateway.java
│   ├── service/                                # 领域服务
│   │   └── order/
│   │       └── OrderAmountValidator.java
│   ├── extension/                              # 扩展点接口
│   │   └── order/
│   │       └── OrderPriceCalculator.java
│   └── exception/                              # 领域异常
│       └── OrderDomainException.java
│
├── infrastructure/                             # 基础设施层
│   ├── gateway/                                # 防腐层实现
│   │   └── feign/InventoryFeignGatewayImpl.java
│   ├── extension/                              # 扩展点实现
│   │   └── order/
│   │       ├── DefaultOrderPriceCalculator.java
│   │       ├── AliOrderPriceCalculator.java
│   │       └── VipOrderPriceCalculator.java
│   ├── security/                               # 安全组件
│   │   ├── JwtTokenProvider.java
│   │   └── PasswordEncoderImpl.java
│   ├── config/                                 # 配置类
│   │   ├── metadata/BoneMetadataConfiguration.java
│   │   ├── extension/ExtensionConfiguration.java
│   │   ├── security/SecurityConfiguration.java
│   │   └── web/WebMvcConfiguration.java
│   └── exception/InfrastructureException.java
│
└── resources/
    ├── sql/order/
    │   ├── findOrderWithItems.sql
    │   └── monthlyOrderStatistics.sql
    └── application.yml
```

---

## 9️⃣ 命名体系（完整版）

### 9.1 包命名

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
| `domain.{aggregate}` | 聚合根包（如 `domain.order`），内含聚合根、实体、值对象、事件 |
| `domain.repository` | 仓储端口 |
| `domain.gateway` | 防腐层端口 |
| `domain.service` | 领域服务（按聚合分包） |
| `domain.extension` | 扩展点接口（按聚合分包） |
| `domain.exception` | 领域异常 |
| `infrastructure.gateway` | 防腐层实现 |
| `infrastructure.extension` | 扩展点实现 |
| `infrastructure.config` | 配置类 |
| `infrastructure.security` | 安全组件 |

### 9.2 类命名

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| 控制器 | `{Domain}Controller` | `OrderController` |
| 请求 DTO | `{Action}{Domain}Request` | `CreateOrderRequest` |
| 响应 DTO | `{Domain}{Action}Response` | `OrderDetailResponse` |
| Assembler | `{Domain}Assembler` | `OrderAssembler` |
| 命令对象 | `{Action}{Domain}Command` | `CreateOrderCommand` |
| 命令处理器 | `{Action}{Domain}CommandHandler` | `CreateOrderCommandHandler` |
| 查询对象 | `{Domain}{Action}Query` | `OrderPageQuery` |
| 查询处理器 | `{Domain}{Action}QueryHandler` | `OrderPageQueryHandler` |
| 简单 DTO | `{Domain}Dto` | `OrderDto` |
| 投影 | `{Domain}{Suffix}Projection` | `OrderWithItemsProjection` |
| 聚合根 | `{Aggregate}` | `Order` |
| 实体（非聚合根） | `{Aggregate}{Component}` | `OrderItem` |
| 值对象 | `{BusinessConcept}` | `OrderStatus`, `Address` |
| 领域事件 | `{Aggregate}{PastEvent}` | `OrderCreatedEvent` |
| 仓储接口 | `{Aggregate}Repository` | `OrderRepository` |
| 网关接口 | `{ExternalSystem}Gateway` | `InventoryGateway` |
| 领域服务 | `{Domain}{Function}Validator` | `OrderAmountValidator` |
| 扩展点接口 | `{Domain}{Function}Calculator` | `OrderPriceCalculator` |
| 扩展实现 | `{Modifier}{Domain}{Function}Calculator` | `DefaultOrderPriceCalculator` |
| 原生查询接口 | `{Domain}NativeQueryRepository` | `OrderNativeQueryRepository` |

### 9.3 MapStruct Assembler 规范

**必须使用 Spring 注入方式**：

```java
@Mapper(componentModel = "spring")
public interface OrderAssembler {
    CreateOrderCommand toCommand(CreateOrderRequest request);
    OrderDetailResponse toResponse(Order order);
}
```

**使用**：
```java
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderAssembler assembler;
}
```

**禁止** `Mappers.getMapper(OrderAssembler.class)`。

---

## 🔟 依赖约束（ArchUnit 核心规则）

完整 ArchUnit 规则集（CI 必须通过）：

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

    // 铁律 2：Domain 纯净（允许 bone.metadata.sdk 注解）
    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "com.bone.core..", "lombok..", "com.bone.metadata.sdk..")
            .check(classes);
    }

    @ArchTest
    static void domainLayerShouldNotUseQueryBuilder(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().haveSimpleName("QueryBuilder")
            .check(classes);
    }

    // 铁律 3：领域服务纯净
    @ArchTest
    static void domainServiceShouldNotHaveSpringAnnotations(JavaClasses classes) {
        noClasses().that().resideInAPackage("..domain.service..")
            .should().beAnnotatedWith("org.springframework.stereotype.Service")
            .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
            .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .orShould().beAnnotatedWith("org.springframework.cache.annotation.Cacheable")
            .check(classes);
    }

    // Repository 空接口（无自定义方法）
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
    static void extensionPointShouldBeInDomainExtension(JavaClasses classes) {
        classes().that().areAnnotatedWith(ExtensionPoint.class)
            .should().resideInAPackage("..domain.extension..")
            .check(classes);
    }

    @ArchTest
    static void extensionImplementationShouldBeInInfrastructureExtension(JavaClasses classes) {
        classes().that().areAnnotatedWith(Extension.class)
            .should().resideInAPackage("..infrastructure.extension..")
            .check(classes);
    }

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

    @ArchTest
    static void queryHandlerShouldNotUseExtension(JavaClasses classes) {
        noClasses().that().resideInAPackage("..application.query.handler..")
            .should().dependOnClassesThat().areAnnotatedWith(ExtensionPoint.class)
            .check(classes);
    }

    // 值对象不可变性
    @ArchTest
    static void valueObjectShouldBeImmutable(JavaClasses classes) {
        classes().that().resideInAPackage("..domain..valueobject..")
            .should().onlyHaveFieldsThatAreFinal()
            .andShould().notHaveMethodsMatching("set.*");
    }

    // 实体类必须有 @Table 注解
    @ArchTest
    static void entityShouldHaveTableAnnotation(JavaClasses classes) {
        classes().that().areAssignableTo(Entity.class)
            .and().doNotHaveSimpleName("Entity")
            .should().beAnnotatedWith(Table.class)
            .check(classes);
    }
}
```

---

## 1️⃣1️⃣ 事务与异常模型

| 位置 | 规则 |
|------|------|
| `application.command.handler` | `@Transactional`（必须） |
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

## 1️⃣2️⃣ 测试体系

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

---

## 1️⃣3️⃣ 渐进式演进路线图

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

### 阶段 2（第 2–3 个月）：引入用例与查询分离
- ✅ 写操作全部迁移到 `application.command.handler`
- ✅ 识别复杂查询模块，升级为 L2（独立 QueryHandler + DTO）
- ✅ 列表查询统一使用 QueryBuilder 或 Criteria
- ✅ 引入 `bone-cli` 脚手架

### 阶段 3（第 6 个月+）：按需高级化
- ✅ 识别高并发模块，启用 L3（读写分离）
- ✅ 引入扩展点机制（多租户场景）
- ✅ 原生 SQL 下沉复杂报表

---

## 1️⃣4️⃣ 团队落地 Checklist

### 项目初始化
- [ ] 引入 `bone-core`、`bone-metadata-sdk`、`bone-extension-sdk`
- [ ] 启动类添加 `@EnableSqlRepositories` 和 `@EnableExtensionPoints`
- [ ] 配置 `bone.blueprint.cqrs.default-level: L1`
- [ ] 创建包结构（见第 8 章）
- [ ] 配置 ArchUnit 规则并集成 CI
- [ ] 确认 `DistributedIdGenerator` 可用（环境变量配置）
- [ ] 数据库主键使用 `BIGINT`，无 `biz_id`
- [ ] 所有实体类已添加 `@Table("table_name")` 注解
- [ ] 聚合根与聚合内实体放在同一包下

### 每个聚合开发（L1 默认）
- [ ] 聚合根充血，包含业务方法
- [ ] 聚合根工厂方法接收 ID 参数，不依赖外部生成器
- [ ] 聚合根、实体类添加 `@Table` 注解
- [ ] Repository 子接口为空（仅继承 `Repository<T, ID>`）
- [ ] 所有自定义查询（存在性、业务键、列表、分页）使用 `Criteria` 或 `QueryBuilder`
- [ ] CommandHandler 处理写操作，带 `@Transactional`
- [ ] 代码量控制：简单 CRUD ≤ 5 个类
- [ ] Assembler 使用 `@Mapper(componentModel = "spring")`，注入使用

### 扩展点使用
- [ ] 扩展点接口定义在 `domain.extension.{context}`
- [ ] 命名格式 `{Domain}{Function}Calculator`
- [ ] 扩展实现放在 `infrastructure.extension.{context}`，命名 `{Modifier}{Domain}{Function}Calculator`
- [ ] 默认实现（`tenant="*", scenario="*"`）必须存在
- [ ] 路由维度完整覆盖业务场景
- [ ] 编写扩展点单元测试和集成测试

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
- [ ] Assembler 是否使用 Spring 注入方式？

---

## 1️⃣5️⃣ 核心口诀（最终版）

> **四铁律：依赖向内不反向，Domain 纯净无框架，业务逻辑要充血，外部系统走 ACL。**  
> **ID 策略：雪花 Long 主键，应用层生成传入，不包装不依赖，领域只收不生产。**  
> **实体注解：@Table 必须加，表名显式蛇形写，聚合根 t_order，明细同包不搞 entity 子包。**  
> **Repository：子接口空，基类方法够用，Criteria 和 QueryBuilder 查一切。**  
> **实体值对象：有标识是实体，无标识是值对象，OrderItem 升实体，独立主键不独立仓。**  
> **扩展点：接口 Calculator，实现也用 Calculator，默认实现必须有，上下文全入口覆盖。**  
> **CQRS：L1 直接用工具类，L2 独立 Handler，L3 读写分离。**  
> **落地法：阶段一建底线，阶段二拆查询，阶段三做分离，架构陪团队成长。**

---

**本规范即日起作为 Bone-Blueprint 工程落地的唯一标准。**  
*文档版本：v16.3 | 生效日期：2026-04-23*  
*核心改进：聚合根直接位于 `domain.{aggregate}` 下，同包放实体、值对象、事件，去掉多余 `model` 层级；扩展点实现类命名统一为 `{Modifier}{Domain}{Function}Calculator`；完善 ArchUnit 约束；工程结构图全面优化，逻辑更自洽。*