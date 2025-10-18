package com.bone.example.extension.promotion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * 促销计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResult {
    private BigDecimal originalTotal;
    private BigDecimal finalTotal;
    private List<AppliedPromotion> appliedPromotions;
    private boolean discountApplied;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppliedPromotion {
        private String promotionId;
        private String promotionName;
        private String promotionType;
        private BigDecimal discountAmount;
        private String description;
    }
}