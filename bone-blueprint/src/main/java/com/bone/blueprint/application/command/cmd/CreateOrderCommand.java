package com.bone.blueprint.application.command.cmd;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CreateOrderCommand {
    private Long customerId;
    private List<OrderItemDto> items;
    
    @Data
    @Builder
    public static class OrderItemDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
