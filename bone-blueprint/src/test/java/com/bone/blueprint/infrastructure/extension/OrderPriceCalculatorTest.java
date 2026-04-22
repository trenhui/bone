package com.bone.blueprint.infrastructure.extension;

import com.bone.blueprint.domain.service.order.OrderPriceCalculator;
import com.bone.engine.extension.support.context.BizContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class OrderPriceCalculatorTest {
    
    @Autowired
    private OrderPriceCalculator orderPriceCalculator;
    
    @Test
    public void testDefaultPriceCalculation() {
        // 构建标准场景请求
        OrderPriceCalculator.OrderPriceRequest request = new OrderPriceCalculator.OrderPriceRequest(
            new BigDecimal(100), "DEFAULT", "standard"
        );
        
        // 构建业务上下文
        BizContext<OrderPriceCalculator.OrderPriceRequest> context = BizContext.builder()
            .tenant("DEFAULT")
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("standard")
            .data(request)
            .build();
        
        // 计算价格
        BigDecimal result = orderPriceCalculator.calculate(request);
        
        // 验证结果
        assertEquals(new BigDecimal(100), result);
    }
    
    @Test
    public void testVipPriceCalculation() {
        // 构建VIP场景请求
        OrderPriceCalculator.OrderPriceRequest request = new OrderPriceCalculator.OrderPriceRequest(
            new BigDecimal(100), "ALI", "vip"
        );
        
        // 构建业务上下文
        BizContext<OrderPriceCalculator.OrderPriceRequest> context = BizContext.builder()
            .tenant("ALI")
            .bizCode("ecommerce")
            .useCase("order")
            .scenario("vip")
            .data(request)
            .build();
        
        // 计算价格
        BigDecimal result = orderPriceCalculator.calculate(request);
        
        // 验证结果（9折）
        assertEquals(new BigDecimal(90), result);
    }
}
