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
        // 处理空请求的边界情况
        if (request == null) {
            log.warn("Received null promotion request");
            return createNullRequestResult();
        }
        
        // 使用ExtensionContextManager创建上下文，支持try-with-resources模式
        try (ExtensionScope scope = ExtensionContextManager.withTenant(tenantCode)
                .withAttribute("userId", request.getUserId() != null ? request.getUserId() : "anonymous")) {
            
            // 创建业务上下文
            BizContext<PromotionRequest> context = ExtensionContextManager.fromData(request);
            
            // 记录请求日志
            String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
            log.info("Calculating promotions for user: {}", userId);
            
            // 通过扩展点计算促销
            PromotionResult result = promotionExtPoint.calculatePromotion(context);
            
            // 如果结果为null，创建默认结果
            if (result == null) {
                result = createDefaultResult(request);
            }
            
            return result;
        } catch (Exception e) {
            log.error("Error calculating promotions for user: {}", request.getUserId() != null ? request.getUserId() : "unknown", e);
            // 创建错误结果
            return createErrorResult(request, e.getMessage());
        }
    }
    
    private PromotionResult createNullRequestResult() {
        PromotionResult result = new PromotionResult();
        result.setOriginalTotal(BigDecimal.ZERO);
        result.setFinalTotal(BigDecimal.ZERO);
        result.setDiscountApplied(false);
        result.setAppliedPromotions(new ArrayList<>());
        return result;
    }
    
    private PromotionResult createDefaultResult(PromotionRequest request) {
        PromotionResult result = new PromotionResult();
        BigDecimal subtotal = request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO;
        result.setOriginalTotal(subtotal);
        result.setFinalTotal(subtotal);
        result.setDiscountApplied(false);
        result.setAppliedPromotions(new ArrayList<>());
        return result;
    }
    
    private PromotionResult createErrorResult(PromotionRequest request, String errorMessage) {
        PromotionResult result = new PromotionResult();
        BigDecimal subtotal = request != null && request.getSubtotal() != null ? request.getSubtotal() : BigDecimal.ZERO;
        result.setOriginalTotal(subtotal);
        result.setFinalTotal(subtotal);
        result.setDiscountApplied(false);
        
        List<PromotionResult.AppliedPromotion> errorPromotions = new ArrayList<>();
        PromotionResult.AppliedPromotion errorPromotion = new PromotionResult.AppliedPromotion();
        errorPromotion.setPromotionId("ERROR");
        errorPromotion.setPromotionName("促销计算失败");
        errorPromotion.setPromotionType("ERROR");
        errorPromotion.setDiscountAmount(BigDecimal.ZERO);
        errorPromotion.setDescription("促销计算过程中发生错误: " + (errorMessage != null ? errorMessage : "PROMOTION_CALCULATION_ERROR"));
        errorPromotions.add(errorPromotion);
        
        result.setAppliedPromotions(errorPromotions);
        return result;
    }
}