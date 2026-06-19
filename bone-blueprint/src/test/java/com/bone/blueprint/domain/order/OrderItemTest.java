package com.bone.blueprint.domain.order;

import static org.junit.jupiter.api.Assertions.*;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderItemTest {

  @Test
  void testCreateOrderItemWithInvalidProductId() {
    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, null, "商品1", 2, new BigDecimal("100"));
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 0L, "商品1", 2, new BigDecimal("100"));
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, -1L, "商品1", 2, new BigDecimal("100"));
        });
  }

  @Test
  void testCreateOrderItemWithInvalidQuantity() {
    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", null, new BigDecimal("100"));
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", 0, new BigDecimal("100"));
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", -1, new BigDecimal("100"));
        });
  }

  @Test
  void testCreateOrderItemWithInvalidUnitPrice() {
    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", 2, null);
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", 2, BigDecimal.ZERO);
        });

    assertThrows(
        DomainException.class,
        () -> {
          OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("-100"));
        });
  }

  @Test
  void testCreateOrderItemSuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));

    assertNotNull(item);
    assertEquals(1L, item.getId());
    assertEquals(1L, item.getOrderId());
    assertEquals(1L, item.getProductId());
    assertEquals("商品1", item.getProductName());
    assertEquals(2, item.getQuantity());
    assertEquals(new BigDecimal("100"), item.getUnitPrice());
    assertEquals(new BigDecimal("200"), item.getSubtotal());
  }

  @Test
  void testUpdateQuantitySuccess() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));

    item.updateQuantity(5);

    assertEquals(5, item.getQuantity());
    assertEquals(new BigDecimal("500"), item.getSubtotal());
  }

  @Test
  void testUpdateQuantityInvalid() {
    OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));

    assertThrows(
        DomainException.class,
        () -> {
          item.updateQuantity(null);
        });

    assertThrows(
        DomainException.class,
        () -> {
          item.updateQuantity(0);
        });

    assertThrows(
        DomainException.class,
        () -> {
          item.updateQuantity(-1);
        });
  }
}
