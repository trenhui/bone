
# 🏛️ Bone-Blueprint DDD UseCase 规范 v1.0

## 企业级 DDD + 分级 CQRS + 双模式 UseCase + 六边形架构 + Bone 元数据驱动框架

&gt; **定位**：从"架构规范"升级为"可演进的企业级业务操作系统"。以 5 条铁律守住架构底线，以双模式策略平衡效率与扩展性，以原子化 Handler 为 AI 可调度的能力积木，以 Flow 为流程编排载体，以 @Capability 为 AI 可发现的能力资产。
&gt; **适用规模**：10-100 人团队 / 单体到分布式演进系统 / 企业级 AI 原生应用
&gt; **核心目标**：结构即规则、命名即语义、依赖即约束、能力即资产、流程可编排、演进有红线。
&gt; **生效日期**：2026-04-24

---

## 0️⃣ 设计哲学

本规范是 Bone-Blueprint 系列的完整融合版本，融合了 **工业级落地规范**、**双模式演进策略** 与 **AI 原生架构**，实现"架构正确性"、"组织可落地性"与"AI 可编排性"的终极平衡。

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
&gt; 先写 A（快），再长成 B（稳），最终进 Flow（智能）。
&gt; 硬约束保底线（防腐化），软能力促生长（提效率）。
&gt; **Controller 永远只认 UseCase，Handler 只给 UseCase 用。**
&gt; **@Capability 是 AI 发现能力的唯一入口。**
&gt; **规则可判定，决策可解释，复杂度可治理。**

---

## 1️⃣ 架构第一性原则（5 条铁律，CI 阻断）

### 🔴 铁律 1：依赖方向必须正确（唯一拓扑约束）

```
adapter → application → domain ← infrastructure
```

- ✅ 只能向内依赖
- ❌ 禁止任何反向 import（如 domain 引用 adapter 或 infrastructure）
- ✅ **ArchUnit 在 CI 中强制阻断**

### 🔴 铁律 2：Controller 统一入口原则

&gt; **Controller 只能依赖 UseCase，禁止直接依赖 Handler 或 QueryHandler**

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

&gt; **为什么**：防止外部系统 API 变更、限流、宕机直接击穿业务核心。

---

## 2️⃣ ID 生成策略（应用层生成，领域层纯接收）

### 2.1 核心原则

- **ID 生成属于基础设施行为，必须在应用层完成**
- 领域层聚合根工厂方法接收 ID 参数，不依赖任何 ID 生成工具
- 统一使用 `bone-core` 提供的 `DistributedIdGenerator`（雪花算法）在应用层直接调用生成 `Long` 型 ID
- 禁止使用数据库自增主键（`IDENTITY`）作为领域 ID
- 禁止创建多余的包装类，直接使用 `DistributedIdGenerator.generateLongId()`

### 2.2 数据库设计规范

| 项目 | 规范 |
|------|------|
| 主键 | `id BIGINT NOT NULL`，应用 Snowflake 生成 |
| 必备审计字段 | `created_by`、`updated_by`、`created_at`、`updated_at` |
| 软删除 | `deleted TINYINT(1) NOT NULL DEFAULT 0` |
| 乐观锁 | 需要并发控制的表加 `version INT NOT NULL DEFAULT 0` |
| 布尔字段 | `is_xxx TINYINT(1)` |
| 时间字段 | `xxx_at DATETIME(3)` |
| 金额字段 | `DECIMAL(18,2)` |
| JSON 字段 | `JSON` 类型 |
| 租户字段 | `tenant_id BIGINT NOT NULL DEFAULT 0` |
| 索引 | 唯一索引 `uk_表名_字段`，普通索引 `idx_表名_字段`，外键只建索引不建约束 |
| 分区 | 日志类大表按年 RANGE 分区，分区键纳入主键 |

### 2.3 数据库表示例（订单场景）

```sql
-- 订单主表
CREATE TABLE t_order (
    id                  BIGINT          NOT NULL COMMENT '雪花算法生成的全局唯一ID',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    customer_id         BIGINT          NOT NULL COMMENT '客户ID',
    total_amount        DECIMAL(18,2)   NOT NULL COMMENT '订单总金额',
    status              VARCHAR(20)     NOT NULL COMMENT '订单状态：CREATED-创建，PAID-已支付，SHIPPED-已发货，DELIVERED-已送达，CANCELLED-已取消',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_order_tenant (tenant_id),
    KEY idx_order_customer (customer_id),
    KEY idx_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单表';

-- 订单明细表（实体，有独立主键，由聚合根管理）
CREATE TABLE t_order_item (
    id                  BIGINT          NOT NULL COMMENT '雪花算法生成的全局唯一ID',
    order_id            BIGINT          NOT NULL COMMENT '关联 t_order.id',
    product_id          BIGINT          NOT NULL COMMENT '商品ID',
    product_name        VARCHAR(200)    NOT NULL COMMENT '商品名称（快照）',
    quantity            INT             NOT NULL COMMENT '商品数量',
    unit_price          DECIMAL(18,2)   NOT NULL COMMENT '商品单价',
    subtotal            DECIMAL(18,2)   NOT NULL COMMENT '商品小计',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='订单明细表';
```

### 2.4 聚合根与实体定义（优化后包结构）

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
public class OrderItem extends AbstractEntity&lt;Long&gt; {

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
        if (productId == null || productId &lt;= 0) {
            throw new DomainException("商品ID无效");
        }
        if (quantity == null || quantity &lt;= 0) {
            throw new DomainException("商品数量必须大于0");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) &lt;= 0) {
            throw new DomainException("商品单价必须大于0");
        }
        return new OrderItem(id, orderId, productId, productName, quantity, unitPrice);
    }

    public void updateQuantity(Integer newQuantity) {
        if (newQuantity == null || newQuantity &lt;= 0) {
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
public class Order extends AggregateRoot&lt;Long&gt; {

    private Long id;
    private Long tenantId;
    private Long customerId;
    private List&lt;OrderItem&gt; items = new ArrayList&lt;&gt;();
    private BigDecimal totalAmount;
    private OrderStatus status;

    public static Order create(long id, Long tenantId, Long customerId, List&lt;OrderItem&gt; items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单至少需要一个商品项");
        }
        Order order = new Order();
        order.id = id;
        order.tenantId = tenantId;
        order.customerId = customerId;
        order.items = new ArrayList&lt;&gt;(items);
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
        if (index &lt; 0 || index &gt;= items.size()) {
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

    public List&lt;OrderItem&gt; getItems() {
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
        if (newTotal == null || newTotal.compareTo(BigDecimal.ZERO) &lt; 0) {
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

- **Repository 子接口只需继承 `Repository&lt;T, ID&gt;`，不添加任何额外方法**
- 基类 `Repository&lt;T, ID&gt;` 已提供完整的 CRUD 能力：
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

public interface OrderRepository extends Repository&lt;Order, Long&gt; {
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
    List&lt;OrderStatisticsProjection&gt; getMonthlyStatistics(@Param("yearMonth") String yearMonth);
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

### 4.3 标准工程结构

```
src/main/java/com/bone/blueprint/
├── BoneBlueprintApplication.java
│
├── adapter/                                    # 入站适配器层
│   ├── web/
│   │   ├── controller/                         # REST 控制器
│   │   ├── dto/request/                        # 请求 DTO
│   │   ├── dto/response/                       # 响应 DTO
│   │   ├── assembler/                          # DTO 转换
│   │   └── interceptor/                        # 拦截器
│   ├── rpc/                                    # RPC 服务
│   │   ├── dto/                                # RPC DTO
│   │   └── {Domain}RpcService.java
│   ├── mq/listener/                            # 消息监听器
│   └── schedule/                               # 定时任务
│
├── application/                                # 应用层
│   ├── usecase/                                # 用例层
│   │   ├── simple/                             # 🟢 Mode A
│   │   └── standard/                           # 🔵 Mode B
│   ├── command/                                # 命令处理器
│   │   ├── cmd/                                # 命令对象
│   │   └── handler/                            # 命令处理器
│   ├── query/                                  # 查询处理器
│   │   ├── qry/                                # 查询对象
│   │   ├── handler/                            # 查询处理器
│   │   ├── dto/                                # 查询结果 DTO
│   │   └── projection/                         # 投影对象
│   ├── event/                                  # 事件处理器
│   └── assembler/                              # 装配器
│
├── domain/                                     # 领域层
│   ├── {aggregate}/                            # 聚合根包
│   │   ├── {Aggregate}.java                    # 聚合根
│   │   ├── {Aggregate}{Component}.java         # 实体
│   │   ├── valueobject/                        # 值对象
│   │   └── event/                              # 领域事件
│   ├── repository/                             # 仓储接口
│   ├── gateway/                                # 防腐层接口
│   ├── service/                                # 领域服务
│   ├── extension/                              # 扩展点接口
│   └── exception/                              # 领域异常
│
└── infrastructure/                             # 基础设施层
    ├── gateway/                                # 网关实现
    ├── extension/                              # 扩展点实现
    ├── query/native/                           # 原生查询
    ├── security/                               # 安全组件
    ├── config/                                 # 配置类
    └── exception/                              # 基础设施异常
```

### 4.4 UseCase 接口定义

```java
// bone-core/usecase/UseCaseExecutor.java
package com.bone.core.usecase;

public interface UseCaseExecutor&lt;C, R&gt; {
    R execute(C command);
}
```

### 4.5 UseCase 元数据注解

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

### 4.6 订单场景模式选择矩阵

| 场景 | 推荐模式 | 判定依据 |
|------|----------|----------|
| **订单创建** | 🔵 **Mode B** | 涉及 Gateway 调用 + 多个 Handler 编排 |
| **订单支付** | 🔵 **Mode B** | 涉及 Gateway 调用 + 扩展点 |
| **订单取消** | 🟢 **Mode A** | 无 Gateway 调用，单一逻辑 |
| **查询订单列表** | 🟢 **Mode A** | 纯读操作 |
| **查询订单详情** | 🟢 **Mode A** | 纯读操作（但封装为 UseCase） |

### 4.7 Handler 粒度上限（强制）

```java
// 🔴 强制规则：单 UseCase 内 Handler 数量 &gt; 5 → 必须合并或引入 Flow 编排
@ArchTest
static void useCaseShouldNotHaveTooManyHandlers(JavaClasses classes) {
    classes().that().haveSimpleNameEndingWith("UseCase")
        .forEach(useCase -&gt; {
            long handlerCount = useCase.getFields().stream()
                .filter(f -&gt; f.getRawType().getName().endsWith("Handler"))
                .count();
            if (handlerCount &gt; 5) {
                System.err.println("[WARN] UseCase " + useCase.getName() + 
                    " has " + handlerCount + " handlers, consider merging or using Flow");
            }
        });
}
```

### 4.8 Mode A：UseCase 直写（简单模式）

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
public class CancelOrderUseCase implements UseCaseExecutor&lt;CancelOrderCommand, Boolean&gt; {

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
public class GetOrderListUseCase implements UseCaseExecutor&lt;OrderPageQuery, PageResult&lt;OrderDto&gt;&gt; {

    private final OrderPageQueryHandler orderPageQueryHandler;

    @Override
    public PageResult&lt;OrderDto&gt; execute(OrderPageQuery query) {
        return orderPageQueryHandler.handle(query);
    }
}
```

#### 查询对象

```java
// application/query/qry/OrderPageQuery.java
package com.bone.blueprint.application.query.qry;

public class OrderPageQuery {
    private Long tenantId;
    private Long customerId;
    private String status;
    private Integer pageSize = 10;
    private Integer pageNum = 1;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

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
    private Long tenantId;
    private Long customerId;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private List&lt;OrderItemDto&gt; items;

    private OrderDto() {
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List&lt;OrderItemDto&gt; getItems() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private Long tenantId;
        private Long customerId;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        private List&lt;OrderItemDto&gt; items;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
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

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder items(List&lt;OrderItemDto&gt; items) {
            this.items = items;
            return this;
        }

        public OrderDto build() {
            OrderDto dto = new OrderDto();
            dto.id = this.id;
            dto.tenantId = this.tenantId;
            dto.customerId = this.customerId;
            dto.totalAmount = this.totalAmount;
            dto.status = this.status;
            dto.createdAt = this.createdAt;
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
    public PageResult&lt;OrderDto&gt; handle(OrderPageQuery query) {
        // 使用 Criteria 查询
        List&lt;Order&gt; orders = getOrdersByQuery(query);
        
        List&lt;OrderDto&gt; orderDtos = orders.stream()
                .map(this::toOrderDto)
                .collect(Collectors.toList());
        
        // 返回分页结果
        return PageResult.of(orderDtos, (long) orderDtos.size(), query.getPageNum(), query.getPageSize());
    }
    
    private List&lt;Order&gt; getOrdersByQuery(OrderPageQuery query) {
        // 使用 Criteria API 进行查询
        // 示例：orderRepository.where().eq("tenantId", query.getTenantId()).list();
        return List.of(); // 实际需要替换为真实查询
    }
    
    private OrderDto toOrderDto(Order order) {
        List&lt;OrderDto.OrderItemDto&gt; itemDtos = order.getItems().stream()
                .map(this::toOrderItemDto)
                .collect(Collectors.toList());
        
        return OrderDto.builder()
                .id(order.getId())
                .tenantId(order.getTenantId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
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

### 4.9 Mode B：UseCase + Handler（企业模式）

#### 命令对象

```java
// application/command/cmd/CreateOrderCommand.java
package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderCommand {
    private Long tenantId;
    private Long customerId;
    private List&lt;OrderItemDto&gt; items;

    private CreateOrderCommand() {
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public List&lt;OrderItemDto&gt; getItems() {
        return items;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long tenantId;
        private Long customerId;
        private List&lt;OrderItemDto&gt; items;

        public Builder tenantId(Long tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder items(List&lt;OrderItemDto&gt; items) {
            this.items = items;
            return this;
        }

        public CreateOrderCommand build() {
            CreateOrderCommand command = new CreateOrderCommand();
            command.tenantId = this.tenantId;
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
    
    public void handle(List&lt;OrderItem&gt; items) {
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
    
    public Order handle(Long orderId, Long tenantId, Long customerId, List&lt;OrderItem&gt; items) {
        Order order = Order.create(orderId, tenantId, customerId, items);
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
public class CreateOrderUseCase implements UseCaseExecutor&lt;CreateOrderCommand, Long&gt; {

    private final CheckStockHandler checkStockHandler;
    private final CreateOrderHandler createOrderHandler;
    private final OrderRepository orderRepository;
    private final OrderPriceCalculator priceCalculator;

    @Override
    public Long execute(CreateOrderCommand cmd) {
        long orderId = DistributedIdGenerator.generateLongId();
        
        List&lt;OrderItem&gt; items = cmd.getItems().stream()
            .map(dto -&gt; OrderItem.create(
                DistributedIdGenerator.generateLongId(),
                orderId,
                dto.getProductId(),
                dto.getProductName(),
                dto.getQuantity(),
                dto.getUnitPrice()))
            .collect(Collectors.toList());
        
        checkStockHandler.handle(items);
        Order order = createOrderHandler.handle(orderId, cmd.getTenantId(), cmd.getCustomerId(), items);
        
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
    
    private final Map&lt;String, CapabilityRegistration&gt; capabilityIndex = new ConcurrentHashMap&lt;&gt;();
    private final ApplicationContext applicationContext;
    
    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        Map&lt;String, Object&gt; handlers = applicationContext.getBeansWithAnnotation(Capability.class);
        handlers.forEach((beanName, handler) -&gt; {
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
    
    public List&lt;CapabilityRegistration&gt; getAllCapabilities() {
        return new ArrayList&lt;&gt;(capabilityIndex.values());
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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "能力管理", description = "AI/Flow 能力发现接口")
@RestController
@RequestMapping("/api/capabilities")
@RequiredArgsConstructor
public class CapabilityController {
    
    private final HandlerRegistry handlerRegistry;
    
    @Operation(summary = "获取所有能力", description = "返回所有可被 AI/Flow 调用的能力清单")
    @GetMapping
    public ApiResponse&lt;List&lt;HandlerRegistry.CapabilityRegistration&gt;&gt; getAllCapabilities() {
        return ApiResponse.success(handlerRegistry.getAllCapabilities());
    }
    
    @Operation(summary = "获取指定能力", description = "根据能力名称获取详细元数据")
    @GetMapping("/{name}")
    public ApiResponse&lt;HandlerRegistry.CapabilityRegistration&gt; getCapability(@PathVariable String name) {
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
    
    private final List&lt;ExtensionDecision&gt; decisions = new ArrayList&lt;&gt;();
    private final ConcurrentHashMap&lt;String, List&lt;ExtensionDecision&gt;&gt; decisionsByPoint = new ConcurrentHashMap&lt;&gt;();
    
    public ExtensionDecision record(String extensionPoint, Object input, 
                                    List&lt;ExtensionCandidate&gt; candidates,
                                    ExtensionCandidate selected) {
        ExtensionDecision decision = ExtensionDecision.builder()
            .extensionPoint(extensionPoint)
            .timestamp(Instant.now())
            .input(input)
            .candidates(candidates.stream().map(c -&gt; c.getExtensionClass().getSimpleName()).collect(Collectors.toList()))
            .selected(selected.getExtensionClass().getSimpleName())
            .reason(selected.getMatchReason())
            .build();
        decisions.add(decision);
        decisionsByPoint.computeIfAbsent(extensionPoint, k -&gt; new ArrayList&lt;&gt;()).add(decision);
        return decision;
    }
    
    public List&lt;ExtensionDecision&gt; getDecisions(String extensionPoint) {
        return decisionsByPoint.getOrDefault(extensionPoint, new ArrayList&lt;&gt;());
    }
    
    @Value
    @Builder
    public static class ExtensionDecision {
        String extensionPoint;
        Instant timestamp;
        Object input;
        List&lt;String&gt; candidates;
        String selected;
        String reason;
    }
    
    @Value
    @Builder
    public static class ExtensionCandidate {
        Class&lt;?&gt; extensionClass;
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
    public ApiResponse&lt;Long&gt; createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
        Long orderId = createOrderUseCase.execute(command);
        return ApiResponse.success(orderId);
    }

    @Operation(summary = "支付订单", description = "支付指定的订单")
    @PostMapping("/{orderId}/pay")
    public ApiResponse&lt;Boolean&gt; payOrder(@PathVariable Long orderId) {
        PayOrderCommand command = PayOrderCommand.builder().orderId(orderId).build();
        Boolean result = payOrderUseCase.execute(command);
        return ApiResponse.success(result);
    }

    @Operation(summary = "取消订单", description = "取消指定的订单")
    @PostMapping("/{orderId}/cancel")
    public ApiResponse&lt;Boolean&gt; cancelOrder(@PathVariable Long orderId) {
        CancelOrderCommand command = CancelOrderCommand.builder().orderId(orderId).build();
        Boolean result = cancelOrderUseCase.execute(command);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详情")
    @GetMapping("/{orderId}")
    public ApiResponse&lt;OrderDetailResponse&gt; getOrderDetail(@PathVariable Long orderId) {
        OrderDetailQuery query = OrderDetailQuery.builder().orderId(orderId).build();
        OrderDetailResponse response = orderDetailQueryHandler.handle(query);
        return ApiResponse.success(response);
    }
}
```

---

## 8️⃣ 强制红线（CI 阻断级）

| ❌ 绝对禁止 | ✅ 正确做法 |
|------------|------------|
| 项目内定义 `AggregateRoot`、`DomainEvent` | 统一使用 `bone-core` |
| 手动编写 `RepositoryImpl` 或 `PO` | 接口继承 `Repository`，SDK 动态代理 |
| 创建 `persistence` 目录及其子包 | 完全信任 SDK 元数据映射 |
| `domain` 对象使用 `@Data` 或 `@Setter` | 仅允许 `@Getter` 和私有构造器注解 |
| Controller 直接依赖 Handler/QueryHandler | Controller 只依赖 UseCase |
| UseCase 内 Handler 数量 &gt; 5 | 合并 Handler 或引入 Flow 编排 |
| 暴露给 AI 的能力未加 `@Capability` | 所有 AI 能力必须添加完整元数据 |
| 多租户/多场景使用 if-else | 使用扩展点实现 |

---

## 9️⃣ 事务模型

| 位置 | 规则 |
|------|------|
| `application.command.handler` | **必须** `@Transactional` |
| `application.query.handler` | `@Transactional(readOnly = true)` |
| `application.usecase` | **可选** `@Transactional`（复杂编排场景） |
| `domain` / `infrastructure` | **严禁**事务注解 |

---

## 🔟 异常处理分层

| 异常类型 | 抛出层级 | HTTP 状态 |
|----------|----------|----------|
| `DomainException` | domain | 400 |
| `NotFoundException` | application / adapter | 404 |
| `InfrastructureException` | infrastructure | 500 |
| `CapabilityNotFoundException` | core.usecase | 404 |

---

## 1️⃣1️⃣ 测试体系

| 层级 | 工具 | 覆盖率要求 |
|------|------|------------|
| Domain 单元测试 | JUnit 5 + AssertJ | ≥90% |
| Application 集成测试 | `@SpringBootTest` + TestContainers | 核心用例 100% |
| Architecture 测试 | ArchUnit | CI 必须通过 |

---

## 1️⃣2️⃣ 命名体系

| 类型 | 命名格式 | 示例 |
|------|----------|------|
| UseCase 实现（Mode A） | `{Action}{Domain}UseCase` | `GetOrderDetailUseCase` |
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

---

## 1️⃣3️⃣ 核心口诀

&gt; **五铁律：依赖向内不反向，Controller 只认 UseCase，Domain 纯净有分级，业务逻辑要充血，外部系统走 ACL。**
&gt; **ID 策略：雪花 Long 主键，应用层生成传入。**
&gt; **Repository：子接口空，Criteria 查一切。**
&gt; **双模式：Mode A 直写快，Mode B 机器判，Handler 不超 5。**
&gt; **AI 可见：@Capability 完整元数据。**
&gt; **扩展点：Calculator 命名，默认实现必有。**

---

**本规范即日起作为团队 Bone-Blueprint 工程落地的唯一标准。**

*文档版本：v1.0 | 生效日期：2026-04-24 | 维护团队：架构组*

---

