package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.ExtensionContextManager;
import com.bone.engine.extension.ExtensionScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

/**
 * 促销服务
 * 负责协调多种促销策略的应用，支持多种促销方式的组合使用
 */
@Slf4j
public class PromotionService {
    
    @Autowired
    private PromotionExtPoint promotionExtPoint;
    
    // 用于测试的setter方法
    public void setPromotionExtPoint(PromotionExtPoint promotionExtPoint) {
        this.promotionExtPoint = promotionExtPoint;
    }
    
    /**
     * 计算订单适用的促销
     * @param request 促销请求
     * @param tenantCode 租户代码
     * @return 最终的促销结果
     */
    public PromotionResult calculatePromotion(PromotionRequest request, String tenantCode) {
        // 使用ExtensionContextManager创建上下文，支持try-with-resources模式
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("userId", request.getUserId())) {
            
            // 创建业务上下文
            BizContext<PromotionRequest> context = ExtensionContextManager.fromData(request);
            
            // 记录请求日志
            log.info("Calculating promotions for user: {}", request.getUserId());
            
            // 通过扩展点计算促销
            PromotionResult result = promotionExtPoint.calculatePromotion(context);
            
            // 如果结果为null，创建默认结果
            if (result == null) {
                result = new PromotionResult();
                result.setOriginalTotal(request.getSubtotal());
                result.setFinalTotal(request.getSubtotal());
                result.setDiscountApplied(false);
                result.setAppliedPromotions(new ArrayList<>());
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error calculating promotions for user: {}", request.getUserId(), e);
            // 创建错误结果
            PromotionResult errorResult = new PromotionResult();
            errorResult.setOriginalTotal(request != null ? request.getSubtotal() : BigDecimal.ZERO);
            errorResult.setFinalTotal(request != null ? request.getSubtotal() : BigDecimal.ZERO);
            errorResult.setDiscountApplied(false);
            
            // 添加错误促销信息
            List<PromotionResult.AppliedPromotion> errorPromotions = new ArrayList<>();
            PromotionResult.AppliedPromotion errorPromotion = new PromotionResult.AppliedPromotion();
            errorPromotion.setPromotionId("ERROR");
            errorPromotion.setPromotionName("促销计算失败");
            errorPromotion.setPromotionType("ERROR");
            errorPromotion.setDiscountAmount(BigDecimal.ZERO);
            errorPromotion.setDescription("促销计算过程中发生错误: PROMOTION_CALCULATION_ERROR");
            errorPromotions.add(errorPromotion);
            
            errorResult.setAppliedPromotions(errorPromotions);
            return errorResult;
        }
    }
}