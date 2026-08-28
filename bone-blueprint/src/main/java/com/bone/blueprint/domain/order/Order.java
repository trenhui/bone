package com.bone.blueprint.domain.order;

import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
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

/** 订单聚合根（多租户 + 领域事件）。 */
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
    order.assertValidTotal();
    order.status = OrderStatus.CREATED;
    Date now = new Date();
    order.createdAt = now;
    order.updatedAt = now;
    order.addDomainEvent(
        new OrderCreatedEvent(
            order.getId(), order.getTenantId(), order.getCustomerId(), Instant.now()));
    return order;
  }

  public void addItem(OrderItem item) {
    if (item == null) {
      throw new DomainException("商品项不能为空");
    }
    this.items.add(item);
    recalculateTotal();
    assertValidTotal();
  }

  public void removeItem(int index) {
    if (index < 0 || index >= items.size()) {
      throw new DomainException("商品项索引无效");
    }
    this.items.remove(index);
    recalculateTotal();
    assertValidTotal();
  }

  private void recalculateTotal() {
    Money sum = Money.zero();
    for (OrderItem item : items) {
      sum = sum.add(item.getSubtotalMoney());
    }
    this.totalAmount = sum.toBigDecimal();
  }

  /** 订单金额上限不变量：任何导致金额变更的路径都必须经过本校验，避免被绕过。 */
  private void assertValidTotal() {
    if (getTotalMoney().greaterThan(MAX_ORDER_AMOUNT)) {
      throw new DomainException("订单金额超过限制");
    }
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
    addDomainEvent(
        new OrderPaidEvent(getId(), getTenantId(), customerId, totalAmount, Instant.now()));
  }

  /**
   * 支付成功回调后确认订单（真实下单支付链路：支付单 {@code Payment.confirmSuccess} 成功后，由事件 订阅驱动本方法把订单从 CREATED 置为 PAID）。
   *
   * <p>与 {@link #pay()} 状态迁移一致，但语义上由「支付网关回调」触发，作为跨聚合（支付→订单）协作的 确认行为；幂等：已 PAID 的订单再次确认直接返回。
   */
  public boolean confirmPaid() {
    if (this.status == OrderStatus.PAID) {
      return false; // 幂等：支付回调重复确认直接跳过
    }
    if (this.status != OrderStatus.CREATED) {
      throw new DomainException("只有新建状态的订单可以确认支付");
    }
    this.status = OrderStatus.PAID;
    this.updatedAt = new Date();
    addDomainEvent(
        new OrderPaidEvent(getId(), getTenantId(), customerId, totalAmount, Instant.now()));
    return true;
  }

  public void cancel() {
    if (this.status == OrderStatus.SHIPPED) {
      throw new DomainException("已发货订单无法取消");
    }
    if (this.status == OrderStatus.DELIVERED) {
      throw new DomainException("已送达订单无法取消");
    }
    if (this.status == OrderStatus.CANCELLED) {
      throw new DomainException("订单已取消");
    }
    this.status = OrderStatus.CANCELLED;
    this.updatedAt = new Date();
    addDomainEvent(new OrderCancelledEvent(getId(), getTenantId(), Instant.now()));
  }

  /** 发货：仅已支付订单可发货（CREATED→PAID→SHIPPED）。 */
  public void ship() {
    if (this.status != OrderStatus.PAID) {
      throw new DomainException("只有已支付订单可以发货");
    }
    this.status = OrderStatus.SHIPPED;
    this.updatedAt = new Date();
  }

  /** 送达：仅已发货订单可送达（SHIPPED→DELIVERED）。 */
  public void deliver() {
    if (this.status != OrderStatus.SHIPPED) {
      throw new DomainException("只有已发货订单可以确认送达");
    }
    this.status = OrderStatus.DELIVERED;
    this.updatedAt = new Date();
  }

  /** 退款：已支付/已发货/已送达订单可退款（进入 REFUNDED）。 */
  public void refund() {
    if (this.status == OrderStatus.CREATED) {
      throw new DomainException("未支付订单无需退款");
    }
    if (this.status == OrderStatus.CANCELLED) {
      throw new DomainException("已取消订单无需退款");
    }
    if (this.status == OrderStatus.REFUNDED) {
      throw new DomainException("订单已退款");
    }
    this.status = OrderStatus.REFUNDED;
    this.updatedAt = new Date();
  }

  /**
   * 应用扩展点定价结果：由聚合自身调用价格计算器并更新总金额，避免外部通过 setter 修改聚合状态。
   *
   * <p>此为领域行为（反贫血红线 §17）：Handler 仅编排调用，不直接 setTotalAmount。
   */
  public void applyPricing(OrderPriceCalculator calculator) {
    if (calculator == null) {
      throw new DomainException("价格计算器不能为空");
    }
    OrderPriceCalculator.OrderPriceRequest request =
        OrderPriceCalculator.OrderPriceRequest.builder()
            .baseAmount(getTotalMoney().toBigDecimal())
            .shippingFee(BigDecimal.ZERO)
            .build();
    Money finalPrice = Money.of(calculator.calculate(request));
    this.totalAmount = finalPrice.toBigDecimal();
    this.updatedAt = new Date();
    assertValidTotal();
  }
}
