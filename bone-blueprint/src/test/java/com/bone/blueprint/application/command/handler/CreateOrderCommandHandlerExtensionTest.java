package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.engine.extension.api.ExtensionPointExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderCommandHandlerExtensionTest {

    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private InventoryGateway inventoryGateway;
    
    @Mock
    private OrderPriceCalculator priceCalculator;
    
    @InjectMocks
    private CreateOrderCommandHandler handler;
    
    @Test
    void testHandleWithStandardPrice() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("200")); // 标准价格
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testHandleWithVipPrice() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("180")); // VIP 9折
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testHandleWithPromotionPrice() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("160")); // 促销8折
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testHandleWithMemberPrice() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("170")); // 会员85折
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testHandleWithEnterprisePrice() {
        CreateOrderCommand.OrderItemDto itemDto = CreateOrderCommand.OrderItemDto.builder()
                .productId(1L)
                .productName("商品1")
                .quantity(2)
                .unitPrice(new BigDecimal("100"))
                .build();
        
        CreateOrderCommand command = CreateOrderCommand.builder()
                .customerId(1L)
                .items(Collections.singletonList(itemDto))
                .build();
        
        when(inventoryGateway.checkStock(anyLong(), anyInt())).thenReturn(true);
        when(priceCalculator.calculate(any())).thenReturn(new BigDecimal("140")); // 企业7折
        
        Long orderId = handler.handle(command);
        
        assertNotNull(orderId);
        verify(priceCalculator, times(1)).calculate(any());
        verify(orderRepository, times(1)).save(any(Order.class));
    }
}