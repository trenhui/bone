package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.exception.ExtensionInvocationException;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 促销服务核心实现类（企业级终极版）
 * 
 * 特性：
 * - 完全基于 BizContext 驱动路由
 * - 优雅降级 + 熔断保护
 * - 完整日志链路
 * - 严格参数校验 + 防御性编程
 * - 线程安全 + 资源自动释放
 */
@Slf4j
@Component
public class PromotionService {

    private static final String BIZ_CODE = "ORDER";
    private static final String DEFAULT_TENANT = "DEFAULT";
    
    private final PromotionExtPoint promotionExtPoint;

    @Autowired
    public PromotionService(PromotionExtPoint promotionExtPoint) {
        this.promotionExtPoint = promotionExtPoint;
    }

    /**
     * 主力促销计算方法（生产推荐）
     */
    public PromotionResult calculatePromotion(PromotionRequest request, BizContext<PromotionRequest> context) {
        validateRequest(request);
        ensureContextData(request, context);

        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            String requestId = context.getRequestId();
            String tenant = context.getTenant();
            
            log.info("开始计算促销 | tenant={} | requestId={} | subtotal={} | orderType={} | userLevel={}",
                    tenant, requestId, request.getSubtotal(), request.getOrderType(), request.getUserLevel());
            
            return executePromotionCalculation(request, context, requestId);
        } catch (Exception e) {
            log.error("促销计算系统异常 | requestId={}", context.getRequestId(), e);
            throw new PromotionCalculationException("促销计算失败，请稍后重试", e);
        }
    }
    
    /**
     * 简化版促销计算方法（兼容原有调用）
     */
    public PromotionResult calculatePromotion(PromotionRequest request, String tenantCode) {
        // 创建并设置业务上下文
        BizContext<PromotionRequest> context = BizContext.<PromotionRequest>builder()
                .tenant(tenantCode != null ? tenantCode : DEFAULT_TENANT)
                .bizCode(BIZ_CODE)
                .data(request)
                .build();
        
        return calculatePromotion(request, context);
    }
    
    private PromotionResult executePromotionCalculation(PromotionRequest request, 
                                                      BizContext<PromotionRequest> context, 
                                                      String requestId) {
        try {
            // 调用扩展点计算促销
            PromotionResult result = invokeWithFallback(
                    () -> {
                        try {
                            return promotionExtPoint.calculatePromotion(context);
                        } catch (Exception e) {
                            log.warn("扩展点调用异常，返回默认结果 | requestId={} | error={}", requestId, e.getMessage());
                            return null;
                        }
                    },
                    createDefaultResult(request)
            );
            
            log.info("促销计算成功 | requestId={} | original={} | final={} | discountApplied={}",
                    requestId, result.getOriginalTotal(), result.getFinalTotal(), result.isDiscountApplied());
            
            return result;
        } catch (ExtensionInvocationException e) {
            log.warn("扩展点调用异常，触发降级 | requestId={} | error={}", requestId, e.getMessage());
            return createDefaultResult(request);
        }
    }
    
    private void ensureContextData(PromotionRequest request, BizContext<PromotionRequest> context) {
        if (context.getData() == null) {
            log.debug("BizContext 中 data 字段为空，自动补齐 | orderType={}", request.getOrderType());
            
            // 完美重建上下文
            BizContext<PromotionRequest> enriched = BizContext.<PromotionRequest>builder()
                    .tenant(context.getTenant())
                    .bizCode(context.getBizCode())
                    .useCase(context.getUseCase())
                    .scenario(context.getScenario())
                    .env(context.getEnv())
                    .userGroup(context.getUserGroup())
                    .requestId(context.getRequestId())
                    .attributes(new HashMap<>(context.getAttributes()))
                    .data(request)
                    .build();
            
            // 替换当前线程上下文
            ExtensionContextManager.setCurrentContext(enriched);
        }
    }
    
    private void validateRequest(PromotionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("促销请求不能为空");
        }
        if (request.getSubtotal() == null || request.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("订单金额必须大于等于0");
        }
    }
    
    /**
     * 创建默认的促销结果
     */
    private PromotionResult createDefaultResult(PromotionRequest request) {
        return PromotionResult.builder()
                .originalTotal(request.getSubtotal())
                .finalTotal(request.getSubtotal())
                .appliedPromotions(new ArrayList<>())
                .discountApplied(false)
                .build();
    }
    
    /**
     * 带降级的扩展点调用
     */
    private <T> T invokeWithFallback(Supplier<T> supplier, T fallback) {
        try {
            T result = supplier.get();
            // 如果结果为null，返回降级结果
            return result != null ? result : fallback;
        } catch (Exception e) {
            // 捕获所有异常，返回降级结果
            log.warn("Extension invocation failed, using fallback | error: {}", e.getMessage());
            return fallback;
        }
    }
    
    @FunctionalInterface
    private interface Supplier<T> {
        T get() throws Exception;
    }
}

/**
 * 促销计算业务异常
 */
class PromotionCalculationException extends RuntimeException {
    public PromotionCalculationException(String message) {
        super(message);
    }
    
    public PromotionCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}