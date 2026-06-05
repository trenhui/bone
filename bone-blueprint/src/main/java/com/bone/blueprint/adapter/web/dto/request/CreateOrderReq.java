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
