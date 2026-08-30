package com.bone.blueprint.domain.order;

import com.bone.blueprint.domain.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.shared.valueobject.Money;
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

  /**
   * 是否处于待支付状态（仅 CREATED 可确认支付 / 发起支付）。
   *
   * <p>意图揭示命名：外部（Handler / 事件订阅器）用本方法判断「能否支付」，**不要**直接比较 {@code getStatus() ==
   * OrderStatus.CREATED}——状态解释权归聚合，避免状态机泄漏到应用层（反贫血 §17）。
   */
  public boolean isAwaitingPayment() {
    return this.status == OrderStatus.CREATED;
  }

  /**
   * 是否处于可退款状态（PAID / SHIPPED / DELIVERED 且尚未退款）。
   *
   * <p>与 {@link #refund()} 的守卫条件严格对应；新增状态时两处须同步修改。
   */
  public boolean isRefundable() {
    return this.status == OrderStatus.PAID
        || this.status == OrderStatus.SHIPPED
        || this.status == OrderStatus.DELIVERED;
  }

  /** 是否已支付（可用于下游判断「钱已到账」）。 */
  public boolean isPaid() {
    return this.status == OrderStatus.PAID;
  }

  /**
   * 确认订单已支付（CREATED → PAID）。
   *
   * <p>订单聚合**唯一**的支付确认入口，由真实支付链路驱动：支付单 {@code Payment.confirmSuccess} 成功 → {@code
   * PaymentSucceededEvent} → 订阅方调本方法。
   *
   * <p>幂等：已 PAID 的订单再次确认直接返回 {@code false}（跳过、不重复发事件）；非 CREATED 状态抛 {@link DomainException}。
   *
   * @return 本次调用是否真正完成状态迁移（false 表示幂等跳过）
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

  /**
   * 上报「钱货不一致」：支付单已成功（钱已收），但订单当前状态无法确认支付（货未付）。
   *
   * <p><b>为何是聚合行为</b>：{@code addDomainEvent} 受保护，且「订单处于何种状态算异常」属订单自身的状态机 知识，判定与事件构造都应归属聚合；Handler
   * 只负责编排与发布。
   *
   * <p><b>本方法不改变订单状态</b>：状态迁移必须由真实业务驱动。异常上报只是把问题<strong>显式化</strong>，
   * 交由补偿链路处理——绝不能为了「让状态对上」而在此自动改单，那会掩盖真正的资金问题。
   *
   * @param paymentId 已成功的支付单号
   * @param reason 不一致原因（人类可读，随事件透传给下游）
   */
  public void reportPaymentInconsistency(long paymentId, String reason) {
    addDomainEvent(
        new OrderPaymentInconsistentEvent(
            getId(),
            getTenantId(),
            paymentId,
            this.status == null ? null : this.status.name(),
            reason,
            Instant.now()));
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

  /** 退款：已支付/已发货/已送达订单可退款（进入 REFUNDED）。守卫条件见 {@link #isRefundable()}。 */
  public void refund() {
    if (this.status == OrderStatus.REFUNDED) {
      throw new DomainException("订单已退款");
    }
    if (!isRefundable()) {
      throw new DomainException("当前状态不支持退款: " + this.status);
    }
    this.status = OrderStatus.REFUNDED;
    this.updatedAt = new Date();
  }

  /**
   * 应用定价结果（扩展点计算后的最终金额）。
   *
   * <p><b>为何不接收 {@code OrderPriceCalculator}</b>：若让聚合持有并调用扩展点接口，等于把扩展点框架类型
   * 引入领域层，领域层将依赖「扩展点机制」这一技术设施；且扩展点实现替换时聚合签名需随之变动。改由应用层 调用扩展点算出最终金额，聚合只认 {@link
   * Money}——扩展点实现可自由替换，领域层零感知。
   *
   * <p>金额合法性由 {@link Money} 构造器保证（负数 →「金额不能为负」），本方法仅拦截 null。
   */
  public void applyPricing(Money finalPrice) {
    if (finalPrice == null) {
      throw new DomainException("定价结果不能为空");
    }
    this.totalAmount = finalPrice.toBigDecimal();
    this.updatedAt = new Date();
    assertValidTotal();
  }
}
