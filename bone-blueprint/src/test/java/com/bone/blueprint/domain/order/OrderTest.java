package com.bone.blueprint.domain.order;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void testCreateOrderWithEmptyItems() {
        assertThrows(DomainException.class, () -> {
            Order.create(1L, 1L, Collections.emptyList());
        });
    }

    @Test
    void testCreateOrderWithNullItems() {
        assertThrows(DomainException.class, () -> {
            Order.create(1L, 1L, null);
        });
    }

    @Test
    void testCreateOrderSuccess() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        assertNotNull(order);
        assertEquals(1L, order.getId());
        assertEquals(1L, order.getCustomerId());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(new BigDecimal("200"), order.getTotalAmount());
        assertEquals(1, order.getItems().size());
    }

    @Test
    void testPayOrderSuccess() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        order.pay();
        
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(1, order.getDomainEvents().size());
    }

    @Test
    void testPayOrderAlreadyPaid() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        order.pay();
        
        assertThrows(DomainException.class, () -> {
            order.pay();
        });
    }

    @Test
    void testCancelOrderSuccess() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        order.cancel();
        
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void testCancelOrderAlreadyCancelled() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        order.cancel();
        
        assertThrows(DomainException.class, () -> {
            order.cancel();
        });
    }

    @Test
    void testAddItem() {
        OrderItem item1 = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item1));
        
        OrderItem item2 = OrderItem.create(2L, 1L, 2L, "商品2", 1, new BigDecimal("50"));
        order.addItem(item2);
        
        assertEquals(2, order.getItems().size());
        assertEquals(new BigDecimal("250"), order.getTotalAmount());
    }

    @Test
    void testRemoveItem() {
        OrderItem item1 = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        OrderItem item2 = OrderItem.create(2L, 1L, 2L, "商品2", 1, new BigDecimal("50"));
        Order order = Order.create(1L, 1L, java.util.Arrays.asList(item1, item2));
        
        order.removeItem(0);
        
        assertEquals(1, order.getItems().size());
        assertEquals(new BigDecimal("50"), order.getTotalAmount());
    }

    @Test
    void testRemoveItemInvalidIndex() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        assertThrows(DomainException.class, () -> {
            order.removeItem(10);
        });
    }

    @Test
    void testUpdateTotalAmount() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        order.updateTotalAmount(new BigDecimal("300"));
        
        assertEquals(new BigDecimal("300"), order.getTotalAmount());
    }

    @Test
    void testUpdateTotalAmountInvalid() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        Order order = Order.create(1L, 1L, Collections.singletonList(item));
        
        assertThrows(DomainException.class, () -> {
            order.updateTotalAmount(new BigDecimal("-100"));
        });
    }
}
