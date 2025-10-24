package com.bone.example.extension.promotion;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;

/**
 * 促销策略扩展点
 * 定义了促销计算和促销适用性检查的核心方法
 */
// 运行时配置 - 提供扩展点基本信息和默认配置
@ExtPoint(
    name = "促销策略扩展点",
    description = "处理各类促销活动计算和适用性检查",
    domain = "营销",
    category = "促销计算",
    version = "1.0.0",
    enabled = true,
    priority = 100
)
// 接口文档 - 详细描述扩展点功能、参数和使用场景（编译时注解，不影响运行时）
@ExtPointDoc(
    title = "促销策略扩展点接口",
    domain = "营销",
    category = "促销计算",
    description = "定义了促销活动计算和适用性检查的标准接口，支持多种促销策略实现。",
    usage = "1. 实现接口并添加@Extension注解\n2. 根据促销类型配置路由条件\n3. 注入到服务层使用",
    bestPractices = "1. 确保实现的幂等性\n2. 性能敏感场景考虑缓存\n3. 区分不同促销类型的实现",
    params = {
        @ExtPointDoc.Param(
            name = "context",
            type = "BizContext<PromotionRequest>",
            description = "包含促销请求信息的业务上下文",
            required = true
        )
    },
    returnInfo = @ExtPointDoc.Return(
        type = "PromotionResult",
        description = "促销计算结果，包含优惠金额等信息",
        successExample = "PromotionResult.builder().discountAmount(100).discountType(\"PERCENT\").build()"
    ),
    creator = "测试团队",
    createDate = "2024-01-01",
    notes = "用于测试促销场景的扩展点实现"
)
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