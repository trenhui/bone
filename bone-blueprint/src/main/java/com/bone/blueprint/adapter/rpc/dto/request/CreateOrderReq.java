package com.bone.blueprint.adapter.rpc.dto.request;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * RPC 创建订单请求。协议由包路径声明（{@code adapter/rpc/dto/request/}），类名只表达业务语义（E-13.0）—— 与 web 侧同名 DTO 并存合法：DTO
 * 不注册为 Spring bean，同名不产生容器冲突。
 */
@Data
public class CreateOrderReq {
  private String tenantId;
  private Long customerId;
  private List<OrderItemReq> items;

  @Data
  public static class OrderItemReq {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
  }
}
