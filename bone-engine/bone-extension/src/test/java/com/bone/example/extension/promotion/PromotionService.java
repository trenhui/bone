package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

/**
 * 促销服务
 * 负责协调多种促销策略的应用，支持多种促销方式的组合使用
 */
@Slf4j
public class PromotionService {
    
    // 常量定义
    private static final String TRANSACTION_PREFIX = "TXN";
    private static final int TRANSACTION_ID_LENGTH = 16;
    
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
        context.setBizCode(tenantCode);
       // 记录请求日志
        log.info("Calculating promotions for user: {}", request.getUserId());
        
        // 通过扩展点计算促销
        PromotionResult result = promotionExtPoint.calculatePromotion(context);
        
        // 结果不需要设置交易ID（该方法不存在）
        if (result == null) {
            result = new PromotionResult();
        }
        
        return result;
    }
    
    /**
     * 生成交易ID
     */
    private String generateTransactionId() {
        return TRANSACTION_PREFIX + UUID.randomUUID().toString().replaceAll("-", "").substring(0, TRANSACTION_ID_LENGTH);
    }
    
    /**
     * 创建业务上下文
     */
    private BizContext<PromotionRequest> createContext(PromotionRequest request) {
        BizContext<PromotionRequest> context = BizContext.create();
        context.setData(request);
        return context;
    }
}