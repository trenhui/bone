package com.bone.example.extension.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 促销计算请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionRequest {
    private String userId;
    private List<OrderItem> items;
    private BigDecimal subtotal;
    private String userLevel;
    private String promotionCode;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private String category;
    }
}