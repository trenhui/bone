package com.bone.example.extension.medical;

import com.bone.example.extension.medical.MedicalClaimResult;
import com.bone.example.extension.result.ValidationResult;
import com.bone.engine.extension.BizContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 医疗保险理赔服务测试类
 */
class MedicalClaimServiceTest {

    private MedicalClaimService medicalClaimService;
    private MedicalClaimExtPoint medicalClaimExtPoint;

    @BeforeEach
    void setUp() {
        // 创建mock对象
        medicalClaimExtPoint = Mockito.mock(MedicalClaimExtPoint.class);
        medicalClaimService = new MedicalClaimService();
        
        // 注入mock对象
        medicalClaimService.setMedicalClaimExtPoint(medicalClaimExtPoint);
    }

    /**
     * 测试成功的理赔处理
     */
    @Test
    void testProcessClaimSuccess() {
        // 准备测试数据
        MedicalClaimRequest request = new MedicalClaimRequest();
        request.setUserId("user123");
        request.setPolicyNo("policy456");
        request.setClaimType(MedicalClaimRequest.ClaimType.INPATIENT);
        request.setTotalAmount(new BigDecimal("5000.0"));
        
        // 模拟验证通过
        ValidationResult validationResult = ValidationResult.success();
        when(medicalClaimExtPoint.validateClaim(any(BizContext.class))).thenReturn(validationResult);
        
        // 配置mock行为
        MedicalClaimResult expectedResult = new MedicalClaimResult();
        expectedResult.setStatus(MedicalClaimResult.ClaimStatus.APPROVED);
        expectedResult.setApprovedAmount(new BigDecimal("4500.0"));
        when(medicalClaimExtPoint.processClaim(any(BizContext.class))).thenReturn(expectedResult);
        
        // 执行测试
        MedicalClaimResult result = medicalClaimService.processClaim(request);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(MedicalClaimResult.ClaimStatus.APPROVED, result.getStatus());
        assertEquals(new BigDecimal("4500.0"), result.getApprovedAmount());
    }

    /**
     * 测试理赔处理失败的情况
     */
    @Test
    void testProcessClaimFailure() {
        // 准备测试数据
        MedicalClaimRequest request = new MedicalClaimRequest();
        request.setUserId("user123");
        request.setPolicyNo("invalid-policy");
        request.setClaimType(MedicalClaimRequest.ClaimType.OUTPATIENT);
        
        // 模拟验证失败
        ValidationResult validationResult = ValidationResult.fail("INVALID_POLICY", "保单无效");
        when(medicalClaimExtPoint.validateClaim(any(BizContext.class))).thenReturn(validationResult);
        
        // 执行测试
        MedicalClaimResult result = medicalClaimService.processClaim(request);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(MedicalClaimResult.ClaimStatus.REJECTED, result.getStatus());
        assertEquals("保单无效", result.getRejectionReason());
    }
}