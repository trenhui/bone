package com.bone.example.extension.promotion;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;

/**
 * 会员折扣促销策略实现
 */
@Extension(condition = "#data.userInfo.memberLevel != null")
public class MemberDiscountPromotionExtension implements PromotionExtPoint {
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        // 返回空的PromotionResult对象
        return new PromotionResult();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        // 简化实现，返回false
        return false;
    }
}