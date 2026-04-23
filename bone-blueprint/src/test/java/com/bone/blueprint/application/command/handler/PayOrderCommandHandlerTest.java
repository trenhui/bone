package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayOrderCommandHandlerTest {

    @Mock
    private OrderRepository orderRepository;
    
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
        
        assertEquals(com.bone.blueprint.domain.order.OrderStatus.PAID, order.getStatus());
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, times(1)).save(any(Order.class));
    }
    
    @Test
    void testHandleOrderNotFound() {
        when(orderRepository.findById(1L)).thenReturn(null);
        
        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(1L);
        
        assertThrows(com.bone.core.exception.DomainException.class, () -> {
            handler.handle(command);
        });
        
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void testHandleAlreadyPaid() {
        order.pay();
        when(orderRepository.findById(1L)).thenReturn(order);
        
        PayOrderCommand command = new PayOrderCommand();
        command.setOrderId(1L);
        
        assertThrows(com.bone.core.exception.DomainException.class, () -> {
            handler.handle(command);
        });
        
        verify(orderRepository, times(1)).findById(1L);
        verify(orderRepository, never()).save(any(Order.class));
    }
}
