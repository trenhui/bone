package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 住院理赔处理扩展实现
 */
public class InpatientClaimExtension implements MedicalClaimExtPoint {
    
    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回成功结果
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回空结果
        return new MedicalClaimResult();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回支持的理赔类型
        return MedicalClaimRequest.ClaimType.INPATIENT;
    }
}