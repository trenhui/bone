package com.bone.blueprint.infrastructure.gateway.feign;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import org.springframework.stereotype.Component;

@Component
public class InventoryFeignGatewayImpl implements InventoryGateway {

    @Override
    public boolean checkStock(Long productId, Integer quantity) {
        return productId != null && quantity != null && quantity > 0;
    }

    @Override
    public void reserveStock(Long orderId, Long productId, Integer quantity) {
        // Feign 调用库存服务预留接口（示范占位）
    }

    @Override
    public void confirmStock(Long orderId, Long productId, Integer quantity) {
        // Feign 调用库存服务确认扣减接口（示范占位）
    }

    @Override
    public void releaseStock(Long orderId) {
        // Feign 调用库存服务释放预留接口（示范占位）
    }
}
