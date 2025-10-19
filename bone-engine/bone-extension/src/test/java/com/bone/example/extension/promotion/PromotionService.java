package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 促销服务
 * 负责协调多种促销策略的应用，支持多种促销方式的组合使用
 */
@Slf4j
public class PromotionService {
    
    @Autowired
    private PromotionExtPoint promotionExtPoint;
    
    /**
     * 计算订单适用的促销
     * @param request 促销请求
     * @param tenantCode 租户代码
     * @return 最终的促销结果
     */
    public PromotionResult calculatePromotion(PromotionRequest request, String tenantCode) {
        // 创建业务上下文
        BizContext<PromotionRequest> context = createContext(request);
        context.setTenantCode(tenantCode);
        
        // 通过扩展点计算促销
        return promotionExtPoint.calculatePromotion(context);
    }
    
    /**
     * 创建业务上下文
     */
    private BizContext<PromotionRequest> createContext(PromotionRequest request) {
        BizContext<PromotionRequest> context = BizContext.create();
        context.setData(request);
        return context;
    }
    
    /**
     * 生成交易ID
     */
    private String generateTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
    }
}