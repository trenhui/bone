package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员折扣促销策略实现
 * 根据用户会员等级提供不同的折扣
 */
@Extension(bizCode = "MEMBER_DISCOUNT")
@Slf4j
public class MemberDiscountPromotionExtension implements PromotionExtPoint {
    
    // 会员等级与折扣比例映射
    private final Map<String, BigDecimal> memberDiscountMap = new HashMap<>();
    
    public MemberDiscountPromotionExtension() {
        // 初始化会员等级折扣配置
        memberDiscountMap.put("GOLD", new BigDecimal("0.9"));     // 黄金会员9折
        memberDiscountMap.put("PLATINUM", new BigDecimal("0.85"));  // 铂金会员85折
        memberDiscountMap.put("DIAMOND", new BigDecimal("0.8"));    // 钻石会员8折
    }
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        PromotionRequest request = context.getData();
        BigDecimal subtotal = request.getSubtotal();
        String userLevel = request.getUserLevel();
        
        if (userLevel != null && memberDiscountMap.containsKey(userLevel)) {
            BigDecimal discountRate = memberDiscountMap.get(userLevel);
            BigDecimal discountedAmount = subtotal.multiply(discountRate);
            BigDecimal discountValue = subtotal.subtract(discountedAmount);
            
            PromotionResult.AppliedPromotion appliedPromotion = PromotionResult.AppliedPromotion.builder()
                .promotionId("MEMBER_DISCOUNT_" + userLevel)
                .promotionName(userLevel + "会员专属折扣")
                .promotionType("MEMBER_DISCOUNT")
                .discountAmount(discountValue)
                .description(userLevel + "会员享受" + (100 - discountRate.multiply(new BigDecimal("100")).intValue()) + "%折扣")
                .build();
            
            List<PromotionResult.AppliedPromotion> promotions = new ArrayList<>();
            promotions.add(appliedPromotion);
            
            return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(discountedAmount)
                .appliedPromotions(promotions)
                .discountApplied(true)
                .build();
        }
        
        // 非会员或不在折扣等级内
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
        // 只要用户有会员等级且在折扣配置中，就适用
        return request.getUserLevel() != null && memberDiscountMap.containsKey(request.getUserLevel());
    }
}