package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOrderCommandHandlerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private PayOrderCommandHandler handler;

    private Order order;

    @BeforeEach
    void setUp() {
        OrderItem item = OrderItem.create(1L, 1L, 1L, "商品1", 2, new BigDecimal("100"));
        order = Order.create(1L, 1L, Collections.singletonList(item));
    }

    @Test
    void testHandleSuccess() {
        when(orderRepository.findById(1L)).thenReturn(order);

        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(1L);

        handler.handle(command);

        assertEquals(com.bone.blueprint.domain.order.valueobject.OrderStatus.PAID, order.getStatus());
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(domainEventPublisher, times(1)).publishAll(any());
    }

    @Test
    void testHandleOrderNotFound() {
        when(orderRepository.findById(1L)).thenReturn(null);

        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(1L);

        assertThrows(NotFoundException.class, () -> handler.handle(command));

        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testHandleAlreadyPaid() {
        order.pay();
        order.clearDomainEvents();
        when(orderRepository.findById(1L)).thenReturn(order);

        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(1L);

        assertThrows(DomainException.class, () -> handler.handle(command));

        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }
}
