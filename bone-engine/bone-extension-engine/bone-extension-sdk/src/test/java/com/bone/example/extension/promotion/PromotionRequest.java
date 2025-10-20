package com.bone.example.extension.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 促销计算请求
 * 支持复杂SpEL条件表达式匹配和多维度促销策略
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
    private String orderType; // 订单类型，用于SpEL条件表达式匹配
    private UserInfo userInfo; // 用户信息，用于复杂条件匹配
    
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
    
    /**
     * 用户信息类，用于复杂SpEL条件表达式
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String memberLevel;
        private int memberPoints;
        private int orderCount;
        private String registrationDate;
    }
}