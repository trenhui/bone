package com.bone.example.extension.medical;

import com.bone.example.extension.result.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 医疗保险理赔服务测试类
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
     */
    @Test
    void testBasicProcessClaim() {
        // 准备测试数据
        MedicalClaimRequest request = new MedicalClaimRequest();
        
        // 执行测试
        MedicalClaimResult result = medicalClaimService.processClaim(request);
        
        // 验证结果不为空
        assertNotNull(result);
    }

    /**
     * 测试验证理赔功能
     */
    @Test
    void testBasicValidateClaim() {
        // 准备测试数据
        MedicalClaimRequest request = new MedicalClaimRequest();
        
        // 执行测试
        ValidationResult result = medicalClaimService.validateClaim(request);
        
        // 验证结果不为空
        assertNotNull(result);
    }
}