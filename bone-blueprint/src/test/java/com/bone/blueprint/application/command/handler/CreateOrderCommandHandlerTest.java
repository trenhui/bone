package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Collections;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class CreateOrderCommandHandlerTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @InjectMocks
    private CreateOrderCommandHandler createOrderCommandHandler;
    
    @Test
    public void testHandle() {
        // 构建创建订单命令
        CreateOrderCommand command = CreateOrderCommand.builder()
            .customerId(1L)
            .totalAmount(new BigDecimal(100))
            .items(Collections.emptyList())
            .build();
        
        // 执行命令
        createOrderCommandHandler.handle(command);
        
        // 验证订单仓库的save方法被调用了一次
        verify(orderRepository, times(1)).save(org.mockito.ArgumentMatchers.any(Order.class));
    }
}
