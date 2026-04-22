
package com.bone.blueprint.adapter.web.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "Customer ID cannot be blank")
    private String customerId;

    @NotEmpty(message = "Order items cannot be empty")
    private List<OrderItem> items;

    @NotBlank(message = "Recipient name cannot be blank")
    private String recipientName;

    @NotBlank(message = "Phone cannot be blank")
    private String phone;

    @NotBlank(message = "Province cannot be blank")
    private String province;

    @NotBlank(message = "City cannot be blank")
    private String city;

    @NotBlank(message = "District cannot be blank")
    private String district;

    @NotBlank(message = "Detail address cannot be blank")
    private String detail;

    private String postalCode;
    private String remark;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        @NotBlank(message = "Product ID cannot be blank")
        private String productId;

        @NotBlank(message = "Product name cannot be blank")
        private String productName;

        @NotNull(message = "Price cannot be null")
        private BigDecimal price;

        @NotNull(message = "Quantity cannot be null")
        private Integer quantity;
    }
}

