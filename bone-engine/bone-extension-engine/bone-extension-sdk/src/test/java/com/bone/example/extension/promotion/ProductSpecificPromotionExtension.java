package com.bone.example.extension.promotion;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionDoc;

/**
 * 特定产品促销扩展点实现
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "特定产品促销实现",
    description = "针对特定产品或商品类别的促销实现",
    tenantCode = "default",
    bizCode = "ORDER",
    scenario = "PRODUCT_SPECIFIC_PROMOTION",
    condition = "#data.products != null && !#data.products.isEmpty()",
    priority = 110,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "针对特定产品或商品类别的促销实现，支持商品级别的促销策略。",
    scenarios = "适用于指定商品促销活动场景",
    implementationDetails = "检查订单中的产品列表，应用特定产品的促销规则",
    differences = "专注于产品维度的促销，与订单级和用户级促销形成互补",
    notes = "测试使用的简化实现",
    author = "测试团队",
    createDate = "2024-01-01"
)
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