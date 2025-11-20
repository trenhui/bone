package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.annotation.ExtensionPoint;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.api.annotation.ExtensionPointDoc;

/**
 * 促销策略扩展点
 * <p>
 * 定义了各类促销活动的标准接口，包括促销计算和促销适用性检查。
 * 提供了统一的促销策略实现规范，支持多种促销类型的灵活扩展。
 */
// 运行时配置 - 提供扩展点基本信息和默认配置
@ExtensionPoint(
    name = "促销策略扩展点",
    description = "处理各类促销活动计算和适用性检查"
)
// 接口文档 - 详细描述扩展点功能、参数和使用场景（编译时注解，不影响运行时）
@ExtensionPointDoc(
    title = "促销策略扩展点接口",
    domain = "营销",
    category = "促销计算",
    description = "定义了促销活动计算和适用性检查的标准接口，支持多种促销策略实现。",
    usage = "1. 实现接口并添加@Extension注解\n2. 根据促销类型配置路由条件\n3. 注入到服务层使用",
    bestPractices = "1. 确保实现的幂等性\n2. 性能敏感场景考虑缓存\n3. 区分不同促销类型的实现\n4. 使用BigDecimal进行金额计算以确保精度",
    params = {
        @ExtensionPointDoc.Param(
            name = "context",
            type = "BizContext<PromotionRequest>",
            description = "包含促销请求信息的业务上下文",
            required = true
        )
    },
    returnInfo = @ExtensionPointDoc.Return(
            type = "PromotionResult",
            description = "促销计算结果，包含优惠金额等信息",
            example = "PromotionResult.builder().discountAmount(BigDecimal.valueOf(100)).discountType(\"PERCENT\").build()"
        ),
    creator = "测试团队",
    createDate = "2024-01-01",
    notes = "用于测试促销场景的扩展点实现"
)
public interface PromotionExtPoint {
    
    /**
     * 计算促销优惠金额
     * <p>
     * 根据促销请求信息，应用相应的促销规则计算最终的优惠金额。
     * 实现类应根据具体的促销策略（如满减、折扣、赠品等）提供相应的计算逻辑。
     * 
     * @param context 业务上下文，包含促销请求信息，不能为空
     * @return 促销计算结果，包含优惠金额、促销类型等信息，非空
     */
    PromotionResult calculatePromotion(BizContext<PromotionRequest> context);
    
    /**
     * 检查促销策略是否适用
     * <p>
     * 根据促销请求信息，判断当前促销策略是否适用于该场景。
     * 例如：满减促销需要判断订单金额是否达到最低标准。
     * 
     * @param context 业务上下文，包含促销请求信息，不能为空
     * @return 如果促销策略适用返回true，否则返回false
     */
    boolean isApplicable(BizContext<PromotionRequest> context);
}