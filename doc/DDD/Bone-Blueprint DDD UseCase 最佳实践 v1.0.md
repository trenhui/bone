# 📁 文档二：Bone-Blueprint-DDD-UseCase最佳实践.md

## 详细代码实现与最佳实践（完整示例版）

> **定位**：提供完整的代码示例、详细注释和最佳实践指导，帮助团队快速上手。

---

# 🏛️ Bone-Blueprint DDD UseCase 最佳实践 v1.0

## 详细代码实现与最佳实践指南

> **定位**：提供完整的代码示例、详细注释和最佳实践指导，帮助团队快速上手。  
> **前置要求**：请先阅读《Bone-Blueprint DDD UseCase 规范 v1.0》了解架构原则和目录结构。  
> **生效日期**：2026-04-24

---

## 0️⃣ 最佳实践概述

本文档提供 Bone-Blueprint DDD UseCase 架构的完整代码实现示例，包括：

1. **领域层**：聚合根、实体、值对象、领域事件
2. **应用层**：UseCase（Mode A / Mode B）、Handler、Query
3. **适配器层**：Controller、RPC、MQ、Schedule
4. **基础设施层**：扩展点、网关、仓储
5. **AI 能力**：@Capability 注解、Handler Registry
6. **测试**：单元测试、集成测试示例

---

## 1️⃣ 领域层实现

### 1.1 聚合根（Order）

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

### 1.2 实体（OrderItem）

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
    private Long orderId;
    private Long productId;
    private String productName;
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

### 1.3 值对象（OrderStatus）

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

### 1.4 领域事件

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

### 1.5 仓储接口（空接口）

```java
// domain/repository/OrderRepository.java
package com.bone.blueprint.domain.repository;

import com.bone.metadata.sdk.Repository;
import com.bone.blueprint.domain.order.Order;

public interface OrderRepository extends Repository<Order, Long> {
    // 空接口，所有查询能力由基类和 Criteria/QueryBuilder 提供
}
```

### 1.6 网关接口（ACL）

```java
// domain/gateway/InventoryGateway.java
package com.bone.blueprint.domain.gateway;

public interface InventoryGateway {
    boolean checkStock(Long productId, Integer quantity);
    void deductStock(Long productId, Integer quantity);
    void releaseStock(Long orderId);
}
```

---

## 2️⃣ 应用层实现

### 2.1 UseCase 接口定义（bone-core）

```java
// bone-core/usecase/UseCaseExecutor.java
package com.bone.core.usecase;

public interface UseCaseExecutor<C, R> {
    R execute(C command);
}
```

### 2.2 UseCase 元数据注解（bone-core）

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

### 2.3 Mode A：简单查询 UseCase

```java
// application/usecase/simple/GetOrderDetailUseCase.java
package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "GetOrderDetail",
    description = "获取订单详情",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase implements UseCaseExecutor<OrderDetailQuery, OrderDto> {

    private final OrderDetailQueryHandler orderDetailQueryHandler;

    @Override
    public OrderDto execute(OrderDetailQuery query) {
        return orderDetailQueryHandler.handle(query);
    }
}
```

### 2.4 Mode B：复杂编排 UseCase

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

### 2.5 Handler（原子能力）

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
```

```java
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

### 2.6 查询处理器

```java
// application/query/handler/OrderDetailQueryHandler.java
package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderDetailQueryHandler {
    
    private final OrderRepository orderRepository;
    
    @Transactional(readOnly = true)
    public OrderDto handle(OrderDetailQuery query) {
        // 使用 Criteria 进行条件查询
        Criteria<Order> criteria = Criteria.<Order>builder()
            .eq(Order::getId, query.getOrderId())
            .build();
        
        Order order = orderRepository.findOneByCriteria(criteria);
        if (order == null) {
            return null;
        }
        
        return toOrderDto(order);
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

### 2.7 命令对象

```java
// application/command/cmd/CreateOrderCommand.java
package com.bone.blueprint.application.command.cmd;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderCommand {
    private Long customerId;
    private List<OrderItemDto> items;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public List<OrderItemDto> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDto> items) {
        this.items = items;
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

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
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

---

## 3️⃣ 适配器层实现

### 3.1 Web 控制器

```java
// adapter/web/controller/OrderController.java
package com.bone.blueprint.adapter.web.controller;

import com.bone.blueprint.adapter.web.assembler.OrderAssembler;
import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.application.usecase.simple.CancelOrderUseCase;
import com.bone.blueprint.application.usecase.standard.CreateOrderUseCase;
import com.bone.blueprint.application.query.handler.OrderDetailQueryHandler;
import com.bone.core.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "订单管理", description = "提供订单相关的Web接口")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderDetailQueryHandler orderDetailQueryHandler;
    private final OrderAssembler orderAssembler;

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderCommand command = orderAssembler.toCreateOrderCommand(request);
        Long orderId = createOrderUseCase.execute(command);
        return ApiResponse.success(orderId);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        CancelOrderCommand command = orderAssembler.toCancelOrderCommand(id);
        cancelOrderUseCase.execute(command);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getById(@PathVariable Long id) {
        OrderDetailQuery query = orderAssembler.toOrderDetailQuery(id);
        var orderDto = orderDetailQueryHandler.handle(query);
        var response = orderAssembler.toOrderDetailResponse(orderDto);
        return ApiResponse.success(response);
    }
}
```

### 3.2 Web 装配器

```java
// adapter/web/assembler/OrderAssembler.java
package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.request.CreateOrderRequest;
import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderDetailQuery;
import com.bone.blueprint.adapter.web.dto.response.OrderDetailResponse;
import org.mapstruct.Mapper;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface OrderAssembler {
    
    CreateOrderCommand toCreateOrderCommand(CreateOrderRequest request);
    
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
    
    default OrderDetailResponse toOrderDetailResponse(OrderDto orderDto) {
        if (orderDto == null) {
            return null;
        }
        
        return OrderDetailResponse.builder()
                .id(orderDto.getId())
                .customerId(orderDto.getCustomerId())
                .totalAmount(orderDto.getTotalAmount())
                .status(orderDto.getStatus())
                .items(orderDto.getItems().stream()
                        .map(item -> OrderDetailResponse.OrderItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .subtotal(item.getSubtotal())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}
```

---

## 4️⃣ 扩展点实现

### 4.1 扩展点接口

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

### 4.2 扩展实现

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

---

## 5️⃣ AI 能力实现

### 5.1 @Capability 注解

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

---

## 6️⃣ 单元测试示例

### 6.1 领域层单元测试

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

---

## 7️⃣ 最佳实践总结

### 7.1 开发流程建议

1. **识别业务能力**：确定需要哪些 UseCase
2. **判断复杂度**：根据机器规则判断 Mode A 还是 Mode B
3. **开发领域模型**：先设计聚合根、实体、值对象
4. **开发 Handler**：Mode B 场景先开发原子能力
5. **开发 UseCase**：编排业务逻辑
6. **开发适配器**：Controller/RPC/MQ/定时任务
7. **编写测试**：领域层 ≥90%，应用层 100%

### 7.2 常见问题解决

**Q1：UseCase 内 Handler 超过 5 个怎么办？**

A：考虑合并相关 Handler，或引入 Flow 编排引擎。

**Q2：查询是否需要封装为 UseCase？**

A：需要。即使简单查询也要封装为 Mode A UseCase，保持架构一致性。

**Q3：如何选择 Mode A 和 Mode B？**

A：遵循机器规则自动判定：
- 有 @Capability 标注 → Mode B
- 有 @ExtensionPoint 参与 → Mode B
- 有 Gateway 调用 → Mode B
- 有 Handler 编排 → Mode B
- 否则 → Mode A

---

**本最佳实践文档与《Bone-Blueprint DDD UseCase 规范 v1.0》配套使用。**

*文档版本：v1.0 | 生效日期：2026-04-24 | 维护团队：架构组*

---

## 📋 文档对照表

| 内容类型 | 规范文档 | 最佳实践文档 |
|----------|----------|--------------|
| 架构原则 | ✅ 详细定义 | ❌ 不涉及 |
| 目录结构 | ✅ 完整展示 | ❌ 不涉及 |
| 命名规范 | ✅ 表格形式 | ❌ 不涉及 |
| 强制红线 | ✅ 明确列出 | ❌ 不涉及 |
| 代码示例 | ❌ 不涉及 | ✅ 完整代码 |
| 详细注释 | ❌ 不涉及 | ✅ 丰富注释 |
| 测试示例 | ❌ 不涉及 | ✅ 完整示例 |
| 最佳实践指导 | ❌ 不涉及 | ✅ 详细说明 |

--