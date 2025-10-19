package com.bone.example.extension.promotion;

import com.bone.engine.extension.BizContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 促销服务测试类
 */
class PromotionServiceTest {

    private PromotionService promotionService;
    private PromotionExtPoint promotionExtPoint;

    @BeforeEach
    void setUp() {
        // 创建mock对象
        promotionExtPoint = Mockito.mock(PromotionExtPoint.class);
        promotionService = new PromotionService();
        
        // 注入mock对象
        promotionService.setPromotionExtPoint(promotionExtPoint);
    }

    /**
     * 测试成功的促销计算
     */
    @Test
    void testCalculatePromotionSuccess() {
        // 准备测试数据
        PromotionRequest request = new PromotionRequest();
        request.setUserId("user123");
        String tenantCode = "DEFAULT";
        
        // 配置mock行为
        PromotionResult expectedResult = new PromotionResult();
        expectedResult.setOriginalTotal(new BigDecimal("200.0"));
        expectedResult.setFinalTotal(new BigDecimal("180.0"));
        expectedResult.setDiscountApplied(true);
        
        // 创建应用的促销列表
        List<PromotionResult.AppliedPromotion> appliedPromotions = new ArrayList<>();
        PromotionResult.AppliedPromotion promo = new PromotionResult.AppliedPromotion();
        promo.setPromotionId("PROMO001");
        promo.setPromotionName("满减优惠");
        promo.setDiscountAmount(new BigDecimal("20.0"));
        appliedPromotions.add(promo);
        expectedResult.setAppliedPromotions(appliedPromotions);
        
        when(promotionExtPoint.calculatePromotion(any(BizContext.class))).thenReturn(expectedResult);
        
        // 执行测试
        PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(new BigDecimal("180.0"), result.getFinalTotal());
        assertTrue(result.isDiscountApplied());
        assertNotNull(result.getAppliedPromotions());
        assertEquals(1, result.getAppliedPromotions().size());
        assertEquals(new BigDecimal("20.0"), result.getAppliedPromotions().get(0).getDiscountAmount());
    }

    /**
     * 测试返回null时的促销计算
     */
    @Test
    void testCalculatePromotionWithNullReturn() {
        // 准备测试数据
        PromotionRequest request = new PromotionRequest();
        request.setUserId("user123");
        String tenantCode = "DEFAULT";
        
        // 配置mock行为 - 返回null
        when(promotionExtPoint.calculatePromotion(any(BizContext.class))).thenReturn(null);
        
        // 执行测试
        PromotionResult result = promotionService.calculatePromotion(request, tenantCode);
        
        // 验证结果 - 应该创建一个新的空结果对象
        assertNotNull(result);
    }
}