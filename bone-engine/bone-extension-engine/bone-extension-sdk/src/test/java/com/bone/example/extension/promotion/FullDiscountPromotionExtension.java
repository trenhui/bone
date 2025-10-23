package com.bone.example.extension.promotion;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.promotion.PromotionRequest;

/**
 * 满减促销扩展实现
 */
@Extension(bizCode = "ORDER", scenario = "FULL_DISCOUNT_PROMOTION")
public class FullDiscountPromotionExtension implements PromotionExtPoint {

    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        // 简化实现，返回null
        return null;
    }

    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        // 简化实现，返回false
        return false;
    }
}