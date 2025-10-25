package com.bone.example.extension.promotion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销计算结果
 */
public class PromotionResult {
    private BigDecimal originalTotal;
    private BigDecimal finalTotal;
    private List<AppliedPromotion> appliedPromotions;
    private boolean discountApplied;
    
    /**
     * 获取原始总金额
     * @return 原始总金额
     */
    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }
    
    /**
     * 设置原始总金额
     * @param originalTotal 原始总金额
     */
    public void setOriginalTotal(BigDecimal originalTotal) {
        this.originalTotal = originalTotal;
    }
    
    /**
     * 获取最终总金额
     * @return 最终总金额
     */
    public BigDecimal getFinalTotal() {
        return finalTotal;
    }
    
    /**
     * 设置最终总金额
     * @param finalTotal 最终总金额
     */
    public void setFinalTotal(BigDecimal finalTotal) {
        this.finalTotal = finalTotal;
    }
    
    /**
     * 获取已应用的促销列表
     * @return 已应用的促销列表
     */
    public List<AppliedPromotion> getAppliedPromotions() {
        return appliedPromotions;
    }
    
    /**
     * 设置已应用的促销列表
     * @param appliedPromotions 已应用的促销列表
     */
    public void setAppliedPromotions(List<AppliedPromotion> appliedPromotions) {
        this.appliedPromotions = appliedPromotions;
    }
    
    /**
     * 是否应用了折扣
     * @return 是否应用了折扣
     */
    public boolean isDiscountApplied() {
        return discountApplied;
    }
    
    /**
     * 设置是否应用了折扣
     * @param discountApplied 是否应用了折扣
     */
    public void setDiscountApplied(boolean discountApplied) {
        this.discountApplied = discountApplied;
    }
    
    /**
     * 创建构建器实例
     * @return PromotionResult构建器
     */
    public static PromotionResultBuilder builder() {
        return new PromotionResultBuilder();
    }
    
    /**
     * PromotionResult构建器类
     */
    public static class PromotionResultBuilder {
        private BigDecimal originalTotal;
        private BigDecimal finalTotal;
        private List<AppliedPromotion> appliedPromotions = new ArrayList<>();
        private boolean discountApplied;
        
        public PromotionResultBuilder originalTotal(BigDecimal originalTotal) {
            this.originalTotal = originalTotal;
            return this;
        }
        
        public PromotionResultBuilder finalTotal(BigDecimal finalTotal) {
            this.finalTotal = finalTotal;
            return this;
        }
        
        public PromotionResultBuilder appliedPromotions(List<AppliedPromotion> appliedPromotions) {
            this.appliedPromotions = appliedPromotions;
            return this;
        }
        
        public PromotionResultBuilder addAppliedPromotion(AppliedPromotion appliedPromotion) {
            this.appliedPromotions.add(appliedPromotion);
            return this;
        }
        
        public PromotionResultBuilder discountApplied(boolean discountApplied) {
            this.discountApplied = discountApplied;
            return this;
        }
        
        public PromotionResult build() {
            PromotionResult result = new PromotionResult();
            result.originalTotal = this.originalTotal;
            result.finalTotal = this.finalTotal;
            result.appliedPromotions = this.appliedPromotions;
            result.discountApplied = this.discountApplied;
            return result;
        }
    }
    
    public static class AppliedPromotion {
        private String promotionId;
        private String promotionName;
        private String promotionType;
        private BigDecimal discountAmount;
        private String description;
        
        /**
         * 获取促销ID
         * @return 促销ID
         */
        public String getPromotionId() {
            return promotionId;
        }
        
        /**
         * 设置促销ID
         * @param promotionId 促销ID
         */
        public void setPromotionId(String promotionId) {
            this.promotionId = promotionId;
        }
        
        /**
         * 获取促销名称
         * @return 促销名称
         */
        public String getPromotionName() {
            return promotionName;
        }
        
        /**
         * 设置促销名称
         * @param promotionName 促销名称
         */
        public void setPromotionName(String promotionName) {
            this.promotionName = promotionName;
        }
        
        /**
         * 获取促销类型
         * @return 促销类型
         */
        public String getPromotionType() {
            return promotionType;
        }
        
        /**
         * 设置促销类型
         * @param promotionType 促销类型
         */
        public void setPromotionType(String promotionType) {
            this.promotionType = promotionType;
        }
        
        /**
         * 获取折扣金额
         * @return 折扣金额
         */
        public BigDecimal getDiscountAmount() {
            return discountAmount;
        }
        
        /**
         * 设置折扣金额
         * @param discountAmount 折扣金额
         */
        public void setDiscountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
        }
        
        /**
         * 获取促销描述
         * @return 促销描述
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * 设置促销描述
         * @param description 促销描述
         */
        public void setDescription(String description) {
            this.description = description;
        }
        
        /**
         * 创建构建器实例
         * @return AppliedPromotion构建器
         */
        public static AppliedPromotionBuilder builder() {
            return new AppliedPromotionBuilder();
        }
        
        /**
         * AppliedPromotion构建器类
         */
        public static class AppliedPromotionBuilder {
            private String promotionId;
            private String promotionName;
            private String promotionType;
            private BigDecimal discountAmount;
            private String description;
            
            public AppliedPromotionBuilder promotionId(String promotionId) {
                this.promotionId = promotionId;
                return this;
            }
            
            public AppliedPromotionBuilder promotionName(String promotionName) {
                this.promotionName = promotionName;
                return this;
            }
            
            public AppliedPromotionBuilder promotionType(String promotionType) {
                this.promotionType = promotionType;
                return this;
            }
            
            public AppliedPromotionBuilder discountAmount(BigDecimal discountAmount) {
                this.discountAmount = discountAmount;
                return this;
            }
            
            public AppliedPromotionBuilder description(String description) {
                this.description = description;
                return this;
            }
            
            public AppliedPromotion build() {
                AppliedPromotion promotion = new AppliedPromotion();
                promotion.promotionId = this.promotionId;
                promotion.promotionName = this.promotionName;
                promotion.promotionType = this.promotionType;
                promotion.discountAmount = this.discountAmount;
                promotion.description = this.description;
                return promotion;
            }
        }
    }
}