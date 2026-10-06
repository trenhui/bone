package com.bone.blueprint.domain.model.order;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.model.shared.valueobject.Money;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.TimeZone;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OrderTest {

  /**
   * P1-3/P2-4: 订单号日期段必须在东八区生成，在 UTC 宿主机下也必须如此。
   *
   * <p>⚠️ <b>本用例在某些时刻会退化为恒真，判别力由 {@link #orderNoDateSegmentIsIndependentOfHostDefaultTimeZone}
   * 承担</b>。 原因：断言两侧（期望与实际）都由 {@code ORDER_NO_ZONE} 推出；若实现漏掉该常量，<b>且此刻 UTC 与东八区恰好同一天</b> （实测 14:30
   * UTC vs 22:30 CST），本用例仍会通过。保留它是因为它还能直接指出「订单号里缺了业务日期段」这类硬错误。
   *
   * @see #orderNoDateSegmentIsIndependentOfHostDefaultTimeZone
   */
  @Test
  void orderNoDateSegmentUsesBusinessTimezone() {
    withHostTimeZone(
        ZoneId.of("UTC"),
        () -> {
          OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 1, new BigDecimal("100"));
          String orderNo = Order.create(1L, 1L, 1L, Collections.singletonList(item)).getOrderNo();
          String businessDate =
              LocalDate.now(Order.ORDER_NO_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
          String hostDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
          assertTrue(
              orderNo.contains(businessDate),
              () ->
                  "订单号应含东八区日期 "
                      + businessDate
                      + "，实际: "
                      + orderNo
                      + "（宿主机UTC 日期为 "
                      + hostDate
                      + "；若二者恰好相同说明此刻 UTC 未跨日，本用例无判别力——见"
                      + " orderNoDateSegmentIsIndependentOfHostDefaultTimeZone 的恒真自检）");
          return orderNo;
        });
  }

  /**
   * <b>跨时区一致性（本用例才是真正有判别力的那一个）</b>。
   *
   * <p><b>时区对的选择是关键</b>：必须挑「日期必然不同」的两个时区，否则测试在某些时刻会退化为恒真。 本用例用 {@code Pacific/Kiritimati}(UTC+14) 与
   * {@code Pacific/Pago_Pago}(UTC-11)——两者相差 <b>25 小时</b>，跨整日的时差使它们<strong>任何时刻都不可能落在同一天</strong>
   * （实测：此刻前者为 10-07、后者为 10-06）。 ⚠️ 不要用 {@code Etc/GMT+14}：{@link ZoneId} 不认该ID（{@code Unknown
   * time-zone ID}）， {@link java.util.TimeZone} 认——两者API 不兼容，必须选 IANA 规范名。
   *
   * <p><b>为什么必须这样</b>：曾用「东八区 / UTC / 纽约」做过一版，看似合理，但<b>变异验证暴露它恒真</b>—— 把实现退回无参 {@code
   * LocalDate.now()} 后测试依然全绿，因为当时 UTC 14:30 与东八区 22:30 <b>恰好是同一天</b>，
   * 任何跨时区断言都比不出差异。<b>「全绿」不等于「有判别力」。</b>
   *
   * <p>断言语义：宿主机时区<b>不得影响</b>订单号日期段，日期段恒为东八区业务日期。
   */
  @Test
  void orderNoDateSegmentIsIndependentOfHostDefaultTimeZone() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 1, new BigDecimal("100"));

    // 相差 25 小时的两端（UTC+14 vs UTC-11）：日期必然不同 ⇒ 若实现依赖宿主机时区，两者日期段必不一致 ⇒ 测试必红
    String atUtcPlus14 =
        withHostTimeZone(
            ZoneId.of("Pacific/Kiritimati"),
            () -> Order.create(1L, 1L, 1L, Collections.singletonList(item)).getOrderNo());
    String atUtcMinus11 =
        withHostTimeZone(
            ZoneId.of("Pacific/Pago_Pago"),
            () -> Order.create(2L, 1L, 1L, Collections.singletonList(item)).getOrderNo());

    // ⚠️ 前置自检：比较的必须是两个时区的「本地日期」（LocalDate.now()），**不是订单号日期段**——
    // 订单号日期段本就该在两个时区下相同（这正是被测行为），拿它当自检会永远失败。
    // 自检的意义：证明此刻这两个时区的本地日期确实不同 ⇒ 后面的断言有判别力。
    String localDateAtPlus14 =
        withHostTimeZone(ZoneId.of("Pacific/Kiritimati"), () -> LocalDate.now().toString());
    String localDateAtMinus11 =
        withHostTimeZone(ZoneId.of("Pacific/Pago_Pago"), () -> LocalDate.now().toString());
    assertNotEquals(
        localDateAtPlus14,
        localDateAtMinus11,
        () ->
            "前置条件：两个极端时区的『本地日期』必须不同，否则本用例退化为恒真（当前 "
                + localDateAtPlus14
                + " vs "
                + localDateAtMinus11
                + "）—— 请复核两个时区常量是否仍相差 25 小时");

    // 无论宿主机时区为何，日期段都必须是东八区业务日期
    String businessDate =
        LocalDate.now(Order.ORDER_NO_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
    assertEquals(
        businessDate,
        dateSegment(atUtcPlus14),
        () -> "宿主机 Kiritimati(UTC+14) 下的订单号日期段应为东八区业务日期 " + businessDate);
    assertEquals(
        businessDate,
        dateSegment(atUtcMinus11),
        () -> "宿主机 Pago_Pago(UTC-11) 下的订单号日期段应为东八区业务日期 " + businessDate);
  }

  /** 取订单号中的日期段（第 3~10 位，形如 20261006）。 */
  private static String dateSegment(String orderNo) {
    return orderNo.length() >= 10 ? orderNo.substring(2, 10) : orderNo;
  }

  /**
   * 在指定宿主机时区下执行 {@code action}，结束后无条件恢复原时区，返回其结果。
   *
   * <p>用 try-finally 保证即便断言失败也不污染后续测试——JVM 默认时区是全局状态，泄漏会让后续用例莫名其妙地红。
   */
  private static <T> T withHostTimeZone(ZoneId zone, Supplier<T> action) {
    TimeZone original = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
      return action.get();
    } finally {
      TimeZone.setDefault(original);
    }
  }

  @Test
  void testCreateOrderWithEmptyItems() {
    assertThrows(DomainException.class, () -> Order.create(1L, 1L, 1L, Collections.emptyList()));
  }

  @Test
  void testCreateOrderWithNullItems() {
    assertThrows(DomainException.class, () -> Order.create(1L, 1L, 1L, null));
  }

  @Test
  void testCreateOrderSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    assertNotNull(order);
    assertEquals(1L, order.getId());
    assertEquals(1L, order.getTenantId());
    assertEquals(1L, order.getCustomerId());
    assertEquals(OrderStatus.CREATED, order.getStatus());
    assertEquals(new BigDecimal("200"), order.getTotalAmount());
    assertEquals(1, order.getItems().size());
  }

  @Test
  void testConfirmPaidSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.confirmPaid();

    assertEquals(OrderStatus.PAID, order.getStatus());
    assertEquals(2, order.getDomainEvents().size());
  }

  @Test
  void testCancelOrderSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.cancel();

    assertEquals(OrderStatus.CANCELLED, order.getStatus());
  }

  /**
   * 锁定「已退款订单可再取消」这一<b>经确认的产品决定</b>（P2-8）。
   *
   * <p>为什么显式锁：守卫里刻意没有 {@code REFUNDED} 分支，后人做「状态机加固」时极易把它补上，
   * 而这会改变一个已被产品确认的行为。本测试让那种改动<b>必须先改测试、并且被迫思考一次</b>。
   *
   * <p>同时钉住一个易被误解的点：取消<b>不产生新的资金动作</b>，退款已在 {@code refund()} 完成。
   */
  @Test
  void refundedOrderCanStillBeCancelledByProductDecision() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.refund();
    assertEquals(OrderStatus.REFUNDED, order.getStatus());

    order.cancel();

    assertEquals(OrderStatus.CANCELLED, order.getStatus());
  }

  @Test
  void testApplyPricingUpdatesTotalAmount() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    // 应用层调用扩展点算出最终金额后传入，聚合只认 Money（不依赖扩展点接口）
    order.applyPricing(Money.of(new BigDecimal("300")));

    assertEquals(new BigDecimal("300"), order.getTotalAmount());
  }

  @Test
  void testApplyPricingWithNullMoneyThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    assertThrows(DomainException.class, () -> order.applyPricing(null));
  }

  @Test
  void testApplyPricingNegativeMoneyThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    // 负数金额由 Money 构造器拦截（领域不变量：金额不可为负）
    assertThrows(DomainException.class, () -> order.applyPricing(Money.of(new BigDecimal("-1"))));
  }

  @Test
  void testAddItemOverLimitThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 1, new BigDecimal("400000"));
    OrderItem extra = OrderItem.create(2L, 1L, 2L, "商品2", 1, new BigDecimal("700000"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    assertThrows(DomainException.class, () -> order.addItem(extra));
  }

  @Test
  void testConfirmPaidMovesToPaid() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    boolean migrated = order.confirmPaid();

    assertTrue(migrated);
    assertEquals(OrderStatus.PAID, order.getStatus());
    // 创建事件 + 确认支付事件
    assertEquals(2, order.getDomainEvents().size());
  }

  @Test
  void testConfirmPaidIsIdempotent() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.clearDomainEvents();

    boolean second = order.confirmPaid();

    // 幂等：已 PAID 再次确认返回 false，不重复发事件
    assertFalse(second);
    assertEquals(OrderStatus.PAID, order.getStatus());
    assertEquals(0, order.getDomainEvents().size());
  }

  @Test
  void testConfirmPaidOnCancelledThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.cancel();

    assertThrows(DomainException.class, order::confirmPaid);
  }

  @Test
  void testShipOnlyFromPaid() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    // CREATED 不可发货
    assertThrows(DomainException.class, order::ship);

    order.confirmPaid();
    order.ship();
    assertEquals(OrderStatus.SHIPPED, order.getStatus());
  }

  @Test
  void testDeliverOnlyFromShipped() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.confirmPaid();
    order.ship();
    order.deliver();
    assertEquals(OrderStatus.DELIVERED, order.getStatus());

    // 已送达不可再送达
    assertThrows(DomainException.class, order::deliver);
  }

  @Test
  void testCancelShippedThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.ship();

    assertThrows(DomainException.class, order::cancel);
  }

  @Test
  void testRefundOnlyAfterPaid() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    // 未支付不可退款
    assertThrows(DomainException.class, order::refund);

    order.confirmPaid();
    order.refund();
    assertEquals(OrderStatus.REFUNDED, order.getStatus());
  }

  @Test
  void testRefundAfterDelivered() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.ship();
    order.deliver();

    order.refund();
    assertEquals(OrderStatus.REFUNDED, order.getStatus());
  }

  @Test
  void testRefundTwiceThrows() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.confirmPaid();
    order.refund();

    assertThrows(DomainException.class, order::refund);
  }
}
