package com.bone.blueprint.adapter.rpc.dto.request;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class CreateOrderRpcReq {
  private String tenantId;
  private Long customerId;
  private List<OrderItemRpcReq> items;

  @Data
  public static class OrderItemRpcReq {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
  }
}
