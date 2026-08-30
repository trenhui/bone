package com.bone.blueprint.domain.order;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.shared.valueobject.Money;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class OrderTest {

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
