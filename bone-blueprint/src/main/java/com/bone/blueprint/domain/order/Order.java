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
import java.util.Date;
import java.util.List;

/**
 * 订单聚合根
 * <p>
 * 表示一个完整的订单，包含订单基本信息、商品项、状态等
 * 封装了订单的核心业务逻辑，如创建、支付、取消等操作
 * </p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order")
public class Order extends AggregateRoot<Long> {

    /**
     * 订单金额上限
     */
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("1000000");

    private Long id;
    private Long customerId;
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal totalAmount;
    private OrderStatus status;

    /**
     * 获取订单ID
     * 
     * @return 订单ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取客户ID
     * 
     * @return 客户ID
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * 获取订单总金额
     * 
     * @return 订单总金额
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * 获取订单状态
     * 
     * @return 订单状态
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * 创建订单
     * 
     * @param id 订单ID
     * @param customerId 客户ID
     * @param items 商品项列表
     * @return 创建的订单对象
     * @throws DomainException 当订单参数不合法时抛出
     */
    public static Order create(long id, Long customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DomainException("订单至少需要一个商品项");
        }
        Order order = new Order();
        order.id = id;
        order.customerId = customerId;
        order.items = new ArrayList<>(items);
        order.recalculateTotal();
        if (order.totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new DomainException("订单金额超过限制");
        }
        order.status = OrderStatus.CREATED;
        order.addDomainEvent(new OrderCreatedEvent(order));
        return order;
    }

    /**
     * 添加商品项
     * 
     * @param item 商品项
     */
    public void addItem(OrderItem item) {
        this.items.add(item);
        recalculateTotal();
    }

    /**
     * 移除商品项
     * 
     * @param index 商品项索引
     * @throws DomainException 当商品项索引无效时抛出
     */
    public void removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            throw new DomainException("商品项索引无效");
        }
        this.items.remove(index);
        recalculateTotal();
    }

    /**
     * 重新计算订单总金额
     */
    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 获取商品项列表（不可修改）
     * 
     * @return 商品项列表
     */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * 支付订单
     * 
     * @throws DomainException 当订单状态不是创建状态时抛出
     */
    public void pay() {
        if (this.status != OrderStatus.CREATED) {
            throw new DomainException("只有新建状态的订单可以支付");
        }
        this.status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(this));
    }

    /**
     * 取消订单
     * 
     * @throws DomainException 当订单已发货或已取消时抛出
     */
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

    /**
     * 更新订单总金额
     * 
     * @param newTotal 新的总金额
     * @throws DomainException 当金额无效时抛出
     */
    public void updateTotalAmount(BigDecimal newTotal) {
        if (newTotal == null || newTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("订单金额无效");
        }
        this.totalAmount = newTotal;
    }
}
