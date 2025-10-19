package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 特定商品促销策略实现
 * 针对特定类别或特定商品提供促销折扣
 */
@Extension(bizCode = "PRODUCT_SPECIFIC")
@Slf4j
public class ProductSpecificPromotionExtension implements PromotionExtPoint {
    
    // 商品类别与折扣比例映射
    private final Map<String, BigDecimal> categoryDiscountMap = new HashMap<>();
    // 特定商品与折扣比例映射
    private final Map<String, BigDecimal> productDiscountMap = new HashMap<>();
    
    public ProductSpecificPromotionExtension() {
        // 初始化商品类别折扣配置
        categoryDiscountMap.put("ELECTRONICS", new BigDecimal("0.9"));  // 电子产品9折
        categoryDiscountMap.put("CLOTHING", new BigDecimal("0.85"));    // 服装85折
        
        // 初始化特定商品折扣配置
        productDiscountMap.put("PROD001", new BigDecimal("0.7"));       // 特定商品7折
        productDiscountMap.put("PROD002", new BigDecimal("0.6"));       // 特定商品6折
    }
    
    @Override
    public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
        PromotionRequest request = context.getData();
        
        List<PromotionResult.AppliedPromotion> appliedPromotions = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        
        // 计算每个商品的折扣
        for (PromotionRequest.OrderItem item : request.getItems()) {
            BigDecimal discount = calculateItemDiscount(item);
            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discount);
                
                String discountType = productDiscountMap.containsKey(item.getProductId()) ? "特定商品" : "类别";
                PromotionResult.AppliedPromotion promotion = PromotionResult.AppliedPromotion.builder()
                    .promotionId(discountType + "_" + (productDiscountMap.containsKey(item.getProductId()) ? 
                                                           item.getProductId() : item.getCategory()))
                    .promotionName(discountType + "促销")
                    .promotionType("PRODUCT_SPECIFIC")
                    .discountAmount(discount)
                    .description(item.getProductName() + "享受特定折扣")
                    .build();
                
                appliedPromotions.add(promotion);
            }
        }
        
        return PromotionResult.builder()
            .originalTotal(request.getSubtotal())
            .finalTotal(request.getSubtotal().subtract(totalDiscount))
            .appliedPromotions(appliedPromotions)
            .discountApplied(!appliedPromotions.isEmpty())
            .build();
    }
    
    @Override
    public boolean isApplicable(BizContext<PromotionRequest> context) {
        PromotionRequest request = context.getData();
        // 检查是否有适用特定商品或类别的商品
        for (PromotionRequest.OrderItem item : request.getItems()) {
            if (productDiscountMap.containsKey(item.getProductId()) || 
                (item.getCategory() != null && categoryDiscountMap.containsKey(item.getCategory()))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 计算单个商品的折扣金额
     */
    private BigDecimal calculateItemDiscount(PromotionRequest.OrderItem item) {
        // 先检查是否有特定商品折扣（优先级高）
        if (productDiscountMap.containsKey(item.getProductId())) {
            BigDecimal discountRate = productDiscountMap.get(item.getProductId());
            BigDecimal originalPrice = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return originalPrice.multiply(BigDecimal.ONE.subtract(discountRate));
        }
        
        // 再检查是否有类别折扣
        if (item.getCategory() != null && categoryDiscountMap.containsKey(item.getCategory())) {
            BigDecimal discountRate = categoryDiscountMap.get(item.getCategory());
            BigDecimal originalPrice = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return originalPrice.multiply(BigDecimal.ONE.subtract(discountRate));
        }
        
        return BigDecimal.ZERO;
    }
}