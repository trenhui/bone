package com.bone.blueprint.adapter.rpc.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * RPC 订单详情响应。
 *
 * <p><b>为什么 RPC 自持一份而不复用 {@code adapter/web} 的同名 DTO</b>：协议 DTO 是**各自适配器的契约**。让 {@code adapter/rpc}
 * 反向 import {@code adapter/web}，会让两个平级入站适配器相互耦合——web 面向人（可加展示字段）、RPC 面向服务（可加机器字段，如已有的 {@code
 * CreateOrderRpcResp#errorMsg}），演化节奏不同步。字段当前与 web 侧一致是**巧合**， 不是约束。
 */
@Data
@Builder
public class OrderDetailResp {
  private Long id;
  private Long customerId;
  private BigDecimal totalAmount;
  private String status;
  private LocalDateTime createdAt;
  private List<OrderItemResp> items;

  @Data
  @Builder
  public static class OrderItemResp {
    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
  }
}
