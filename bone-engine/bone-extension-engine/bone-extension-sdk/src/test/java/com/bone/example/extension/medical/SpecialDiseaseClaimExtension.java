package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;

/**
 * 特殊疾病理赔处理扩展实现
 */
@Extension(bizCode = "SPECIAL_DISEASE_CLAIM")
public class SpecialDiseaseClaimExtension implements MedicalClaimExtPoint {
    
    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        // 简化实现，直接返回成功
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        // 返回空的MedicalClaimResult对象
        return new MedicalClaimResult();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 返回null
        return null;
    }
}