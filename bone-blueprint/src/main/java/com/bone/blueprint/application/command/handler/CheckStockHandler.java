package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.core.capability.Capability;
import com.bone.core.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Capability(
    name = "CheckStock",
    description = "检查商品库存是否充足",
    inputSchema = "{\"items\": [{\"productId\": \"long\", \"quantity\": \"int\"}]}",
    outputSchema = "{\"success\": \"boolean\"}",
    idempotent = true,
    cost = 2,
    retryable = true,
    timeout = 10
)
@Component
@RequiredArgsConstructor
public class CheckStockHandler {
    private final InventoryGateway inventoryGateway;
    
    public void handle(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (!inventoryGateway.checkStock(item.getProductId(), item.getQuantity())) {
                throw new DomainException("商品库存不足: " + item.getProductId());
            }
        }
    }
}