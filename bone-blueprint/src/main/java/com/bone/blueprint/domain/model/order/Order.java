
package com.bone.blueprint.domain.model.order;

import com.bone.blueprint.domain.model.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.model.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.model.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.model.order.vo.OrderId;
import com.bone.blueprint.domain.model.order.vo.OrderItem;
import com.bone.blueprint.domain.model.order.vo.OrderStatus;
import com.bone.blueprint.domain.model.order.vo.ShippingAddress;
import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Order extends AggregateRoot&lt;OrderId&gt; {

    private OrderId id;
    private String customerId;
    private List&lt;OrderItem&gt; items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private ShippingAddress shippingAddress;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime paidTime;
    private LocalDateTime shippedTime;
    private LocalDateTime completedTime;
    private LocalDateTime cancelledTime;

    public static Order create(String customerId, List&lt;OrderItem&gt; items,
                                ShippingAddress shippingAddress, String remark) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }
        if (shippingAddress == null) {
            throw new IllegalArgumentException("Shipping address cannot be null");
        }

        Order order = new Order();
        order.id = OrderId.of(UUID.randomUUID().toString());
        order.customerId = customerId;
        order.items = new ArrayList&lt;&gt;(items);
        order.totalAmount = calculateTotalAmount(items);
        order.status = OrderStatus.CREATED;
        order.shippingAddress = shippingAddress;
        order.remark = remark;
        order.createTime = LocalDateTime.now();
        order.updateTime = LocalDateTime.now();
        order.addDomainEvent(new OrderCreatedEvent(order.id, customerId));
        return order;
    }

    private static BigDecimal calculateTotalAmount(List&lt;OrderItem&gt; items) {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void pay(String paymentId) {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("Only created orders can be paid");
        }
        this.status = OrderStatus.PAID;
        this.paidTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        addDomainEvent(new OrderPaidEvent(this.id, paymentId));
    }

    public void ship() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("Only paid orders can be shipped");
        }
        this.status = OrderStatus.SHIPPED;
        this.shippedTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public void complete() {
        if (status != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Only shipped orders can be completed");
        }
        this.status = OrderStatus.COMPLETED;
        this.completedTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public void cancel(String reason) {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order cannot be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        addDomainEvent(new OrderCancelledEvent(this.id, reason));
    }

    public List&lt;OrderItem&gt; getItems() {
        return Collections.unmodifiableList(items);
    }
}

