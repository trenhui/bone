package com.bone.example.extension.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 促销服务测试类
 */
class PromotionServiceTest {

    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        // 创建测试对象
        promotionService = new PromotionService();
    }

    /**
     * 基本功能测试
     */
    @Test
    void testBasicFunctionality() {
        // 创建简单的测试数据
        PromotionRequest request = new PromotionRequest();
        String tenantCode = "DEFAULT";
        
        // 执行测试
        PromotionResult result = promotionService.applyPromotion(request, tenantCode);
        
        // 验证结果不为空
        assertNotNull(result);
    }
}