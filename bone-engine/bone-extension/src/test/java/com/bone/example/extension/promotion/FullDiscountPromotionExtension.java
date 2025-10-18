package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 满减促销策略实现
 * 支持阶梯式满减规则
 */
@Extension(bizCode = "FULL_DISCOUNT_PROMOTION")
@Slf4j
public class FullDiscountPromotionExtension implements PromotionExtPoint {
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        PromotionRequest request = context.getData();
        BigDecimal subtotal = request.getSubtotal();
        
        // 获取适用的满减规则
        FullDiscountRule rule = getApplicableRule(subtotal);
        
        if (rule != null) {
            BigDecimal discount = rule.getDiscountAmount();
            
            PromotionResult.AppliedPromotion appliedPromotion = PromotionResult.AppliedPromotion.builder()
                .promotionId(rule.getId())
                .promotionName(rule.getName())
                .promotionType("FULL_DISCOUNT")
                .discountAmount(discount)
                .description("满" + rule.getThreshold() + "减" + discount)
                .build();
            
            List<PromotionResult.AppliedPromotion> promotions = new ArrayList<>();
            promotions.add(appliedPromotion);
            
            return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(subtotal.subtract(discount))
                .appliedPromotions(promotions)
                .discountApplied(true)
                .build();
        }
        
        // 无适用的满减规则
        return PromotionResult.builder()
            .originalTotal(subtotal)
            .finalTotal(subtotal)
            .appliedPromotions(new ArrayList<>())
            .discountApplied(false)
            .build();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        PromotionRequest request = context.getData();
        // 满减促销一般对所有订单开放，只要达到门槛
        return getApplicableRule(request.getSubtotal()) != null;
    }
    
    /**
     * 获取适用的满减规则
     */
    private FullDiscountRule getApplicableRule(BigDecimal amount) {
        // 这里模拟满减规则配置
        List<FullDiscountRule> rules = new ArrayList<>();
        rules.add(new FullDiscountRule("RULE_001", "满100减10", new BigDecimal("100.00"), new BigDecimal("10.00")));
        rules.add(new FullDiscountRule("RULE_002", "满200减30", new BigDecimal("200.00"), new BigDecimal("30.00")));
        rules.add(new FullDiscountRule("RULE_003", "满500减100", new BigDecimal("500.00"), new BigDecimal("100.00")));
        
        // 找到金额门槛最高且不超过当前金额的规则
        FullDiscountRule applicableRule = null;
        for (FullDiscountRule rule : rules) {
            if (amount.compareTo(rule.getThreshold()) >= 0) {
                if (applicableRule == null || rule.getThreshold().compareTo(applicableRule.getThreshold()) > 0) {
                    applicableRule = rule;
                }
            }
        }
        
        return applicableRule;
    }
    
    /**
     * 满减规则类
     */
    static class FullDiscountRule {
        private String id;
        private String name;
        private BigDecimal threshold;
        private BigDecimal discountAmount;
        
        public FullDiscountRule(String id, String name, BigDecimal threshold, BigDecimal discountAmount) {
            this.id = id;
            this.name = name;
            this.threshold = threshold;
            this.discountAmount = discountAmount;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public BigDecimal getThreshold() { return threshold; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
    }
}