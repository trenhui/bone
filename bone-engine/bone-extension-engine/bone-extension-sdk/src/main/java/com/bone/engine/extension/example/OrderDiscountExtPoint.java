package com.bone.engine.extension.example;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.ExtPoint;

/**
 * 订单折扣扩展点接口
 * 用于定义订单折扣计算的扩展能力
 */
@ExtPoint(
    name = "订单折扣扩展点",
    description = "订单折扣扩展点，用于计算订单折扣金额"
)
public interface OrderDiscountExtPoint {
    
    /**
     * 计算订单折扣
     * @param context 业务上下文，包含订单信息
     * @return 折扣结果，包含折扣金额和折扣率
     */
    DiscountResult calculate(BizContext<Order> context);
}