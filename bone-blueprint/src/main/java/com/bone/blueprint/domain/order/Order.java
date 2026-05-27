package com.bone.blueprint.domain.order;

import com.bone.blueprint.domain.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.valueobject.Money;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 订单聚合根（多租户 + 领域事件）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order")
public class Order extends TenantAggregateRoot<Long> {

    private static final Money MAX_ORDER_AMOUNT = Money.of(new BigDecimal("1000000"));

    private Long customerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Date createdAt;
    private Date updatedAt;

    public Money getTotalMoney() {
        return totalAmount == null ? Money.zero() : Money.of(totalAmount);
    }

    public static Order create(long id, Long tenantId, Long customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单至少需要一个商品项");
        }
        Order order = new Order();
        order.setId(id);
        order.setTenantId(tenantId);
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.recalculateTotal();
        if (order.getTotalMoney().greaterThan(MAX_ORDER_AMOUNT)) {
            throw new DomainException("订单金额超过限制");
        }
        order.status = OrderStatus.CREATED;
        Date now = new Date();
        order.createdAt = now;
        order.updatedAt = now;
        order.addDomainEvent(new OrderCreatedEvent(order.getId(), order.getTenantId(), order.getCustomerId(), Instant.now()));
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
        Money sum = Money.zero();
        for (OrderItem item : items) {
            sum = sum.add(item.getSubtotalMoney());
        }
        this.totalAmount = sum.toBigDecimal();
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void pay() {
        if (this.status != OrderStatus.CREATED) {
            throw new DomainException("只有新建状态的订单可以支付");
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = new Date();
        addDomainEvent(new OrderPaidEvent(
                getId(), getTenantId(), customerId, totalAmount, Instant.now()));
    }

    public void cancel() {
        if (this.status == OrderStatus.SHIPPED) {
            throw new DomainException("已发货订单无法取消");
        }
        if (this.status == OrderStatus.CANCELLED) {
            throw new DomainException("订单已取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = new Date();
        addDomainEvent(new OrderCancelledEvent(getId(), getTenantId(), Instant.now()));
    }

    public void updateTotalAmount(Money newTotal) {
        if (newTotal == null) {
            throw new DomainException("订单金额无效");
        }
        this.totalAmount = newTotal.toBigDecimal();
        this.updatedAt = new Date();
    }
}
