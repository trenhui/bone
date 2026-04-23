package com.bone.blueprint.infrastructure.gateway;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import org.springframework.stereotype.Component;

@Component
public class InventoryGatewayImpl implements InventoryGateway {
    @Override
    public boolean checkStock(Long productId, Integer quantity) {
        return true;
    }
}
