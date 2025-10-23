package com.bone.example.extension.medical;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 特殊疾病理赔处理扩展实现
 */
@Extension(
    name = "特殊疾病理赔实现",
    description = "处理特殊疾病类型医疗理赔的专用实现",
    tenantCode = "*",
    bizCode = "SPECIAL_DISEASE_CLAIM",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    description = "特殊疾病理赔专用实现，处理特定疾病类型的医疗理赔申请。",
    scenarios = "特殊疾病类型的医疗理赔场景",
    implementationDetails = "实现特殊疾病理赔的验证和处理逻辑，包含特定的理赔规则",
    performance = "测试实现，单次执行耗时<4ms",
    notes = "针对特殊疾病类型的专用理赔实现",
    author = "测试团队",
    createDate = "2024-01-01"
)
@Component
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