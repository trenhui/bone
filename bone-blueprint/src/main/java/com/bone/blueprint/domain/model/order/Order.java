package com.bone.blueprint.domain.model.order;

import com.bone.blueprint.domain.model.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.model.order.event.OrderCreatedEvent;
import com.bone.blueprint.domain.model.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.model.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.model.shared.valueobject.Money;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 订单聚合根（多租户 + 领域事件）。 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_order")
public class Order extends TenantAggregateRoot<Long> {

  /** 金额上限常量：{@code static} 字段本就不参与 SDK 映射，无需（且不应）标注 {@code @Transient}。 */
  private static final Money MAX_ORDER_AMOUNT = Money.of(new BigDecimal("1000000"));

  private Long customerId;

  /**
   * 业务订单号（对外展示 / 客服检索键，与物理 {@code id} 分离——业界订单系统的通行做法： 内部用雪花 ID 做关联键，对外用可读单号，避免单号暴露数据量与分片信息）。
   *
   * <p>由聚合在构造期按 {@code SO + yyyyMMdd + id} 生成：id 已全局唯一，故单号天然唯一且不依赖外部序列器， 便于单测与对账（同一订单任何时刻推导出的单号一致）。
   */
  private String orderNo;

  /** 订单来源渠道（APP / H5 / 小程序 / POS）——运营分析第一维度。 */
  private String channelSource;

  /** 运费（金额三口径：总额 = 明细小计之和 + 运费 − 优惠）。 */
  private BigDecimal freightAmount;

  /** 优惠总额（营销核算；不含运费抵扣）。 */
  private BigDecimal discountAmount;

  /** 支付完成时刻（订单生命周期时间轴刻度，与 CREATED/PAID 状态迁移同点写入）。 */
  private Instant paidTime;

  /**
   * 聚合内部集合：{@code @Transient} 避免映射为 t_order 列；{@code @Cascade} 由 SDK 在根 save/insert/update
   * 后级联落盘（能力需求二 MVP）。
   */
  @Transient
  @Cascade(foreignKey = "orderId")
  private List<OrderItem> items = new ArrayList<>();

  private BigDecimal totalAmount;
  private OrderStatus status;
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * 乐观锁版本号（E-5.3：可并发写聚合必须声明并验证并发策略）。
   *
   * <p><b>SDK 原生 @Version（ADR-0031 D2）</b>：由 {@code bone-metadata-sdk} 统一管理—— 写路径 {@code
   * DynamicUpdateBuilder} 改写 {@code SET version = version + 1}、{@code WHERE version = :old}（old
   * 取实体加载时值）， {@code BaseRepository.update()} 成功后反射回写实体字段；读路径经 {@code findById} / Criteria 自然加载。
   * 并发写 0 行由 SDK 抛 {@code OptimisticLockingFailureException}，在应用层翻译为 {@code
   * OptimisticLockConflictException}。
   *
   * <p>领域层不再手写版本递增（{@code incrementVersion()} 已随 D2 退役），版本由 SDK 原子维护；{@code create} 仍置初值 0 与库一致。
   */
  @Version private Long version;

  public Money getTotalMoney() {
    return totalAmount == null ? Money.zero() : Money.of(totalAmount);
  }

  /**
   * 创建订单（缺省渠道与金额附加项）。
   *
   * @see #create(long, Long, Long, List, String, BigDecimal, BigDecimal)
   */
  public static Order create(long id, Long tenantId, Long customerId, List<OrderItem> items) {
    return create(id, tenantId, customerId, items, null, null, null);
  }

  /**
   * 创建订单（完整真实场景入参）。
   *
   * @param channelSource 来源渠道，可空（未知渠道）
   * @param freightAmount 运费，可空视为 0
   * @param discountAmount 优惠总额，可空视为 0
   */
  public static Order create(
      long id,
      Long tenantId,
      Long customerId,
      List<OrderItem> items,
      String channelSource,
      BigDecimal freightAmount,
      BigDecimal discountAmount) {
    if (items == null || items.isEmpty()) {
      throw new DomainException("订单至少需要一个商品项");
    }
    Money freight = freightAmount == null ? Money.zero() : Money.of(freightAmount);
    Money discount = discountAmount == null ? Money.zero() : Money.of(discountAmount);
    Order order = new Order();
    order.setId(id);
    order.setTenantId(tenantId);
    order.customerId = customerId;
    order.orderNo = generateOrderNo(id);
    order.channelSource = channelSource;
    order.freightAmount = freight.toBigDecimal();
    order.discountAmount = discount.toBigDecimal();
    order.items = new ArrayList<>(items);
    order.recalculateTotal();
    order.assertValidTotal();
    order.status = OrderStatus.CREATED;
    order.version = 0L;
    Instant now = Instant.now();
    order.createdAt = now;
    order.updatedAt = now;
    order.addDomainEvent(
        new OrderCreatedEvent(
            order.getId(), order.getTenantId(), order.getCustomerId(), Instant.now()));
    return order;
  }

  /**
   * 追加商品项（仅内存态）。调用方须再 {@code save} 聚合根，SDK 才会按 {@code @Cascade} 落盘；{@code findById} 不回填集合。
   *
   * <p>没有配对的 {@code removeItem}：原实现全仓零引用。真要删明细，在内存集合中移除后 {@code save} 根，由级联清除孤儿行。
   */
  public void addItem(OrderItem item) {
    if (item == null) {
      throw new DomainException("商品项不能为空");
    }
    this.items.add(item);
    recalculateTotal();
    assertValidTotal();
  }

  /**
   * 聚合内部明细数量变更的<strong>唯一</strong>入口（包级可见）。
   *
   * <p>直接调 {@link OrderItem#updateQuantity} 会绕过本聚合根导致 {@code totalAmount} 不重算—— {@code
   * updateQuantity} 已改为包级可见（{@code OrderItem.updateQuantity}），外部必须经本方法修改明细。
   */
  void updateItemQuantity(long itemId, int newQuantity) {
    OrderItem target = null;
    for (OrderItem item : items) {
      if (item.getId() != null && item.getId() == itemId) {
        target = item;
        break;
      }
    }
    if (target == null) {
      throw new DomainException("订单明细不存在: itemId=" + itemId);
    }
    target.updateQuantity(newQuantity);
    recalculateTotal();
    assertValidTotal();
    this.updatedAt = Instant.now();
  }

  /**
   * 重算总额（金额三口径）：{@code 明细小计之和 + 运费 − 优惠}，下限为 0。
   *
   * <p>下限保护的原因：优惠可能来自营销券，金额由外部系统决定，若券额大于商品额仍应得到 0 元订单（真实业务的 「0 元单」），而不是负金额——{@link Money}
   * 构造器会拒绝负数，直接相减会在这种场景抛「金额不能为负」的领域异常，掩盖真实原因。
   */
  private void recalculateTotal() {
    Money sum = Money.zero();
    for (OrderItem item : items) {
      sum = sum.add(item.getSubtotalMoney());
    }
    Money freight = freightAmount == null ? Money.zero() : Money.of(freightAmount);
    Money discount = discountAmount == null ? Money.zero() : Money.of(discountAmount);
    Money payable = sum.add(freight);
    // 优惠大于应付时得到 0 元订单（真实业务的「0 元单」）——Money 构造拒绝负数，直接相减会抛
    // 「金额不能为负」，把业务场景包装成领域异常，掩盖真实原因。
    this.totalAmount =
        discount.greaterThan(payable)
            ? Money.zero().toBigDecimal()
            : payable.subtract(discount).toBigDecimal();
  }

  /** 业务单号生成策略：{@code SO + yyyyMMdd + 雪花 ID}。由 id 派生，全局唯一且可重复推导（对账友好）。 */
  private static String generateOrderNo(long id) {
    return "SO" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + id;
  }

  /** 订单金额上限不变量：任何导致金额变更的路径都必须经过本校验，避免被绕过。 */
  private void assertValidTotal() {
    if (getTotalMoney().greaterThan(MAX_ORDER_AMOUNT)) {
      throw new DomainException("订单金额超过限制");
    }
  }

  /**
   * 聚合内的明细集合（<b>包级可见</b>）。
   *
   * <p><b>为何不对外公开</b>：SDK 重载聚合不做级联，{@code findById} 得到的订单其 {@code items} 恒为空。 若暴露为 public，调用方会自然写出
   * {@code order.getItems()} 并拿到空列表——一个"看起来成功、实际什么都没做"的静默错误 （历史上有两个事件订阅器踩过）。明细一律经读侧端口 {@code
   * OrderRepository.findOrderWithItems} 获取。
   *
   * <p>本方法仅供领域内与同包聚合单测使用，代表"创建期 / 内存态"的明细。
   */
  List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }

  /**
   * 是否处于待支付状态（仅 CREATED 可确认支付 / 发起支付）。
   *
   * <p>意图揭示命名：外部（Handler / 事件订阅器）用本方法判断「能否支付」，**不要**直接比较 {@code getStatus() ==
   * OrderStatus.CREATED}——状态解释权归聚合，避免状态机泄漏到应用层（反贫血 E-6.4）。
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
    // 支付完成时刻与状态迁移同点写入：时间轴刻度由状态机唯一维护，避免下游各写一份造成对账分歧
    this.paidTime = Instant.now();
    this.updatedAt = Instant.now();
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
    this.updatedAt = Instant.now();
    addDomainEvent(new OrderCancelledEvent(getId(), getTenantId(), Instant.now()));
  }

  /**
   * 发货：仅已支付订单可发货（PAID→SHIPPED）。
   *
   * <p><b>不发 DomainEvent</b>：内部状态迁移，无跨聚合协作需求—— 订单发货后无任何下游聚合需要以此为前置条件触发自身行为。
   */
  public void ship() {
    if (this.status != OrderStatus.PAID) {
      throw new DomainException("只有已支付订单可以发货");
    }
    this.status = OrderStatus.SHIPPED;
    this.updatedAt = Instant.now();
  }

  /**
   * 送达：仅已发货订单可送达（SHIPPED→DELIVERED）。
   *
   * <p><b>不发 DomainEvent</b>：内部状态迁移，无跨聚合协作需求—— 订单送达是生命周期终态的业务确认，不触发任何下游聚合行为。
   */
  public void deliver() {
    if (this.status != OrderStatus.SHIPPED) {
      throw new DomainException("只有已发货订单可以确认送达");
    }
    this.status = OrderStatus.DELIVERED;
    this.updatedAt = Instant.now();
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
    this.updatedAt = Instant.now();
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
    this.updatedAt = Instant.now();
    assertValidTotal();
  }
}
