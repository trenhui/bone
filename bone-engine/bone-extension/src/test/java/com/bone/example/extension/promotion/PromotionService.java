package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import com.bone.engine.extension.ExtensionExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 促销服务
 * 负责协调多种促销策略的应用，支持多种促销方式的组合使用
 */
@Slf4j
public class PromotionService {
    
    @Autowired
    private ExtensionExecutor extensionExecutor;
    
    /**
     * 计算订单适用的促销
     * @param request 促销请求
     * @param tenantCode 租户代码
     * @return 最终的促销结果
     */
    public PromotionResult calculatePromotion(PromotionRequest request, String tenantCode) {
        try {
            // 创建并设置业务上下文
            try (BizContexts.ContextManager manager = BizContexts.use()) {
                manager.setTenantCode(tenantCode);
                manager.setBizDomain("PROMOTION_SERVICE");
                
                // 收集所有适用的促销策略计算结果
                List<PromotionResult> applicableResults = new ArrayList<>();
                
                // 执行所有适用的促销扩展点
                extensionExecutor.executeMulti(PromotionExtPoint.class, extPoint -> {
                    // 检查促销策略是否适用
                    if (extPoint.isApplicable(BizContexts.getContext(request))) {
                        try {
                            PromotionResult result = extPoint.calculatePromotion(BizContexts.getContext(request));
                            if (result.isDiscountApplied()) {
                                applicableResults.add(result);
                            }
                        } catch (Exception e) {
                            log.error("Error calculating promotion for {}", extPoint.getClass().getSimpleName(), e);
                            // 单个促销计算失败不影响整体流程
                        }
                    }
                });
                
                // 合并促销结果（根据业务规则决定如何组合多个促销）
                return mergePromotionResults(request.getSubtotal(), applicableResults);
            }
        } catch (Exception e) {
            log.error("Promotion calculation failed for tenant {}", tenantCode, e);
            // 返回原始金额，确保促销计算失败不影响主流程
            return PromotionResult.builder()
                .originalTotal(request.getSubtotal())
                .finalTotal(request.getSubtotal())
                .appliedPromotions(new ArrayList<>())
                .discountApplied(false)
                .build();
        }
    }
    
    /**
     * 合并多个促销结果
     * 在实际业务中，这里可能会有不同的规则，比如：
     * 1. 取最大折扣
     * 2. 叠加折扣（需要注意规则冲突）
     * 3. 按优先级应用
     */
    private PromotionResult mergePromotionResults(BigDecimal originalTotal, List<PromotionResult> results) {
        if (results.isEmpty()) {
            return PromotionResult.builder()
                .originalTotal(originalTotal)
                .finalTotal(originalTotal)
                .appliedPromotions(new ArrayList<>())
                .discountApplied(false)
                .build();
        }
        
        // 这里简化处理，取所有促销中的最大折扣
        // 实际业务中可能需要更复杂的规则，比如哪些促销可以叠加
        BigDecimal maxDiscount = BigDecimal.ZERO;
        List<PromotionResult.AppliedPromotion> allAppliedPromotions = new ArrayList<>();
        
        for (PromotionResult result : results) {
            allAppliedPromotions.addAll(result.getAppliedPromotions());
            
            BigDecimal discount = result.getOriginalTotal().subtract(result.getFinalTotal());
            if (discount.compareTo(maxDiscount) > 0) {
                maxDiscount = discount;
            }
        }
        
        // 为了演示，这里仅返回最大折扣的结果
        // 在实际业务中，可能需要根据业务规则决定如何组合多个促销
        return PromotionResult.builder()
            .originalTotal(originalTotal)
            .finalTotal(originalTotal.subtract(maxDiscount))
            .appliedPromotions(allAppliedPromotions)
            .discountApplied(!allAppliedPromotions.isEmpty())
            .build();
    }
}