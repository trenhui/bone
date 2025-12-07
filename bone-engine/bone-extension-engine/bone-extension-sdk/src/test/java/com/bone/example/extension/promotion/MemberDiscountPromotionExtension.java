package com.bone.example.extension.promotion;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.api.annotation.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员折扣促销策略实现
 */
@Extension(
    name = "会员折扣促销实现",
    description = "根据会员等级提供相应折扣的促销实现",
    tenant = "default",
    bizCode = "ORDER",
    scenario = "MEMBER_DISCOUNT_PROMOTION",
    condition = "#data.userLevel == 'VIP' || #data.userLevel == 'GOLD' || #data.userLevel == 'DIAMOND'",
    order = 120,
    enabled = true,
    version = "1.0.0"
)
public class MemberDiscountPromotionExtension implements PromotionExtPoint {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MemberDiscountPromotionExtension.class);
    
    // 会员等级折扣规则
    private static final Map<String, BigDecimal> MEMBER_DISCOUNT_MAP = new HashMap<>();
    
    static {
        MEMBER_DISCOUNT_MAP.put("VIP", BigDecimal.valueOf(0.9)); // VIP会员享受9折
        MEMBER_DISCOUNT_MAP.put("GOLD", BigDecimal.valueOf(0.85)); // 黄金会员享受8.5折
        MEMBER_DISCOUNT_MAP.put("DIAMOND", BigDecimal.valueOf(0.8)); // 钻石会员享受8折
    }
    
    // 常量定义
    private static final String PROMOTION_ID = "MEMBER_DISCOUNT_001";
    private static final String PROMOTION_NAME = "会员折扣促销";
    private static final String PROMOTION_TYPE = "MEMBER_DISCOUNT";
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        LOGGER.info("开始计算会员折扣促销优惠");
        
        PromotionRequest request = context.getData();
        BigDecimal subtotal = request.getSubtotal();
        
        // 获取会员等级
        String memberLevel = request.getUserLevel();
        
        // 计算折扣金额
        BigDecimal discountRate = MEMBER_DISCOUNT_MAP.getOrDefault(memberLevel, BigDecimal.ONE);
        BigDecimal finalAmount = subtotal.multiply(discountRate);
        BigDecimal discountAmount = subtotal.subtract(finalAmount);
        
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 没有会员折扣，返回原价
            LOGGER.info("未达到会员折扣条件，不应用会员折扣");
            return PromotionResult.builder()
                    .originalTotal(subtotal)
                    .finalTotal(subtotal)
                    .appliedPromotions(new ArrayList<>())
                    .discountApplied(false)
                    .build();
        }
        
        // 创建应用的促销信息
        PromotionResult.AppliedPromotion appliedPromotion = PromotionResult.AppliedPromotion.builder()
                .promotionId(PROMOTION_ID)
                .promotionName(PROMOTION_NAME)
                .promotionType(PROMOTION_TYPE)
                .discountAmount(discountAmount)
                .description(String.format("会员等级折扣：%s享%s折", memberLevel, discountRate.multiply(BigDecimal.valueOf(10))))
                .build();
        
        // 创建最终促销结果
        return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(finalAmount)
                .addAppliedPromotion(appliedPromotion)
                .discountApplied(true)
                .build();
    }
}