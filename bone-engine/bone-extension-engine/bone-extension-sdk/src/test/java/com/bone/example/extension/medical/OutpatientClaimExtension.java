package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 门诊理赔扩展实现
 */
public class OutpatientClaimExtension implements MedicalClaimExtPoint {

    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，返回成功结果
        return ValidationResult.success();
    }

    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，返回空结果
        return new MedicalClaimResult();
    }

    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回支持的理赔类型
        return MedicalClaimRequest.ClaimType.OUTPATIENT;
    }
}