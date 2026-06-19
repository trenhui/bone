package com.bone.blueprint.domain.order;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.blueprint.domain.order.valueobject.OrderStatus;
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
  void testPayOrderSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.pay();

    assertEquals(OrderStatus.PAID, order.getStatus());
    assertEquals(2, order.getDomainEvents().size());
  }

  @Test
  void testPayOrderAlreadyPaid() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));
    order.pay();

    assertThrows(DomainException.class, order::pay);
  }

  @Test
  void testCancelOrderSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.cancel();

    assertEquals(OrderStatus.CANCELLED, order.getStatus());
  }

  @Test
  void testUpdateTotalAmountWithMoney() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
    Order order = Order.create(1L, 1L, 1L, Collections.singletonList(item));

    order.updateTotalAmount(
        com.bone.blueprint.domain.order.valueobject.Money.of(new BigDecimal("300")));

    assertEquals(new BigDecimal("300"), order.getTotalAmount());
  }
}
