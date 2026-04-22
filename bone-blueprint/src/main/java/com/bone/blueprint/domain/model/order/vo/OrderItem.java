
package com.bone.blueprint.domain.model.order.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderItem {

    private final String productId;
    private final String productName;
    private final BigDecimal price;
    private final Integer quantity;
    private final BigDecimal subtotal;

    public static OrderItem of(String productId, String productName, BigDecimal price, Integer quantity) {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be null or blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) &lt;= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity == null || quantity &lt;= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal subtotal = price.multiply(new BigDecimal(quantity));
        return new OrderItem(productId, productName, price, quantity, subtotal);
    }
}

