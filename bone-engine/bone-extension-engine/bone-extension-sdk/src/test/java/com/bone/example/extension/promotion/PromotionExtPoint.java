package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.support.context.BizContext;

/**
 * 促销策略扩展点
 * 定义了各类促销活动的标准接口，包括促销计算。
 */
@ExtensionPoint(
        name = "促销策略扩展点",
        description = "处理各类促销活动计算"
)
public interface PromotionExtPoint {

    /**
     * 计算促销优惠金额
     * 根据促销请求信息，应用相应的促销规则计算最终的优惠金额。
     *
     * @param context 业务上下文，包含促销请求信息
     * @return 促销计算结果，包含优惠金额、促销类型等信息
     */
    PromotionResult calculatePromotion(BizContext<PromotionRequest> context);
}