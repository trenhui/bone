package com.bone.example.extension.promotion;

import com.bone.engine.extension.context.BizContext;

/**
 * 特定产品促销扩展点实现
 */
public class ProductSpecificPromotionExtension implements PromotionExtPoint {

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