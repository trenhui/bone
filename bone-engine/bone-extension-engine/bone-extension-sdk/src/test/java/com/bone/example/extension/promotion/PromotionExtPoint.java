package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtPoint;

/**
 * 促销策略扩展点
 * 定义了促销计算和促销适用性检查的核心方法
 */
@ExtPoint
public interface PromotionExtPoint {
    
    /**
     * 计算促销优惠金额
     * @param context 业务上下文，包含促销请求信息
     * @return 促销计算结果
     */
    PromotionResult calculatePromotion(BizContext<PromotionRequest> context);
    
    /**
     * 检查促销策略是否适用
     * @param context 业务上下文
     * @return 是否适用
     */
    boolean isApplicable(BizContext<PromotionRequest> context);
}