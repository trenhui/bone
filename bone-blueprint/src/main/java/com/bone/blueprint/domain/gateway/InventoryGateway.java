package com.bone.blueprint.domain.gateway;

public interface InventoryGateway {
    boolean checkStock(Long productId, Integer quantity);
}
