package com.bone.blueprint.infrastructure.gateway.feign;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import org.springframework.stereotype.Component;

@Component
public class InventoryFeignGatewayImpl implements InventoryGateway {
    
    @Override
    public boolean checkStock(Long productId, Integer quantity) {
        // 实际应该调用库存服务的 Feign 接口
        // 简化实现，模拟库存充足
        return true;
    }
    
    @Override
    public void deductStock(Long productId, Integer quantity) {
        // 实际应该调用库存服务的 Feign 接口
        // 简化实现，模拟扣减库存
    }

    @Override
    public void releaseStock(Long orderId) {
        // 实际应该调用库存服务的 Feign 接口
        // 简化实现，模拟释放库存
    }
}