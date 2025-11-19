package com.bone.example.extension.promotion;

/**
 * 促销服务
 * 负责协调促销活动的处理流程
 */
public class PromotionService {
    
    /**
     * 应用促销活动
     * @param request 促销请求
     * @param tenantCode 租户代码
     * @return 促销应用结果
     */
    public PromotionResult applyPromotion(PromotionRequest request, String tenantCode) {
        // 简化实现，直接返回空结果
        return new PromotionResult();
    }
}