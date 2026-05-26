package com.bone.blueprint.application.command.handler;

import com.bone.blueprint.application.command.cmd.CheckStockCommand;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
        name = "CheckStock",
        description = "检查商品库存是否充足",
        inputSchema = "{\"items\": [{\"productId\": \"long\", \"quantity\": \"int\"}]}",
        outputSchema = "{\"success\": \"boolean\"}",
        idempotent = true,
        cost = 2,
        retryable = true,
        timeout = 10)
@Component
@RequiredArgsConstructor
public class CheckStockCommandHandler {

    private final InventoryGateway inventoryGateway;

    @Transactional(readOnly = true)
    public void handle(CheckStockCommand command) {
        for (CheckStockCommand.Item item : command.getItems()) {
            if (!inventoryGateway.checkStock(item.getProductId(), item.getQuantity())) {
                throw BizException.of("商品库存不足: " + item.getProductId());
            }
        }
    }
}
