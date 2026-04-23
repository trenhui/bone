package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerTest {

    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private InventoryGateway inventoryGateway;
    
    @Mock
    private OrderPriceCalculator priceCalculator;
    
    @InjectMocks
    private CreateOrderCommandHandler handler;
    
    private CreateOrderCommand command;
    
    @BeforeEach
    void setUp() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
    }
    
    @Test
    void testHandleSuccess() {
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("200"));
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(inventoryGateway, times(1)).checkStock(1L, 2);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }
    
    @Test
    void testHandleInsufficientStock() {
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(false);
        
        assertThrows(com.bone.core.exception.DomainException.class, () -> {
            handler.handle(command);
        });
        
        verify(orderRepository, never()).save(any(Order.class));
    }
    
    @Test
    void testHandleMultipleItems() {
        CreateOrderCommand.OrderItemDto item1 = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand.OrderItemDto item2 = CreateOrderCommand.OrderItemDto.builder()
                .productId(2L)
                .productName("商品2")
                .quantity(1)
                .unitPrice(new BigDecimal("50"))
                .build();
        
        CreateOrderCommand multiItemCommand = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Arrays.asList(item1, item2))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("250"));
        
        Long orderId = handler.handle(multiItemCommand);
        
        assertNotNull(orderId);
        verify(inventoryGateway, times(2)).checkStock(anyLong(), anyInt());
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }
}
