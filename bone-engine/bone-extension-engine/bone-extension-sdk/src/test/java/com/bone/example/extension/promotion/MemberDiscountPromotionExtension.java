package com.bone.example.extension.promotion;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.annotation.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;

/**
 * 会员折扣促销策略实现
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "会员折扣促销实现",
    description = "根据会员等级提供相应折扣的促销实现",
    tenantCode = "default",
    bizCode = "ORDER",
    scenario = "MEMBER_DISCOUNT_PROMOTION",
    condition = "#data.userInfo.memberLevel != null",
    priority = 120,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "基于会员等级的折扣促销实现，为不同等级会员提供差异化优惠。",
    scenarios = "适用于会员专享折扣场景",
    implementationDetails = "根据用户会员等级动态计算折扣比例，等级越高折扣越大",
    differences = "与满减促销不同，此实现基于用户属性而非订单金额",
    notes = "测试使用的简化实现，仅检查会员等级是否存在",
    author = "测试团队",
    createDate = "2024-01-01"
)
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