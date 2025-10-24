package com.bone.example.extension.promotion;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.promotion.PromotionRequest;

/**
 * 满减促销扩展实现
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "满减促销实现",
    description = "处理订单满减促销活动的计算和适用性检查",
    tenantCode = "default",
    bizCode = "ORDER",
    scenario = "FULL_DISCOUNT_PROMOTION",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "满减促销活动的具体实现，根据订单金额和促销规则计算优惠金额。",
    scenarios = "适用于订单满减促销活动场景",
    implementationDetails = "基于订单总金额和预设的满减规则进行计算",
    differences = "专注于满减类型促销，与折扣和会员专享等其他促销类型有明显区别",
    notes = "测试使用的简化实现",
    author = "测试团队",
    createDate = "2024-01-01"
)
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