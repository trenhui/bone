package com.bone.blueprint.adapter.rpc.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderRpcRequest {
    private String tenantId;
    private Long customerId;
    private List<OrderItemRpcRequest> items;
    
    @Data
    public static class OrderItemRpcRequest {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}