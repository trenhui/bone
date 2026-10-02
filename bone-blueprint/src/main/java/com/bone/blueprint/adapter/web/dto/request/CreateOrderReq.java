package com.bone.blueprint.adapter.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class CreateOrderReq {
  @NotNull(message = "客户ID不能为空")
  private Long customerId;

  @NotEmpty(message = "订单项不能为空")
  @Valid
  private List<OrderItemReq> items;

  /** 订单来源渠道码（来自字典 source_channel：WEB / APP / MINI）。 可空：未知渠道允许下单；非空时由应用层校验是否命中字典已启用选项。 */
  private String channelSource;

  @Data
  public static class OrderItemReq {
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "商品名称不能为空")
    private String productName;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于0")
    private Integer quantity;

    @NotNull(message = "单价不能为空")
    @Positive(message = "单价必须大于0")
    private BigDecimal unitPrice;
  }
}
