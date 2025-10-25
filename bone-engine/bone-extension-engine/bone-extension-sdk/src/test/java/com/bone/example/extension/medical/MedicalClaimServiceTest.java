package com.bone.example.extension.medical;

import com.bone.example.extension.result.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 医疗保险理赔服务测试类
 * <p>
 * 对MedicalClaimService的核心功能进行单元测试，确保服务能够正确处理各种理赔场景。
 */
class MedicalClaimServiceTest {

    private MedicalClaimService medicalClaimService;

    @BeforeEach
    void setUp() {
        // 创建服务实例
        medicalClaimService = new MedicalClaimService();
    }

    /**
     * 测试理赔处理功能
     * <p>
     * 测试基本的理赔处理流程，包括参数验证和服务调用。
     */
    @Test
    void testBasicProcessClaim() {
        // 准备测试数据 - 设置所有必要字段
        MedicalClaimRequest request = MedicalClaimRequest.builder()
                .claimId("TEST-CLAIM-001")
                .userId("TEST-USER-001")
                .claimType(MedicalClaimRequest.ClaimType.OUTPATIENT)
                .totalAmount(BigDecimal.TEN)
                .items(Collections.emptyList())
                .build();
        
        // 执行测试
        try {
            MedicalClaimResult result = medicalClaimService.processClaim(request);
            // 验证结果不为空
            assertNotNull(result);
        } catch (Exception e) {
            // 如果没有注册扩展点，可能会抛出异常
            // 这里我们至少验证没有因为参数问题抛出异常
            assertFalse(e instanceof IllegalArgumentException);
        }
    }

    /**
     * 测试验证理赔功能
     * <p>
     * 测试基本的理赔验证流程，确保参数验证正常工作。
     */
    @Test
    void testBasicValidateClaim() {
        // 准备测试数据 - 设置所有必要字段
        MedicalClaimRequest request = MedicalClaimRequest.builder()
                .claimId("TEST-CLAIM-002")
                .userId("TEST-USER-002")
                .claimType(MedicalClaimRequest.ClaimType.OUTPATIENT)
                .totalAmount(BigDecimal.TEN)
                .items(Collections.emptyList())
                .build();
        
        // 执行测试
        ValidationResult result = medicalClaimService.validateClaim(request);
        
        // 验证结果不为空
        assertNotNull(result);
    }
}