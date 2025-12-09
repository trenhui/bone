package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 默认促销扩展实现
 * 当没有匹配到其他特定场景的促销扩展时，使用此默认实现
 */
@Extension(
        name = "默认促销实现",
        description = "当没有匹配到其他特定场景的促销扩展时，使用此默认实现",
        tenant = "default",
        bizCode = "ORDER",
        order = 999, // 设置较低优先级，确保在没有其他匹配时才会被调用
        enabled = true,
        version = "1.0.0"
)
public class DefaultPromotionExtension implements PromotionExtPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPromotionExtension.class);

    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        LOGGER.info("使用默认促销实现");
        
        PromotionRequest request = context.getData();
        BigDecimal subtotal = request.getSubtotal();
        
        // 默认不应用任何折扣
        return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(subtotal)
                .appliedPromotions(new ArrayList<>())
                .discountApplied(false)
                .build();
    }
}
